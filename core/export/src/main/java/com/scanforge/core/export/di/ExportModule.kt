package com.scanforge.core.export.di

import com.scanforge.core.domain.export.ExportManager
import com.scanforge.core.export.WorkManagerExportManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the WorkManager-backed exporter to the domain [ExportManager] contract. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds
    @Singleton
    abstract fun bindExportManager(impl: WorkManagerExportManager): ExportManager
}
