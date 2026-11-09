package com.piyush.vault.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.piyush.vault.data.local.VaultDatabase
import com.piyush.vault.data.local.entity.*
import com.piyush.vault.data.preferences.SmsPreferences
import com.piyush.vault.data.repository.LedgerStore
import com.piyush.vault.data.sms.parsing.GenericFinancialSmsParser
import com.piyush.vault.data.sms.notifications.SmsReviewNotifier
import com.piyush.vault.domain.model.*
import com.piyush.vault.domain.repository.SmsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.time.*
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

/** Serializes capture, review writes and opt-out; the database is still the durable queue. */
@Singleton
class SmsCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: VaultDatabase, private val prefs: SmsPreferences, private val scheduler: SmsWorkScheduler,
    private val parser: FinancialSmsParser, private val ledger: LedgerStore, private val clock: Clock,
    private val notifier: SmsReviewNotifier,
) : SmsRepository {
    private val mutex = Mutex()
    override val settings get() = prefs.settings
    @Suppress("DEPRECATION")
    override val isAvailable: Boolean get() = context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().contains(Manifest.permission.READ_SMS)
    private fun has(permission: String) = ContextCompat.checkSelfPermission(context,permission) == PackageManager.PERMISSION_GRANTED
    override fun observeCandidates() = db.smsDao().observeCandidates().map { rows -> rows.mapNotNull { row -> row.toDomain().let { s -> s.observation?.let { SmsCandidate(s,it,s.matchedAccountId) } } } }
    override fun observeSource(id: String): Flow<SourceRecord?> = db.smsDao().observeSource(id).map { it?.toDomain() }
    override suspend fun setNotificationsEnabled(enabled: Boolean) = mutex.withLock {
        require(!enabled || (isAvailable && prefs.snapshot().liveEnabled && has(Manifest.permission.RECEIVE_SMS))) { "Enable incoming SMS capture first." }
        require(!enabled || notifier.available()) { "Allow transaction review notifications in Android settings first." }
        prefs.notifications(enabled)
        if (!enabled) cancelPrompts()
    }
    override suspend fun openPrompt(sourceId: String) = mutex.withLock { updatePrompt(sourceId,"OPENED") }
    override suspend fun deferPrompt(sourceId: String) = mutex.withLock { updatePrompt(sourceId,"DEFERRED") }
    private suspend fun updatePrompt(sourceId: String,state: String) {
        notifier.cancel(sourceId)
        val source = db.ledgerDao().source(sourceId) ?: return
        if (source.sourceType != TransactionSource.SMS) return
        source.smsEnvelopeId?.let { db.smsDao().promptState(it,if (source.state in listOf("NEEDS_ACCOUNT","NEEDS_REVIEW")) state else "RESOLVED") }
    }
    private suspend fun resolvePromptAfterCommit(sourceId: String) {
        try { updatePrompt(sourceId,"RESOLVED") }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* Financial commit succeeded; recovery reconciles delivery metadata. */ }
    }
    private suspend fun cancelPrompts() {
        db.smsDao().prompts().forEach { envelope ->
            db.smsDao().promptState(envelope.id,"DEFERRED")
            notifier.cancel("source:${envelope.id}")
        }
    }
    /** Called after source commits. Posting and Room cannot be one atomic transaction. */
    private suspend fun dispatchPrompts() = mutex.withLock {
        val settings = prefs.snapshot()
        val permitted = isAvailable && settings.liveEnabled && settings.notificationsEnabled && has(Manifest.permission.RECEIVE_SMS)
        for (envelope in db.smsDao().prompts()) {
            val id = "source:${envelope.id}"
            val source = db.ledgerDao().source(id)?.toDomain()
            when {
                source == null || source.eventId != null || source.state !in listOf("NEEDS_ACCOUNT","NEEDS_REVIEW") -> {
                    db.smsDao().promptState(envelope.id,"RESOLVED"); notifier.cancel(id)
                }
                !permitted || envelope.consentGeneration != settings.generation || envelope.captureOrigin != "LIVE" || !notifier.available() -> {
                    db.smsDao().promptState(envelope.id,"BLOCKED"); notifier.cancel(id)
                }
                envelope.promptState == "READY" -> {
                    val age = clock.millis() - envelope.receivedAtEpochMillis
                    if (age !in 0..Duration.ofMinutes(10).toMillis()) db.smsDao().promptState(envelope.id,"DEFERRED")
                    else db.smsDao().promptState(envelope.id,if (notifier.post(source)) "POSTED" else "BLOCKED")
                }
            }
        }
    }
    override suspend fun enable(history: Boolean,live: Boolean) = mutex.withLock {
        require(!history || SmsCapabilities.HISTORY_IMPORT_ENABLED) { "Historical SMS import is disabled for now." }
        require(isAvailable && (history || live)) { "Choose a supported SMS feature." }
        require(!history || has(Manifest.permission.READ_SMS)) { "SMS history access was not granted." }
        require(!live || has(Manifest.permission.RECEIVE_SMS)) { "Incoming SMS access was not granted." }
        prefs.scopes(history,live,clock.millis())
        scheduler.maintenance(); scheduler.drain()
    }
    override suspend fun disable() = mutex.withLock {
        prefs.scopes(false,false,clock.millis())
        scheduler.cancel()
        prefs.notifications(false)
        cancelPrompts()
        db.withTransaction { db.smsDao().clearPending(); db.smsDao().clearCandidates() }
        prefs.status("SMS disabled; imported transactions are retained")
    }
    suspend fun capture(message: IncomingSms) = mutex.withLock {
        val settings = prefs.snapshot()
        if (!settings.liveEnabled || !has(Manifest.permission.RECEIVE_SMS) || !isAvailable) return@withLock
        stage(message,settings.generation,"LIVE")
        scheduler.drain()
    }
    private suspend fun stage(message: IncomingSms,generation: Long,origin: String) {
        if (!parser.isPotentialAlert(message.sender,message.body)) return
        val normalized = message.sender.trim().uppercase(Locale.ROOT)+"\n"+message.body.trim().replace(Regex("\\s+")," ")+"\n"+(message.sentAtMillis.takeIf { it>0 } ?: message.receivedAtMillis)
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        db.smsDao().stage(SmsEnvelopeEntity("sms:$hash",hash,message.sender,message.body,message.sentAtMillis,message.receivedAtMillis,message.providerId,
            generation,"PENDING",0,null,clock.millis()+Duration.ofDays(7).toMillis(),captureOrigin = origin))
    }
    suspend fun drain() {
        while (true) {
            val processed = mutex.withLock {
                val s = prefs.snapshot()
                db.smsDao().expire(clock.millis())
                if (!s.enabled || !isAvailable) return@withLock false
                val batch = db.smsDao().pending()
                if (batch.isEmpty()) return@withLock false
                batch.forEach { envelope ->
                    val allowed = (s.historyEnabled && has(Manifest.permission.READ_SMS)) || (s.liveEnabled && has(Manifest.permission.RECEIVE_SMS))
                    if (!allowed || envelope.consentGeneration != s.generation) { db.smsDao().finish(envelope.id,"DISABLED"); return@forEach }
                    db.withTransaction {
                        db.smsDao().attempted(envelope.id)
                        val parsed = parser.parse(IncomingSms(envelope.sender,envelope.rawBody.orEmpty(),envelope.sentAtEpochMillis,envelope.receivedAtEpochMillis,envelope.providerRowId))
                        if (parsed == null) db.smsDao().finish(envelope.id,"IGNORED")
                        else {
                            val accounts = db.ledgerDao().accounts().filter { it.systemRole == null && !it.isArchived && it.currencyCode == parsed.currencyCode && parsed.lastFour != null && it.lastFour == parsed.lastFour && parsed.institution != null && it.institutionLabel.equals(parsed.institution,ignoreCase = true) }
                            val matched = accounts.singleOrNull()?.id
                            val source = SourceRecord("source:${envelope.id}",TransactionSource.SMS,envelope.fingerprint,null,
                                if (matched == null) "NEEDS_ACCOUNT" else "NEEDS_REVIEW",sourceDescription = parsed.description,
                                observedAtEpochMillis = envelope.receivedAtEpochMillis,smsEnvelopeId = envelope.id,matchedAccountId = matched,
                                observation = parsed,parserId = GenericFinancialSmsParser.PARSER_ID,parserVersion = GenericFinancialSmsParser.PARSER_VERSION)
                            if (db.ledgerDao().source(source.id) == null) {
                                db.ledgerDao().putSources(listOf(source.toEntity()))
                                if (envelope.captureOrigin == "LIVE" && s.liveEnabled && s.notificationsEnabled) db.smsDao().promptState(envelope.id,"READY")
                            }
                            db.smsDao().finish(envelope.id,"PARSED")
                        }
                    }
                }
                true
            }
            if (!processed) { dispatchPrompts(); return }
            yield()
        }
    }
    override suspend fun scanHistory(start: LocalDate,end: LocalDate) = mutex.withLock {
        require(SmsCapabilities.HISTORY_IMPORT_ENABLED) { "Historical SMS import is disabled for now." }
        require(start <= end && java.time.temporal.ChronoUnit.DAYS.between(start,end) <= 366) { "Choose a date range of at most one year." }
        val s = prefs.snapshot()
        require(isAvailable && s.historyEnabled && has(Manifest.permission.READ_SMS)) { "Enable historical SMS access first." }
        val token = UUID.randomUUID().toString()
        prefs.newScan(token,start.atStartOfDay(clock.zone).toInstant().toEpochMilli(),end.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli())
        scheduler.history(token)
    }
    suspend fun readHistory(token: String) {
        // Also stops workers queued before this product gate was introduced, before inbox access.
        if (!SmsCapabilities.HISTORY_IMPORT_ENABLED) return
        while (true) {
            val more = mutex.withLock {
                val s = prefs.snapshot()
                val scan = prefs.scan() ?: return@withLock false
                if (scan.token != token || !s.historyEnabled || !has(Manifest.permission.READ_SMS) || !isAvailable) return@withLock false
                var count = 0; var lastDate = scan.cursorDate; var lastId = scan.cursorId
                val columns = arrayOf(Telephony.Sms._ID,Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE,Telephony.Sms.DATE_SENT)
                val selection = "${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.DATE}>=? AND ${Telephony.Sms.DATE}<? AND (${Telephony.Sms.DATE}>? OR (${Telephony.Sms.DATE}=? AND ${Telephony.Sms._ID}>?))"
                val args = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),scan.start.toString(),scan.end.toString(),scan.cursorDate.toString(),scan.cursorDate.toString(),scan.cursorId.toString())
                // CursorWindow bounds memory. Stop after a page rather than materializing an inbox list.
                context.contentResolver.query(Telephony.Sms.CONTENT_URI,columns,selection,args,"${Telephony.Sms.DATE} ASC,${Telephony.Sms._ID} ASC")?.use { cursor ->
                    while (count < 100 && cursor.moveToNext()) {
                        val id = cursor.getLong(0); val received = cursor.getLong(3)
                        stage(IncomingSms(cursor.getString(1).orEmpty(),cursor.getString(2).orEmpty(),cursor.getLong(4),received,id.toString()),s.generation,"HISTORY")
                        lastId = id; lastDate = received; count++
                    }
                } ?: throw IllegalStateException("SMS provider unavailable")
                prefs.checkpoint(token,lastDate,lastId,scan.count+count)
                scheduler.drain()
                if (count < 100) prefs.complete(token)
                count == 100
            }
            if (!more) return
            yield()
        }
    }
    override suspend fun assignAccount(sourceId: String,accountId: String) = mutex.withLock {
        db.withTransaction {
            val source = requireNotNull(db.ledgerDao().source(sourceId)) { "Alert not found." }
            require(source.state in listOf("NEEDS_ACCOUNT","NEEDS_REVIEW")) { "Alert was already handled." }
            val account = requireNotNull(db.ledgerDao().account(accountId)) { "Account not found." }
            require(account.systemRole == null && !account.isArchived && account.currencyCode == source.observedCurrency) { "Choose an active account in the same currency." }
            db.ledgerDao().putSources(listOf(source.copy(matchedAccountId = accountId,state = "NEEDS_REVIEW")))
        }
    }
    override suspend fun confirm(sourceId: String,draft: TransactionDraft) = mutex.withLock {
        require(prefs.snapshot().enabled) { "SMS review was disabled; enter the transaction manually." }
        ledger.acceptSms(sourceId,draft)
        resolvePromptAfterCommit(sourceId)
    }
    override suspend fun attach(sourceId: String,eventId: String) = mutex.withLock { ledger.attachSms(sourceId,eventId); resolvePromptAfterCommit(sourceId) }
    override suspend fun reject(sourceId: String) = mutex.withLock {
        db.withTransaction {
        val source = requireNotNull(db.ledgerDao().source(sourceId))
        require(source.eventId == null) { "This alert is already attached." }
        db.ledgerDao().putSources(listOf(source.copy(state = "REJECTED",sourceDescription = "",detectedInstitution = null,maskedLastFour = null,externalReference = null,observedAmountMinor = null)))
        source.smsEnvelopeId?.let { db.smsDao().finish(it,"REJECTED") }; Unit
        }
        resolvePromptAfterCommit(sourceId)
    }
    suspend fun cleanup() {
        mutex.withLock { db.smsDao().expire(clock.millis()) }
        dispatchPrompts()
    }
    override suspend fun recover() {
        mutex.withLock {
            db.smsDao().expire(clock.millis())
            if (isAvailable && prefs.snapshot().enabled) {
                scheduler.maintenance(); scheduler.drain()
                if (SmsCapabilities.HISTORY_IMPORT_ENABLED) prefs.scan()?.let { scheduler.history(it.token) }
            }
        }
        dispatchPrompts()
    }
    suspend fun failure(message: String) { prefs.status(message) }
}
