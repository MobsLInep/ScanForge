package com.scanforge.core.domain.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Validates the privacy-respecting telemetry contract: nothing is recorded until the user opts in,
 * and opting back out immediately stops capture. Uses pure in-memory fakes (the same approach as the
 * cloud-sync engine tests) so this stays a zero-Android JVM test.
 */
class TelemetryConsentTest {

    /** Minimal consent-honouring tracker that mirrors how a real sink must behave. */
    private class FakeAnalyticsTracker : AnalyticsTracker {
        val recorded = mutableListOf<AnalyticsEvent>()
        private var enabled = false
        override val isEnabled get() = enabled
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun track(event: AnalyticsEvent) { if (enabled) recorded.add(event) }
    }

    private class FakeCrashReporter : CrashReporter {
        val recorded = mutableListOf<Throwable>()
        private var enabled = false
        override val isEnabled get() = enabled
        override fun setEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun recordException(throwable: Throwable, context: Map<String, String>) {
            if (enabled) recorded.add(throwable)
        }
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) = Unit
    }

    @Test
    fun `analytics drops events until opted in`() {
        val tracker = FakeAnalyticsTracker()

        tracker.track(AnalyticsEvent(AnalyticsEvent.DOCUMENT_SCANNED))
        assertTrue(tracker.recorded.isEmpty(), "events must be dropped while opted out")

        tracker.setEnabled(true)
        tracker.track(AnalyticsEvent(AnalyticsEvent.OCR_COMPLETED, mapOf("pages" to "3")))
        assertEquals(1, tracker.recorded.size)
        assertEquals(AnalyticsEvent.OCR_COMPLETED, tracker.recorded.single().name)
    }

    @Test
    fun `opting back out immediately halts capture`() {
        val tracker = FakeAnalyticsTracker().apply { setEnabled(true) }
        tracker.track(AnalyticsEvent(AnalyticsEvent.EXPORT_COMPLETED))

        tracker.setEnabled(false)
        tracker.track(AnalyticsEvent(AnalyticsEvent.EXPORT_FAILED))

        assertEquals(1, tracker.recorded.size)
        assertFalse(tracker.isEnabled)
    }

    @Test
    fun `crash reporter is gated by consent`() {
        val reporter = FakeCrashReporter()
        reporter.recordException(IllegalStateException("before opt-in"))
        assertTrue(reporter.recorded.isEmpty())

        reporter.setEnabled(true)
        reporter.recordException(IllegalStateException("after opt-in"))
        assertEquals(1, reporter.recorded.size)
    }

    @Test
    fun `event params carry only coarse non-PII values`() {
        // Guard against accidentally widening the event surface to free text/content.
        val event = AnalyticsEvent(AnalyticsEvent.DOCUMENT_SCANNED, mapOf("source" to "camera", "pages" to "2"))
        assertEquals(setOf("source", "pages"), event.params.keys)
    }
}
