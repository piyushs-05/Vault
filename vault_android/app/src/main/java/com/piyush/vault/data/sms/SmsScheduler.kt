package com.piyush.vault.data.sms

import android.content.Context
import androidx.work.*
import com.piyush.vault.data.sms.worker.*
import com.piyush.vault.domain.model.SmsCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.util.concurrent.TimeUnit

interface SmsWorkScheduler {
    fun drain()
    fun history(token: String)
    fun maintenance()
    fun cancel()
}

class SmsScheduler @Inject constructor(@param:ApplicationContext private val context: Context) : SmsWorkScheduler {
    private val manager get() = WorkManager.getInstance(context)
    override fun drain() { manager.enqueueUniqueWork("sms-drain",ExistingWorkPolicy.APPEND_OR_REPLACE,OneTimeWorkRequestBuilder<SmsDrainWorker>().addTag(TAG).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()) }
    override fun history(token: String) {
        if (!SmsCapabilities.HISTORY_IMPORT_ENABLED) return
        manager.enqueueUniqueWork("sms-history",ExistingWorkPolicy.APPEND_OR_REPLACE,OneTimeWorkRequestBuilder<SmsHistoryWorker>().setInputData(workDataOf("scan_token" to token)).addTag(TAG).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    }
    override fun maintenance() { manager.enqueueUniquePeriodicWork("sms-cleanup",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<SmsCleanupWorker>(24,TimeUnit.HOURS).build()) }
    override fun cancel() { manager.cancelAllWorkByTag(TAG) }
    private companion object { const val TAG = "financial-sms" }
}
