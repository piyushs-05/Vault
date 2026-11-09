package com.piyush.vault.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.piyush.vault.domain.model.OnboardingProgress
import com.piyush.vault.domain.model.OnboardingStep
import com.piyush.vault.domain.repository.OnboardingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private val Context.onboardingStore by preferencesDataStore("onboarding")

@Singleton
class OnboardingPreferences @Inject constructor(@param:ApplicationContext context: Context) : OnboardingRepository {
    private val store = context.applicationContext.onboardingStore
    override val progress = store.data.map { prefs ->
        OnboardingProgress(
            step = prefs[STEP]?.let { value -> OnboardingStep.entries.firstOrNull { it.name == value } } ?: OnboardingStep.WELCOME,
            completed = prefs[COMPLETED] ?: false,
        )
    }
    override suspend fun moveTo(step: OnboardingStep) { store.edit { it[STEP] = step.name } }
    override suspend fun complete() {
        store.edit { it[STEP] = OnboardingStep.FINISH.name; it[COMPLETED] = true }
    }
    private companion object {
        val STEP = stringPreferencesKey("current_step")
        val COMPLETED = booleanPreferencesKey("completed")
    }
}
