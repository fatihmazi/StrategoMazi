package com.stratego.game.ui.setup

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratego.game.models.*
import kotlin.math.roundToInt

/**
 * Taş yerleştirme ana ekranı
 */
@Composable
fun PieceSetupScreen(
    gameId: String,
    player: Player,
    onSetupComplete: (Map<Position, PieceType>) -> Unit,
    onBackPressed: () -> Unit
) {
    var gameState by remember { mutableStateOf(GameState()) }
    var availablePieces by remember { mutableStateOf(createInitialPiecePool()) }
    var placedPieces by remember { mutableStateOf<Map<Position, PieceType>>(emptyMap()) }
    var draggedPiece by remember { mutableStateOf<PieceType?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedTemplate by remember { mutableStateOf<String?>(null) }
    var setupTimeRemaining by remember { mutableStateOf(240) } // 4 dakika
    var showTemplateDialog by remember { mutableStateOf(false) }

    // Geri sayım timer
    LaunchedEffect(setupTimeRemaining) {
        if (setupTimeRemaining > 0) {
            kotlinx.coroutines.delay(1000)
            setupTimeRemaining--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        // Üst bar - zaman ve kontroller
        SetupTopBar(
            timeRemaining = setupTimeRemaining,
            onTemplateClick = { showTemplateDialog = true },
            onBackPressed = onBackPressed
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Oyun tahtası
        GameBoardSetupView(
            placedPieces = placedPieces,
            player = player,
            onPieceDropped = { position, pieceType ->
                if (canPlacePiece(position, player)) {
                    placedPieces = placedPieces + (position to pieceType)
                    availablePieces = removePieceFromPool(availablePieces, pieceType)
                }
            },
            onPieceRemoved = { position ->
                placedPieces[position]?.let { pieceType ->
                    placedPieces = placedPieces - position
                    availablePieces = addPieceToPool(availablePieces, pieceType)
                }
            },
            draggedPiece = draggedPiece,
            dragOffset = dragOffset
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Mevcut taşlar listesi
        AvailablePiecesSection(
            availablePieces = availablePieces,
            onPieceDragStart = { pieceType, offset ->
                draggedPiece = pieceType
                dragOffset = offset
            },
            onPieceDragEnd = {
                draggedPiece = null
                dragOffset = Offset.Zero
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Alt kontroller
        SetupBottomControls(
            isSetupComplete = placedPieces.size == 40,
            onResetClick = {
                placedPieces = emptyMap()
                availablePieces = createInitialPiecePool()
            },
            onCompleteClick = {
                if (placedPieces.size == 40) {
                    onSetupComplete(placedPieces)
                }
            }
        )
    }

    // Şablon seçim dialog
    if (showTemplateDialog) {
        TemplateSelectionDialog(
            onTemplateSelected = { template ->
                applyTemplate(template, player)?.let { newPlacement ->
                    placedPieces = newPlacement
                    availablePieces = createInitialPiecePool()
                    // Yerleştirilen taşları havuzdan çıkar
                    newPlacement.values.forEach { pieceType ->
                        availablePieces = removePieceFromPool(availablePieces, pieceType)
                    }
                }
                showTemplateDialog = false
            },
            onDismiss = { showTemplateDialog = false }
        )
    }
}

/**
 * Üst kontrol barı
 */
@Composable
fun SetupTopBar(
    timeRemaining: Int,
    onTemplateClick: () -> Unit,
    onBackPressed: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Geri butonu
        IconButton(onClick = onBackPressed) {
            Text("← Geri", fontSize = 16.sp)
        }

        // Zaman göstergesi
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (timeRemaining <= 30) Color(0xFFFFEBEE) else Color(0xFFE3F2FD)
            )
        ) {
            Text(
                text = "⏱️ ${timeRemaining / 60}:${(timeRemaining % 60).toString().padStart(2, '0')}",
                modifier = Modifier.padding(12.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (timeRemaining <= 30) Color(0xFFC62828) else Color(0xFF1976D2)
            )
        }

        // Şablon butonu
        TextButton(onClick = onTemplateClick) {
            Text("📋 Şablonlar")
        }
    }
}

/**
 * Oyun tahtası kurulum görünümü
 */
@Composable
fun GameBoardSetupView(
    placedPieces: Map<Position, PieceType>,
    player: Player,
    onPieceDropped: (Position, PieceType) -> Unit,
    onPieceRemoved: (Position) -> Unit,
    draggedPiece: PieceType?,
    dragOffset: Offset
) {
    val density = LocalDensity.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Tahta başlığı
            Text(
                text = "🏰 Taş Yerleştirme Alanı",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 10x10 tahta grid
            Column {
                for (row in 0..9) {
                    Row {
                        for (col in 0..9) {
                            val position = Position(row, col)
                            BoardSquare(
                                position = position,
                                piece = placedPieces[position],
                                isPlayerArea = isPlayerSetupArea(position, player),
                                isWater = position.isWater(),
                                onPieceDropped = { pieceType ->
                                    onPieceDropped(position, pieceType)
                                },
                                onPieceRemoved = {
                                    onPieceRemoved(position)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Sürüklenen taş overlay
    if (draggedPiece != null) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        dragOffset.x.roundToInt(),
                        dragOffset.y.roundToInt()
                    )
                }
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                .border(2.dp, Color(0xFF1976D2), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = draggedPiece.emoji,
                fontSize = 24.sp
            )
        }
    }
}

/**
 * Tahta karesi
 */
@Composable
fun BoardSquare(
    position: Position,
    piece: PieceType?,
    isPlayerArea: Boolean,
    isWater: Boolean,
    onPieceDropped: (PieceType) -> Unit,
    onPieceRemoved: () -> Unit
) {
    var isDragOver by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isWater -> Color(0xFF4FC3F7)
        isDragOver && isPlayerArea -> Color(0xFFFFEB3B)
        isPlayerArea -> Color(0xFFE8F5E8)
        (position.row + position.col) % 2 == 0 -> Color(0xFFF0D9B5)
        else -> Color(0xFFB58863)
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.2f)
            )
            .pointerInput(Unit) {
                // Drag & Drop algılama
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { isDragOver = false },
                    onDrag = { change, _ ->
                        isDragOver = isPlayerArea
                    }
                )
            }
            .clickable {
                if (piece != null && isPlayerArea) {
                    onPieceRemoved()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (piece != null) {
            Text(
                text = piece.emoji,
                fontSize = 16.sp,
                modifier = Modifier.alpha(if (isPlayerArea) 1f else 0.3f)
            )
        } else if (isWater) {
            Text("🌊", fontSize = 12.sp)
        }
    }
}

/**
 * Mevcut taşlar bölümü
 */
@Composable
fun AvailablePiecesSection(
    availablePieces: Map<PieceType, Int>,
    onPieceDragStart: (PieceType, Offset) -> Unit,
    onPieceDragEnd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🎯 Yerleştirilecek Taşlar",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availablePieces.toList()) { (pieceType, count) ->
                    if (count > 0) {
                        PieceItem(
                            pieceType = pieceType,
                            count = count,
                            onDragStart = onPieceDragStart,
                            onDragEnd = onPieceDragEnd
                        )
                    }
                }
            }
        }
    }
}

/**
 * Taş öğesi
 */
@Composable
fun PieceItem(
    pieceType: PieceType,
    count: Int,
    onDragStart: (PieceType, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var elementPosition by remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = Modifier
            .width(80.dp)
            .onGloballyPositioned { coordinates ->
                elementPosition = coordinates.positionInRoot()
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onDragStart(pieceType, elementPosition + offset)
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd()
                    },
                    onDrag = { change, _ ->
                        // Drag handling
                    }
                )
            }
            .alpha(if (isDragging) 0.5f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = pieceType.emoji,
                fontSize = 24.sp
            )

            Text(
                text = pieceType.displayName,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            if (count > 1) {
                Text(
                    text = "($count)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }
        }
    }
}

/**
 * Alt kontroller
 */
@Composable
fun SetupBottomControls(
    isSetupComplete: Boolean,
    onResetClick: () -> Unit,
    onCompleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Reset butonu
        OutlinedButton(
            onClick = onResetClick,
            modifier = Modifier.weight(1f)
        ) {
            Text("🔄 Sıfırla")
        }

        // Tamamla butonu
        Button(
            onClick = onCompleteClick,
            enabled = isSetupComplete,
            modifier = Modifier.weight(2f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSetupComplete) Color(0xFF4CAF50) else Color.Gray
            )
        ) {
            Text(
                text = if (isSetupComplete) "✅ Hazır!" else "❌ ${40 - 0} taş kaldı", // TODO: Calculate remaining
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Şablon seçim dialog
 */
@Composable
fun TemplateSelectionDialog(
    onTemplateSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📋 Hazır Dizilim Şablonları")
        },
        text = {
            Column {
                TemplateOption(
                    title = "⚔️ Saldırgan",
                    description = "Güçlü taşlar önde, hızlı saldırı odaklı",
                    onClick = { onTemplateSelected("aggressive") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TemplateOption(
                    title = "🛡️ Savunma",
                    description = "Kaşifler önde, bayrak iyi korunmuş",
                    onClick = { onTemplateSelected("defensive") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TemplateOption(
                    title = "⚖️ Dengeli",
                    description = "Karışık strateji, çok yönlü",
                    onClick = { onTemplateSelected("balanced") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

/**
 * Şablon seçeneği
 */
@Composable
fun TemplateOption(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

// Yardımcı fonksiyonlar

/**
 * Başlangıç taş havuzunu oluşturur
 */
fun createInitialPiecePool(): Map<PieceType, Int> {
    return PieceType.values().associateWith { it.count }
}

/**
 * Taş havuzundan bir taş çıkarır
 */
fun removePieceFromPool(pool: Map<PieceType, Int>, pieceType: PieceType): Map<PieceType, Int> {
    val currentCount = pool[pieceType] ?: 0
    return if (currentCount > 0) {
        pool + (pieceType to currentCount - 1)
    } else {
        pool
    }
}

/**
 * Taş havuzuna bir taş ekler
 */
fun addPieceToPool(pool: Map<PieceType, Int>, pieceType: PieceType): Map<PieceType, Int> {
    val currentCount = pool[pieceType] ?: 0
    return pool + (pieceType to currentCount + 1)
}

/**
 * Oyuncunun yerleştirme alanını kontrol eder
 */
fun isPlayerSetupArea(position: Position, player: Player): Boolean {
    return when (player) {
        Player.PLAYER1 -> position.row in 0..3
        Player.PLAYER2 -> position.row in 6..9
    }
}

/**
 * Taşın yerleştirilebilir olup olmadığını kontrol eder
 */
fun canPlacePiece(position: Position, player: Player): Boolean {
    return isPlayerSetupArea(position, player) && !position.isWater()
}

/**
 * Şablonu uygular
 */
fun applyTemplate(template: String, player: Player): Map<Position, PieceType>? {
    val baseTemplate = when (template) {
        "aggressive" -> PieceSetupTemplates.getAggressiveSetup()
        "defensive" -> PieceSetupTemplates.getDefensiveSetup()
        "balanced" -> PieceSetupTemplates.getBalancedSetup()
        else -> return null
    }

    // Player 2 için pozisyonları çevir
    return if (player == Player.PLAYER2) {
        baseTemplate.mapKeys { (pos, _) ->
            Position(9 - pos.row, pos.col)
        }
    } else {
        baseTemplate
    }
}