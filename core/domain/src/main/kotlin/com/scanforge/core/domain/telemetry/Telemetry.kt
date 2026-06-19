package com.scanforge.core.domain.telemetry

/**
 * Privacy-respecting telemetry seams. Both contracts are pure Kotlin (zero Android) so the domain
 * stays platform-free and they are trivially fakeable in tests.
 *
 * ScanForge is on-device first: the shipped implementations are no-ops and every reporter is gated
 * behind an explicit, off-by-default opt-in ([com.scanforge.core.domain.model.ScanSettings]). A real
 * backend (e.g. Firebase Crashlytics) can be dropped in behind these interfaces without touching
 * call sites — the same approach used for the cloud-sync provider.
 */

/** Records non-fatal/fatal exceptions and breadcrumb logs when the user has opted in. */
interface CrashReporter {
    /** Whether reports are actually delivered. The shipped no-op reporter is never enabled. */
    val isEnabled: Boolean

    /** Turn delivery on/off in response to the user's consent choice. */
    fun setEnabled(enabled: Boolean)

    /** Record a handled/unhandled [throwable] with optional non-PII key/value context. */
    fun recordException(throwable: Throwable, context: Map<String, String> = emptyMap())

    /** Leave a breadcrumb that accompanies the next recorded exception. Must not contain PII. */
    fun log(message: String)

    /** Attach a non-PII custom key (build flavour, screen name, …) to subsequent reports. */
    fun setCustomKey(key: String, value: String)
}

/**
 * A single, privacy-respecting analytics event. Events describe *what kind of thing* happened, never
 * the user's content — no document titles, OCR text, file paths or free text are ever carried here.
 */
data class AnalyticsEvent(
    val name: String,
    /** Coarse, non-identifying parameters only (counts, enum names, booleans as strings). */
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        // Canonical event names kept in one place so they stay stable and reviewable.
        const val DOCUMENT_SCANNED = "document_scanned"
        const val OCR_COMPLETED = "ocr_completed"
        const val OCR_FAILED = "ocr_failed"
        const val EXPORT_COMPLETED = "export_completed"
        const val EXPORT_FAILED = "export_failed"
        const val BACKUP_CREATED = "backup_created"
        const val RESTORE_COMPLETED = "restore_completed"
    }
}

/** Records [AnalyticsEvent]s when the user has opted in. Opt-out drops everything on the floor. */
interface AnalyticsTracker {
    val isEnabled: Boolean

    fun setEnabled(enabled: Boolean)

    fun track(event: AnalyticsEvent)
}
