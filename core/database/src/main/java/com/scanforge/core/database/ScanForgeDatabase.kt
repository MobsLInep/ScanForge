package com.scanforge.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.scanforge.core.database.dao.DocumentDao
import com.scanforge.core.database.dao.FolderDao
import com.scanforge.core.database.dao.PageDao
import com.scanforge.core.database.dao.TagDao
import com.scanforge.core.database.entity.DocumentEntity
import com.scanforge.core.database.entity.DocumentFtsEntity
import com.scanforge.core.database.entity.DocumentTagCrossRef
import com.scanforge.core.database.entity.FolderEntity
import com.scanforge.core.database.entity.PageEntity
import com.scanforge.core.database.entity.TagEntity

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class,
        TagEntity::class,
        DocumentTagCrossRef::class,
        FolderEntity::class,
        DocumentFtsEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ScanForgeDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val NAME = "scanforge.db"

        /** Current Room schema version; mirrors the `@Database(version = …)` above. Used by backup. */
        const val VERSION = 4

        /** v2 adds the non-destructive edit recipe column (`pages.processing_params`). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN processing_params TEXT")
            }
        }

        /** v3 adds the structured OCR output column (`pages.ocr_blocks`). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN ocr_blocks TEXT")
            }
        }

        /**
         * v4 turns ScanForge into a document manager: favourites, folders (nestable collections),
         * soft-delete trash, size-for-sorting, and a full-text search index. Defaults match the
         * entity `@ColumnInfo` defaults so Room's schema validation passes.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documents ADD COLUMN folder_id INTEGER")
                db.execSQL("ALTER TABLE documents ADD COLUMN size_bytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documents ADD COLUMN deleted_at INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_folder_id ON documents(folder_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_deleted_at ON documents(deleted_at)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        parent_id INTEGER,
                        color_hex TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_parent_id ON folders(parent_id)")

                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS document_fts USING FTS4(`title`, `body`, `tags`)",
                )
            }
        }
    }
}
