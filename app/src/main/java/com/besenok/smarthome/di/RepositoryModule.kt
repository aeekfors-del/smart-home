package com.besenok.smarthome.di

import com.besenok.smarthome.data.repository.IobrokerRepositoryImpl
import com.besenok.smarthome.data.repository.TimeRepositoryImpl
import com.besenok.smarthome.data.repository.WeatherRepositoryImpl
import com.besenok.smarthome.domain.repository.IobrokerRepository
import com.besenok.smarthome.domain.repository.TimeRepository
import com.besenok.smarthome.domain.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindIobrokerRepository(impl: IobrokerRepositoryImpl): IobrokerRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindTimeRepository(impl: TimeRepositoryImpl): TimeRepository
}
