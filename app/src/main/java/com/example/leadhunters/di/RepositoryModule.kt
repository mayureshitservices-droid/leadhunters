package com.example.leadhunters.di

import com.example.leadhunters.data.local.dao.TeleCallerDao
import com.example.leadhunters.data.repository.AuthRepository
import com.example.leadhunters.data.repository.AuthRepositoryImpl
import com.example.leadhunters.data.repository.CallRepository
import com.example.leadhunters.data.repository.CallRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    companion object {
        @Provides
        @Singleton
        fun provideCallRepository(teleCallerDao: TeleCallerDao): CallRepository {
            return CallRepositoryImpl(teleCallerDao)
        }
    }
}
