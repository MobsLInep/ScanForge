package com.scanforge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge.core.domain.model.AccentColor
import com.scanforge.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Theme + first-launch gating for the whole app. */
data class AppUiState(
    val loading: Boolean = true,
    /** `null` follows the system setting. */
    val darkTheme: Boolean? = true,
    val accent: AccentColor = AccentColor.Amber,
    val onboardingComplete: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.observeSettings(),
        settingsRepository.observeOnboardingComplete(),
    ) { settings, onboarded ->
        AppUiState(
            loading = false,
            darkTheme = settings.darkTheme,
            accent = settings.accent,
            onboardingComplete = onboarded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())
}
