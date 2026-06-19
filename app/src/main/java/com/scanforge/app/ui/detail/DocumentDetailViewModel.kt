package com.scanforge.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.scanforge.app.navigation.ScanForgeRoute
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.Folder
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.ocr.OcrLanguageMode
import com.scanforge.core.domain.ocr.OcrScheduler
import com.scanforge.core.domain.repository.DocumentRepository
import com.scanforge.core.domain.repository.FolderRepository
import com.scanforge.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** UI state for the document detail (page grid) screen, including batch OCR progress. */
data class DocumentDetailUiState(
    val loading: Boolean = true,
    val title: String = "",
    val document: Document? = null,
    val folders: List<Folder> = emptyList(),
    val pages: List<Page> = emptyList(),
    val ocrStatus: OcrStatus = OcrStatus.NotStarted,
    val ocrDone: Int = 0,
    val ocrTotal: Int = 0,
) {
    val ocrRunning: Boolean
        get() = ocrStatus == OcrStatus.Queued || ocrStatus == OcrStatus.InProgress
    val ocrComplete: Boolean get() = ocrTotal > 0 && ocrDone == ocrTotal
    val isFavorite: Boolean get() = document?.isFavorite == true
}

/**
 * Observes a document's title and its ordered pages. Owns page reordering and deletion, and the
 * batch OCR controls: [runOcr] (re-)queues every page for the worker, with progress derived live
 * from the pages' reactive OCR statuses. If the user's `autoOcr` preference is on and the document
 * has never been recognised, OCR kicks off automatically on first open.
 */
@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val folderRepository: FolderRepository,
    private val scheduler: OcrScheduler,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentId: Long = savedStateHandle.toRoute<ScanForgeRoute.DocumentDetail>().documentId
    private val autoOcrTriggered = AtomicBoolean(false)

    val uiState: StateFlow<DocumentDetailUiState> =
        combine(
            repository.observeDocument(documentId),
            repository.observeDocumentPages(documentId),
            folderRepository.observeFolders(),
        ) { document, pages, folders ->
            maybeAutoRunOcr(pages)
            DocumentDetailUiState(
                loading = false,
                title = document?.title.orEmpty(),
                document = document,
                folders = folders,
                pages = pages,
                ocrStatus = aggregate(pages),
                ocrDone = pages.count { it.ocrStatus == OcrStatus.Completed },
                ocrTotal = pages.size,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DocumentDetailUiState(),
        )

    fun toggleFavorite() {
        val current = uiState.value.isFavorite
        viewModelScope.launch { repository.setFavorite(documentId, !current) }
    }

    fun rename(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameDocument(documentId, trimmed) }
    }

    fun moveToFolder(folderId: Long?) {
        viewModelScope.launch { repository.moveToFolder(listOf(documentId), folderId) }
    }

    /** Soft-deletes this document; the screen should navigate away after calling. */
    fun moveToTrash() {
        viewModelScope.launch { repository.moveToTrash(listOf(documentId)) }
    }

    /** Deep-copies the document; returns the new id via [onDuplicated] for navigation. */
    fun duplicate(onDuplicated: (Long) -> Unit) {
        viewModelScope.launch {
            val newId = repository.duplicateDocument(documentId)
            if (newId > 0) onDuplicated(newId)
        }
    }

    fun reorder(orderedPageIds: List<Long>) {
        viewModelScope.launch { repository.reorderPages(documentId, orderedPageIds) }
    }

    fun deletePage(pageId: Long) {
        viewModelScope.launch { repository.deletePage(pageId) }
    }

    /** (Re-)runs OCR across every page of the document with the default Auto language selection. */
    fun runOcr() {
        viewModelScope.launch {
            repository.queueDocumentForOcr(documentId, OcrLanguageMode.Auto)
            scheduler.enqueueDocument(documentId)
        }
    }

    private suspend fun maybeAutoRunOcr(pages: List<Page>) {
        if (pages.isEmpty() || !autoOcrTriggered.compareAndSet(false, true)) return
        val neverRun = pages.all { it.ocrStatus == OcrStatus.NotStarted }
        if (neverRun && settingsRepository.observeSettings().first().autoOcr) {
            repository.queueDocumentForOcr(documentId, OcrLanguageMode.Auto)
            scheduler.enqueueDocument(documentId)
        }
    }

    private fun aggregate(pages: List<Page>): OcrStatus = when {
        pages.any { it.ocrStatus == OcrStatus.InProgress } -> OcrStatus.InProgress
        pages.any { it.ocrStatus == OcrStatus.Queued } -> OcrStatus.Queued
        pages.isNotEmpty() && pages.all { it.ocrStatus == OcrStatus.Completed } -> OcrStatus.Completed
        pages.any { it.ocrStatus == OcrStatus.Failed } -> OcrStatus.Failed
        else -> OcrStatus.NotStarted
    }
}
