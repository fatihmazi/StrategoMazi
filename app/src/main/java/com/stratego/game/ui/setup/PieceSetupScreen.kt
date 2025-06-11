package com.stratego.game.ui.setup

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stratego.game.models.*

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
    var availablePieces by remember { mutableStateOf(createInitialPiecePool()) }
    var placedPieces by remember { mutableStateOf<Map<Position, PieceType>>(emptyMap()) }
    var selectedPiece by remember { mutableStateOf<PieceType?>(null) }
    var selectedPosition by remember { mutableStateOf<Position?>(null) }
    var setupTimeRemaining by remember { mutableStateOf(240) }
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
        // Üst bar
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
            selectedPosition = selectedPosition,
            onSquareClick = { position ->
                when {
                    // Taş seçili ve boş kareye tıkladı
                    selectedPiece != null && !placedPieces.containsKey(position) && canPlacePiece(position, player) -> {
                        placedPieces = placedPieces + (position to selectedPiece!!)
                        availablePieces = removePieceFromPool(availablePieces, selectedPiece!!)
                        selectedPiece = null
                        selectedPosition = null
                    }
                    // Dolu kareye tıkladı - taşı kaldır
                    placedPieces.containsKey(position) -> {
                        val pieceType = placedPieces[position]!!
                        placedPieces = placedPieces - position
                        availablePieces = addPieceToPool(availablePieces, pieceType)
                        selectedPosition = null
                    }
                    // Boş kareyi seçti
                    canPlacePiece(position, player) -> {
                        selectedPosition = position
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Taş seçimi grid
        AvailablePiecesSection(
            availablePieces = availablePieces,
            selectedPiece = selectedPiece,
            onPieceSelected = { pieceType ->
                if (availablePieces[pieceType]!! > 0) {
                    selectedPiece = pieceType
                    // Eğer pozisyon seçiliyse, taşı oraya yerleştir
                    selectedPosition?.let { position ->
                        if (canPlacePiece(position, player)) {
                            placedPieces = placedPieces + (position to pieceType)
                            availablePieces = removePieceFromPool(availablePieces, pieceType)
                            selectedPiece = null
                            selectedPosition = null
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Alt kontroller
        SetupBottomControls(
            isSetupComplete = placedPieces.size == 40,
            remainingPieces = 40 - placedPieces.size,
            onResetClick = {
                placedPieces = emptyMap()
                availablePieces = createInitialPiecePool()
                selectedPiece = null
                selectedPosition = null
            },
            onCompleteClick = {
                if (placedPieces.size == 40) {
                    onSetupComplete(placedPieces)
                }
            }
        )
    }

    // Şablon dialog
    if (showTemplateDialog) {
        TemplateSelectionDialog(
            onTemplateSelected = { template ->
                applyTemplate(template, player)?.let { newPlacement ->
                    placedPieces = newPlacement
                    availablePieces = createInitialPiecePool()
                    newPlacement.values.forEach { pieceType ->
                        availablePieces = removePieceFromPool(availablePieces, pieceType)
                    }
                    selectedPiece = null
                    selectedPosition = null
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
    selectedPosition: Position?,
    onSquareClick: (Position) -> Unit
) {
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
                                isSelected = position == selectedPosition,
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
 * Tahta karesi
 */
@Composable
fun BoardSquare(
    position: Position,
    piece: PieceType?,
    isPlayerArea: Boolean,
    isWater: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isWater -> Color(0xFF4FC3F7)
        isSelected -> Color(0xFFFFEB3B)
        isPlayerArea -> Color(0xFFE8F5E8)
        (position.row + position.col) % 2 == 0 -> Color(0xFFF0D9B5)
        else -> Color(0xFFB58863)
    }

    val borderColor = when {
        isSelected -> Color(0xFFFF5722)
        else -> Color.Black.copy(alpha = 0.2f)
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            piece != null -> {
                Text(
                    text = piece.emoji,
                    fontSize = 16.sp
                )
            }
            isWater -> {
                Text("🌊", fontSize = 12.sp)
            }
            isSelected -> {
                Text("⭕", fontSize = 16.sp, color = Color(0xFFFF5722))
            }
        }
    }
}

/**
 * Mevcut taşlar bölümü - 3 satır x 4 sütun grid
 */
@Composable
fun AvailablePiecesSection(
    availablePieces: Map<PieceType, Int>,
    onPieceSelected: (PieceType) -> Unit,
    selectedPiece: PieceType? = null
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

            // 3 satır x 4 sütun grid
            val pieceTypes = PieceType.values().toList()
            val rows = pieceTypes.chunked(4) // Her satırda 4 taş

            rows.forEach { rowPieces ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowPieces.forEach { pieceType ->
                        val count = availablePieces[pieceType] ?: 0
                        PieceGridItem(
                            pieceType = pieceType,
                            count = count,
                            isSelected = selectedPiece == pieceType,
                            onPieceClick = { onPieceSelected(pieceType) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Eksik sütunları doldur
                    repeat(4 - rowPieces.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                if (rowPieces != rows.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * Grid taş öğesi
 */
@Composable
fun PieceGridItem(
    pieceType: PieceType,
    count: Int,
    isSelected: Boolean,
    onPieceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(4.dp)
            .clickable { if (count > 0) onPieceClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                count == 0 -> Color.Gray.copy(alpha = 0.3f)
                isSelected -> Color(0xFFFFEB3B).copy(alpha = 0.7f)
                else -> Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Taş emoji
            Text(
                text = pieceType.emoji,
                fontSize = 20.sp,
                modifier = Modifier.alpha(if (count > 0) 1f else 0.3f)
            )

            // Taş adı (kısaltılmış)
            Text(
                text = when (pieceType) {
                    PieceType.MARSHAL -> "Mareşal"
                    PieceType.GENERAL -> "General"
                    PieceType.COLONEL -> "Albay"
                    PieceType.MAJOR -> "Binbaşı"
                    PieceType.CAPTAIN -> "Yüzbaşı"
                    PieceType.LIEUTENANT -> "Teğmen"
                    PieceType.SERGEANT -> "Astsubay"
                    PieceType.SCOUT -> "Keşifçi"
                    PieceType.MINER -> "Miner"
                    PieceType.SPY -> "Casus"
                    PieceType.BOMB -> "Bomba"
                    PieceType.FLAG -> "Bayrak"
                },
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = if (count > 0) Color.Black else Color.Gray
            )

            // Adet göstergesi
            if (count > 1) {
                Text(
                    text = "($count)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            } else if (count == 0) {
                Text(
                    text = "(0)",
                    fontSize = 10.sp,
                    color = Color.Gray
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
    remainingPieces: Int,
    onResetClick: () -> Unit,
    onCompleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onResetClick,
            modifier = Modifier.weight(1f)
        ) {
            Text("🔄 Sıfırla")
        }

        Button(
            onClick = onCompleteClick,
            enabled = isSetupComplete,
            modifier = Modifier.weight(2f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSetupComplete) Color(0xFF4CAF50) else Color.Gray
            )
        ) {
            Text(
                text = if (isSetupComplete) "✅ Hazır!" else "❌ $remainingPieces taş kaldı",
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
                    description = "Keşifler önde, bayrak iyi korunmuş",
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
        Player.PLAYER1 -> position.row in 6..9  // Alt 4 sıra (oyuncu)
        Player.PLAYER2 -> position.row in 0..3  // Üst 4 sıra (rakip)
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

    // Player 1 için pozisyonları alt 4 sıraya çevir (6-9)
    return if (player == Player.PLAYER1) {
        baseTemplate.mapKeys { (pos, _) ->
            Position(pos.row + 6, pos.col) // 0-3 → 6-9'a kaydır
        }
    } else {
        baseTemplate // Player 2 için aynı kalsın (0-3)
    }
}