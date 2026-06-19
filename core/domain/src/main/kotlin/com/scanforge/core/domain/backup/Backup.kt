package com.scanforge.core.domain.backup

import kotlinx.serialization.Serializable

/**
 * Metadata header embedded in a ScanForge backup archive. Travels as JSON inside the (encrypted) zip
 * so a restore can validate compatibility and report what it is about to import before touching data.
 *
 * Pure value object so the JSON round-trip is unit-testable on the JVM with no Android dependency.
 */
@Serializable
data class BackupManifest(
    /** Archive schema version; bumped if the on-disk layout changes. */
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** App `versionName` that produced the archive, for diagnostics. */
    val appVersion: String,
    /** Room schema version at backup time, so a restore can refuse a newer DB. */
    val databaseVersion: Int,
    /** Epoch millis the backup was created. */
    val createdAtEpochMillis: Long,
    /** Number of documents captured, shown in the restore confirmation. */
    val documentCount: Int,
    /** Number of page image files bundled. */
    val imageCount: Int,
    /** Whether the payload entries are AES-encrypted. */
    val encrypted: Boolean,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1

        /** Manifest entry name inside the archive (always stored in clear text). */
        const val ENTRY_NAME = "manifest.json"
    }
}

/** Outcome of writing a backup archive. */
sealed interface BackupResult {
    data class Success(val manifest: BackupManifest, val bytesWritten: Long) : BackupResult
    data class Failure(val reason: String) : BackupResult
}

/** Outcome of restoring from a backup archive. */
sealed interface RestoreResult {
    data class Success(val manifest: BackupManifest) : RestoreResult

    /** The supplied password did not decrypt the archive. */
    data object WrongPassword : RestoreResult

    /** The archive is not a ScanForge backup or its manifest is unreadable. */
    data object InvalidArchive : RestoreResult

    /** The archive was produced by a newer, incompatible app/database version. */
    data class Incompatible(val manifest: BackupManifest) : RestoreResult

    data class Failure(val reason: String) : RestoreResult
}

/**
 * Exports/imports a full ScanForge backup (database + page images) as a single, optionally
 * password-encrypted archive. The Android implementation streams to/from a user-chosen location
 * (SAF). Restore replaces local data and requires an app restart to reopen the database.
 */
interface BackupManager {
    /**
     * Writes a backup to [destinationUri]. When [password] is non-blank the payload is AES-encrypted;
     * the [BackupManifest] header is always readable so a restore can report what it holds.
     */
    suspend fun createBackup(destinationUri: String, password: String): BackupResult

    /** Reads the manifest without importing, so the UI can confirm before overwriting. */
    suspend fun peek(sourceUri: String, password: String): RestoreResult

    /** Restores from [sourceUri], replacing local database and image files. */
    suspend fun restoreBackup(sourceUri: String, password: String): RestoreResult
}
