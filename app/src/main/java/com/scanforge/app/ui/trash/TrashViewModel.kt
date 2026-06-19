package com.scanforge.app.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.repository.DocumentRepository
import com.scanforge.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val loading: Boolean = true,
    val documents: List<Document> = emptyList(),
    val retentionDays: Int = 30,
) {
    val isEmpty: Boolean get() = !loading && documents.isEmpty()
}

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: DocumentRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<TrashUiState> = combine(
        repository.observeTrash(),
        settingsRepository.observeSettings().map { it.trashRetentionDays },
    ) { docs, days ->
        TrashUiState(loading = false, documents = docs, retentionDays = days)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun restore(id: Long) {
        viewModelScope.launch { repository.restoreFromTrash(listOf(id)) }
    }

    fun deleteForever(id: Long) {
        viewModelScope.launch { repository.deleteDocument(id) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repository.emptyTrash() }
    }
}
