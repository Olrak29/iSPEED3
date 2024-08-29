package com.olrak.ispeed.app.core

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth()  = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseUser() = FirebaseAuth.getInstance().currentUser

    @Provides
    @Singleton
    fun provideFireStore() = Firebase.firestore
}