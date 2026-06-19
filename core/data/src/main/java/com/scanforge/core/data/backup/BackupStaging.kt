package com.scanforge.core.data.backup

import android.content.Context
import com.scanforge.core.database.ScanForgeDatabase
import java.io.File

/**
 * Restore is staged, never applied to a live database. [restoreBackup] extracts the archive into a
 * private staging directory and drops a [READY_MARKER]; [applyPending] — called from
 * `Application.onCreate` *before* Room opens the database — atomically swaps the staged DB and image
 * directories into place. This avoids corrupting an open SQLite handle (an app restart finishes it).
 */
object BackupStaging {
    private const val DIR = "restore_staging"
    private const val READY_MARKER = "READY"

    const val DB_DIR = "db"
    const val PAGES_DIR = "pages"
    const val THUMBS_DIR = "thumbnails"

    fun stagingDir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun markReady(context: Context) {
        File(stagingDir(context), READY_MARKER).writeText("1")
    }

    fun hasPending(context: Context): Boolean = File(stagingDir(context), READY_MARKER).exists()

    /**
     * If a restore is staged, move the DB files and page/thumbnail directories into their live
     * locations, then clear the staging area. Returns `true` if a restore was applied.
     */
    fun applyPending(context: Context): Boolean {
        val staging = stagingDir(context)
        if (!File(staging, READY_MARKER).exists()) return false

        // 1. Database files (scanforge.db + -wal/-shm), replacing the live ones.
        val liveDb = context.getDatabasePath(ScanForgeDatabase.NAME)
        liveDb.parentFile?.mkdirs()
        val stagedDb = File(staging, DB_DIR)
        if (stagedDb.isDirectory) {
            // Clear stale sidecars so a restored, checkpointed DB isn't shadowed by old WAL.
            listOf("", "-wal", "-shm").forEach { File(liveDb.path + it).delete() }
            stagedDb.listFiles()?.forEach { src ->
                src.copyTo(File(liveDb.parentFile, src.name), overwrite = true)
            }
        }

        // 2. Page + thumbnail directories, fully replaced.
        replaceDir(File(staging, PAGES_DIR), File(context.filesDir, "pages"))
        replaceDir(File(staging, THUMBS_DIR), File(context.filesDir, "thumbnails"))

        staging.deleteRecursively()
        return true
    }

    private fun replaceDir(staged: File, live: File) {
        if (!staged.isDirectory) return
        live.deleteRecursively()
        live.mkdirs()
        staged.listFiles()?.forEach { src -> src.copyTo(File(live, src.name), overwrite = true) }
    }
}
