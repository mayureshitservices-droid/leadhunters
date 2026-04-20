package com.example.leadhunters.di

import android.content.Context
import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.repository.AuthRepository
import com.example.leadhunters.data.repository.AuthRepositoryImpl
import com.example.leadhunters.data.repository.CallRepository
import com.example.leadhunters.data.repository.CallRepositoryImpl
import com.example.leadhunters.util.AnalyticsHelper
import com.example.leadhunters.data.repository.WorkRepository
import com.example.leadhunters.data.repository.WorkRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindWorkRepository(impl: WorkRepositoryImpl): WorkRepository

    companion object {
        @Provides
        @Singleton
        fun provideCallRepository(
            teleCallerDao: TeleCallerDao,
            @ApplicationContext context: Context,
            analyticsHelper: AnalyticsHelper
        ): CallRepository {
            return CallRepositoryImpl(teleCallerDao, context, analyticsHelper)
        }
    }
}
