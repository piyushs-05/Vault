package com.piyush.vault.data.sms.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piyush.vault.data.sms.SmsCoordinator
import com.piyush.vault.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class SmsPromptActionReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: SmsCoordinator
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsReviewNotifier.ACTION_LATER) return
        val id = intent.getStringExtra(SmsReviewNotifier.EXTRA_SOURCE) ?: return
        val pending = goAsync()
        scope.launch {
            try { withTimeout(8_000) { coordinator.deferPrompt(id) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Source remains pending; a notification is never the ledger. */ }
            finally { pending.finish() }
        }
    }
}
