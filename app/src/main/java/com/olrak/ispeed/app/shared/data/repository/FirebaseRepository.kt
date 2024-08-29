package com.olrak.ispeed.app.shared.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.olrak.ispeed.app.shared.data.UserDetails
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class FirebaseRepository @Inject constructor(
    private val fireStore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {

    suspend fun getUserDetails() : UserDetails? {
        val details = fireStore.collection("User Information")
        .document(firebaseAuth.uid.orEmpty()).get().await()

        return details.toObject(UserDetails::class.java)
    }

    suspend fun forgotPassword(email: String): Boolean {
        val result = firebaseAuth.sendPasswordResetEmail(email)
        result.await()
        return result.isSuccessful
    }

    fun logoutUser() = firebaseAuth.signOut()
}