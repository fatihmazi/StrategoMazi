package com.stratego.game.models

/**
 * Oyun tahtası koordinat sistemi
 */
data class Position(
    val row: Int,
    val col: Int
) {
    fun isValid(): Boolean = row in 0..9 && col in 0..9

    fun isWater(): Boolean {
        // Sol göl: C5, C6, D5, D6 → (4,2), (4,3), (5,2), (5,3)
        // Sağ göl: G5, G6, H5, H6 → (4,6), (4,7), (5,6), (5,7)
        return (row in 4..5 && col in 2..3) || (row in 4..5 && col in 6..7)
    }

    fun getAdjacentPositions(): List<Position> {
        return listOf(
            Position(row - 1, col), // Yukarı
            Position(row + 1, col), // Aşağı
            Position(row, col - 1), // Sol
            Position(row, col + 1)  // Sağ
        ).filter { it.isValid() && !it.isWater() }
    }
}

/**
 * Taş türleri ve özellikleri
 */
enum class PieceType(
    val displayName: String,
    val rank: Int,
    val count: Int,
    val emoji: String,
    val canMove: Boolean = true
) {
    MARSHAL("Mareşal", 1, 1, "👨‍✈️"),
    GENERAL("General", 2, 1, "⭐"),
    COLONEL("Albay", 3, 2, "🎖️"),
    MAJOR("Binbaşı", 4, 3, "🏅"),
    CAPTAIN("Yüzbaşı", 5, 4, "🎯"),
    LIEUTENANT("Teğmen", 6, 4, "🔰"),
    SERGEANT("Astsubay", 7, 4, "⚡"),
    SCOUT("Keşifçi", 8, 8, "🔍"),
    MINER("Mayın Temizleyici", 9, 5, "⛏️"),
    SPY("Casus", 10, 1, "🕵️"),
    BOMB("Bomba", 11, 6, "💣", false),
    FLAG("Bayrak", 12, 1, "🏴", false);

    fun canDefeat(other: PieceType): Boolean {
        return when {
            // Casus sadece Mareşali yenebilir
            this == SPY && other == MARSHAL -> true
            this == SPY && other != MARSHAL -> false

            // Mayın Temizleyici bombayı yok edebilir
            this == MINER && other == BOMB -> true

            // Bomba sadece Mayın Temizleyici tarafından yok edilebilir
            other == BOMB && this != MINER -> false

            // Bayrak her zaman yenilir
            other == FLAG -> true

            // Normal rütbe karşılaştırması (düşük rank daha güçlü)
            else -> this.rank < other.rank
        }
    }

}

/**
 * Oyun taşı sınıfı
 */
data class GamePiece(
    val id: String,
    val type: PieceType,
    val player: Player,
    var position: Position? = null,
    val isVisible: Boolean = false // Rakip için görünür mü?
) {
    fun canMoveTo(targetPosition: Position, board: GameBoard): Boolean {
        if (!type.canMove) return false
        if (position == null) return false

        val currentPos = position!!

        // Scout özel hareketi
        if (type == PieceType.SCOUT) {
            return canScoutMoveTo(currentPos, targetPosition, board)
        }

        // Normal taşlar sadece komşu karelere gidebilir
        return targetPosition in currentPos.getAdjacentPositions()
    }

    private fun canScoutMoveTo(from: Position, to: Position, board: GameBoard): Boolean {
        // Scout sadece düz çizgide hareket edebilir
        if (from.row != to.row && from.col != to.col) return false

        // Arada engel var mı kontrol et
        val positions = getPositionsBetween(from, to)
        return positions.all { board.getPiece(it) == null }
    }

    private fun getPositionsBetween(from: Position, to: Position): List<Position> {
        val positions = mutableListOf<Position>()

        when {
            from.row == to.row -> {
                // Yatay hareket
                val start = minOf(from.col, to.col) + 1
                val end = maxOf(from.col, to.col)
                for (col in start until end) {
                    positions.add(Position(from.row, col))
                }
            }
            from.col == to.col -> {
                // Dikey hareket
                val start = minOf(from.row, to.row) + 1
                val end = maxOf(from.row, to.row)
                for (row in start until end) {
                    positions.add(Position(row, from.col))
                }
            }
        }

        return positions
    }
}

/**
 * Oyuncu enum'u
 */
enum class Player {
    PLAYER1, PLAYER2;

    fun opponent(): Player = when (this) {
        PLAYER1 -> PLAYER2
        PLAYER2 -> PLAYER1
    }
}

/**
 * Savaş sonucu
 */
