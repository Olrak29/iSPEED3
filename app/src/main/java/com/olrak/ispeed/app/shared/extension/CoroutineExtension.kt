package com.olrak.ispeed.app.shared.extension

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.CancellationException

@Suppress("RedundantSuspendModifier")
suspend inline fun <T, R> T.coRunCatching(block: T.() -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val listenerRegistration = addSnapshotListener { value, error ->
        if (error != null) {
            close()
            return@addSnapshotListener
        }
        if (value != null) trySend(value)
    }
    awaitClose(listenerRegistration::remove)
}