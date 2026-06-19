package com.scanforge.core.common.storage

/**
 * Pure, platform-free storage-headroom check used before space-hungry operations (export, backup,
 * import). Keeping the decision logic here makes "graceful low-storage handling" unit-testable on the
 * JVM; the Android side only has to supply the raw free-byte count (e.g. via `StatFs`).
 */
object StorageGuard {

    /** Always keep this much breathing room free so the system/UI doesn't get wedged. */
    const val DEFAULT_SAFETY_MARGIN_BYTES = 50L * 1024 * 1024 // 50 MB

    /**
     * @return whether [requiredBytes] can be written while still leaving [safetyMarginBytes] free.
     * A non-positive [requiredBytes] is always allowed; a negative [freeBytes] (unknown) is treated
     * as "no headroom" so callers fail closed rather than risk a mid-write ENOSPC.
     */
    fun hasHeadroom(
        freeBytes: Long,
        requiredBytes: Long,
        safetyMarginBytes: Long = DEFAULT_SAFETY_MARGIN_BYTES,
    ): Boolean {
        if (requiredBytes <= 0L) return true
        if (freeBytes < 0L) return false
        return freeBytes - requiredBytes >= safetyMarginBytes
    }

    /** Classify free space for messaging/telemetry without leaking the exact byte count. */
    fun classify(
        freeBytes: Long,
        requiredBytes: Long,
        safetyMarginBytes: Long = DEFAULT_SAFETY_MARGIN_BYTES,
    ): StorageStatus = when {
        hasHeadroom(freeBytes, requiredBytes, safetyMarginBytes) -> StorageStatus.Ok
        freeBytes >= 0L && freeBytes >= requiredBytes -> StorageStatus.Tight
        else -> StorageStatus.Insufficient
    }
}

/** Coarse storage outcome — never carries the raw byte count to keep it non-identifying. */
enum class StorageStatus {
    /** Enough free space plus the safety margin. */
    Ok,

    /** The write would fit but eats into the safety margin — warn but allow. */
    Tight,

    /** Not enough room; the operation should be blocked with a friendly message. */
    Insufficient,
}
