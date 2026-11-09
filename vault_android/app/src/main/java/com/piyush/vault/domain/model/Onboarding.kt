package com.piyush.vault.domain.model

enum class OnboardingStep { WELCOME, ACCOUNT, SMS, NOTIFICATIONS, FINISH }

data class OnboardingProgress(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val completed: Boolean = false,
)
