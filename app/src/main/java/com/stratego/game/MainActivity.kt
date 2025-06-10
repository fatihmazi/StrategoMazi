package com.stratego.game

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.stratego.game.models.Player
import com.stratego.game.auth.AuthManager
import com.stratego.game.auth.AuthResult
import com.stratego.game.auth.rememberGoogleSignInLauncher
import com.stratego.game.data.FirebaseRepository
import com.stratego.game.ui.theme.StrategoTheme
import kotlinx.coroutines.launch

/**
 * Ana Activity - Oyun giriş ve lobi yönetimi
 */
class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase başlatma
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        setContent {
            StrategoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StrategoMainScreen()
                }
            }
        }
    }
}

/**
 * Ana ekran - Giriş kontrolü ve lobi yönlendirmesi
 */
@Composable
fun StrategoMainScreen() {
    var isLoggedIn by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<UserProfile?>(null) }

    // Kullanıcı giriş durumu kontrolü
    LaunchedEffect(Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            isLoggedIn = true
            currentUser = UserProfile(
                uid = user.uid,
                displayName = user.displayName ?: "Anonim Oyuncu",
                email = user.email ?: "",
                photoUrl = user.photoUrl?.toString() ?: ""
            )
        }
    }

    if (isLoggedIn && currentUser != null) {
        GameLobbyScreen(currentUser!!)
    } else {
        LoginScreen(onLoginSuccess = { user ->
            currentUser = user
            isLoggedIn = true
        })
    }
}

/**
 * Kullanıcı profil veri sınıfı
 */
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val currentStreak: Int = 0,
    val isOnline: Boolean = false
)

/**
 * Oyun daveti veri sınıfı
 */
data class GameInvite(
    val id: String = "",
    val fromUserId: String = "",
    val fromUserName: String = "",
    val toUserId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "pending" // pending, accepted, declined, expired
)

/**
 * Giriş ekranı bileşeni
 */
