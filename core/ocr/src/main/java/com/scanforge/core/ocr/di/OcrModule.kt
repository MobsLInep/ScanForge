package com.scanforge.core.ocr.di

import com.scanforge.core.domain.ocr.OcrEngine
import com.scanforge.core.domain.ocr.OcrScheduler
import com.scanforge.core.ocr.MlKitOcrEngine
import com.scanforge.core.ocr.WorkManagerOcrScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the ML Kit OCR engine and the WorkManager scheduler to their domain contracts. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: MlKitOcrEngine): OcrEngine

    @Binds
    @Singleton
    abstract fun bindOcrScheduler(impl: WorkManagerOcrScheduler): OcrScheduler
}
