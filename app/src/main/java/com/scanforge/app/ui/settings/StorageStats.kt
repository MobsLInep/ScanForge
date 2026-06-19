package com.scanforge.app.ui.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Storage breakdown surfaced in Settings. */
data class StorageUsage(
    val images: Long = 0,
    val thumbnails: Long = 0,
    val database: Long = 0,
    val cache: Long = 0,
) {
    val total: Long get() = images + thumbnails + database + cache
}

/** Measures on-device storage used by ScanForge and can clear the cache. */
@Singleton
class StorageStats @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun measure(): StorageUsage = withContext(Dispatchers.IO) {
        StorageUsage(
            images = dirSize(File(context.filesDir, "pages")),
            thumbnails = dirSize(File(context.filesDir, "thumbnails")),
            database = databaseSize(),
            cache = dirSize(context.cacheDir),
        )
    }

    /** Deletes cached files (e.g. generated exports). Returns bytes freed. */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val before = dirSize(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        before - dirSize(context.cacheDir)
    }

    private fun databaseSize(): Long {
        val db = context.getDatabasePath(DATABASE_NAME)
        return listOf(db, File(db.path + "-wal"), File(db.path + "-shm"))
            .filter { it.exists() }
            .sumOf { it.length() }
    }

    private companion object {
        // Mirrors ScanForgeDatabase.NAME; :app doesn't depend on :core:database directly.
        const val DATABASE_NAME = "scanforge.db"
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
