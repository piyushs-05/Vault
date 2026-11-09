package com.piyush.vault.data.repository

import androidx.room.withTransaction
import com.piyush.vault.data.local.VaultDatabase
import com.piyush.vault.data.local.entity.*
import com.piyush.vault.data.mapper.toDomain
import com.piyush.vault.data.mapper.toEntity
import com.piyush.vault.domain.ledger.LedgerPlanner
import com.piyush.vault.domain.model.*
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The only writer of financial aggregates. Every public mutation is an atomic Room transaction. */
@Singleton
class LedgerStore @Inject constructor(private val db: VaultDatabase, private val clock: Clock) {
    private val dao get() = db.ledgerDao()

    suspend fun seed() = db.withTransaction { ensureSystemAccounts("INR") }

    private suspend fun ensureSystemAccounts(currency: String) {
        val now = clock.millis()
        dao.seedAccounts(SystemAccountRole.entries.map { role ->
            Account(role.id(currency), when (role) {
                SystemAccountRole.LEGACY_UNASSIGNED -> "Unassigned history"
                SystemAccountRole.UNALLOCATED_CASH -> "Unallocated cash"
                else -> role.name.lowercase().replace('_', ' ')
            }, role.type, null, currency, role, origin = AccountOrigin.SYSTEM,
                createdAtEpochMillis = now, updatedAtEpochMillis = now).toEntity()
        })
    }

    suspend fun saveAccount(draft: AccountDraft) = db.withTransaction {
        require(draft.displayName.isNotBlank()) { "Give this account a name." }
        require(draft.lastFour.isNullOrBlank() || draft.lastFour.matches(Regex("[0-9]{4}"))) { "Enter only the last four digits, or leave them blank." }
        require((draft.openingBalanceMinor == null) == (draft.openingBalanceDate == null)) { "Opening balance and date must be set together." }
        draft.openingBalanceMinor?.let { require(it in -LedgerPlanner.MAX_AMOUNT_MINOR..LedgerPlanner.MAX_AMOUNT_MINOR) { "Opening balance is too large." } }
        val existing = draft.id?.let { requireNotNull(dao.account(it)) { "Account not found." }.toDomain() }
        require(existing?.systemRole == null) { "System accounts cannot be edited." }
        require(existing == null || existing.accountKind == draft.kind) { "Create another account to change its kind." }
        val now = clock.millis()
        val account = Account(existing?.id ?: UUID.randomUUID().toString(), draft.displayName.trim(),
            if (draft.kind == AccountKind.CREDIT_CARD) LedgerType.LIABILITY else LedgerType.ASSET,
            draft.kind, currencyCode = existing?.currencyCode ?: "INR", institutionLabel = draft.institutionLabel?.trim()?.takeIf { it.isNotEmpty() },
            lastFour = draft.lastFour?.takeIf { it.isNotBlank() }, isArchived = existing?.isArchived ?: false,
            openingBalanceMinor = draft.openingBalanceMinor, openingBalanceDate = draft.openingBalanceDate,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now, updatedAtEpochMillis = now)
        ensureSystemAccounts(account.currencyCode)
        dao.putAccount(account.toEntity())
        reanchor(setOf(account.id))
    }

    suspend fun archive(id: String, archived: Boolean) = db.withTransaction {
        require(dao.account(id)?.systemRole == null && dao.account(id) != null) { "Choose a user account." }
        dao.archive(id, archived, clock.millis())
    }

    suspend fun save(transaction: Transaction) = db.withTransaction { saveInternal(transaction); Unit }

