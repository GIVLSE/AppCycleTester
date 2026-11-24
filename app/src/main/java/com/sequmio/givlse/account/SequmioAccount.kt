package com.sequmio.givlse.account

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sequmio.givlse.BuildConfig
import kotlinx.coroutines.tasks.await

data class AccountProfile(
    val uid: String,
    val name: String,
    val email: String,
    val photoUrl: String?
)

/**
 * Real Google Credential Manager -> Firebase Authentication.
 * No local "signed-in" substitute. Firebase config is injected at build time.
 * Profile is written only after successful authenticated sign-in.
 */
class SequmioAccount(context: Context) {
    private val appContext = context.applicationContext
    val configured: Boolean =
        BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() &&
        BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
        BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
        BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    private val firebaseApp: FirebaseApp? = if (configured) {
        val existing = FirebaseApp.getApps(appContext).firstOrNull { it.name == "SequmioCloud" }
        existing ?: FirebaseApp.initializeApp(
            appContext,
            FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build(),
            "SequmioCloud"
        )
    } else null

    private val auth = firebaseApp?.let { FirebaseAuth.getInstance(it) }
    private val db = firebaseApp?.let { FirebaseFirestore.getInstance(it) }

    /** Public catalog can be read as a guest; community writes require sign-in. */
    fun cloudStore(): FirebaseFirestore? = db


    var profile: AccountProfile? by mutableStateOf(auth?.currentUser?.let { user ->
        AccountProfile(user.uid, user.displayName.orEmpty(), user.email.orEmpty(),
            user.photoUrl?.toString())
    })
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    private val authListener = FirebaseAuth.AuthStateListener { state ->
        val user = state.currentUser
        profile = user?.let {
            AccountProfile(it.uid, it.displayName.orEmpty(),
                it.email.orEmpty(), it.photoUrl?.toString())
        }
    }

    init { auth?.addAuthStateListener(authListener) }

    suspend fun signIn(activity: Activity) {
        val firebaseAuth = auth ?: run {
            status = "Google sign-in is not configured in this build."
            return
        }
        if (busy) return
        busy = true
        status = null
        try {
            val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val credential = CredentialManager.create(activity)
                .getCredential(context = activity, request = request).credential
            require(credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                "Unexpected Google credential type"
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val result = firebaseAuth.signInWithCredential(
                GoogleAuthProvider.getCredential(token, null)
            ).await()
            val user = requireNotNull(result.user) { "Firebase returned no user" }
            profile = AccountProfile(user.uid, user.displayName.orEmpty(),
                user.email.orEmpty(), user.photoUrl?.toString())

            // Owner-only Firestore security rules are provided in firestore.rules.
            try {
                db?.collection("users")?.document(user.uid)?.set(
                    mapOf(
                        "displayName" to user.displayName.orEmpty(),
                        "email" to user.email.orEmpty(),
                        "photoUrl" to (user.photoUrl?.toString() ?: ""),
                        "lastLogin" to com.google.firebase.Timestamp.now()
                    ),
                    SetOptions.merge()
                )?.await()
                status = "Signed in with Google."
            } catch (error: Exception) {
                status = "Signed in. Profile sync unavailable: " +
                    error.javaClass.simpleName
            }
        } catch (error: Exception) {
            status = "Sign-in failed or cancelled: " + error.javaClass.simpleName
        } finally {
            busy = false
        }
    }

    suspend fun signOut() {
        auth?.signOut()
        profile = null
        status = "Signed out."
        try {
            CredentialManager.create(appContext).clearCredentialState(
                ClearCredentialStateRequest()
            )
        } catch (_: Exception) { /* Local Firebase sign-out still succeeds. */ }
    }
}
