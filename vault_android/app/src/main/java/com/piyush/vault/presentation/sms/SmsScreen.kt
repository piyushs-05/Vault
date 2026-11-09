package com.piyush.vault.presentation.sms

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.piyush.vault.domain.model.*
import com.piyush.vault.presentation.transactions.TransactionFormScreen
import com.piyush.vault.presentation.transactions.TransactionFormUiState
import com.piyush.vault.presentation.ui.components.ChoicePicker
import com.piyush.vault.presentation.ui.components.ScreenHeader
import com.piyush.vault.presentation.ui.theme.VaultCardColors
import java.time.LocalDate

@Composable
fun SmsScreen(state: SmsUiState,vm: SmsViewModel,onBack: ()->Unit,onReview: (String)->Unit,onAddAccount: ()->Unit) {
    var history by rememberSaveable { mutableStateOf(false) }
    val historyRequested = SmsCapabilities.HISTORY_IMPORT_ENABLED && history
    var live by rememberSaveable { mutableStateOf(true) }
    var pair by remember { mutableStateOf(false) }
    var outgoing by rememberSaveable { mutableStateOf<String?>(null) }
    var incoming by rememberSaveable { mutableStateOf<String?>(null) }
    val form by vm.form.collectAsState()
    var disclose by remember { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(30).toString()) }
    var end by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val readGranted = !historyRequested || ContextCompat.checkSelfPermission(context,Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val liveGranted = !live || ContextCompat.checkSelfPermission(context,Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (readGranted && liveGranted) vm.enable(historyRequested,live) else vm.denied()
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)) { padding -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 8.dp),verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ScreenHeader("SMS activity", onBack, subtitle = "Optional suggestions · review before saving.")
        Text("SMS alerts can be missing or inaccurate. Accounts detected here are suggestions you confirm, not bank-verified accounts. Confirm every alert before it changes your history.")
        if (!state.available) Text("SMS capture isn't available in this build. Use manual entry or CSV import.")
        else {
            if (!state.settings.enabled) {
                if (SmsCapabilities.HISTORY_IMPORT_ENABLED) {
                    Row { Checkbox(history,{ history = it }); Text("Scan existing messages") }
                }
                Row { Checkbox(live,{ live = it }); Text("Detect incoming alerts") }
                Button(onClick = { disclose = true },enabled = !state.busy && (historyRequested || live)) { Text("Set up SMS access") }
            } else {
                Text("History: ${if (state.settings.historyEnabled) "enabled" else "off"} · Incoming: ${if (state.settings.liveEnabled) "enabled" else "off"}")
                OutlinedButton(onClick = vm::disable,enabled = !state.busy) { Text("Disable SMS and clear pending alerts") }
                if (SmsCapabilities.HISTORY_IMPORT_ENABLED && state.settings.historyEnabled) {
                    OutlinedTextField(start,{ start = it },label = { Text("From (YYYY-MM-DD)") },singleLine = true)
                    OutlinedTextField(end,{ end = it },label = { Text("Through (YYYY-MM-DD)") },singleLine = true)
                    Button(onClick = { vm.scan(start,end) },enabled = !state.busy) { Text("Scan history") }
                }
            }
            if (!SmsCapabilities.HISTORY_IMPORT_ENABLED) Text("Historical SMS import is disabled for now. Only new incoming alerts are captured.")
            SmsNotificationSettings(state.settings,state.busy,vm::notifications)
            Text(state.settings.scanStatus)
        }
        state.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onAddAccount) { Text("Add an account manually") }
        TextButton(onClick = { pair = true }) { Text("Reconcile two recorded transfer legs") }
        Text("Awaiting review (${state.candidates.size})",style = MaterialTheme.typography.titleMedium)
        state.candidates.forEach { candidate ->
            val o = candidate.observation
            Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = VaultCardColors()) { Column(Modifier.padding(12.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${o.institution ?: "Unknown institution"} · ${o.lastFour?.let { "ending $it" } ?: "unknown account"}")
                Text("${o.amountMinor?.let(::minorUnitsText) ?: "Check amount"} ${o.currencyCode} · ${o.date}")
                Text(o.description,maxLines = 4)
                Text(o.reviewReason)
                Row {
                    TextButton(onClick = { onReview(candidate.source.id) },enabled = !state.busy) { Text("Review") }
                    TextButton(onClick = { vm.reject(candidate.source.id) },enabled = !state.busy) { Text("Ignore") }
                }
            } }
        }
    } }
    if (pair) AlertDialog(onDismissRequest = { pair = false },title = { Text("Confirm an own-account transfer") },
        text = { Column {
            Text("Only select two unknown movements you know belong to the same transfer. This keeps one transfer with both accounts and both source records. Amounts must match; record fees separately.")
            fun options(direction: TransactionDirection) = listOf<Pair<String?,String>>(null to "Select") + form.originals.filter { it.kind == EventKind.UNKNOWN && it.direction == direction }.map { it.id to "${it.occurredDate} · ${it.accountName} · ${minorUnitsText(it.amountMinor)}" }
            ChoicePicker("Outgoing",outgoing,options(TransactionDirection.EXPENSE),{ outgoing = it })
            ChoicePicker("Incoming",incoming,options(TransactionDirection.INCOME),{ incoming = it })
            state.error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        } },confirmButton = { TextButton(onClick = { vm.reconcile(requireNotNull(outgoing),requireNotNull(incoming)); outgoing = null; incoming = null },enabled = outgoing != null && incoming != null && !state.busy) { Text("Confirm merge") } },dismissButton = { TextButton(onClick = { pair = false }) { Text("Close") } })
    if (disclose) AlertDialog(onDismissRequest = { disclose = false },title = { Text("Allow local SMS processing?") },
        text = { Text("Vault needs Android SMS access to find financial alerts. Android grants access to messages beyond bank alerts; Vault filters them on this phone. Relevant message text is stored temporarily for processing (cleared on processing, or on the next cleanup after 7 days); parsed details remain locally for review. OTP and unrelated messages are filtered out. No SMS text is uploaded. You choose the accounts and confirm transactions. You can disable access here or revoke permission in Android settings. Skipping leaves manual entry and CSV available.") },
        confirmButton = { TextButton(onClick = {
            disclose = false
            permission.launch(buildList { if (historyRequested) add(Manifest.permission.READ_SMS); if (live) add(Manifest.permission.RECEIVE_SMS) }.toTypedArray())
        }) { Text("Continue to Android permissions") } },dismissButton = { TextButton(onClick = { disclose = false }) { Text("Skip") } })
}

