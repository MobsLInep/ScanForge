package com.scanforge.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    /** OCR script tags the user has pre-selected; Latin on by default. */
    val selectedLanguages: Set<String> = setOf(OcrLanguage.Latin.tag),
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun toggleLanguage(tag: String) {
        _uiState.update { state ->
            val next = state.selectedLanguages.toMutableSet().apply {
                if (!add(tag)) remove(tag)
            }
            // Always keep at least one language selected.
            state.copy(selectedLanguages = if (next.isEmpty()) state.selectedLanguages else next)
        }
    }

    /** Persists the language choice and marks onboarding complete (the root then shows the app). */
    fun finish() {
        val langs = _uiState.value.selectedLanguages.toList().ifEmpty { listOf(OcrLanguage.Latin.tag) }
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(ocrLanguages = langs) }
            settingsRepository.setOnboardingComplete(true)
        }
    }
}
