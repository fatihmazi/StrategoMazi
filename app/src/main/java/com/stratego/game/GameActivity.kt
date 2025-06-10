package com.stratego.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.stratego.game.models.*
import com.stratego.game.ui.game.GameScreen
import com.stratego.game.ui.setup.PieceSetupScreen
import com.stratego.game.ui.theme.StrategoTheme
import com.stratego.game.data.FirebaseRepository
import kotlinx.coroutines.launch

/**
 * Ana oyun activity'si
 */
class GameActivity : ComponentActivity() {

    private lateinit var gameId: String
    private lateinit var currentPlayer: Player
    private lateinit var gameRepository: FirebaseRepository
    private var isSetupComplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intent'ten oyun bilgilerini al
        gameId = intent.getStringExtra("GAME_ID") ?: run {
            finish()
            return
        }

        val playerNumber = intent.getIntExtra("PLAYER_NUMBER", 1)
        currentPlayer = if (playerNumber == 1) Player.PLAYER1 else Player.PLAYER2

        gameRepository = FirebaseRepository()

        setContent {
            StrategoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameFlowScreen()
                }
            }
        }
    }

    @Composable
    fun GameFlowScreen() {
        var gameState by remember { mutableStateOf<GameState?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        // Mock oyun durumu - Firebase implementasyonu sonra eklenecek
        LaunchedEffect(Unit) {
            // Şimdilik mock data ile test
            gameState = GameState(
                phase = GamePhase.SETUP,
                player1SetupComplete = false,
                player2SetupComplete = false
            )
            isLoading = false
        }

        when {
            isLoading -> {
                LoadingScreen()
            }
            error != null -> {
                ErrorScreen(error = error!!, onRetry = {
                    error = null
                    isLoading = true
                })
            }
            gameState != null -> {
                when (gameState!!.phase) {
                    GamePhase.SETUP -> {
                        if (!isPlayerSetupComplete(gameState!!, currentPlayer)) {
                            PieceSetupScreen(
                                gameId = gameId,
                                player = currentPlayer,
                                onSetupComplete = { pieceSetup ->
                                    lifecycleScope.launch {
                                        // TODO: Firebase'e setup gönder
                                        println("Setup tamamlandı: ${pieceSetup.size} taş yerleştirildi")
                                        isSetupComplete = true
                                    }
                                },
                                onBackPressed = { finish() }
                            )
                        } else {
                            WaitingForOpponentScreen(gameState!!)
                        }
                    }
                    GamePhase.PLAYING -> {
                        GameScreen(
                            gameId = gameId,
                            player = currentPlayer,
                            initialGameState = gameState!!,
                            onGameEnd = { result ->
                                showGameEndDialog(result)
                            },
                            onBackPressed = { finish() }
                        )
                    }
                    GamePhase.FINISHED -> {
                        GameEndScreen(gameState!!, currentPlayer)
                    }
                }
            }
        }
    }

    @Composable
    fun LoadingScreen() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFF1976D2)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Oyun yükleniyor...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    @Composable
    fun ErrorScreen(error: String, onRetry: () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "❌",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Bağlantı Hatası",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRetry) {
                    Text("🔄 Tekrar Dene")
                }
            }
        }
    }

    @Composable
    fun WaitingForOpponentScreen(gameState: GameState) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "⏳",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Rakip Hazırlanıyor",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Rakibinin taşlarını yerleştirmesi bekleniyor...",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
            }
        }
    }

    @Composable
    fun GameEndScreen(gameState: GameState, player: Player) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "🏆",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Oyun Bitti!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { finish() }) {
                    Text("Ana Menüye Dön")
                }
            }
        }
    }

    private fun isPlayerSetupComplete(gameState: GameState, player: Player): Boolean {
        return when (player) {
            Player.PLAYER1 -> gameState.player1SetupComplete
            Player.PLAYER2 -> gameState.player2SetupComplete
        }
    }

    private fun showGameEndDialog(result: GameOverResult) {
        // TODO: Oyun sonucu dialog implementasyonu
        println("Oyun bitti: ${result.winner} kazandı - ${result.reason}")
    }
}

/**
 * Firebase'de saklanan oyun verisi
 */
data class GameStateData(
    val id: String = "",
    val player1: String = "",
    val player2: String = "",
    val phase: String = GamePhase.SETUP.name,
    val currentPlayer: String = Player.PLAYER1.name,
    val player1Setup: Map<String, String>? = null,
    val player2Setup: Map<String, String>? = null,
    val player1SetupComplete: Boolean = false,
    val player2SetupComplete: Boolean = false,
    val moveCount: Int = 0,
    val lastMoveTime: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "active" // active, paused, finished
)

/**
 * Oyun hamlesi verisi
 */
data class GameMove(
    val from: Position,
    val to: Position,
    val player: Player,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Oyun sonuç türleri
 */
sealed class GameResult {
    data class Success(val gameState: GameState) : GameResult()
    data class Error(val message: String) : GameResult()
    object Loading : GameResult()
}