data class BattleResult(
    val attacker: GamePiece,
    val defender: GamePiece,
    val winner: GamePiece?,
    val attackerDestroyed: Boolean,
    val defenderDestroyed: Boolean
) {
    companion object {
        fun calculateBattle(attacker: GamePiece, defender: GamePiece): BattleResult {
            return when {
                attacker.type.canDefeat(defender.type) -> {
                    // Saldıran kazandı
                    BattleResult(
                        attacker = attacker,
                        defender = defender,
                        winner = attacker,
                        attackerDestroyed = false,
                        defenderDestroyed = true
                    )
                }
                defender.type.canDefeat(attacker.type) -> {
                    // Savunan kazandı
                    BattleResult(
                        attacker = attacker,
                        defender = defender,
                        winner = defender,
                        attackerDestroyed = true,
                        defenderDestroyed = false
                    )
                }
                else -> {
                    // Beraberlik - ikisi de yok olur
                    BattleResult(
                        attacker = attacker,
                        defender = defender,
                        winner = null,
                        attackerDestroyed = true,
                        defenderDestroyed = true
                    )
                }
            }
        }
    }
}

/**
 * Oyun tahtası sınıfı
 */
class GameBoard {
    private val pieces = mutableMapOf<Position, GamePiece>()

    fun placePiece(piece: GamePiece, position: Position): Boolean {
        if (position.isWater() || pieces.containsKey(position)) {
            return false
        }

        pieces[position] = piece
        piece.position = position
        return true
    }

    fun removePiece(position: Position): GamePiece? {
        val piece = pieces.remove(position)
        piece?.position = null
        return piece
    }

    fun movePiece(from: Position, to: Position): MoveResult {
        val piece = pieces[from] ?: return MoveResult.InvalidMove("Hareket ettirilecek taş bulunamadı")

        if (!piece.canMoveTo(to, this)) {
            return MoveResult.InvalidMove("Geçersiz hareket")
        }

        val targetPiece = pieces[to]

        return if (targetPiece != null) {
            // Savaş durumu
            if (targetPiece.player == piece.player) {
                MoveResult.InvalidMove("Kendi taşınıza saldıramazsınız")
            } else {
                val battleResult = BattleResult.calculateBattle(piece, targetPiece)
                handleBattle(battleResult, from, to)
            }
        } else {
            // Boş alana hareket
            pieces.remove(from)
            pieces[to] = piece
            piece.position = to
            MoveResult.Move(piece, from, to)
        }
    }

    private fun handleBattle(battle: BattleResult, fromPos: Position, toPos: Position): MoveResult {
        pieces.remove(fromPos)

        if (battle.defenderDestroyed) {
            pieces.remove(toPos)
        }

        if (!battle.attackerDestroyed && battle.winner == battle.attacker) {
            pieces[toPos] = battle.attacker
            battle.attacker.position = toPos
        }

        return MoveResult.Battle(battle, fromPos, toPos)
    }

    fun getPiece(position: Position): GamePiece? = pieces[position]

    fun getAllPieces(): Map<Position, GamePiece> = pieces.toMap()

    fun getPiecesByPlayer(player: Player): List<GamePiece> {
        return pieces.values.filter { it.player == player }
    }

    fun canPlayerMove(player: Player): Boolean {
        return getPiecesByPlayer(player).any { piece ->
            piece.type.canMove && piece.position?.getAdjacentPositions()?.any { pos ->
                val targetPiece = getPiece(pos)
                targetPiece == null || targetPiece.player != player
            } == true
        }
    }

    fun isGameOver(): GameOverResult? {
        val player1Pieces = getPiecesByPlayer(Player.PLAYER1)
        val player2Pieces = getPiecesByPlayer(Player.PLAYER2)

        // Bayrak ele geçirildi mi?
        val player1Flag = player1Pieces.find { it.type == PieceType.FLAG }
        val player2Flag = player2Pieces.find { it.type == PieceType.FLAG }

        return when {
            player1Flag == null -> GameOverResult(Player.PLAYER2, "Bayrak ele geçirildi")
            player2Flag == null -> GameOverResult(Player.PLAYER1, "Bayrak ele geçirildi")
            !canPlayerMove(Player.PLAYER1) -> GameOverResult(Player.PLAYER2, "Hamle yapacak taş kalmadı")
            !canPlayerMove(Player.PLAYER2) -> GameOverResult(Player.PLAYER1, "Hamle yapacak taş kalmadı")
            else -> null
        }
    }
}

/**
 * Hamle sonucu türleri
 */
