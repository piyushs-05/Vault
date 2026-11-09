package com.piyush.vault.domain.repository

import com.piyush.vault.domain.model.OnboardingProgress
import com.piyush.vault.domain.model.OnboardingStep
import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    val progress: Flow<OnboardingProgress>
    suspend fun moveTo(step: OnboardingStep)
    suspend fun complete()
}
