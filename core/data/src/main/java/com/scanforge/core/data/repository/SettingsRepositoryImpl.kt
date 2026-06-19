package com.scanforge.core.data.repository

import com.scanforge.core.datastore.ScanForgePreferencesDataSource
import com.scanforge.core.domain.model.ScanSettings
import com.scanforge.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val preferences: ScanForgePreferencesDataSource,
) : SettingsRepository {

    override fun observeSettings(): Flow<ScanSettings> = preferences.settings

    override suspend fun updateSettings(transform: (ScanSettings) -> ScanSettings) =
        preferences.update(transform)

    override fun observeOnboardingComplete(): Flow<Boolean> = preferences.onboardingComplete

    override suspend fun setOnboardingComplete(complete: Boolean) =
        preferences.setOnboardingComplete(complete)
}