sealed class MoveResult {
    data class Move(val piece: GamePiece, val from: Position, val to: Position) : MoveResult()
    data class Battle(val result: BattleResult, val from: Position, val to: Position) : MoveResult()
    data class InvalidMove(val reason: String) : MoveResult()
}

/**
 * Oyun bitişi sonucu
 */
data class GameOverResult(
    val winner: Player,
    val reason: String
)

/**
 * Oyun durumu
 */
data class GameState(
    val board: GameBoard = GameBoard(),
    val currentPlayer: Player = Player.PLAYER1,
    val phase: GamePhase = GamePhase.SETUP,
    val player1SetupComplete: Boolean = false,
    val player2SetupComplete: Boolean = false,
    val moveCount: Int = 0,
    val lastMoveTime: Long = System.currentTimeMillis(),
    val timePerMove: Int = 20 // saniye
) {
    fun switchTurn(): GameState {
        return copy(
            currentPlayer = currentPlayer.opponent(),
            lastMoveTime = System.currentTimeMillis()
        )
    }

    fun isSetupComplete(): Boolean {
        return player1SetupComplete && player2SetupComplete
    }
}

/**
 * Oyun aşamaları
 */
enum class GamePhase {
    SETUP,    // Taş yerleştirme
    PLAYING,  // Oyun oynama
    FINISHED  // Oyun bitti
}

/**
 * Hazır taş dizilim şablonları
 */
object PieceSetupTemplates {

    fun getAggressiveSetup(): Map<Position, PieceType> {
        return mapOf(
            // 1. sıra (10 taş) - Keşifçiler ve güçlü taşlar önde
            Position(0, 0) to PieceType.SCOUT,
            Position(0, 1) to PieceType.SCOUT,
            Position(0, 2) to PieceType.SCOUT,
            Position(0, 3) to PieceType.SCOUT,
            Position(0, 4) to PieceType.MARSHAL,
            Position(0, 5) to PieceType.GENERAL,
            Position(0, 6) to PieceType.COLONEL,
            Position(0, 7) to PieceType.COLONEL,
            Position(0, 8) to PieceType.MAJOR,
            Position(0, 9) to PieceType.MAJOR,

            // 2. sıra (10 taş) - Orta seviye
            Position(1, 0) to PieceType.SCOUT,
            Position(1, 1) to PieceType.SCOUT,
            Position(1, 2) to PieceType.SCOUT,
            Position(1, 3) to PieceType.SCOUT,
            Position(1, 4) to PieceType.MAJOR,
            Position(1, 5) to PieceType.CAPTAIN,
            Position(1, 6) to PieceType.CAPTAIN,
            Position(1, 7) to PieceType.CAPTAIN,
            Position(1, 8) to PieceType.CAPTAIN,
            Position(1, 9) to PieceType.LIEUTENANT,

            // 3. sıra (10 taş) - Karışık savunma
            Position(2, 0) to PieceType.LIEUTENANT,
            Position(2, 1) to PieceType.LIEUTENANT,
            Position(2, 2) to PieceType.LIEUTENANT,
            Position(2, 3) to PieceType.SERGEANT,
            Position(2, 4) to PieceType.SERGEANT,
            Position(2, 5) to PieceType.SERGEANT,
            Position(2, 6) to PieceType.SERGEANT,
            Position(2, 7) to PieceType.MINER,
            Position(2, 8) to PieceType.MINER,
            Position(2, 9) to PieceType.SPY,

            // 4. sıra (10 taş) - Savunma hattı
            Position(3, 0) to PieceType.BOMB,
            Position(3, 1) to PieceType.FLAG,
            Position(3, 2) to PieceType.BOMB,
            Position(3, 3) to PieceType.BOMB,
            Position(3, 4) to PieceType.BOMB,
            Position(3, 5) to PieceType.BOMB,
            Position(3, 6) to PieceType.BOMB,
            Position(3, 7) to PieceType.MINER,
            Position(3, 8) to PieceType.MINER,
            Position(3, 9) to PieceType.MINER
        )
    }

