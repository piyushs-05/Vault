package com.piyush.vault.data.sms.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.piyush.vault.R
import com.piyush.vault.domain.model.SourceRecord
import com.piyush.vault.domain.model.TransactionDirection
import com.piyush.vault.domain.model.minorUnitsText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Android delivery only. It never posts a financial event or starts an Activity itself. */
class SmsReviewNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val manager get() = NotificationManagerCompat.from(context)

    fun available(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!manager.areNotificationsEnabled()) return false
        createChannel()
        return Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.sms_review_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.sms_review_channel_description)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
            )
        }
    }

    fun post(source: SourceRecord): Boolean {
        if (!available()) return false
        val observation = source.observation ?: return false
        val review = PendingIntent.getActivity(context, 0,
            Intent().setClassName(context, "com.piyush.vault.presentation.sms.SmsReviewActivity")
                .setData(identity(source.id, "review")).putExtra(EXTRA_SOURCE, source.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val later = PendingIntent.getBroadcast(context, 0,
            Intent(context, SmsPromptActionReceiver::class.java).setAction(ACTION_LATER)
                .setData(identity(source.id, "later")).putExtra(EXTRA_SOURCE, source.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val direction = when (observation.direction) {
            TransactionDirection.EXPENSE -> context.getString(R.string.sms_money_out)
            TransactionDirection.INCOME -> context.getString(R.string.sms_money_in)
            null -> context.getString(R.string.sms_check_direction)
        }
        val amount = observation.amountMinor?.let { "${minorUnitsText(it)} ${observation.currencyCode}" }
            ?: context.getString(R.string.sms_check_amount)
        val account = observation.lastFour?.let { context.getString(R.string.sms_account_ending, it) }
            ?: context.getString(R.string.sms_select_account)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_transaction_review).setContentTitle(context.getString(R.string.sms_review_private)).build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_transaction_review)
            .setContentTitle(context.getString(R.string.sms_review_title))
            .setContentText("$amount · $direction · $account")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$amount · $direction\n$account\n${context.getString(R.string.sms_confirm_details)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
            .setContentIntent(review).setDeleteIntent(later).setAutoCancel(true).setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.sms_review_action), review)
            .addAction(0, context.getString(R.string.sms_later_action), later).build()
        return try { manager.notify(source.id, NOTIFICATION_ID, notification); true }
        catch (_: SecurityException) { false }
    }

    fun cancel(sourceId: String) {
        try { manager.cancel(sourceId, NOTIFICATION_ID) }
        catch (_: SecurityException) { /* Revoked notification access cannot roll back a ledger save. */ }
    }

    companion object {
        const val EXTRA_SOURCE = "sms_source_id"
        const val ACTION_LATER = "com.piyush.vault.SMS_REVIEW_LATER"
        const val CHANNEL = "sms_transaction_review"
        private const val NOTIFICATION_ID = 4101
        private fun identity(id: String, action: String) = Uri.Builder().scheme("vault-internal")
            .authority("sms-review").appendPath(action).appendPath(id).build()
    }
}