@Composable
fun SmsReviewScreen(state: TransactionFormUiState,vm: SmsViewModel,onBack: ()->Unit,onAddAccount: ()->Unit) {
    val review by vm.review.collectAsState()
    if (!review.loading && !review.ready) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(review.message ?: "This alert is no longer available.")
            TextButton(onClick = onBack) { Text("Close") }
        }
        return
    }
    var duplicate by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Text("Confirm SMS details",Modifier.padding(horizontal = 20.dp),style = MaterialTheme.typography.titleMedium)
        if (review.reason.isNotBlank()) Text(review.reason,Modifier.padding(horizontal = 20.dp),style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onAddAccount) { Text("Account missing? Add it") }
        TextButton(onClick = { duplicate = true }) { Text("Already recorded / other side of a transfer") }
        TransactionFormScreen(state,{ v -> vm.change { copy(amountText = v) } },{ v -> vm.change { copy(description = v) } },
            vm::chooseDirection,{ v -> vm.change { copy(categoryId = v) } },{ v -> vm.change { copy(occurredDate = v) } },
            { v -> vm.change { copy(occurredTime = v) } },vm::confirm,onBack,
            onKindChange = vm::chooseKind,
            onAccountChange = { v -> vm.change { copy(accountId = v) } },onDestinationChange = { v -> vm.change { copy(destinationAccountId = v) } },
            onOriginalChange = { v -> vm.change { copy(originalEventId = v) } },onNoteChange = { v -> vm.change { copy(userNote = v) } })
    }
    if (duplicate) AlertDialog(onDismissRequest = { duplicate = false },title = { Text("Link to an existing transaction") },
        text = { Column {
            Text("Choose the alert's account in the form first. Linking adds no money. The original parsed amount and direction must match that account's entry; inspect the date and description too.")
            ChoicePicker("Existing transaction",chosen,listOf<Pair<String?,String>>(null to "Select")+state.originals.filter { it.currencyCode == state.currencyCode && (it.accountId == state.accountId || it.destinationAccountId == state.accountId) }.map { it.id to "${it.occurredDate} · ${minorUnitsText(it.amountMinor)} · ${it.description.take(35)}" },{ chosen = it })
            state.errorMessage?.let { Text(it,color = MaterialTheme.colorScheme.error) }
        } },confirmButton = { TextButton(onClick = { chosen?.let(vm::attach) },enabled = chosen != null && !state.isSaving) { Text("Confirm link") } },dismissButton = { TextButton(onClick = { duplicate = false }) { Text("Cancel") } })
}
