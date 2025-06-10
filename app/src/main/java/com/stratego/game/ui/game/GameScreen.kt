package com.stratego.game.ui.game

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratego.game.models.*
import kotlinx.coroutines.delay

/**
 * Ana oyun ekranı
 */
@Composable
fun GameScreen(
    gameId: String,
    player: Player,
    initialGameState: GameState,
    onGameEnd: (GameOverResult) -> Unit,
    onBackPressed: () -> Unit
) {
    var gameState by remember { mutableStateOf(initialGameState) }
    var selectedPosition by remember { mutableStateOf<Position?>(null) }
    var possibleMoves by remember { mutableStateOf<List<Position>>(emptyList()) }
    var lastBattleResult by remember { mutableStateOf<BattleResult?>(null) }
    var destroyedPieces by remember { mutableStateOf<Map<Player, List<PieceType>>>(emptyMap()) }
    var timeRemaining by remember { mutableStateOf(gameState.timePerMove) }
    var showBattleAnimation by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Bağlı") }

    // Zaman sayacı
    LaunchedEffect(gameState.currentPlayer, gameState.lastMoveTime) {
        timeRemaining = gameState.timePerMove
        while (timeRemaining > 0 && gameState.phase == GamePhase.PLAYING) {
            delay(1000)
            timeRemaining--
        }
        // Süre doldu, otomatik hamle yap
        if (timeRemaining == 0 && gameState.currentPlayer == player) {
            // TODO: Random hamle yap
        }
    }

    // Oyun bitişi kontrolü
    LaunchedEffect(gameState) {
        gameState.board.isGameOver()?.let { result ->
            onGameEnd(result)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Üst oyun bilgileri
        GameTopBar(
            gameState = gameState,
            currentPlayer = player,
            timeRemaining = timeRemaining,
            connectionStatus = connectionStatus,
            onBackPressed = onBackPressed
        )

        // Ana oyun alanı
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
        ) {
            // Sol panel - rakip yenilen taşlar
            DestroyedPiecesPanel(
                title = "Rakip Kaybettiği",
                pieces = destroyedPieces[player.opponent()] ?: emptyList(),
                modifier = Modifier.weight(0.2f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Oyun tahtası
            GameBoardView(
                gameState = gameState,
                player = player,
                selectedPosition = selectedPosition,
                possibleMoves = possibleMoves,
                onSquareClick = { position ->
                    handleSquareClick(
                        position = position,
                        gameState = gameState,
                        player = player,
                        selectedPosition = selectedPosition,
                        onPositionSelected = { pos, moves ->
                            selectedPosition = pos
                            possibleMoves = moves
                        },
                        onMoveExecuted = { newState, battleResult ->
                            gameState = newState
                            battleResult?.let {
                                lastBattleResult = it
                                showBattleAnimation = true
                                // Yenilen taşları kaydet
                                updateDestroyedPieces(it, destroyedPieces) { newDestroyed ->
                                    destroyedPieces = newDestroyed
                                }
                            }
                            selectedPosition = null
                            possibleMoves = emptyList()
                        }
                    )
                },
                modifier = Modifier.weight(0.6f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Sağ panel - kendi yenilen taşlar
            DestroyedPiecesPanel(
                title = "Sen Kaybettiğin",
                pieces = destroyedPieces[player] ?: emptyList(),
                modifier = Modifier.weight(0.2f)
            )
        }

        // Alt oyun kontrolları
        GameBottomControls(
            gameState = gameState,
            player = player,
            selectedPiece = selectedPosition?.let { gameState.board.getPiece(it) }
        )
    }

    // Savaş animasyon dialog
    if (showBattleAnimation && lastBattleResult != null) {
        BattleAnimationDialog(
            battleResult = lastBattleResult!!,
            onAnimationComplete = {
                showBattleAnimation = false
                lastBattleResult = null
            }
        )
    }
}

/**
 * Üst oyun bilgi barı
 */
@Composable
fun GameTopBar(
    gameState: GameState,
    currentPlayer: Player,
    timeRemaining: Int,
    connectionStatus: String,
    onBackPressed: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Geri butonu
            TextButton(onClick = onBackPressed) {
                Text("← Çık")
            }

            // Oyun durumu
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (gameState.currentPlayer == currentPlayer) "📍 Senin Sıran" else "⏳ Rakip Oynuyor",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (gameState.currentPlayer == currentPlayer) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )

                Text(
                    text = "Hamle: ${gameState.moveCount}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Zaman sayacı
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        timeRemaining <= 5 -> Color(0xFFFFEBEE)
                        timeRemaining <= 10 -> Color(0xFFFFF3E0)
                        else -> Color(0xFFE3F2FD)
                    }
                )
            ) {
                Text(
                    text = "⏱️ $timeRemaining",
                    modifier = Modifier.padding(8.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        timeRemaining <= 5 -> Color(0xFFC62828)
                        timeRemaining <= 10 -> Color(0xFFE65100)
                        else -> Color(0xFF1976D2)
                    }
                )
            }
        }
    }
}

