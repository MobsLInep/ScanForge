package com.scanforge.app

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.scanforge.core.data.backup.BackupStaging
import com.scanforge.core.domain.repository.SettingsRepository
import com.scanforge.core.domain.telemetry.AnalyticsTracker
import com.scanforge.core.domain.telemetry.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Provides WorkManager's [Configuration] so background OCR workers can be Hilt-injected, applies any
 * staged restore before Room opens, installs debug-only [StrictMode] policies, and wires the
 * privacy-respecting telemetry seam to the user's opt-in consent.
 */
@HiltAndroidApp
class ScanForgeApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var crashReporter: CrashReporter
    @Inject lateinit var analyticsTracker: AnalyticsTracker

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        if (BuildConfig.DEBUG) installStrictMode()
        super.onCreate()
        // Finish any staged restore before Room opens the database (swaps DB + image dirs into place).
        BackupStaging.applyPending(this)
        installCrashHandler()
        observeTelemetryConsent()
    }

    /**
     * Surface accidental main-thread disk/network I/O and leaked resources during development. Logs
     * only (never crashes) so debug builds stay usable while making regressions loud.
     */
    private fun installStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build(),
        )
    }

    /**
     * Chain an uncaught-exception handler that forwards fatal crashes to the (consent-gated) reporter
     * before delegating to the platform default. With the shipped no-op reporter this is inert unless
     * the user has opted in.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                crashReporter.recordException(throwable, mapOf("thread" to thread.name))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Keep both reporters in lock-step with the user's opt-in choices; OFF by default. */
    private fun observeTelemetryConsent() {
        appScope.launch {
            settingsRepository.observeSettings()
                .map { it.crashReportingEnabled to it.analyticsEnabled }
                .distinctUntilChanged()
                .onEach { (crash, analytics) ->
                    crashReporter.setEnabled(crash)
                    analyticsTracker.setEnabled(analytics)
                }
                .collect {}
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
