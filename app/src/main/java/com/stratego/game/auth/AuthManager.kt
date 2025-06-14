package com.stratego.game.auth

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Authentication işlemlerini yöneten sınıf
 */
class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("618450109098-ibq8bgunnp80vkk5hmh017dl2kp4hgi5.apps.googleusercontent.com")
        .requestEmail()
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        auth.addAuthStateListener(authStateListener)

        awaitClose {
            auth.removeAuthStateListener(authStateListener)
        }
    }.distinctUntilChanged()

    fun createGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    suspend fun handleGoogleSignInResult(data: Intent?): AuthResult {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            if (account != null) {
                firebaseAuthWithGoogle(account)
            } else {
                AuthResult.Error("Google hesabı bilgileri alınamadı")
            }
        } catch (e: ApiException) {
            AuthResult.Error("Google Sign-In hatası: ${e.message}")
        }
    }

    private suspend fun firebaseAuthWithGoogle(account: GoogleSignInAccount): AuthResult {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user

            if (user != null) {
                saveUserProfile(user, account)
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Firebase authentication başarısız")
            }
        } catch (e: Exception) {
            AuthResult.Error("Authentication hatası: ${e.message}")
        }
    }

    private suspend fun saveUserProfile(user: FirebaseUser, account: GoogleSignInAccount) {
        try {
            val userProfile = mapOf(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: account.displayName ?: "Anonim Oyuncu"),
                "email" to (user.email ?: account.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: account.photoUrl?.toString() ?: ""),
                "createdAt" to System.currentTimeMillis(),
                "lastSeenAt" to System.currentTimeMillis(),
                "isOnline" to true,
                "gamesPlayed" to 0,
                "gamesWon" to 0,
                "currentStreak" to 0,
                "bestStreak" to 0
            )

            database.child("users").child(user.uid).setValue(userProfile).await()
            setUserOnlineStatus(user.uid, true)

        } catch (e: Exception) {
            println("Kullanıcı profili kaydetme hatası: ${e.message}")
        }
    }

    private fun setUserOnlineStatus(userId: String, isOnline: Boolean) {
        val userStatusRef = database.child("users").child(userId).child("isOnline")
        val lastSeenRef = database.child("users").child(userId).child("lastSeenAt")

        if (isOnline) {
            userStatusRef.setValue(true)
            userStatusRef.onDisconnect().setValue(false)
            lastSeenRef.onDisconnect().setValue(System.currentTimeMillis())
        } else {
            userStatusRef.setValue(false)
            lastSeenRef.setValue(System.currentTimeMillis())
        }
    }

    suspend fun signOut(): Boolean {
        return try {
            auth.currentUser?.let { user ->
                setUserOnlineStatus(user.uid, false)
            }

            auth.signOut()
            googleSignInClient.signOut().await()

            true
        } catch (e: Exception) {
            println("Sign out hatası: ${e.message}")
            false
        }
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }
}

sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Composable
fun rememberGoogleSignInLauncher(
    onResult: (AuthResult) -> Unit
): ActivityResultLauncher<Intent> {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }

    return androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        CoroutineScope(Dispatchers.Main).launch {
            val authResult = authManager.handleGoogleSignInResult(result.data)
            onResult(authResult)
        }
    }
}