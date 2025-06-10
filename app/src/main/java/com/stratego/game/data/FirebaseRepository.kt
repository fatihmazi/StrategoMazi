package com.stratego.game.data

import com.google.firebase.database.*
import com.stratego.game.UserProfile
import com.stratego.game.GameInvite
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Realtime Database işlemleri
 */
class FirebaseRepository {

    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference

    /**
     * Çevrimiçi kullanıcıları dinler
     */
    fun getOnlineUsers(): Flow<List<UserProfile>> = callbackFlow {
        val query = database.child("users").orderByChild("isOnline").equalTo(true)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = mutableListOf<UserProfile>()
                for (userSnapshot in snapshot.children) {
                    userSnapshot.getValue(UserProfile::class.java)?.let { user ->
                        users.add(user)
                    }
                }
                trySend(users)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)

        awaitClose {
            query.removeEventListener(listener)
        }
    }

    /**
     * Kullanıcı davetlerini dinler
     */
    fun getUserInvites(userId: String): Flow<List<GameInvite>> = callbackFlow {
        val query = database.child("invites").orderByChild("toUserId").equalTo(userId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val invites = mutableListOf<GameInvite>()
                for (inviteSnapshot in snapshot.children) {
                    inviteSnapshot.getValue(GameInvite::class.java)?.let { invite ->
                        if (invite.status == "pending") {
                            invites.add(invite)
                        }
                    }
                }
                trySend(invites)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        query.addValueEventListener(listener)

        awaitClose {
            query.removeEventListener(listener)
        }
    }

    /**
     * Oyun daveti gönderir
     */
    suspend fun sendGameInvite(fromUser: UserProfile, toUser: UserProfile): Boolean {
        return try {
            val inviteId = database.child("invites").push().key ?: return false

            val invite = GameInvite(
                id = inviteId,
                fromUserId = fromUser.uid,
                fromUserName = fromUser.displayName,
                toUserId = toUser.uid,
                timestamp = System.currentTimeMillis(),
                status = "pending"
            )

            database.child("invites").child(inviteId).setValue(invite).await()
            true
        } catch (e: Exception) {
            println("Davet gönderme hatası: ${e.message}")
            false
        }
    }

    /**
     * Daveti kabul eder
     */
    suspend fun acceptGameInvite(invite: GameInvite): String? {
        return try {
            // Daveti güncelle
            database.child("invites").child(invite.id).child("status").setValue("accepted").await()

            // Oyun odası oluştur
            val gameId = database.child("games").push().key ?: return null

            val gameRoom = mapOf(
                "id" to gameId,
                "player1" to invite.toUserId,
                "player2" to invite.fromUserId,
                "status" to "setup", // setup, playing, finished
                "createdAt" to System.currentTimeMillis(),
                "currentTurn" to invite.toUserId // Daveti kabul eden başlar
            )

            database.child("games").child(gameId).setValue(gameRoom).await()

            gameId
        } catch (e: Exception) {
            println("Davet kabul etme hatası: ${e.message}")
            null
        }
    }

    /**
     * Daveti reddeder
     */
    suspend fun declineGameInvite(invite: GameInvite): Boolean {
        return try {
            database.child("invites").child(invite.id).child("status").setValue("declined").await()
            true
        } catch (e: Exception) {
            println("Davet reddetme hatası: ${e.message}")
            false
        }
    }

    /**
     * Rastgele eşleştirme kuyruğuna ekler
     */
    suspend fun joinMatchmakingQueue(userId: String): Boolean {
        return try {
            val queueEntry = mapOf(
                "userId" to userId,
                "timestamp" to System.currentTimeMillis(),
                "status" to "searching"
            )

            database.child("matchmaking").child(userId).setValue(queueEntry).await()
            true
        } catch (e: Exception) {
            println("Matchmaking kuyruğu ekleme hatası: ${e.message}")
            false
        }
    }

    /**
     * Matchmaking kuyruğundan çıkar
     */
    suspend fun leaveMatchmakingQueue(userId: String): Boolean {
        return try {
            database.child("matchmaking").child(userId).removeValue().await()
            true
        } catch (e: Exception) {
            println("Matchmaking kuyruğu çıkma hatası: ${e.message}")
            false
        }
    }
}