/**
 * Ana oyun tahtası görünümü
 */
@Composable
fun GameBoardView(
    gameState: GameState,
    player: Player,
    selectedPosition: Position?,
    possibleMoves: List<Position>,
    onSquareClick: (Position) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚔️ STRATEGO SAVAŞ ALANI",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 10x10 tahta
            Column {
                for (row in 0..9) {
                    Row {
                        for (col in 0..9) {
                            val position = Position(row, col)
                            val piece = gameState.board.getPiece(position)

                            GameBoardSquare(
                                position = position,
                                piece = piece,
                                isSelected = position == selectedPosition,
                                isPossibleMove = position in possibleMoves,
                                isPlayersPiece = piece?.player == player,
                                canPlayerSee = piece?.player == player || piece?.isVisible == true,
                                onClick = { onSquareClick(position) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Oyun tahtası karesi
 */
@Composable
fun GameBoardSquare(
    position: Position,
    piece: GamePiece?,
    isSelected: Boolean,
    isPossibleMove: Boolean,
    isPlayersPiece: Boolean,
    canPlayerSee: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        position.isWater() -> Color(0xFF4FC3F7)
        isSelected -> Color(0xFFFFEB3B)
        isPossibleMove -> Color(0xFFC8E6C9)
        (position.row + position.col) % 2 == 0 -> Color(0xFFF0D9B5)
        else -> Color(0xFFB58863)
    }

    val borderColor = when {
        isSelected -> Color(0xFFFF5722)
        isPossibleMove -> Color(0xFF4CAF50)
        else -> Color.Black.copy(alpha = 0.2f)
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .background(backgroundColor)
            .border(
                width = if (isSelected || isPossibleMove) 2.dp else 1.dp,
                color = borderColor
            )
            .clickable { onClick() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            piece != null && canPlayerSee -> {
                // Görülebilen taş
                Text(
                    text = piece.type.emoji,
                    fontSize = 20.sp,
                    modifier = Modifier.alpha(if (isPlayersPiece) 1f else 0.9f)
                )
            }
            piece != null && !canPlayerSee -> {
                // Rakip taşı (gizli)
                Text(
                    text = "❓",
                    fontSize = 18.sp,
                    color = Color(0xFFF44336)
                )
            }
            position.isWater() -> {
                Text("🌊", fontSize = 14.sp)
            }
            isPossibleMove -> {
                Text("⭕", fontSize = 16.sp, color = Color(0xFF4CAF50))
            }
        }
    }
}

/**
 * Yenilen taşlar paneli
 */
@Composable
fun DestroyedPiecesPanel(
    title: String,
    pieces: List<PieceType>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Taş türlerine göre grupla ve say
            val groupedPieces = pieces.groupBy { it }.mapValues { it.value.size }

            Column {
                groupedPieces.forEach { (pieceType, count) ->
                    DestroyedPieceItem(
                        pieceType = pieceType,
                        count = count
                    )
                }
            }
        }
    }
}

/**
 * Yenilen taş öğesi
 */
@Composable
fun DestroyedPieceItem(
    pieceType: PieceType,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pieceType.emoji,
            fontSize = 16.sp
        )
        if (count > 1) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
        }
    }
}

/**
 * Alt oyun kontrolleri
 */
@Composable
fun GameBottomControls(
    gameState: GameState,
    player: Player,
    selectedPiece: GamePiece?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seçili taş bilgisi
            if (selectedPiece != null && selectedPiece.player == player) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedPiece.type.emoji,
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = selectedPiece.type.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedPiece.type == PieceType.SCOUT) {
                                Text(
                                    text = "Düz çizgide hareket eder",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = if (gameState.currentPlayer == player) "🎯 Taşını seç ve hareket et" else "⏳ Rakibin hamlesi bekleniyor",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            // Oyun durumu istatistikleri
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "Kalan taşların: ${gameState.board.getPiecesByPlayer(player).size}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Rakip taşları: ${gameState.board.getPiecesByPlayer(player.opponent()).size}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * Savaş animasyon dialog
 */
@Composable
fun BattleAnimationDialog(
    battleResult: BattleResult,
    onAnimationComplete: () -> Unit
) {
    var showAnimation by remember { mutableStateOf(true) }

    // Animasyon süresi
    LaunchedEffect(Unit) {
        delay(2000) // 2 saniye animasyon
        showAnimation = false
        delay(500) // Kısa bekleme
        onAnimationComplete()
    }

    if (showAnimation) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(
                    text = "⚔️ SAVAŞ!",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Savaşan taşlar
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Saldıran
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = battleResult.attacker.type.emoji,
                                fontSize = 48.sp,
                                modifier = Modifier.animateContentSize()
                            )
                            Text(
                                text = "Saldıran",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = battleResult.attacker.type.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // VS
                        Text(
                            text = "⚡",
                            fontSize = 32.sp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        // Savunan
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = battleResult.defender.type.emoji,
                                fontSize = 48.sp,
                                modifier = Modifier.animateContentSize()
                            )
                            Text(
                                text = "Savunan",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = battleResult.defender.type.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sonuç
                    val resultText = when {
                        battleResult.winner == battleResult.attacker -> "🏆 Saldıran Kazandı!"
                        battleResult.winner == battleResult.defender -> "🛡️ Savunan Kazandı!"
                        else -> "💥 Beraberlik! İkisi de yok oldu"
                    }

                    Text(
                        text = resultText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            battleResult.winner == battleResult.attacker -> Color(0xFF4CAF50)
                            battleResult.winner == battleResult.defender -> Color(0xFF2196F3)
                            else -> Color(0xFFFF9800)
                        }
                    )
                }
            },
            confirmButton = { }
        )
    }
}

