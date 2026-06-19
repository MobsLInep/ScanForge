package com.scanforge.core.imaging.di

import com.scanforge.core.domain.imaging.ImagePipeline
import com.scanforge.core.imaging.OpenCvImagePipeline
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the OpenCV-backed imaging pipeline to the domain [ImagePipeline] contract. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ImagingModule {

    @Binds
    @Singleton
    abstract fun bindImagePipeline(impl: OpenCvImagePipeline): ImagePipeline
}
