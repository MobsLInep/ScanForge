@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.scanforge.app.ui.settings

import com.scanforge.core.domain.backup.BackupManager
import com.scanforge.core.domain.backup.BackupManifest
import com.scanforge.core.domain.backup.BackupResult
import com.scanforge.core.domain.model.ScanSettings
import com.scanforge.core.domain.repository.SettingsRepository
import com.scanforge.core.domain.sync.SyncScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsViewModelTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val backupManager: BackupManager = mockk(relaxed = true)
    private val syncScheduler: SyncScheduler = mockk(relaxed = true)
    private val storageStats: StorageStats = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { settingsRepository.observeSettings() } returns flowOf(ScanSettings())
        coEvery { storageStats.measure() } returns StorageUsage()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        SettingsViewModel(settingsRepository, backupManager, syncScheduler, storageStats)

    @Test
    fun `enabling sync persists the flag and schedules background sync`() = runTest {
        val vm = viewModel()
        vm.setSyncEnabled(true)

        verify { syncScheduler.enable() }
        coVerify { settingsRepository.updateSettings(any()) }
    }

    @Test
    fun `disabling sync cancels scheduled sync`() = runTest {
        val vm = viewModel()
        vm.setSyncEnabled(false)

        verify { syncScheduler.disable() }
    }

    @Test
    fun `retention is clamped to the allowed range`() = runTest {
        val vm = viewModel()
        val transform = slot<(ScanSettings) -> ScanSettings>()
        coEvery { settingsRepository.updateSettings(capture(transform)) } returns Unit

        vm.setRetention(10_000) // far above the max

        val result = transform.captured(ScanSettings())
        assertEquals(SettingsViewModel.MAX_DAYS, result.trashRetentionDays)
    }

    @Test
    fun `successful backup surfaces a success message`() = runTest {
        coEvery { backupManager.createBackup(any(), any()) } returns
            BackupResult.Success(
                manifest = BackupManifest(
                    appVersion = "x", databaseVersion = 4, createdAtEpochMillis = 0,
                    documentCount = 3, imageCount = 4, encrypted = true,
                ),
                bytesWritten = 2048,
            )

        val vm = viewModel()
        vm.backup("content://out.zip", password = "pw")

        assertEquals("backup_ok:2048", vm.uiState.value.message)
        assertEquals(SettingsBusy.None, vm.uiState.value.busy)
    }
}