    private suspend fun saveInternal(t: Transaction, smsSource: SourceRecordEntity? = null) {
        require(t.kind.hasCategory || t.categoryId == null) { "This movement does not use a category." }
        require(t.originalEventId == null || t.originalEventId != t.id) { "A transaction cannot link to itself." }
        require(t.description.isNotBlank()) { "Description cannot be blank." }
        require(t.amountMinor in 1..LedgerPlanner.MAX_AMOUNT_MINOR) { "Amount is outside the supported range." }
        ensureSystemAccounts(t.currencyCode)
        val existing = dao.event(t.id)
        require(existing?.systemKey == null) { "Use account setup to change an opening balance." }
        require(existing == null || existing.revision == t.revision) { "This transaction changed. Reopen it before saving." }
        val previousEntries = dao.entries(t.id)
        val previous = db.transactionDao().getById(t.id)?.toDomain()
        val related = dao.related(t.id)
        if (existing != null && related.any { it.kind == EventKind.REVERSAL }) require(previous?.accountId == t.accountId) { "Handle the linked reversal before changing this account." }
        if (existing != null && related.isNotEmpty()) {
            require(previous != null && previous.amountMinor == t.amountMinor && previous.kind == t.kind &&
                previous.destinationAccountId == t.destinationAccountId &&
                previous.categoryId == t.categoryId && previous.occurredDate == t.occurredDate) {
                "Handle linked refunds or reversals before changing this transaction's money fields."
            }
        }
        val account = requireNotNull(dao.account(t.accountId ?: SystemAccountRole.LEGACY_UNASSIGNED.id(t.currencyCode))) { "Account no longer exists." }.toDomain()
        require(!account.isArchived || previous?.accountId == account.id) { "Choose an active account." }
        require(account.isUserAccount || account.systemRole == SystemAccountRole.LEGACY_UNASSIGNED) { "This account cannot be used here." }
        val destination = t.destinationAccountId?.let { requireNotNull(dao.account(it)) { "Destination account not found." }.toDomain() }
        require(destination == null || !destination.isArchived || previous?.destinationAccountId == destination.id) { "Choose an active destination." }
        if (t.kind == EventKind.TRANSFER) require(account.isUserAccount) { "Choose a source account for the transfer." }
        if (t.categoryId != null && t.kind != EventKind.REFUND) {
            val cat = requireNotNull(db.categoryDao().getById(t.categoryId)) { "Category no longer exists." }
            require(!cat.isArchived || previous?.categoryId == cat.id) { "Choose an active category." }
            val required = if (t.kind == EventKind.INCOME) CategoryApplicability.INCOME else CategoryApplicability.EXPENSE
            require(cat.appliesTo == CategoryApplicability.BOTH || cat.appliesTo == required) { "Category does not apply to this movement." }
        }
        var categoryId = t.categoryId
        val entries = if (t.kind == EventKind.REVERSAL) {
            val original = requireNotNull(t.originalEventId?.let { dao.event(it) }) { "Choose the original transaction." }
            require(original.kind !in listOf(EventKind.OPENING_BALANCE, EventKind.REVERSAL)) { "This event cannot be reversed here." }
            require(original.currencyCode == t.currencyCode && t.occurredDate >= original.occurredDate) { "Check reversal currency/date." }
            require(dao.related(original.id).none { it.id != t.id }) { "Resolve existing refunds/reversals first." }
            val originalEntries = dao.entries(original.id)
            require(kotlin.math.abs(originalEntries.first().amountMinor) == t.amountMinor) { "A full reversal must equal the original amount." }
            originalEntries.map { it.copy(id = "${t.id}:${it.position}", eventId = t.id, amountMinor = -it.amountMinor) }
        } else {
            if (t.kind == EventKind.REFUND) {
                val original = requireNotNull(t.originalEventId?.let { db.transactionDao().getById(it) }) { "Choose the expense being refunded." }
                require(original.kind == EventKind.EXPENSE && original.currencyCode == t.currencyCode) { "Refunds must link to an expense in the same currency." }
                require(t.occurredDate >= original.occurredDate) { "A refund cannot precede its expense." }
                require(dao.related(original.id).none { it.kind == EventKind.REVERSAL }) { "This expense has already been reversed." }
                require(Math.addExact(dao.refunded(original.id,t.id),t.amountMinor) <= original.amountMinor) { "Refunds exceed the original expense." }
                categoryId = original.categoryId
            } else require(t.originalEventId == null) { "Only refunds/reversals link to an original event." }
            LedgerPlanner.entries(t.copy(categoryId = categoryId), account, destination).map { it.toEntity() }
        }
        LedgerPlanner.validate(entries.map { it.toDomain() })
        val now = clock.millis()
        dao.putEvent(FinancialEventEntity(t.id,t.kind,t.currencyCode,t.description.trim(),t.userNote?.trim()?.takeIf { it.isNotEmpty() },t.occurredDate,t.occurredTime,
            existing?.dateBasis ?: "USER_CONFIRMED",t.originalEventId,null,(existing?.revision ?: -1L)+1L,existing?.createdAtEpochMillis ?: t.createdAtEpochMillis,now))
        dao.removeEntries(t.id)
        dao.insertEntries(entries)
        if (smsSource != null) {
            dao.putSources(listOf(smsSource.copy(eventId = t.id,state = "ATTACHED",matchedAccountId = t.accountId)))
        } else if (dao.sources(t.id).isEmpty()) {
            dao.putSources(listOf(SourceRecordEntity("source:${t.id}",t.source,"event:${t.id}",t.id,"ATTACHED",t.importBatchId,null,t.description,t.createdAtEpochMillis)))
        }
        reanchor((previousEntries + entries).map { it.accountId }.toSet())
    }

