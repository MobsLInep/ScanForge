package com.scanforge.app.ui.scan

import app.cash.turbine.test
import com.scanforge.core.domain.model.NewPage
import com.scanforge.core.domain.model.Page
import com.scanforge.core.domain.repository.DocumentRepository
import com.scanforge.core.domain.scanning.DetectedQuad
import com.scanforge.core.domain.scanning.EdgeDetector
import com.scanforge.core.domain.scanning.PageImageStore
import com.scanforge.core.domain.scanning.PageImporter
import com.scanforge.core.domain.scanning.StoredPageImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * State-machine coverage for the capture flow. Uses fakes for the domain contracts so the whole
 * camera → processing → review → saving → saved sequence, batch reorder/delete, and import run on
 * the JVM with no device.
 */
class ScanViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var documentRepository: FakeDocumentRepository
    private lateinit var edgeDetector: FakeEdgeDetector
    private lateinit var imageStore: FakeImageStore
    private lateinit var importer: FakeImporter

    private fun viewModel() = ScanViewModel(
        documentRepository = documentRepository,
        edgeDetector = edgeDetector,
        imageStore = imageStore,
        pageImporter = importer,
        titleProvider = object : ScanTitleProvider {
            override fun scanTitle() = "Scan"
            override fun importTitle() = "Import"
        },
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        documentRepository = FakeDocumentRepository()
        edgeDetector = FakeEdgeDetector()
        imageStore = FakeImageStore()
        importer = FakeImporter()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts in camera step with single mode`() {
        val vm = viewModel()
        assertInstanceOf(ScanStep.Camera::class.java, vm.uiState.value.step)
        assertEquals(CaptureMode.Single, vm.uiState.value.mode)
    }

    @Test
    fun `toggles flash grid and batch mode`() {
        val vm = viewModel()
        vm.toggleFlash(); vm.toggleGrid(); vm.toggleMode()
        with(vm.uiState.value) {
            assertTrue(flashEnabled)
            assertTrue(gridEnabled)
            assertEquals(CaptureMode.Batch, mode)
        }
    }

    @Test
    fun `capture with detected edges moves to review`() = runTest {
        edgeDetector.result = DetectedQuad.FULL_FRAME
        val vm = viewModel()
        vm.onImageCaptured(byteArrayOf(1, 2, 3))
        val step = vm.uiState.value.step
        assertInstanceOf(ScanStep.Review::class.java, step)
        assertTrue((step as ScanStep.Review).page.edgesDetected)
    }

    @Test
    fun `capture without edges falls back to manual full-frame review`() = runTest {
        edgeDetector.result = null
        val vm = viewModel()
        vm.onImageCaptured(byteArrayOf(1))
        val page = (vm.uiState.value.step as ScanStep.Review).page
        assertEquals(false, page.edgesDetected)
        assertEquals(DetectedQuad.FULL_FRAME, page.quad)
    }

    @Test
    fun `single-mode confirm saves a one-page document`() = runTest {
        val vm = viewModel()
        vm.onImageCaptured(byteArrayOf(1))
        vm.onConfirmReview(DetectedQuad.FULL_FRAME)
        assertInstanceOf(ScanStep.Saved::class.java, vm.uiState.value.step)
        assertEquals(1, documentRepository.created.single().second.size)
    }

    @Test
    fun `batch-mode confirm accumulates pages and returns to camera`() = runTest {
        val vm = viewModel()
        vm.toggleMode() // Batch
        vm.onImageCaptured(byteArrayOf(1)); vm.onConfirmReview(DetectedQuad.FULL_FRAME)
        vm.onImageCaptured(byteArrayOf(2)); vm.onConfirmReview(DetectedQuad.FULL_FRAME)
        assertInstanceOf(ScanStep.Camera::class.java, vm.uiState.value.step)
        assertEquals(2, vm.uiState.value.pageCount)
        assertTrue(documentRepository.created.isEmpty())
    }

    @Test
    fun `batch done creates a single multi-page document`() = runTest {
        val vm = viewModel()
        vm.toggleMode()
        vm.onImageCaptured(byteArrayOf(1)); vm.onConfirmReview(DetectedQuad.FULL_FRAME)
        vm.onImageCaptured(byteArrayOf(2)); vm.onConfirmReview(DetectedQuad.FULL_FRAME)
        vm.onDoneBatch()
        assertInstanceOf(ScanStep.Saved::class.java, vm.uiState.value.step)
        assertEquals(2, documentRepository.created.single().second.size)
        assertEquals(0, vm.uiState.value.pageCount)
    }

    @Test
    fun `delete removes a captured page`() = runTest {
        val vm = viewModel()
        vm.toggleMode()
        vm.onImageCaptured(byteArrayOf(1)); vm.onConfirmReview(DetectedQuad.FULL_FRAME)
        val id = vm.uiState.value.capturedPages.single().localId
        vm.onDeletePage(id)
        assertEquals(0, vm.uiState.value.pageCount)
    }

    @Test
    fun `move page reorders the batch`() = runTest {
        val vm = viewModel()
        vm.toggleMode()
        repeat(3) { i -> vm.onImageCaptured(byteArrayOf(i.toByte())); vm.onConfirmReview(DetectedQuad.FULL_FRAME) }
        val ids = vm.uiState.value.capturedPages.map { it.localId }
        vm.onMovePage(ids[2], toEarlier = true) // move last one slot earlier
        val reordered = vm.uiState.value.capturedPages.map { it.localId }
        assertEquals(listOf(ids[0], ids[2], ids[1]), reordered)
    }

    @Test
    fun `retake discards the page and returns to camera`() = runTest {
        val vm = viewModel()
        vm.onImageCaptured(byteArrayOf(1))
        vm.onRetake()
        assertInstanceOf(ScanStep.Camera::class.java, vm.uiState.value.step)
        assertTrue(imageStore.deleted.isNotEmpty())
    }

    @Test
    fun `import images creates a document and reaches saved`() = runTest {
        importer.images = listOf(NewPage("a.jpg"), NewPage("b.jpg"))
        val vm = viewModel()
        vm.onImportImages(listOf("uri-a", "uri-b"))
        assertInstanceOf(ScanStep.Saved::class.java, vm.uiState.value.step)
        assertEquals(2, documentRepository.created.single().second.size)
    }

    @Test
    fun `empty import surfaces an error`() = runTest {
        importer.images = emptyList()
        val vm = viewModel()
        vm.onImportImages(listOf("uri"))
        assertInstanceOf(ScanStep.Error::class.java, vm.uiState.value.step)
    }

    @Test
    fun `capture failure surfaces an error`() = runTest {
        imageStore.failSave = true
        val vm = viewModel()
        vm.uiState.test {
            assertInstanceOf(ScanStep.Camera::class.java, awaitItem().step)
            vm.onImageCaptured(byteArrayOf(1))
            // Processing then Error.
            val next = awaitItem()
            assertTrue(next.step is ScanStep.Processing || next.step is ScanStep.Error)
            cancelAndIgnoreRemainingEvents()
        }
        assertInstanceOf(ScanStep.Error::class.java, vm.uiState.value.step)
    }

    // ── Fakes ────────────────────────────────────────────────────────────────────────────────
    private class FakeImageStore : PageImageStore {
        var failSave = false
        val deleted = mutableListOf<String?>()
        private var counter = 0
        override suspend fun savePage(jpegBytes: ByteArray): StoredPageImages {
            if (failSave) error("boom")
            val n = counter++
            return StoredPageImages("orig_$n.jpg", "thumb_$n.jpg")
        }
        override suspend fun deletePageImages(vararg paths: String?) { deleted.addAll(paths) }
    }

    private class FakeEdgeDetector : EdgeDetector {
        var result: DetectedQuad? = DetectedQuad.FULL_FRAME
        override suspend fun detectQuad(imagePath: String): DetectedQuad? = result
    }

    private class FakeImporter : PageImporter {
        var images: List<NewPage> = emptyList()
        var pdf: List<NewPage> = emptyList()
        override suspend fun importImages(uris: List<String>): List<NewPage> = images
        override suspend fun importPdf(uri: String): List<NewPage> = pdf
    }

    private class FakeDocumentRepository : DocumentRepository {
        val created = mutableListOf<Pair<String, List<NewPage>>>()
        override suspend fun createDocumentWithPages(title: String, pages: List<NewPage>): Long {
            created += title to pages
            return created.size.toLong()
        }
        // Unused by these tests.
        override fun observeDocuments(): Flow<List<com.scanforge.core.domain.model.Document>> = flowOf(emptyList())
        override fun observeDocument(id: Long): Flow<com.scanforge.core.domain.model.Document?> = flowOf(null)
        override fun searchDocuments(query: String): Flow<List<com.scanforge.core.domain.model.Document>> = flowOf(emptyList())
        override fun observeDocumentPages(documentId: Long): Flow<List<Page>> = flowOf(emptyList())
        override fun observePage(pageId: Long): Flow<Page?> = flowOf(null)
        override suspend fun updatePageProcessing(
            pageId: Long,
            processedImagePath: String,
            thumbnailPath: String,
            processing: com.scanforge.core.domain.imaging.PageProcessing,
        ) = Unit
        override suspend fun createDocument(title: String): Long = 0
        override suspend fun getPage(pageId: Long): Page? = null
        override suspend fun getDocumentPages(documentId: Long): List<Page> = emptyList()
        override suspend fun queueDocumentForOcr(
            documentId: Long,
            mode: com.scanforge.core.domain.ocr.OcrLanguageMode,
        ) = Unit
        override suspend fun queuePageForOcr(
            pageId: Long,
            mode: com.scanforge.core.domain.ocr.OcrLanguageMode,
        ) = Unit
        override suspend fun setPageOcrStatus(
            pageId: Long,
            status: com.scanforge.core.domain.model.OcrStatus,
        ) = Unit
        override suspend fun savePageOcrResult(
            pageId: Long,
            result: com.scanforge.core.domain.ocr.OcrDocument,
        ) = Unit
        override suspend fun updatePageText(pageId: Long, text: String) = Unit
        override suspend fun addPage(documentId: Long, page: NewPage): Long = 0
        override suspend fun deletePage(pageId: Long) = Unit
        override suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>) = Unit
        override suspend fun renameDocument(id: Long, title: String) = Unit
        override suspend fun deleteDocument(id: Long) = Unit

        override fun observeLibrary(
            sort: com.scanforge.core.domain.library.DocumentSort,
            filter: com.scanforge.core.domain.library.DocumentFilter,
        ): Flow<List<com.scanforge.core.domain.model.Document>> = flowOf(emptyList())

        override fun search(query: String): Flow<List<com.scanforge.core.domain.library.SearchResult>> =
            flowOf(emptyList())

        override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit
        override suspend fun moveToFolder(documentIds: Collection<Long>, folderId: Long?) = Unit
        override suspend fun duplicateDocument(id: Long): Long = 0
        override fun observeTrash(): Flow<List<com.scanforge.core.domain.model.Document>> = flowOf(emptyList())
        override suspend fun moveToTrash(ids: Collection<Long>) = Unit
        override suspend fun restoreFromTrash(ids: Collection<Long>) = Unit
        override suspend fun emptyTrash(): Int = 0
        override suspend fun purgeTrashedBefore(threshold: java.time.Instant): Int = 0
    }
}
