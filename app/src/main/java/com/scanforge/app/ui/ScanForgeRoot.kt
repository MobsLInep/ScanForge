package com.scanforge.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanforge.app.ui.onboarding.OnboardingScreen
import com.scanforge.core.domain.model.AccentColor
import com.scanforge.designsystem.theme.ScanForgeTheme
import com.scanforge.designsystem.theme.SfAccent

/**
 * App root: applies the user's theme/accent preferences and gates first launch behind onboarding.
 * Until preferences load it shows nothing (a frame), avoiding a theme flash.
 */
@Composable
fun ScanForgeRoot() {
    val viewModel: AppViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val dark = state.darkTheme ?: isSystemInDarkTheme()
    val accent = when (state.accent) {
        AccentColor.Amber -> SfAccent.Amber
        AccentColor.Teal -> SfAccent.Teal
    }

    ScanForgeTheme(darkTheme = dark, accent = accent) {
        when {
            state.loading -> Unit // brief: preferences resolve on the first frame
            !state.onboardingComplete -> OnboardingScreen()
            else -> ScanForgeApp()
        }
    }
}
