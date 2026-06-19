package com.scanforge.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scanforge.core.database.dao.DocumentDao
import com.scanforge.core.database.dao.PageDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DocumentDaoTest {

    private lateinit var db: ScanForgeDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, ScanForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        documentDao = db.documentDao()
        pageDao = db.pageDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndObserveDocument() = runTest {
        val id = documentDao.insert(documentEntity("Invoice"))

        val docs = documentDao.observeDocuments().first()

        assertEquals(1, docs.size)
        assertEquals("Invoice", docs.first().document.title)
        assertEquals(id, docs.first().document.id)
    }

    @Test
    fun observeDocumentsIsOrderedByUpdatedAtDescending() = runTest {
        documentDao.insert(documentEntity("Older", updatedAt = FIXED_TIME))
        documentDao.insert(documentEntity("Newer", updatedAt = FIXED_TIME.plusSeconds(60)))

        val titles = documentDao.observeDocuments().first().map { it.document.title }

        assertEquals(listOf("Newer", "Older"), titles)
    }

    @Test
    fun populatedDocumentIncludesAllPages() = runTest {
        val docId = documentDao.insert(documentEntity("Report"))
        pageDao.insert(pageEntity(docId, pageOrder = 1))
        pageDao.insert(pageEntity(docId, pageOrder = 0))

        val populated = documentDao.observeDocument(docId).first()!!

        // Room @Relation does not guarantee row order; ordering by page_order is the domain
        // mapper's responsibility (see EntityMappersTest). Here we assert the relation is complete.
        assertEquals(setOf(0, 1), populated.pages.map { it.pageOrder }.toSet())
    }

    @Test
    fun pageDaoObservesPagesOrderedByPageOrder() = runTest {
        val docId = documentDao.insert(documentEntity("Report"))
        pageDao.insert(pageEntity(docId, pageOrder = 2))
        pageDao.insert(pageEntity(docId, pageOrder = 0))
        pageDao.insert(pageEntity(docId, pageOrder = 1))

        // The explicit ORDER BY in PageDao.observePages *does* guarantee order.
        assertEquals(listOf(0, 1, 2), pageDao.observePages(docId).first().map { it.pageOrder })
    }

    @Test
    fun updateTitleIsReflectedInObservers() = runTest {
        val id = documentDao.insert(documentEntity("Draft"))

        documentDao.updateTitle(id, "Final", FIXED_TIME.plusSeconds(10))

        assertEquals("Final", documentDao.observeDocument(id).first()!!.document.title)
    }

    @Test
    fun deletingDocumentCascadesToPages() = runTest {
        val docId = documentDao.insert(documentEntity("Throwaway"))
        pageDao.insert(pageEntity(docId, pageOrder = 0))

        documentDao.delete(docId)

        assertNull(documentDao.observeDocument(docId).first())
        assertEquals(0, pageDao.countPages(docId))
    }

    @Test
    fun searchMatchesTitleAndPageText() = runTest {
        val matchingByTitle = documentDao.insert(documentEntity("Tax 2025"))
        val matchingByPageText = documentDao.insert(documentEntity("Unnamed"))
        pageDao.insert(pageEntity(matchingByPageText, pageOrder = 0, extractedText = "annual tax summary"))
        documentDao.insert(documentEntity("Recipe"))

        val results = documentDao.searchDocuments("tax").first().map { it.document.id }

        assertEquals(2, results.size)
        assertTrue(results.containsAll(listOf(matchingByTitle, matchingByPageText)))
    }
}
