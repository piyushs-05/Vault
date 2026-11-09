package com.piyush.vault.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piyush.vault.domain.model.SmsSettings

@Composable
fun WelcomeSetupScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeading(
            kicker = "A LITTLE AT A TIME",
            title = "Small spends add up.",
            copy = "A coffee here, a ride there. Track each one and see where your money goes.",
        )
        CoinVaultScene()
        InfoCard(
            icon = "✓",
            title = "Your money details stay on this phone",
            body = "No sign-up or bank password needed.",
        )
        Text("You choose what to add.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AccountSetupScreen(accountCount: Int?, busy: Boolean, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeading(
            kicker = "ACCOUNT SETUP",
            title = "Add an account.",
            copy = "Add cash, a bank account, wallet, or card to see its balance. A nickname is all you need.",
        )
        SetupIllustration(SetupIllustrationKind.ACCOUNT, "Just a nickname")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Your accounts", style = MaterialTheme.typography.titleSmall)
                }
                if (accountCount == null) {
                    Text("Your accounts are still loading.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else if (accountCount == 0) {
                    Text("No accounts yet. You can still track purchases without one.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("$accountCount ${if (accountCount == 1) "account" else "accounts"} saved on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onAdd, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (accountCount != null && accountCount > 0) "Add another account" else "Add an account")
                }
            }
        }
        Text("You can add or change accounts later.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SmsSetupScreen(available: Boolean, enabled: Boolean, busy: Boolean, onRequest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeading(
            kicker = "TEXT ALERTS · YOUR CHOICE",
            title = "Let Vault spot money alerts.",
            copy = "Vault can find money alerts in texts on this phone. You check each one before it’s added to your spending.",
        )
        SetupIllustration(SetupIllustrationKind.MESSAGES, "You check first")
        if (!available) {
            InfoCard("✓", "You’re still all set", "You can add purchases yourself or import a statement.")
        } else if (enabled) {
            InfoCard("✓", "Text alerts are on", "You can review each one before it’s added.")
        } else {
            InfoCard("✉", "Your messages stay on this phone", "Vault only uses money alerts to help you keep track.")
            OutlinedButton(onClick = onRequest, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Setting up…" else "Set up text alerts")
            }
        }
        Text("Adding purchases yourself or importing a statement works too.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun NotificationSetupScreen(available: Boolean, settings: SmsSettings, busy: Boolean, onEnabled: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeading(
            kicker = "PHONE REMINDERS · YOUR CHOICE",
            title = "Want a reminder?",
            copy = "Vault can let you know when a money alert is ready for you to check. Nothing is added until you say so.",
        )
        SetupIllustration(SetupIllustrationKind.REMINDER, "You stay in control")
        if (available) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.piyush.vault.presentation.sms.SmsNotificationSettings(settings, busy, onEnabled)
                }
            }
        } else {
            InfoCard("✓", "Reminders aren’t available here", "You can still track spending by adding purchases yourself.")
        }
        Text("You can change this later.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SetupFinishScreen(state: OnboardingUiState, smsAvailable: Boolean, receiveGranted: Boolean, notificationAllowed: Boolean) {
    val alerts = when {
        !smsAvailable -> "Not available"
        !state.smsSettingsLoaded -> "Loading"
        state.sms.liveEnabled && receiveGranted -> "On"
        else -> "Off"
    }
    val reminders = when {
        !smsAvailable -> "Not available"
        !state.smsSettingsLoaded -> "Loading"
        state.sms.notificationsEnabled && state.sms.liveEnabled && receiveGranted && notificationAllowed -> "On"
        else -> "Off"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeading(
            kicker = "YOUR SETUP, YOUR CHOICE",
            title = "You’re ready to make sense of it.",
            copy = "Your choices are saved. You can change them whenever you like.",
        )
        FinishMascotScene()
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your setup", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text("READY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
                SummaryRow("✓", "Your money details", "Stay on this phone")
                SummaryRow("₹", "Accounts", state.accountCount?.let { if (it == 0) "Add later" else "$it added" } ?: "Add later")
                SummaryRow("✉", "Money alerts in texts", alerts)
                SummaryRow("•", "Phone reminders", reminders)
            }
        }
        InfoCard("✓", "You’re in control", "Add a purchase, import a statement, or set up an account whenever you’re ready.")
    }
}

@Composable
private fun ScreenHeading(kicker: String, title: String, copy: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(kicker, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(copy, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoCard(icon: String, title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(Modifier.size(34.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFFEAF3EE)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text(icon, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SummaryRow(icon: String, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(38.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(23.dp), shape = CircleShape, color = Color(0xFFEAF3EE)) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text(icon, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
