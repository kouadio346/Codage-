package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.PrototypeProject
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PrototypeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Connexion Google requise pour accéder à vos prototypes.")
    }

    fun observePrototypes(userId: String): Flow<List<PrototypeProject>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/prototypes"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("prototypes")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PrototypeProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createPrototype(
        title: String,
        sourceLabel: String,
        htmlCode: String,
        cssCode: String,
        jsCode: String,
        aiNotes: String,
        interactiveFeatures: List<String>,
        favorite: Boolean = false,
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/prototypes"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().ifBlank { "Prototype sans titre" }.take(100)
            val cleanSource = sourceLabel.trim().take(120)
            val cleanHtml = htmlCode.take(120000)
            val cleanCss = cssCode.take(120000)
            val cleanJs = jsCode.take(120000)
            val cleanNotes = aiNotes.take(20000)
            val cleanFeatures = interactiveFeatures
                .map { it.trim().take(120) }
                .filter { it.isNotEmpty() }
                .take(15)

            val docRef = db.collection("users").document(uid).collection("prototypes").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "sourceLabel" to cleanSource,
                "htmlCode" to cleanHtml,
                "cssCode" to cleanCss,
                "jsCode" to cleanJs,
                "aiNotes" to cleanNotes,
                "interactiveFeatures" to cleanFeatures,
                "favorite" to favorite,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePrototype(
        prototypeId: String,
        title: String,
        sourceLabel: String,
        htmlCode: String,
        cssCode: String,
        jsCode: String,
        aiNotes: String,
        interactiveFeatures: List<String>,
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/prototypes/$prototypeId"
        return try {
            val uid = requireUserId()
            require(prototypeId.isNotBlank()) { "Identifiant du prototype requis pour la mise à jour." }
            val cleanTitle = title.trim().ifBlank { "Prototype sans titre" }.take(100)
            val cleanSource = sourceLabel.trim().take(120)
            val cleanFeatures = interactiveFeatures
                .map { it.trim().take(120) }
                .filter { it.isNotEmpty() }
                .take(15)

            val docRef = db.collection("users")
                .document(uid)
                .collection("prototypes")
                .document(prototypeId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "sourceLabel" to cleanSource,
                    "htmlCode" to htmlCode.take(120000),
                    "cssCode" to cssCode.take(120000),
                    "jsCode" to jsCode.take(120000),
                    "aiNotes" to aiNotes.take(20000),
                    "interactiveFeatures" to cleanFeatures,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(prototypeId: String, favorite: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/prototypes/$prototypeId"
        return try {
            val uid = requireUserId()
            require(prototypeId.isNotBlank()) { "Identifiant du prototype requis." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("prototypes")
                .document(prototypeId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "favorite" to favorite,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePrototype(prototypeId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/prototypes/$prototypeId"
        return try {
            val uid = requireUserId()
            require(prototypeId.isNotBlank()) { "Identifiant du prototype requis." }
            val docRef = db.collection("users")
                .document(uid)
                .collection("prototypes")
                .document(prototypeId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
