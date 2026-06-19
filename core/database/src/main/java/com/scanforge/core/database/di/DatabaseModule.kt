package com.scanforge.core.database.di

import android.content.Context
import androidx.room.Room
import com.scanforge.core.database.ScanForgeDatabase
import com.scanforge.core.database.dao.DocumentDao
import com.scanforge.core.database.dao.FolderDao
import com.scanforge.core.database.dao.PageDao
import com.scanforge.core.database.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScanForgeDatabase =
        Room.databaseBuilder(context, ScanForgeDatabase::class.java, ScanForgeDatabase.NAME)
            // Foreign-key cascade (page/tag cleanup) relies on PRAGMA foreign_keys = ON.
            .addMigrations(
                ScanForgeDatabase.MIGRATION_1_2,
                ScanForgeDatabase.MIGRATION_2_3,
                ScanForgeDatabase.MIGRATION_3_4,
            )
            .build()

    @Provides
    fun provideDocumentDao(database: ScanForgeDatabase): DocumentDao = database.documentDao()

    @Provides
    fun providePageDao(database: ScanForgeDatabase): PageDao = database.pageDao()

    @Provides
    fun provideTagDao(database: ScanForgeDatabase): TagDao = database.tagDao()

    @Provides
    fun provideFolderDao(database: ScanForgeDatabase): FolderDao = database.folderDao()
}