    fun getDefensiveSetup(): Map<Position, PieceType> {
        return mapOf(
            // 1. sıra (10 taş) - Keşifçiler önde
            Position(0, 0) to PieceType.SCOUT,
            Position(0, 1) to PieceType.SCOUT,
            Position(0, 2) to PieceType.SCOUT,
            Position(0, 3) to PieceType.SCOUT,
            Position(0, 4) to PieceType.SCOUT,
            Position(0, 5) to PieceType.SCOUT,
            Position(0, 6) to PieceType.SCOUT,
            Position(0, 7) to PieceType.SCOUT,
            Position(0, 8) to PieceType.SERGEANT,
            Position(0, 9) to PieceType.SERGEANT,

            // 2. sıra (10 taş) - Orta savunma
            Position(1, 0) to PieceType.SERGEANT,
            Position(1, 1) to PieceType.SERGEANT,
            Position(1, 2) to PieceType.LIEUTENANT,
            Position(1, 3) to PieceType.LIEUTENANT,
            Position(1, 4) to PieceType.LIEUTENANT,
            Position(1, 5) to PieceType.LIEUTENANT,
            Position(1, 6) to PieceType.CAPTAIN,
            Position(1, 7) to PieceType.CAPTAIN,
            Position(1, 8) to PieceType.CAPTAIN,
            Position(1, 9) to PieceType.CAPTAIN,

            // 3. sıra (10 taş) - Güçlü savunma
            Position(2, 0) to PieceType.MAJOR,
            Position(2, 1) to PieceType.MAJOR,
            Position(2, 2) to PieceType.MAJOR,
            Position(2, 3) to PieceType.COLONEL,
            Position(2, 4) to PieceType.COLONEL,
            Position(2, 5) to PieceType.GENERAL,
            Position(2, 6) to PieceType.MARSHAL,
            Position(2, 7) to PieceType.SPY,
            Position(2, 8) to PieceType.MINER,
            Position(2, 9) to PieceType.MINER,

            // 4. sıra (10 taş) - Arka savunma
            Position(3, 0) to PieceType.MINER,
            Position(3, 1) to PieceType.MINER,
            Position(3, 2) to PieceType.MINER,
            Position(3, 3) to PieceType.BOMB,
            Position(3, 4) to PieceType.FLAG,
            Position(3, 5) to PieceType.BOMB,
            Position(3, 6) to PieceType.BOMB,
            Position(3, 7) to PieceType.BOMB,
            Position(3, 8) to PieceType.BOMB,
            Position(3, 9) to PieceType.BOMB
        )
    }

    fun getBalancedSetup(): Map<Position, PieceType> {
        return mapOf(
            // 1. sıra (10 taş) - Karışık ön hat
            Position(0, 0) to PieceType.SCOUT,
            Position(0, 1) to PieceType.SCOUT,
            Position(0, 2) to PieceType.SERGEANT,
            Position(0, 3) to PieceType.LIEUTENANT,
            Position(0, 4) to PieceType.CAPTAIN,
            Position(0, 5) to PieceType.CAPTAIN,
            Position(0, 6) to PieceType.LIEUTENANT,
            Position(0, 7) to PieceType.SERGEANT,
            Position(0, 8) to PieceType.SCOUT,
            Position(0, 9) to PieceType.SCOUT,

            // 2. sıra (10 taş) - Dengeli dağılım
            Position(1, 0) to PieceType.SCOUT,
            Position(1, 1) to PieceType.MINER,
            Position(1, 2) to PieceType.LIEUTENANT,
            Position(1, 3) to PieceType.CAPTAIN,
            Position(1, 4) to PieceType.MAJOR,
            Position(1, 5) to PieceType.MAJOR,
            Position(1, 6) to PieceType.CAPTAIN,
            Position(1, 7) to PieceType.LIEUTENANT,
            Position(1, 8) to PieceType.MINER,
            Position(1, 9) to PieceType.SCOUT,

            // 3. sıra (10 taş) - Güçlü orta hat
            Position(2, 0) to PieceType.SCOUT,
            Position(2, 1) to PieceType.SERGEANT,
            Position(2, 2) to PieceType.MAJOR,
            Position(2, 3) to PieceType.COLONEL,
            Position(2, 4) to PieceType.GENERAL,
            Position(2, 5) to PieceType.MARSHAL,
            Position(2, 6) to PieceType.COLONEL,
            Position(2, 7) to PieceType.SPY,
            Position(2, 8) to PieceType.SERGEANT,
            Position(2, 9) to PieceType.SCOUT,

            // 4. sıra (10 taş) - Savunma hattı
            Position(3, 0) to PieceType.BOMB,
            Position(3, 1) to PieceType.MINER,
            Position(3, 2) to PieceType.BOMB,
            Position(3, 3) to PieceType.FLAG,
            Position(3, 4) to PieceType.BOMB,
            Position(3, 5) to PieceType.BOMB,
            Position(3, 6) to PieceType.BOMB,
            Position(3, 7) to PieceType.BOMB,
            Position(3, 8) to PieceType.MINER,
            Position(3, 9) to PieceType.MINER
        )
    }
}