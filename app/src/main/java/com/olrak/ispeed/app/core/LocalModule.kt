package com.olrak.ispeed.app.core

import android.content.Context
import com.olrak.ispeed.app.local.iSpeedClient
import com.olrak.ispeed.app.local.iSpeedDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class LocalModule {

    @Singleton
    @Provides
    fun executeRoomInstance(
        @ApplicationContext
        context: Context,
    ) = iSpeedClient.getInstance(context)

    @Singleton
    @Provides
    fun executeDatabaseDao(
        databaseService: iSpeedDatabase
    ) = databaseService.trackInternetDao()
}