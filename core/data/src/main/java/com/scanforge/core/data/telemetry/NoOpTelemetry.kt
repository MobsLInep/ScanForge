package com.scanforge.core.data.telemetry

import android.util.Log
import com.scanforge.core.domain.telemetry.AnalyticsEvent
import com.scanforge.core.domain.telemetry.AnalyticsTracker
import com.scanforge.core.domain.telemetry.CrashReporter
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The shipped, on-device-first crash reporter: it honours the consent flag but never sends anything
 * off the device. A real Crashlytics-backed reporter can replace this binding in [TelemetryModule]
 * without touching any call site. When opted in (debug builds), reports are echoed to Logcat so the
 * seam is observable during development; in release this is effectively inert.
 */
@Singleton
class NoOpCrashReporter @Inject constructor() : CrashReporter {

    private val enabled = AtomicBoolean(false)

    override val isEnabled: Boolean get() = enabled.get()

    override fun setEnabled(enabled: Boolean) {
        this.enabled.set(enabled)
    }

    override fun recordException(throwable: Throwable, context: Map<String, String>) {
        if (!enabled.get()) return
        Log.w(TAG, "recordException ${context}", throwable)
    }

    override fun log(message: String) {
        if (!enabled.get()) return
        Log.d(TAG, message)
    }

    override fun setCustomKey(key: String, value: String) {
        if (!enabled.get()) return
        Log.d(TAG, "key $key=$value")
    }

    private companion object {
        const val TAG = "ScanForgeCrash"
    }
}

/**
 * The shipped analytics tracker. Opt-out (the default) drops every event; opt-in only logs the event
 * name/params to Logcat — no network, no persistence, no identifiers. This is the privacy-respecting
 * default; swap the binding for a real sink if/when a backend is ever introduced.
 */
@Singleton
class NoOpAnalyticsTracker @Inject constructor() : AnalyticsTracker {

    private val enabled = AtomicBoolean(false)

    override val isEnabled: Boolean get() = enabled.get()

    override fun setEnabled(enabled: Boolean) {
        this.enabled.set(enabled)
    }

    override fun track(event: AnalyticsEvent) {
        if (!enabled.get()) return
        Log.d(TAG, "${event.name} ${event.params}")
    }

    private companion object {
        const val TAG = "ScanForgeAnalytics"
    }
}
