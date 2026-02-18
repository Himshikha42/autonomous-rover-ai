package com.rover.ai.memory

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for memory system dependency injection.
 * 
 * Provides bindings for memory components:
 * - ShortTermMemory (already @Inject @Singleton)
 * - SpatialMap (already @Inject @Singleton)
 * - LongTermMemory (binds interface to stub implementation)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryModule {
    
    /**
     * Binds the LongTermMemory interface to its stub implementation.
     * Will be updated to bind to Room-based implementation once database is ready.
     */
    @Binds
    @Singleton
    abstract fun bindLongTermMemory(
        impl: LongTermMemoryStub
    ): LongTermMemory
}
