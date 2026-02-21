package com.rover.ai.di

import com.rover.ai.ai.litert.LiteRtModelManager
import com.rover.ai.ai.litert.LiteRtModelManagerImpl
import com.rover.ai.ai.vision.DepthEstimator
import com.rover.ai.ai.vision.DepthEstimatorImpl
import com.rover.ai.ai.vision.YoloDetector
import com.rover.ai.ai.vision.YoloDetectorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for AI/ML dependency injection.
 *
 * Provides bindings for AI model interfaces to their implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindLiteRtModelManager(
        impl: LiteRtModelManagerImpl
    ): LiteRtModelManager

    @Binds
    @Singleton
    abstract fun bindYoloDetector(
        impl: YoloDetectorImpl
    ): YoloDetector

    @Binds
    @Singleton
    abstract fun bindDepthEstimator(
        impl: DepthEstimatorImpl
    ): DepthEstimator
}
