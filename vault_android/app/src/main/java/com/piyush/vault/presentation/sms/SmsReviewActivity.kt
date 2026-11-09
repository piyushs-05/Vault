package com.piyush.vault.presentation.sms

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.vault.MainActivity
import com.piyush.vault.presentation.ui.theme.VaultTheme
import dagger.hilt.android.AndroidEntryPoint

/** Notification-only entry point; declared non-exported in the debug manifest. */
@AndroidEntryPoint
class SmsReviewActivity : ComponentActivity() {
    private val vm: SmsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        val sourceId = intent.getStringExtra(EXTRA_SOURCE)
        if (sourceId.isNullOrBlank() || sourceId.length > 200) { finish(); return }
        vm.begin(sourceId)
        setContent {
            VaultTheme {
                val form by vm.form.collectAsStateWithLifecycle()
                val review by vm.review.collectAsStateWithLifecycle()
                var pendingExit by rememberSaveable { mutableStateOf<String?>(null) }
                var ignore by rememberSaveable { mutableStateOf(false) }
                fun fullReview() {
                    startActivity(Intent(this@SmsReviewActivity,MainActivity::class.java)
                        .putExtra(EXTRA_SOURCE,sourceId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish()
                }
                fun leave(action: String) {
                    if (form.isSaving) return
                    if (review.dirty && review.ready) pendingExit = action
                    else if (action == "full") fullReview() else vm.later()
                }
                BackHandler { leave("later") }
                LaunchedEffect(vm) { vm.completed.collect { finish() } }
                Surface(Modifier.fillMaxSize(),color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(8.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { leave("later") },enabled = !form.isSaving) { Text("Later") }
                            TextButton(onClick = { leave("full") },enabled = !form.isSaving) { Text("Open full review") }
                            TextButton(onClick = { ignore = true },enabled = review.ready && !form.isSaving) { Text("Ignore") }
                        }
                        Box(Modifier.weight(1f)) {
                            SmsReviewScreen(form,vm,{ leave("later") },{ leave("full") })
                        }
                    }
                }
                if (pendingExit != null) AlertDialog(
                    onDismissRequest = { pendingExit = null },
                    title = { Text("Discard unsaved edits?") },
                    text = { Text(if (pendingExit == "full") "Full review reloads the captured alert. These unsaved edits won't transfer." else "The alert will remain in your review queue. These edits won't be saved.") },
                    confirmButton = { TextButton(onClick = {
                        val action = pendingExit; pendingExit = null
                        if (action == "full") fullReview() else vm.later()
                    }) { Text("Discard edits") } },
                    dismissButton = { TextButton(onClick = { pendingExit = null }) { Text("Keep editing") } },
                )
                if (ignore) AlertDialog(
                    onDismissRequest = { ignore = false },title = { Text("Ignore this alert?") },
                    text = { Text("No transaction will be saved. This alert will be removed from the review queue.") },
                    confirmButton = { TextButton(onClick = { ignore = false; vm.ignoreCurrent() }) { Text("Ignore alert") } },
                    dismissButton = { TextButton(onClick = { ignore = false }) { Text("Cancel") } },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        window.setLayout(minOf((metrics.widthPixels * 0.94f).toInt(),(560 * metrics.density).toInt()),(metrics.heightPixels * 0.88f).toInt())
    }

    companion object { const val EXTRA_SOURCE = "sms_source_id" }
}
