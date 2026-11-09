package com.piyush.vault.presentation.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.vault.domain.model.OnboardingStep
import com.piyush.vault.presentation.accounts.AccountFormScreen
import com.piyush.vault.presentation.accounts.AccountsViewModel
import com.piyush.vault.presentation.navigation.VaultApp
import com.piyush.vault.presentation.sms.smsReviewNotificationsAllowed
import com.piyush.vault.presentation.ui.components.VaultMascot

/** Root gate: completing setup opens the app; pending review links are then handled by VaultApp. */
@Composable
fun VaultEntry(reviewSourceId: String?, onReviewRequestConsumed: () -> Unit) {
    val vm: OnboardingViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    when {
        state.loadError != null -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Text(requireNotNull(state.loadError), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = vm::retry) { Text("Try again") }
        }
        state.progress == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.progress?.completed == true -> VaultApp(reviewSourceId, onReviewRequestConsumed)
        else -> OnboardingFlow(state, vm)
    }
}

private fun OnboardingStep.label() = when (this) {
    OnboardingStep.WELCOME -> "Welcome"
    OnboardingStep.ACCOUNT -> "Account"
    OnboardingStep.SMS -> "Text alerts"
    OnboardingStep.NOTIFICATIONS -> "Reminders"
    OnboardingStep.FINISH -> "Ready"
}

@Composable
private fun OnboardingFlow(state: OnboardingUiState, vm: OnboardingViewModel) {
    val step = requireNotNull(state.progress).step
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var discloseSms by rememberSaveable { mutableStateOf(false) }
    var addingAccount by rememberSaveable { mutableStateOf(false) }
    var accountAttempt by rememberSaveable { mutableIntStateOf(0) }
    // Re-check real system permissions when returning from Android settings.
    val access = remember(context, permissionRefresh, state.sms) {
        (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) to smsReviewNotificationsAllowed(context)
    }
    val receiveGranted = access.first
    val notificationAllowed = access.second
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val receivePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefresh++
        if (granted) vm.enableSms() else vm.permissionDenied()
    }
    BackHandler(enabled = state.busy || addingAccount || step != OnboardingStep.WELCOME) {
        if (!state.busy) {
            if (addingAccount) addingAccount = false else vm.back()
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OnboardingHeader(step)

            if (step == OnboardingStep.ACCOUNT && addingAccount) {
                val accountsVm: AccountsViewModel = hiltViewModel(key = "setup-account-$accountAttempt")
                val accountState by accountsVm.state.collectAsStateWithLifecycle()
                LaunchedEffect(accountsVm) { accountsVm.begin(null) }
                LaunchedEffect(accountsVm) {
                    accountsVm.savedEvents.collect { addingAccount = false; vm.next() }
                }
                BackHandler { if (!accountState.isSaving) addingAccount = false }
                Box(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    AccountFormScreen(accountState, accountsVm) {
                        if (!accountState.isSaving) { addingAccount = false; vm.next() }
                    }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    state.error?.let { SetupErrorCard(it); Spacer(Modifier.height(12.dp)) }
                    Crossfade(targetState = step, label = "onboarding-step") { visibleStep ->
                        when (visibleStep) {
                            OnboardingStep.WELCOME -> WelcomeSetupScreen()
                            OnboardingStep.ACCOUNT -> AccountSetupScreen(state.accountCount, state.busy) { accountAttempt++; addingAccount = true }
                            OnboardingStep.SMS -> SmsSetupScreen(
                                vm.smsAvailable,
                                state.sms.liveEnabled && receiveGranted,
                                state.busy || !state.smsSettingsLoaded,
                            ) { discloseSms = true }
                            OnboardingStep.NOTIFICATIONS -> NotificationSetupScreen(
                                vm.smsAvailable,
                                state.sms.copy(liveEnabled = state.sms.liveEnabled && receiveGranted),
                                state.busy || !state.smsSettingsLoaded,
                            ) { enabled -> permissionRefresh++; vm.notifications(enabled) }
                            OnboardingStep.FINISH -> SetupFinishScreen(state, vm.smsAvailable, receiveGranted, notificationAllowed)
                        }
                    }
                }
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (step != OnboardingStep.WELCOME) {
                                TextButton(onClick = vm::back, enabled = !state.busy, modifier = Modifier.defaultMinSize(minWidth = 72.dp)) { Text("Back") }
                            } else Spacer(Modifier.width(72.dp))
                            Spacer(Modifier.weight(1f))
                            Button(onClick = if (step == OnboardingStep.FINISH) vm::finish else vm::next, enabled = !state.busy) {
                                Text(when {
                                    state.busy -> "Saving…"
                                    step == OnboardingStep.FINISH -> "Start using Vault"
                                    step == OnboardingStep.ACCOUNT && state.accountCount == 0 -> "Skip · optional"
                                    step == OnboardingStep.SMS && !(state.sms.liveEnabled && receiveGranted) -> "Skip for now"
                                    step == OnboardingStep.NOTIFICATIONS && !state.sms.notificationsEnabled -> "Skip for now"
                                    else -> "Continue"
                                })
                            }
                        }
                        Text(
                            when (step) {
                                OnboardingStep.WELCOME -> "No sign-up or bank link needed"
                                OnboardingStep.FINISH -> "Your choices are saved on this phone"
                                else -> "You can skip this and change it later"
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (discloseSms) AlertDialog(
        onDismissRequest = { discloseSms = false },
        title = { Text("Set up text alerts?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Vault can find new money alerts in texts on this phone. You check each one before it’s added to your spending.")
            Text("Your messages stay on this phone. Turn this on only if you want Vault to look for those alerts.")
            Text("If you skip, you can still add purchases yourself or import a statement.")
        } },
        confirmButton = { TextButton(onClick = {
            discloseSms = false
            if (receiveGranted) vm.enableSms() else receivePermission.launch(Manifest.permission.RECEIVE_SMS)
        }) { Text("Allow text alerts") } },
        dismissButton = { TextButton(onClick = { discloseSms = false }) { Text("Not now") } },
    )
}

@Composable
private fun OnboardingHeader(step: OnboardingStep) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VaultMascot(Modifier.size(30.dp))
            Text("vault", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text("On this phone", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (step == OnboardingStep.FINISH) "Setup complete" else "Step ${step.ordinal + 1} · ${step.label()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            Text("${step.ordinal + 1} of ${OnboardingStep.entries.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OnboardingStep.entries.forEachIndexed { index, _ ->
                Box(
                    Modifier.weight(1f).height(4.dp).background(
                        if (index <= step.ordinal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp),
                    ),
                )
            }
        }
    }
}

@Composable
private fun SetupErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .2f)),
    ) {
        Text(message, Modifier.fillMaxWidth().padding(11.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
