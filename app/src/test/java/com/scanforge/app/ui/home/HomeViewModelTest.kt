@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.scanforge.app.ui.home

import app.cash.turbine.test
import com.scanforge.core.domain.library.DocumentSort
import com.scanforge.core.domain.model.Document
import com.scanforge.core.domain.model.ScanSettings
import com.scanforge.core.domain.repository.DocumentRepository
import com.scanforge.core.domain.repository.FolderRepository
import com.scanforge.core.domain.repository.SettingsRepository
import com.scanforge.core.domain.repository.TagRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class HomeViewModelTest {

    private val repository: DocumentRepository = mockk(relaxed = true)
    private val folderRepository: FolderRepository = mockk(relaxed = true)
    private val tagRepository: TagRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)

    private fun doc(id: Long, title: String = "Doc", favorite: Boolean = false) = Document(
        id = id,
        title = title,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.ofEpochMilli(id),
        pageCount = 1,
        isFavorite = favorite,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { folderRepository.observeFolders() } returns flowOf(emptyList())
        every { tagRepository.observeTags() } returns flowOf(emptyList())
        every { settingsRepository.observeSettings() } returns flowOf(ScanSettings())
        coEvery { repository.purgeTrashedBefore(any()) } returns 0
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun stub(documents: List<Document>) {
        every { repository.observeDocuments() } returns flowOf(documents)
        every { repository.observeLibrary(any(), any()) } returns flowOf(documents)
    }

    private fun viewModel() = HomeViewModel(repository, folderRepository, tagRepository, settingsRepository)

    @Test
    fun `empty repository reports empty state`() = runTest {
        stub(emptyList())
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertTrue(state.isEmpty)
            assertFalse(state.loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `documents and favourites surface in state`() = runTest {
        val docs = listOf(doc(1, "Invoice"), doc(2, "Notes", favorite = true))
        stub(docs)
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertEquals(2, state.documents.size)
            assertEquals(listOf(2L), state.favorites.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entering and toggling selection updates state`() = runTest {
        stub(listOf(doc(1), doc(2), doc(3)))
        val vm = viewModel()
        vm.uiState.test {
            expectMostRecentItem()
            vm.enterSelection(1)
            vm.toggleSelection(2)
            val state = expectMostRecentItem()
            assertTrue(state.inSelectionMode)
            assertEquals(setOf(1L, 2L), state.selection.selectedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `select all selects every visible document`() = runTest {
        stub(listOf(doc(1), doc(2), doc(3)))
        val vm = viewModel()
        vm.uiState.test {
            expectMostRecentItem()
            vm.selectAllVisible()
            assertEquals(setOf(1L, 2L, 3L), expectMostRecentItem().selection.selectedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `batch trash moves selected to trash and exits selection`() = runTest {
        stub(listOf(doc(1), doc(2)))
        val vm = viewModel()
        vm.uiState.test {
            expectMostRecentItem()
            vm.enterSelection(1)
            vm.toggleSelection(2)
            vm.batchTrash()
            assertFalse(expectMostRecentItem().inSelectionMode)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { repository.moveToTrash(setOf(1L, 2L)) }
    }

    @Test
    fun `setting sort feeds observeLibrary`() = runTest {
        stub(listOf(doc(1)))
        val vm = viewModel()
        vm.uiState.test {
            expectMostRecentItem()
            vm.setSort(DocumentSort.NameAZ)
            assertEquals(DocumentSort.NameAZ, expectMostRecentItem().sort)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle favourite delegates to repository`() = runTest {
        stub(listOf(doc(1)))
        viewModel().toggleFavorite(1, true)
        coVerify { repository.setFavorite(1, true) }
    }
}