// Yardımcı fonksiyonlar

/**
 * Kare tıklama işleyicisi
 */
fun handleSquareClick(
    position: Position,
    gameState: GameState,
    player: Player,
    selectedPosition: Position?,
    onPositionSelected: (Position?, List<Position>) -> Unit,
    onMoveExecuted: (GameState, BattleResult?) -> Unit
) {
    if (gameState.currentPlayer != player || gameState.phase != GamePhase.PLAYING) return

    val clickedPiece = gameState.board.getPiece(position)

    when {
        // Kendi taşını seçti
        clickedPiece != null && clickedPiece.player == player && clickedPiece.type.canMove -> {
            val possibleMoves = calculatePossibleMoves(position, gameState.board)
            onPositionSelected(position, possibleMoves)
        }

        // Seçili taşı hareket ettiriyor
        selectedPosition != null -> {
            val moveResult = gameState.board.movePiece(selectedPosition, position)
            when (moveResult) {
                is MoveResult.Move -> {
                    val newState = gameState.copy(
                        moveCount = gameState.moveCount + 1
                    ).switchTurn()
                    onMoveExecuted(newState, null)
                }
                is MoveResult.Battle -> {
                    val newState = gameState.copy(
                        moveCount = gameState.moveCount + 1
                    ).switchTurn()
                    onMoveExecuted(newState, moveResult.result)
                }
                is MoveResult.InvalidMove -> {
                    // Geçersiz hamle, seçimi temizle
                    onPositionSelected(null, emptyList())
                }
            }
        }

        else -> {
            // Seçimi temizle
            onPositionSelected(null, emptyList())
        }
    }
}

/**
 * Mümkün hamleleri hesaplar
 */
fun calculatePossibleMoves(position: Position, board: GameBoard): List<Position> {
    val piece = board.getPiece(position) ?: return emptyList()

    return if (piece.type == PieceType.SCOUT) {
        calculateScoutMoves(position, board)
    } else {
        position.getAdjacentPositions().filter { targetPos ->
            val targetPiece = board.getPiece(targetPos)
            targetPiece == null || targetPiece.player != piece.player
        }
    }
}

/**
 * Scout'un mümkün hamlelerini hesaplar
 */
fun calculateScoutMoves(position: Position, board: GameBoard): List<Position> {
    val moves = mutableListOf<Position>()
    val piece = board.getPiece(position) ?: return moves

    // Dört yönde hareket et
    val directions = listOf(
        Pair(-1, 0), // Yukarı
        Pair(1, 0),  // Aşağı
        Pair(0, -1), // Sol
        Pair(0, 1)   // Sağ
    )

    for ((rowDelta, colDelta) in directions) {
        var currentRow = position.row + rowDelta
        var currentCol = position.col + colDelta

        while (currentRow in 0..9 && currentCol in 0..9) {
            val currentPos = Position(currentRow, currentCol)

            if (currentPos.isWater()) break

            val targetPiece = board.getPiece(currentPos)
            when {
                targetPiece == null -> {
                    moves.add(currentPos)
                }
                targetPiece.player != piece.player -> {
                    moves.add(currentPos)
                    break // Rakip taş, daha ileriye gidemez
                }
                else -> {
                    break // Kendi taşı, daha ileriye gidemez
                }
            }

            currentRow += rowDelta
            currentCol += colDelta
        }
    }

    return moves
}

/**
 * Yenilen taşları günceller
 */
fun updateDestroyedPieces(
    battleResult: BattleResult,
    currentDestroyed: Map<Player, List<PieceType>>,
    onUpdate: (Map<Player, List<PieceType>>) -> Unit
) {
    val newDestroyed = currentDestroyed.toMutableMap()

    if (battleResult.attackerDestroyed) {
        val attackerList = newDestroyed[battleResult.attacker.player] ?: emptyList()
        newDestroyed[battleResult.attacker.player] = attackerList + battleResult.attacker.type
    }

    if (battleResult.defenderDestroyed) {
        val defenderList = newDestroyed[battleResult.defender.player] ?: emptyList()
        newDestroyed[battleResult.defender.player] = defenderList + battleResult.defender.type
    }

    onUpdate(newDestroyed)
}