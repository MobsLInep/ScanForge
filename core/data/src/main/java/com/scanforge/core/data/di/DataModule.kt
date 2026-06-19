package com.scanforge.core.data.di

import com.scanforge.core.data.backup.AndroidBackupManager
import com.scanforge.core.data.repository.DocumentRepositoryImpl
import com.scanforge.core.data.repository.FolderRepositoryImpl
import com.scanforge.core.data.repository.SettingsRepositoryImpl
import com.scanforge.core.data.repository.TagRepositoryImpl
import com.scanforge.core.data.scanning.AndroidPageImageStore
import com.scanforge.core.data.scanning.AndroidPageImporter
import com.scanforge.core.data.scanning.OpenCvEdgeDetector
import com.scanforge.core.data.sync.GoogleDriveSyncProvider
import com.scanforge.core.data.telemetry.NoOpAnalyticsTracker
import com.scanforge.core.data.telemetry.NoOpCrashReporter
import com.scanforge.core.domain.backup.BackupManager
import com.scanforge.core.domain.repository.DocumentRepository
import com.scanforge.core.domain.repository.FolderRepository
import com.scanforge.core.domain.repository.SettingsRepository
import com.scanforge.core.domain.repository.TagRepository
import com.scanforge.core.domain.scanning.EdgeDetector
import com.scanforge.core.domain.scanning.PageImageStore
import com.scanforge.core.domain.scanning.PageImporter
import com.scanforge.core.domain.sync.CloudSyncProvider
import com.scanforge.core.domain.telemetry.AnalyticsTracker
import com.scanforge.core.domain.telemetry.CrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds domain repository contracts to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindEdgeDetector(impl: OpenCvEdgeDetector): EdgeDetector

    @Binds
    @Singleton
    abstract fun bindPageImageStore(impl: AndroidPageImageStore): PageImageStore

    @Binds
    @Singleton
    abstract fun bindPageImporter(impl: AndroidPageImporter): PageImporter

    @Binds
    @Singleton
    abstract fun bindBackupManager(impl: AndroidBackupManager): BackupManager

    /** The cloud provider behind the (off-by-default) sync flag. Drive ships as a stub for now. */
    @Binds
    @Singleton
    abstract fun bindCloudSyncProvider(impl: GoogleDriveSyncProvider): CloudSyncProvider

    /** On-device-first telemetry: no-op reporters gated behind off-by-default opt-in flags. */
    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: NoOpCrashReporter): CrashReporter

    @Binds
    @Singleton
    abstract fun bindAnalyticsTracker(impl: NoOpAnalyticsTracker): AnalyticsTracker
}