@Composable
fun LoginScreen(onLoginSuccess: (UserProfile) -> Unit) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberGoogleSignInLauncher { result ->
        isLoading = false
        when (result) {
            is AuthResult.Success -> {
                val user = UserProfile(
                    uid = result.user.uid,
                    displayName = result.user.displayName ?: "Anonim Oyuncu",
                    email = result.user.email ?: "",
                    photoUrl = result.user.photoUrl?.toString() ?: ""
                )
                onLoginSuccess(user)
            }
            is AuthResult.Error -> {
                errorMessage = result.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Oyun logosu
        Text(
            text = "🏰 STRATEGO",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Online Strateji Oyunu",
            fontSize = 18.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(64.dp))

        // Google ile giriş butonu
        Button(
            onClick = {
                if (!isLoading) {
                    isLoading = true
                    errorMessage = null
                    val signInIntent = authManager.createGoogleSignInIntent()
                    googleSignInLauncher.launch(signInIntent)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4285F4)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Giriş yapılıyor...")
            } else {
                Text(
                    text = "🔐 Google ile Giriş Yap",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Hata mesajı
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Text(
                    text = "❌ $errorMessage",
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFFC62828),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Oyuna katılmak için Google hesabınızla\ngiriş yapmanız gerekmektedir.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

/**
 * Ana oyun lobi ekranı
 */
@Composable
fun GameLobbyScreen(user: UserProfile) {
    var onlineUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var incomingInvites by remember { mutableStateOf<List<GameInvite>>(emptyList()) }
    var isSearchingRandomGame by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    val firebaseRepository = remember { FirebaseRepository() }
    val scope = rememberCoroutineScope()

    // Mock data ile test
    LaunchedEffect(Unit) {
        onlineUsers = listOf(
            UserProfile("user1", "Ahmet_85", "ahmet@example.com", "", 15, 10, 3, true),
            UserProfile("user2", "ZeynepStr", "zeynep@example.com", "", 8, 6, 2, true),
            UserProfile("user3", "CanKing", "can@example.com", "", 22, 14, 1, true)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Üst bar - kullanıcı bilgisi ve menü
        TopAppBar(user = user, onStatsClick = { showStats = true })

        Spacer(modifier = Modifier.height(16.dp))

        // Gelen davetler
        if (incomingInvites.isNotEmpty()) {
            IncomingInvitesSection(
                invites = incomingInvites,
                onAcceptInvite = { invite ->
                    scope.launch {
                        val gameId = firebaseRepository.acceptGameInvite(invite)
                        if (gameId != null) {
                            println("Oyun başlatılıyor: $gameId")
                        }
                    }
                },
                onDeclineInvite = { invite ->
                    scope.launch {
                        firebaseRepository.declineGameInvite(invite)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Oyun butonları
        GameActionButtons(
            isSearchingRandomGame = isSearchingRandomGame,
            onRandomGameClick = {
                scope.launch {
                    if (isSearchingRandomGame) {
                        firebaseRepository.leaveMatchmakingQueue(user.uid)
                    } else {
                        firebaseRepository.joinMatchmakingQueue(user.uid)
                    }
                    isSearchingRandomGame = !isSearchingRandomGame
                }
            },
            onPracticeClick = {
                println("AI antrenman modu açılıyor...")
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Çevrimiçi oyuncular listesi
        OnlinePlayersSection(
            onlineUsers = onlineUsers,
            onSendInvite = { targetUser ->
                scope.launch {
                    val success = firebaseRepository.sendGameInvite(user, targetUser)
                    if (success) {
                        println("Davet gönderildi: ${targetUser.displayName}")
                    }
                }
            }
        )
    }

    // İstatistikler dialog
    if (showStats) {
        PlayerStatsDialog(
            user = user,
            onDismiss = { showStats = false }
        )
    }
}

/**
 * Üst menü barı
 */
@Composable
fun TopAppBar(user: UserProfile, onStatsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kullanıcı bilgisi
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Profil fotoğrafı placeholder
            Card(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.displayName.firstOrNull()?.toString() ?: "?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = user.displayName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "🟢 Çevrimiçi",
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50)
                )
            }
        }

        // İstatistikler butonu
        TextButton(onClick = onStatsClick) {
            Text("📊 İstatistikler")
        }
    }
}

/**
 * Gelen davetler bölümü
 */
@Composable
fun IncomingInvitesSection(
    invites: List<GameInvite>,
    onAcceptInvite: (GameInvite) -> Unit,
    onDeclineInvite: (GameInvite) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📨 Gelen Davetler",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            invites.forEach { invite ->
                InviteItem(
                    invite = invite,
                    onAccept = { onAcceptInvite(invite) },
                    onDecline = { onDeclineInvite(invite) }
                )
            }
        }
    }
}

/**
 * Tek davet öğesi
 */
@Composable
fun InviteItem(
    invite: GameInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${invite.fromUserName} seni oyuna davet ediyor",
            fontSize = 14.sp
        )

        Row {
            TextButton(onClick = onAccept) {
                Text("✅ Kabul Et", color = Color(0xFF4CAF50))
            }
            TextButton(onClick = onDecline) {
                Text("❌ Reddet", color = Color(0xFFF44336))
            }
        }
    }
}

/**
 * Oyun aksiyonu butonları
 */
@Composable
fun GameActionButtons(
    isSearchingRandomGame: Boolean,
    onRandomGameClick: () -> Unit,
    onPracticeClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Rastgele rakip bul
        Button(
            onClick = onRandomGameClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSearchingRandomGame) Color(0xFFF44336) else Color(0xFF4CAF50)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isSearchingRandomGame) "⏹️ Aramayı Durdur" else "🎯 Rastgele Rakip Bul",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // AI antrenman
        OutlinedButton(
            onClick = onPracticeClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "🤖 AI ile Antrenman",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Çevrimiçi oyuncular bölümü
 */
@Composable
fun OnlinePlayersSection(
    onlineUsers: List<UserProfile>,
    onSendInvite: (UserProfile) -> Unit
) {
    Column {
        Text(
            text = "👥 Çevrimiçi Oyuncular (${onlineUsers.size})",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (onlineUsers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Şu anda çevrimiçi oyuncu yok.\nRastgele rakip bul özelliğini kullanabilirsin.",
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn {
                items(onlineUsers) { user ->
                    OnlinePlayerItem(
                        user = user,
                        onInviteClick = { onSendInvite(user) }
                    )
                }
            }
        }
    }
}

/**
 * Çevrimiçi oyuncu öğesi
 */
@Composable
fun OnlinePlayerItem(user: UserProfile, onInviteClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Profil placeholder
                Card(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.displayName.firstOrNull()?.toString() ?: "?",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = user.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (user.gamesPlayed > 0) {
                        Text(
                            text = "${user.gamesWon}/${user.gamesPlayed} galibiyet",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Button(
                onClick = onInviteClick,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("⚔️ Davet Et", fontSize = 12.sp)
            }
        }
    }
}

/**
 * Oyuncu istatistikleri dialog
 */
@Composable
fun PlayerStatsDialog(user: UserProfile, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📊 ${user.displayName} İstatistikleri")
        },
        text = {
            Column {
                StatRow("Toplam Oyun:", "${user.gamesPlayed}")
                StatRow("Galibiyet:", "${user.gamesWon}")
                StatRow("Mağlubiyet:", "${user.gamesPlayed - user.gamesWon}")
                StatRow("Başarı Oranı:", "${if (user.gamesPlayed > 0) (user.gamesWon * 100 / user.gamesPlayed) else 0}%")
                StatRow("Mevcut Seri:", "${user.currentStreak}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tamam")
            }
        }
    )
}

/**
 * İstatistik satırı
 */
@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.Medium)
        Text(text = value, color = Color(0xFF1976D2))
    }
    Spacer(modifier = Modifier.height(4.dp))
}