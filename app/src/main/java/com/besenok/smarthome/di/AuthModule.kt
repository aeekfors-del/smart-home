package com.besenok.smarthome.di

import com.besenok.smarthome.util.RateLimiter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideRateLimiter(): RateLimiter {
        return RateLimiter(maxRequests = 10, windowSeconds = 1)
    }
}
