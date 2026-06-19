package com.scanforge.core.data.backup

import android.content.Context
import android.net.Uri
import com.scanforge.core.database.ScanForgeDatabase
import com.scanforge.core.domain.backup.BackupManager
import com.scanforge.core.domain.backup.BackupManifest
import com.scanforge.core.domain.backup.BackupResult
import com.scanforge.core.domain.backup.RestoreResult
import com.scanforge.core.domain.repository.DocumentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android [BackupManager]: gathers the Room database files and the page/thumbnail image
 * directories into an (optionally encrypted) archive via [BackupArchiver], streamed to/from a
 * user-chosen SAF location. Restore is *staged* — see [BackupStaging] — and finished on app restart.
 */
@Singleton
class AndroidBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documents: DocumentRepository,
) : BackupManager {

    private val archiver = BackupArchiver()

    override suspend fun createBackup(destinationUri: String, password: String): BackupResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val dbFiles = databaseFiles()
                val pageFiles = imageFiles(File(context.filesDir, "pages"))
                val thumbFiles = imageFiles(File(context.filesDir, "thumbnails"))
                val documentCount = documents.observeDocuments().first().size

                val manifest = BackupManifest(
                    appVersion = appVersion(),
                    databaseVersion = ScanForgeDatabase.VERSION,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    documentCount = documentCount,
                    imageCount = pageFiles.size + thumbFiles.size,
                    encrypted = password.isNotBlank(),
                )

                val entries = sequence {
                    dbFiles.forEach { yield(BackupEntry("${BackupStaging.DB_DIR}/${it.name}", it.readBytes())) }
                    pageFiles.forEach { yield(BackupEntry("${BackupStaging.PAGES_DIR}/${it.name}", it.readBytes())) }
                    thumbFiles.forEach { yield(BackupEntry("${BackupStaging.THUMBS_DIR}/${it.name}", it.readBytes())) }
                }

                val out = context.contentResolver.openOutputStream(Uri.parse(destinationUri))
                    ?: error("Cannot open destination")
                out.use { archiver.write(it, manifest, entries, password.ifBlank { null }) }

                val size = runCatching {
                    context.contentResolver.openFileDescriptor(Uri.parse(destinationUri), "r")
                        ?.use { it.statSize } ?: 0L
                }.getOrDefault(0L)
                BackupResult.Success(manifest, size)
            }.getOrElse { BackupResult.Failure(it.message ?: "Backup failed") }
        }

    override suspend fun peek(sourceUri: String, password: String): RestoreResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val manifest = context.contentResolver.openInputStream(Uri.parse(sourceUri))
                    ?.use { archiver.readManifest(it) }
                    ?: return@withContext RestoreResult.InvalidArchive
                if (manifest.databaseVersion > ScanForgeDatabase.VERSION) RestoreResult.Incompatible(manifest)
                else RestoreResult.Success(manifest)
            }.getOrElse {
                if (it is InvalidBackupException) RestoreResult.InvalidArchive
                else RestoreResult.Failure(it.message ?: "Could not read backup")
            }
        }

    override suspend fun restoreBackup(sourceUri: String, password: String): RestoreResult =
        withContext(Dispatchers.IO) {
            // Validate first so an incompatible/invalid archive never clears the staging area.
            when (val pre = peek(sourceUri, password)) {
                is RestoreResult.Incompatible, is RestoreResult.InvalidArchive, is RestoreResult.Failure -> return@withContext pre
                else -> Unit
            }

            val staging = BackupStaging.stagingDir(context).also { it.deleteRecursively(); it.mkdirs() }
            runCatching {
                val manifest = context.contentResolver.openInputStream(Uri.parse(sourceUri))!!.use { input ->
                    archiver.read(input, password.ifBlank { null }) { name, bytes ->
                        val target = File(staging, name)
                        target.parentFile?.mkdirs()
                        target.writeBytes(bytes)
                    }
                }
                BackupStaging.markReady(context)
                RestoreResult.Success(manifest)
            }.getOrElse {
                staging.deleteRecursively()
                when (it) {
                    is WrongBackupPasswordException -> RestoreResult.WrongPassword
                    is InvalidBackupException -> RestoreResult.InvalidArchive
                    else -> RestoreResult.Failure(it.message ?: "Restore failed")
                }
            }
        }

    /** The live DB file plus any WAL/SHM sidecars that exist. */
    private fun databaseFiles(): List<File> {
        val db = context.getDatabasePath(ScanForgeDatabase.NAME)
        return listOf(db, File(db.path + "-wal"), File(db.path + "-shm")).filter { it.exists() }
    }

    private fun imageFiles(dir: File): List<File> =
        dir.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}
