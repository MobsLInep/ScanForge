package com.scanforge.app.ui.ocr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.scanforge.app.navigation.ScanForgeRoute
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.core.domain.ocr.OcrLanguageMode
import com.scanforge.core.domain.ocr.OcrScheduler
import com.scanforge.core.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the OCR results screen. Page content (image, status, recognised text, structured boxes) is
 * observed reactively from the repository so a running OCR worker updates the UI live. UI-only state
 * — view mode, heatmap toggle, and the inline-edit buffer — is held locally and merged in.
 *
 * Running/Re-running OCR queues the page (so status flips to PENDING immediately) then enqueues the
 * WorkManager job; the resulting status/text changes flow back through the repository.
 */
@HiltViewModel
class OcrResultViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val scheduler: OcrScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // The type-safe route stores `pageId` under its property name; reading the key directly keeps the
    // VM unit-testable (the `toRoute` path needs android.os.Bundle, unavailable in JVM tests).
    private val pageId: Long =
        savedStateHandle.get<Long>("pageId")
            ?: savedStateHandle.toRoute<ScanForgeRoute.OcrResult>().pageId

    private val viewMode = MutableStateFlow(OcrViewMode.Text)
    private val heatmap = MutableStateFlow(false)
    /** Non-null only while the user is editing; holds the in-progress correction. */
    private val draft = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OcrResultUiState> =
        combine(
            repository.observePage(pageId),
            viewMode,
            heatmap,
            draft,
        ) { page, mode, heatmapOn, editingDraft ->
            if (page == null) {
                OcrResultUiState(loading = false, missing = true)
            } else {
                OcrResultUiState(
                    loading = false,
                    imagePath = page.processedImagePath ?: page.originalImagePath,
                    status = page.ocrStatus,
                    text = page.extractedText.orEmpty(),
                    ocr = page.ocrData,
                    viewMode = mode,
                    heatmapEnabled = heatmapOn,
                    editing = editingDraft != null,
                    draft = editingDraft.orEmpty(),
                    languageMode = OcrLanguageMode.fromTag(page.language),
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OcrResultUiState(),
        )

    fun setViewMode(mode: OcrViewMode) { viewMode.value = mode }

    fun toggleHeatmap() { heatmap.update { !it } }

    fun startEditing() { draft.value = uiState.value.text }

    fun updateDraft(value: String) { draft.update { if (it == null) value else value } }

    fun cancelEditing() { draft.value = null }

    fun saveEditing() {
        val edited = draft.value ?: return
        draft.value = null
        viewModelScope.launch { repository.updatePageText(pageId, edited) }
    }

    /** Runs OCR for the first time using the currently selected (or Auto) language. */
    fun runOcr() = enqueue(uiState.value.languageMode)

    /** Re-runs OCR after the user picks a different script/Auto. */
    fun reRunWith(mode: OcrLanguageMode) = enqueue(mode)

    private fun enqueue(mode: OcrLanguageMode) {
        if (uiState.value.status == OcrStatus.InProgress) return
        viewModelScope.launch {
            repository.queuePageForOcr(pageId, mode)
            scheduler.enqueuePage(pageId)
        }
    }

    companion object {
        /** Scripts offered in the language picker (Latin + Devanagari + CJK), plus Auto in the UI. */
        val SELECTABLE_LANGUAGES: List<OcrLanguage> = OcrLanguage.entries
    }
}
