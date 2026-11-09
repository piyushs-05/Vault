package com.piyush.vault.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.piyush.vault.di.ApplicationScope
import com.piyush.vault.domain.model.IncomingSms
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

/** Durable staging only; no UI, network calls, or financial posting in the broadcast. */
@AndroidEntryPoint
class FinancialSmsReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: SmsCoordinator
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        if (parts.isEmpty()) return
        val message = IncomingSms(parts.first().originatingAddress.orEmpty(),parts.joinToString("") { it.messageBody.orEmpty() },parts.first().timestampMillis,System.currentTimeMillis())
        val pending = goAsync()
        scope.launch {
            try { withTimeout(8_000) { coordinator.capture(message) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { coordinator.failure("An SMS couldn't be captured. Scan history to recover it.") }
            finally { pending.finish() }
        }
    }
}
