package com.piyush.vault.presentation.sms

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.piyush.vault.domain.model.SmsSettings

@Composable
fun SmsNotificationSettings(settings: SmsSettings,busy: Boolean,onEnabled: (Boolean) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(smsReviewNotificationsAllowed(context)) }
    var denied by remember { mutableStateOf(false) }
    DisposableEffect(owner,context) {
        val observer = LifecycleEventObserver { _,event -> if (event == Lifecycle.Event.ON_RESUME) allowed = smsReviewNotificationsAllowed(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = smsReviewNotificationsAllowed(context)
        denied = !granted
        if (granted) onEnabled(true)
    }
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Remind me to check",Modifier.weight(1f))
            Switch(checked = settings.notificationsEnabled,enabled = settings.liveEnabled && !busy,onCheckedChange = { enabled ->
                denied = false
                if (!enabled) onEnabled(false)
                else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else onEnabled(true)
            })
        }
        Text("We’ll let you know when a money alert is ready. Nothing is added until you say so.",style = MaterialTheme.typography.bodySmall)
        if (!settings.liveEnabled) Text("Turn on text alerts first to get reminders.",style = MaterialTheme.typography.bodySmall)
        if (denied || !allowed) {
            Text("Reminders are off in your phone settings. You can turn them on later.",style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }) { Text("Open phone settings") }
        }
    }
}

internal fun smsReviewNotificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    return Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
        .getNotificationChannel("sms_transaction_review")?.importance != NotificationManager.IMPORTANCE_NONE
}
