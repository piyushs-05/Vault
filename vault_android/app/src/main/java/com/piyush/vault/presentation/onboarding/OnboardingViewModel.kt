package com.piyush.vault.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piyush.vault.domain.model.OnboardingProgress
import com.piyush.vault.domain.model.OnboardingStep
import com.piyush.vault.domain.model.SmsSettings
import com.piyush.vault.domain.repository.OnboardingRepository
import com.piyush.vault.domain.usecase.ObserveAccountsUseCase
import com.piyush.vault.domain.usecase.SmsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val progress: OnboardingProgress? = null,
    val loadError: String? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val accountCount: Int? = null,
    val sms: SmsSettings = SmsSettings(),
    val smsSettingsLoaded: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
    accounts: ObserveAccountsUseCase,
    private val sms: SmsUseCases,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state = _state.asStateFlow()
    val smsAvailable get() = sms.isAvailable
    private var loadJob: Job? = null

    init {
        retry()
        viewModelScope.launch {
            accounts().catch { _state.update { it.copy(error = "Couldn't load the account summary. You can still continue.") } }
                .collect { rows -> _state.update { it.copy(accountCount = rows.count { row -> row.account.isUserAccount && !row.account.isArchived }) } }
        }
        viewModelScope.launch {
            sms.settings.catch { _state.update { it.copy(error = "Couldn't load SMS settings. Configure them later in More → SMS activity.") } }
                .collect { settings -> _state.update { it.copy(sms = settings,smsSettingsLoaded = true) } }
        }
    }
    fun retry() {
        loadJob?.cancel()
        _state.update { it.copy(loadError = null) }
        loadJob = viewModelScope.launch {
            repository.progress.catch { _state.update { it.copy(loadError = "Couldn't read setup progress. Please retry.") } }
                .collect { progress -> _state.update { it.copy(progress = progress,loadError = null) } }
        }
    }
    private fun perform(block: suspend () -> Unit) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true,error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { _state.update { it.copy(error = e.message) } }
            catch (_: Exception) { _state.update { it.copy(error = "Couldn't save this setup choice. Please try again.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun next() = perform {
        val current = requireNotNull(state.value.progress)
        val next = OnboardingStep.entries[(current.step.ordinal + 1).coerceAtMost(OnboardingStep.FINISH.ordinal)]
        repository.moveTo(next)
        _state.update { it.copy(progress = current.copy(step = next)) }
    }
    fun back() = perform {
        val current = requireNotNull(state.value.progress)
        val previous = OnboardingStep.entries[(current.step.ordinal - 1).coerceAtLeast(0)]
        repository.moveTo(previous)
        _state.update { it.copy(progress = current.copy(step = previous)) }
    }
    fun finish() = perform {
        repository.complete()
        _state.update { it.copy(progress = OnboardingProgress(OnboardingStep.FINISH,true)) }
    }
    fun enableSms() = perform { sms.enable(history = false,live = true) }
    fun notifications(enabled: Boolean) = perform { sms.notifications(enabled) }
    fun permissionDenied() { _state.update { it.copy(error = "Text alerts weren’t turned on. You can still add purchases yourself or import a statement.") } }
}
