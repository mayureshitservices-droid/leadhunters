package com.example.leadhunters.di

import android.content.Context
import androidx.room.Room
import com.example.leadhunters.data.local.AppDatabase
import com.example.leadhunters.data.local.dao.TeleCallerDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lead_hunters_db"
        )
        .fallbackToDestructiveMigration(false)
        .build()
    }

    @Provides
    fun provideTeleCallerDao(database: AppDatabase): TeleCallerDao {
        return database.teleCallerDao()
    }
}