    /** Claim the source and post the entire aggregate in one transaction. Repeated taps cannot post twice. */
    suspend fun acceptSms(sourceId: String, draft: TransactionDraft) = db.withTransaction {
        val source = pendingSms(sourceId)
        require(draft.id == null && draft.accountId != null) { "Choose an account for this alert." }
        require(draft.currencyCode == source.observedCurrency) { "Check the alert currency." }
        require(dao.account(draft.accountId)?.systemRole == null && dao.account(draft.accountId) != null) { "Choose a user account." }
        val now = clock.millis()
        saveInternal(Transaction(UUID.randomUUID().toString(),draft.amountMinor,draft.currencyCode,draft.direction,
            draft.description,draft.categoryId,draft.occurredDate,draft.occurredTime,now,now,TransactionSource.SMS,
            kind = draft.kind,accountId = draft.accountId,destinationAccountId = draft.destinationAccountId,
            userNote = draft.userNote,originalEventId = draft.originalEventId),source)
    }

    /** An explicit duplicate/second-side confirmation links provenance without posting more money. */
    suspend fun attachSms(sourceId: String, eventId: String) = db.withTransaction {
        val source = pendingSms(sourceId)
        val event = requireNotNull(dao.event(eventId)) { "Transaction no longer exists." }
        require(event.kind != EventKind.OPENING_BALANCE && event.currencyCode == source.observedCurrency) { "Currencies do not match." }
        val account = requireNotNull(source.matchedAccountId) { "Confirm the alert account first." }
        val amount = requireNotNull(source.observedAmountMinor) { "This alert has no unambiguous amount; create a corrected transaction instead." }
        val direction = requireNotNull(source.observedDirection) { "Check the alert direction." }
        val signed = if (direction == TransactionDirection.EXPENSE) -amount else amount
        require(dao.entries(eventId).any { it.accountId == account && it.amountMinor == signed }) { "The transaction does not match this account, amount and direction." }
        dao.putSources(listOf(source.copy(eventId = eventId,state = "ATTACHED")))
    }

