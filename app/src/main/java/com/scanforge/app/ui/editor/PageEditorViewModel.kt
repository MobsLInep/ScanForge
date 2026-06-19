package com.scanforge.app.ui.editor

import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge.app.R
import com.scanforge.app.navigation.ScanForgeRoute
import com.scanforge.core.domain.imaging.EnhancementFilter
import com.scanforge.core.domain.imaging.EnhancementSettings
import com.scanforge.core.domain.imaging.ImagePipeline
import com.scanforge.core.domain.imaging.PageProcessing
import com.scanforge.core.domain.repository.DocumentRepository
import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.core.domain.scanning.PageImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.navigation.toRoute
import javax.inject.Inject

/**
 * Drives the page editor. Holds the editable [PageProcessing] recipe and re-renders a downscaled
 * "after" preview (debounced, latest-wins) whenever it changes, so the source file is never touched
 * until the user applies. Apply renders at full resolution, stores it as the processed image, and
 * persists the recipe for later re-editing.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class PageEditorViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val imagePipeline: ImagePipeline,
    private val imageStore: PageImageStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pageId: Long = savedStateHandle.toRoute<ScanForgeRoute.PageEditor>().pageId

    private val _uiState = MutableStateFlow(PageEditorUiState())
    val uiState: StateFlow<PageEditorUiState> = _uiState.asStateFlow()

    /** Latest edit recipe; previews react to this with a short debounce + latest-wins rendering. */
    private val recipe = MutableStateFlow(PageProcessing.DEFAULT)

    init {
        viewModelScope.launch {
            val page = documentRepository.observePage(pageId).first()
            if (page == null) {
                _uiState.update { it.copy(loading = false, errorRes = R.string.editor_error) }
                return@launch
            }
            val initial = page.processing ?: PageProcessing.DEFAULT
            recipe.value = initial
            _uiState.update {
                it.copy(loading = false, originalImagePath = page.originalImagePath, processing = initial)
            }
            renderPreview(page.originalImagePath, initial)
        }

        // Live preview: collapse rapid slider changes, render only the most recent recipe.
        viewModelScope.launch {
            recipe.drop(1).debounce(PREVIEW_DEBOUNCE_MS).collect { processing ->
                _uiState.value.originalImagePath?.let { renderPreview(it, processing) }
            }
        }
    }

    // ── Edit operations (all just mutate the recipe) ───────────────────────────────────────────--
    fun selectFilter(filter: EnhancementFilter) = mutate { it.copy(filter = filter) }

    fun setBrightness(value: Float) = mutate { it.copy(brightness = value) }

    fun setContrast(value: Float) = mutate { it.copy(contrast = value) }

    fun setSharpness(value: Float) = mutate { it.copy(sharpness = value) }

    fun toggleDeskew(enabled: Boolean) = mutate { it.copy(deskew = enabled) }

    fun toggleShadows(enabled: Boolean) = mutate { it.copy(removeShadows = enabled) }

    fun toggleDenoise(enabled: Boolean) = mutate { it.copy(denoise = enabled) }

    fun rotate() = mutate { it.rotatedClockwise() }

    fun reset() {
        val reset = PageProcessing(cropQuad = recipe.value.cropQuad, enhancement = EnhancementSettings())
        recipe.value = reset
        _uiState.update { it.copy(processing = reset) }
    }

    // ── Crop editor ─────────────────────────────────────────────────────────────────────────────
    fun openCropEditor() = _uiState.update { it.copy(showCropEditor = true) }

    fun cancelCropEditor() = _uiState.update { it.copy(showCropEditor = false) }

    fun applyCrop(quad: DetectedQuad) {
        val updated = recipe.value.copy(cropQuad = quad)
        recipe.value = updated
        _uiState.update { it.copy(processing = updated, showCropEditor = false) }
    }

    // ── Apply (full-res render + persist) ─────────────────────────────────────────────────────--
    fun apply() {
        val state = _uiState.value
        val source = state.originalImagePath ?: return
        if (state.saving) return
        val processing = recipe.value
        _uiState.update { it.copy(saving = true, errorRes = null) }
        viewModelScope.launch {
            runCatching {
                val bytes = imagePipeline.render(source, processing, maxEdge = null)
                    ?: error("Pipeline returned no output")
                val stored = imageStore.savePage(bytes)
                documentRepository.updatePageProcessing(
                    pageId = pageId,
                    processedImagePath = stored.originalImagePath,
                    thumbnailPath = stored.thumbnailPath,
                    processing = processing,
                )
            }.onSuccess {
                _uiState.update { it.copy(saving = false, saved = true) }
            }.onFailure {
                _uiState.update { it.copy(saving = false, errorRes = R.string.editor_error) }
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorRes = null) }

    private fun mutate(transform: (EnhancementSettings) -> EnhancementSettings) {
        val updated = recipe.value.copy(enhancement = transform(recipe.value.enhancement))
        recipe.value = updated
        _uiState.update { it.copy(processing = updated) }
    }

    private suspend fun renderPreview(source: String, processing: PageProcessing) {
        _uiState.update { it.copy(rendering = true) }
        val bytes = imagePipeline.render(source, processing, maxEdge = PREVIEW_MAX_EDGE)
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        _uiState.update { it.copy(preview = bitmap ?: it.preview, rendering = false) }
    }

    private companion object {
        const val PREVIEW_MAX_EDGE = 1280
        const val PREVIEW_DEBOUNCE_MS = 110L
    }
}
