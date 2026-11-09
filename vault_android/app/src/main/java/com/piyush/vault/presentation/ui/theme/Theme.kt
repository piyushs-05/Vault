package com.piyush.vault.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = VaultDarkPrimary,
    onPrimary = VaultDarkOnPrimary,
    primaryContainer = VaultDarkPrimaryContainer,
    onPrimaryContainer = VaultDarkOnPrimaryContainer,
    secondary = VaultDarkSecondary,
    onSecondary = VaultDarkOnSecondary,
    secondaryContainer = VaultDarkSecondaryContainer,
    onSecondaryContainer = VaultDarkOnSecondaryContainer,
    tertiary = VaultDarkTertiary,
    onTertiary = VaultDarkOnTertiary,
    tertiaryContainer = VaultDarkTertiaryContainer,
    onTertiaryContainer = VaultDarkOnTertiaryContainer,
    error = VaultDarkError,
    errorContainer = VaultDarkErrorContainer,
    onErrorContainer = VaultDarkOnSurface,
    background = VaultDarkBackground,
    surface = VaultDarkSurface,
    surfaceDim = VaultDarkBackground,
    surfaceBright = VaultDarkSurfaceContainerHighest,
    surfaceContainerLowest = VaultDarkSurfaceContainerLowest,
    surfaceContainerLow = VaultDarkSurfaceContainerLow,
    surfaceContainer = VaultDarkSurfaceContainer,
    surfaceContainerHigh = VaultDarkSurfaceContainerHigh,
    surfaceContainerHighest = VaultDarkSurfaceContainerHighest,
    onSurface = VaultDarkOnSurface,
    surfaceVariant = VaultDarkSurfaceVariant,
    onSurfaceVariant = VaultDarkOnSurfaceVariant,
    outline = VaultDarkOutline,
    outlineVariant = VaultDarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = VaultPrimary,
    onPrimary = VaultOnPrimary,
    primaryContainer = VaultPrimaryContainer,
    onPrimaryContainer = VaultOnPrimaryContainer,
    secondary = VaultSecondary,
    onSecondary = VaultOnSecondary,
    secondaryContainer = VaultSecondaryContainer,
    onSecondaryContainer = VaultOnSecondaryContainer,
    tertiary = VaultTertiary,
    onTertiary = VaultOnTertiary,
    tertiaryContainer = VaultTertiaryContainer,
    onTertiaryContainer = VaultOnTertiaryContainer,
    error = VaultError,
    errorContainer = VaultErrorContainer,
    onErrorContainer = VaultOnSurface,
    background = VaultBackground,
    onBackground = VaultOnBackground,
    surface = VaultSurface,
    surfaceDim = VaultSurfaceVariant,
    surfaceBright = VaultSurface,
    surfaceContainerLowest = VaultSurfaceContainerLowest,
    surfaceContainerLow = VaultSurfaceContainerLow,
    surfaceContainer = VaultSurfaceContainer,
    surfaceContainerHigh = VaultSurfaceContainerHigh,
    surfaceContainerHighest = VaultSurfaceContainerHighest,
    onSurface = VaultOnSurface,
    surfaceVariant = VaultSurfaceVariant,
    onSurfaceVariant = VaultOnSurfaceVariant,
    outline = VaultOutline,
    outlineVariant = VaultOutlineVariant,
)

@Composable
fun VaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun VaultCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface,
)
