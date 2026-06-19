package com.scanforge.app.ui.ocr

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.scanforge.core.domain.model.OcrStatus
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.ocr.OcrLanguage
import com.scanforge.core.domain.ocr.OcrLanguageMode
import com.scanforge.core.domain.ocr.OcrScheduler
import com.scanforge.core.domain.repository.DocumentRepository
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class OcrResultViewModelTest {

    // Under JVM unit tests `toRoute` decodes route ids as 0 (android.os.Bundle is stubbed), so the
    // VM's pageId is 0; matchers keep these tests independent of the concrete id.
    private val pageFlow = MutableStateFlow<Page?>(null)
    private lateinit var repository: DocumentRepository
    private lateinit var scheduler: OcrScheduler

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        io.mockk.every { repository.observePage(any()) } returns pageFlow
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = OcrResultViewModel(
        repository = repository,
        scheduler = scheduler,
        savedStateHandle = SavedStateHandle(mapOf("pageId" to 0L)),
    )

    private fun page(status: OcrStatus, text: String?, language: String? = null) = Page(
        id = 0L,
        documentId = 1,
        pageOrder = 0,
        originalImagePath = "/tmp/p.jpg",
        extractedText = text,
        language = language,
        ocrStatus = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `state reflects the page's status and recognised text`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            pageFlow.value = page(OcrStatus.Completed, "Hello world")
            val ready = expectMostRecentItem()
            assertEquals(OcrStatus.Completed, ready.status)
            assertEquals("Hello world", ready.text)
            assertTrue(ready.isDone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing draft saves the correction back to the repository`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem() // loading
            pageFlow.value = page(OcrStatus.Completed, "teh cat")
            awaitItem()

            vm.startEditing()
            assertEquals("teh cat", awaitItem().draft)
            vm.updateDraft("the cat")
            assertEquals("the cat", awaitItem().draft)
            vm.saveEditing()
            assertFalse(awaitItem().editing)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { repository.updatePageText(any(), "the cat") }
    }

    @Test
    fun `re-run queues the page with the chosen language and enqueues work`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            pageFlow.value = page(OcrStatus.Completed, "x")
            awaitItem()
            vm.reRunWith(OcrLanguageMode.Manual(OcrLanguage.Devanagari))
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { repository.queuePageForOcr(any(), OcrLanguageMode.Manual(OcrLanguage.Devanagari)) }
        verify { scheduler.enqueuePage(any()) }
    }

    @Test
    fun `toggling view mode and heatmap updates state`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            pageFlow.value = page(OcrStatus.Completed, "x")
            awaitItem()
            vm.setViewMode(OcrViewMode.Image)
            assertEquals(OcrViewMode.Image, awaitItem().viewMode)
            vm.toggleHeatmap()
            assertTrue(awaitItem().heatmapEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
