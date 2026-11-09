package com.piyush.vault.data.sms

import android.Manifest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.piyush.vault.data.directory.BundledInstitutionDirectory
import com.piyush.vault.data.local.VaultDatabase
import com.piyush.vault.data.preferences.SmsPreferences
import com.piyush.vault.data.repository.LedgerStore
import com.piyush.vault.data.sms.parsing.GenericFinancialSmsParser
import com.piyush.vault.domain.model.*
import java.io.File
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SmsPipelineTest {
    private class Queue : SmsWorkScheduler {
        var drains = 0
        override fun drain() { drains++ }
        override fun history(token: String) {}
        override fun maintenance() {}
        override fun cancel() {}
    }
    @Test fun durableCaptureRecoversDeduplicatesRejectsAndOptOutClearsRawText() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context,VaultDatabase::class.java).build()
        val job = SupervisorJob()
        val file = File(context.cacheDir,"sms-${UUID.randomUUID()}.preferences_pb")
        val prefs = SmsPreferences(PreferenceDataStoreFactory.create(scope = CoroutineScope(job+Dispatchers.IO),produceFile = { file }))
        val queue = Queue()
        val clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"),ZoneOffset.UTC)
        val ledger = LedgerStore(db,clock)
        val parser = GenericFinancialSmsParser(BundledInstitutionDirectory(),clock)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_SMS,Manifest.permission.RECEIVE_SMS)
        try {
            ledger.seed()
            val first = SmsCoordinator(context,db,prefs,queue,parser,ledger,clock,com.piyush.vault.data.sms.notifications.SmsReviewNotifier(context))
            first.enable(history = false,live = true)
            val message = IncomingSms("VM-HDFCBK","A/c XX1234 debited INR 100 on 02/11/2026",clock.millis(),clock.millis())
            first.capture(message)
            assertEquals(1,db.smsDao().pending().size)
            // New coordinator simulates process restart between database staging and worker execution.
            val recovered = SmsCoordinator(context,db,prefs,queue,parser,ledger,clock,com.piyush.vault.data.sms.notifications.SmsReviewNotifier(context))
            recovered.recover(); recovered.drain()
            val candidate = recovered.observeCandidates().first().single()
            assertNull(db.smsDao().envelope(requireNotNull(candidate.source.smsEnvelopeId))?.rawBody)
            recovered.capture(message); recovered.drain()
            assertEquals(1,recovered.observeCandidates().first().size)
            recovered.reject(candidate.source.id)
            recovered.capture(message); recovered.drain()
            assertTrue(recovered.observeCandidates().first().isEmpty())
            recovered.capture(message.copy(body = message.body.replace("100","200")))
            assertEquals(1,db.smsDao().pending().size)
            recovered.disable()
            assertTrue(db.smsDao().pending().isEmpty())
            assertFalse(prefs.snapshot().enabled)
            recovered.capture(message.copy(body = message.body.replace("100","300")))
            assertTrue(db.smsDao().pending().isEmpty())
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            job.cancelAndJoin(); db.close(); file.delete()
        }
    }
}
