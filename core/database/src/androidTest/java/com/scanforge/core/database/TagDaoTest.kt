package com.scanforge.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.scanforge.core.database.dao.DocumentDao
import com.scanforge.core.database.dao.TagDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TagDaoTest {

    private lateinit var db: ScanForgeDatabase
    private lateinit var tagDao: TagDao
    private lateinit var documentDao: DocumentDao

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, ScanForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tagDao = db.tagDao()
        documentDao = db.documentDao()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndObserveTagsSortedByName() = runTest {
        tagDao.insert(tagEntity("Work"))
        tagDao.insert(tagEntity("Archive"))

        val names = tagDao.observeTags().first().map { it.name }

        assertEquals(listOf("Archive", "Work"), names)
    }

    @Test
    fun duplicateTagNameIsIgnored() = runTest {
        tagDao.insert(tagEntity("Receipts"))
        tagDao.insert(tagEntity("Receipts"))

        assertEquals(1, tagDao.observeTags().first().size)
    }

    @Test
    fun setDocumentTagsReplacesAssociations() = runTest {
        val docId = documentDao.insert(documentEntity("Tagged"))
        val work = tagDao.insert(tagEntity("Work"))
        val home = tagDao.insert(tagEntity("Home"))
        val urgent = tagDao.insert(tagEntity("Urgent"))

        tagDao.setDocumentTags(docId, setOf(work, home))
        assertEquals(
            setOf("Home", "Work"),
            tagDao.observeTagsForDocument(docId).first().map { it.name }.toSet(),
        )

        // Replacing with a different set drops the old associations.
        tagDao.setDocumentTags(docId, setOf(urgent))
        val finalNames = tagDao.observeTagsForDocument(docId).first().map { it.name }
        assertEquals(listOf("Urgent"), finalNames)
    }

    @Test
    fun deletingTagRemovesItFromDocuments() = runTest {
        val docId = documentDao.insert(documentEntity("Tagged"))
        val work = tagDao.insert(tagEntity("Work"))
        tagDao.setDocumentTags(docId, setOf(work))

        tagDao.delete(work)

        assertTrue(tagDao.observeTagsForDocument(docId).first().isEmpty())
    }
}
