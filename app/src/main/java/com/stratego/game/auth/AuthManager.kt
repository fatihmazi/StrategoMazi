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
import com.google.android.gms.tasks.Task
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
 * Google Sign-In entegrasyonu ve kullanıcı durumu yönetimi
 */
class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    // Google Sign-In konfigürasyonu
    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("618450109098-ibq8bgunnp80vkk5hmh017dl2kp4hgi5.apps.googleusercontent.com")
        .requestEmail()
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, googleSignInOptions)

    /**
     * Mevcut kullanıcı durumunu Flow olarak döndürür
     */
    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        auth.addAuthStateListener(authStateListener)

        awaitClose {
            auth.removeAuthStateListener(authStateListener)
        }
    }.distinctUntilChanged()

    /**
     * Google Sign-In intent'i oluşturur
     */
    fun createGoogleSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    /**
     * Google Sign-In sonucunu işler
     */
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

    /**
     * Google hesabı ile Firebase'e giriş yapar
     */
    private suspend fun firebaseAuthWithGoogle(account: GoogleSignInAccount): AuthResult {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user

            if (user != null) {
                // Kullanıcı profilini veritabanına kaydet
                saveUserProfile(user, account)
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Firebase authentication başarısız")
            }
        } catch (e: Exception) {
            AuthResult.Error("Authentication hatası: ${e.message}")
        }
    }

    /**
     * Kullanıcı profilini Firebase Realtime Database'e kaydeder
     */
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

            // Kullanıcıyı online olarak işaretle
            setUserOnlineStatus(user.uid, true)

        } catch (e: Exception) {
            println("Kullanıcı profili kaydetme hatası: ${e.message}")
        }
    }

    /**
     * Kullanıcının online durumunu günceller
     */
    private fun setUserOnlineStatus(userId: String, isOnline: Boolean) {
        val userStatusRef = database.child("users").child(userId).child("isOnline")
        val lastSeenRef = database.child("users").child(userId).child("lastSeenAt")

        if (isOnline) {
            userStatusRef.setValue(true)
            // Kullanıcı çevrimdışı olduğunda otomatik güncelleme
            userStatusRef.onDisconnect().setValue(false)
            lastSeenRef.onDisconnect().setValue(System.currentTimeMillis())
        } else {
            userStatusRef.setValue(false)
            lastSeenRef.setValue(System.currentTimeMillis())
        }
    }

    /**
     * Kullanıcı çıkış işlemi
     */
    suspend fun signOut(): Boolean {
        return try {
            // Online durumunu false yap
            auth.currentUser?.let { user ->
                setUserOnlineStatus(user.uid, false)
            }

            // Firebase'den çıkış yap
            auth.signOut()

            // Google hesabından çıkış yap
            googleSignInClient.signOut().await()

            true
        } catch (e: Exception) {
            println("Sign out hatası: ${e.message}")
            false
        }
    }

    /**
     * Mevcut kullanıcıyı döndürür
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Kullanıcının giriş yapıp yapmadığını kontrol eder
     */
    fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }
}

/**
 * Authentication sonuç sınıfları
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Google Sign-In için Composable helper
 */
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

// Extension fonksiyonlar
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return kotlinx.coroutines.tasks.await(this)
}