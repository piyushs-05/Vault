package com.piyush.vault

import android.os.Bundle
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.piyush.vault.presentation.onboarding.VaultEntry
import com.piyush.vault.presentation.ui.theme.VaultTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var reviewSourceId by mutableStateOf<String?>(null)
    private fun readReviewIntent(intent: Intent) {
        reviewSourceId = if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) intent.getStringExtra("sms_source_id")?.takeIf { it.startsWith("source:sms:") && it.length <= 200 } else null
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readReviewIntent(intent)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readReviewIntent(intent)
        enableEdgeToEdge()
        setContent {
            VaultTheme {
                VaultEntry(reviewSourceId) {
                    intent.removeExtra("sms_source_id")
                    reviewSourceId = null
                }
            }
        }
    }
}
