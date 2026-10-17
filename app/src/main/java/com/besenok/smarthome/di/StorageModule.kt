package com.besenok.smarthome.di

import android.content.Context
import androidx.room.Room
import com.besenok.smarthome.data.local.room.SmartHomeDatabase
import com.besenok.smarthome.data.local.room.dao.DeviceStateDao
import com.besenok.smarthome.data.local.room.dao.ScenarioDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideSmartHomeDatabase(@ApplicationContext context: Context): SmartHomeDatabase {
        return Room.databaseBuilder(
            context,
            SmartHomeDatabase::class.java,
            "smarthome_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDeviceStateDao(database: SmartHomeDatabase): DeviceStateDao {
        return database.deviceStateDao()
    }

    @Provides
    @Singleton
    fun provideScenarioDao(database: SmartHomeDatabase): ScenarioDao {
        return database.scenarioDao()
    }
}