    /** Explicitly reconcile two already-recorded unknown legs; never infer a transfer from amount alone. */
    suspend fun mergeUnknownTransfer(outgoingId: String,incomingId: String) = db.withTransaction {
        require(outgoingId != incomingId) { "Choose two different movements." }
        val outgoing = requireNotNull(db.transactionDao().getById(outgoingId)) { "Outgoing movement not found." }.toDomain()
        val incoming = requireNotNull(db.transactionDao().getById(incomingId)) { "Incoming movement not found." }.toDomain()
        require(outgoing.kind == EventKind.UNKNOWN && incoming.kind == EventKind.UNKNOWN) { "Only unknown movements can be reconciled here." }
        require(outgoing.direction == TransactionDirection.EXPENSE && incoming.direction == TransactionDirection.INCOME) { "Choose an outgoing and an incoming movement." }
        require(outgoing.amountMinor == incoming.amountMinor && outgoing.currencyCode == incoming.currencyCode) { "Amounts and currencies must match exactly. Record fees separately." }
        require(outgoing.accountId != null && incoming.accountId != null && outgoing.accountId != incoming.accountId) { "Choose different user accounts." }
        require(kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(outgoing.occurredDate,incoming.occurredDate)) <= 3) { "These movements are more than three days apart. Review them manually." }
        require(dao.related(outgoing.id).isEmpty() && dao.related(incoming.id).isEmpty()) { "Resolve related adjustments first." }
        val incomingSources = dao.sources(incoming.id)
        saveInternal(outgoing.copy(kind = EventKind.TRANSFER,destinationAccountId = incoming.accountId,categoryId = null,
            userNote = listOfNotNull(outgoing.userNote,incoming.userNote?.let { "Receiving note: $it" }).joinToString("\n").takeIf { it.isNotEmpty() }))
        dao.putSources(incomingSources.map { it.copy(eventId = outgoing.id,state = "ATTACHED") })
        dao.removeEvent(incoming.id)
        reanchor(setOf(outgoing.accountId,incoming.accountId))
    }

    private suspend fun pendingSms(id: String): SourceRecordEntity {
        val source = requireNotNull(dao.source(id)) { "Alert not found." }
        require(source.sourceType == TransactionSource.SMS && source.eventId == null && source.state in listOf("NEEDS_ACCOUNT","NEEDS_REVIEW")) { "Alert was already handled." }
        return source
    }

    suspend fun importBatch(batch: ImportBatch, transactions: List<Transaction>,accounts: List<Account>,categories: List<Category>) = db.withTransaction {
        categories.forEach { category ->
            require(category.name.isNotBlank() && !category.isSystem && !category.isArchived) { "Invalid imported category." }
            require(db.categoryDao().getById(category.id) == null) { "An imported category ID is already in use." }
            db.categoryDao().upsert(category.toEntity())
        }
        accounts.forEach { account ->
            val existing = dao.account(account.id)
            require(account.accountKind != null) { "Imported accounts need a supported kind." }
            require(account.systemRole == null && !account.id.startsWith("system:") && account.displayName.isNotBlank()) { "Invalid imported account." }
            require(account.lastFour == null || account.lastFour.matches(Regex("[0-9]{4}"))) { "Invalid account suffix." }
            require((account.openingBalanceMinor == null) == (account.openingBalanceDate == null)) { "Incomplete opening metadata." }
            require(account.ledgerType == if (account.accountKind == AccountKind.CREDIT_CARD) LedgerType.LIABILITY else LedgerType.ASSET) { "Account kind and ledger type disagree." }
            if (existing == null) { ensureSystemAccounts(account.currencyCode); dao.putAccount(account.copy(createdAtEpochMillis = clock.millis(),updatedAtEpochMillis = clock.millis()).toEntity()) }
            else require(existing.systemRole == null && existing.currencyCode == account.currencyCode && existing.accountKind == account.accountKind) { "Imported account ID conflicts with an existing account." }
        }
        db.importBatchDao().insert(batch.toEntity())
        transactions.forEach { t -> require(dao.event(t.id) == null) { "This import was already committed." }; saveInternal(t) }
        reanchor(accounts.map { it.id }.toSet())
    }

    suspend fun delete(id: String): Transaction? = db.withTransaction {
        val projection = db.transactionDao().getById(id)?.toDomain() ?: return@withTransaction null
        require(dao.related(id).isEmpty()) { "Remove linked refunds/reversals first." }
        val event = requireNotNull(dao.event(id))
        require(event.systemKey == null) { "Opening balances are managed in account setup." }
        val entries = dao.entries(id)
        val snapshot = LedgerSnapshot(event.toDomain(),entries.map { it.toDomain() },dao.sources(id).map { it.toDomain() })
        dao.suppressSources(id)
        dao.removeEvent(id)
        reanchor(entries.map { it.accountId }.toSet())
        projection.copy(deletedSnapshot = snapshot)
    }

    suspend fun restore(t: Transaction) = db.withTransaction {
        val snapshot = requireNotNull(t.deletedSnapshot) { "No deletion snapshot is available." }
        require(dao.event(t.id) == null) { "This event already exists." }
        snapshot.event.originalEventId?.let { require(dao.event(it) != null) { "The original transaction no longer exists." } }
        val entries = snapshot.entries.map { entry ->
            require(dao.account(entry.accountId) != null) { "An account no longer exists." }
            val category = entry.categoryId?.let { db.categoryDao().getById(it) }
            entry.copy(categoryId = category?.id)
        }
        LedgerPlanner.validate(entries)
        if (snapshot.event.kind == EventKind.REFUND) {
            val original = requireNotNull(snapshot.event.originalEventId?.let { db.transactionDao().getById(it) }) { "Original expense no longer exists." }
            require(dao.related(original.id).none { it.kind == EventKind.REVERSAL }) { "The original was reversed." }
            require(Math.addExact(dao.refunded(original.id,t.id),t.amountMinor) <= original.amountMinor) { "Later refunds leave insufficient refundable amount." }
        }
        if (snapshot.event.kind == EventKind.REVERSAL) require(dao.related(requireNotNull(snapshot.event.originalEventId)).isEmpty()) { "The original now has another adjustment." }
        snapshot.sources.forEach { source ->
            val current = dao.source(source.id)
            require(current == null || (current.eventId == null && current.state == "SUPPRESSED")) { "The source was changed after deletion." }
        }
        dao.putEvent(snapshot.event.copy(revision = snapshot.event.revision+1,updatedAtEpochMillis = clock.millis()).toEntity())
        dao.insertEntries(entries.map { it.toEntity() })
        dao.putSources(snapshot.sources.map { it.copy(state = "ATTACHED").toEntity() })
        reanchor(entries.map { it.accountId }.toSet())
    }

    suspend fun assign(ids: List<String>, accountId: String) = db.withTransaction {
        val account = requireNotNull(dao.account(accountId)) { "Account not found." }
        require(account.systemRole == null && !account.isArchived) { "Choose an active account." }
        ids.distinct().forEach { id ->
            val t = requireNotNull(db.transactionDao().getById(id)) { "Transaction not found." }.toDomain()
            require(t.accountId == null) { "Only unassigned history can be bulk assigned." }
            require(t.kind != EventKind.REVERSAL) { "Assign the original before recreating its linked reversal." }
            saveInternal(t.copy(accountId = accountId,categoryId = if (t.kind.hasCategory) t.categoryId else null))
        }
    }

    /** Keep the user's dated balance assertion stable when earlier movements change. */
    private suspend fun reanchor(accountIds: Set<String>) {
        accountIds.forEach { accountId ->
            val account = dao.account(accountId) ?: return@forEach
            if (account.systemRole != null) return@forEach
            val id = "opening:$accountId"
            val previous = dao.event(id)
            if (previous != null) dao.removeEvent(id)
            val anchor = account.openingBalanceMinor ?: return@forEach
            val date = requireNotNull(account.openingBalanceDate)
            val adjustment = Math.subtractExact(anchor, dao.sumBefore(accountId,date))
            if (adjustment == 0L) return@forEach
            val now = clock.millis()
            val entries = listOf(LedgerEntry("$id:0",id,accountId,0,adjustment),LedgerEntry("$id:1",id,SystemAccountRole.OPENING_EQUITY.id(account.currencyCode),1,-adjustment))
            LedgerPlanner.validate(entries)
            dao.putEvent(FinancialEventEntity(id,EventKind.OPENING_BALANCE,account.currencyCode,"Opening balance adjustment",null,date,null,"USER_ANCHOR",null,id,(previous?.revision ?: -1)+1,previous?.createdAtEpochMillis ?: now,now))
            dao.insertEntries(entries.map { it.toEntity() })
        }
    }
}
