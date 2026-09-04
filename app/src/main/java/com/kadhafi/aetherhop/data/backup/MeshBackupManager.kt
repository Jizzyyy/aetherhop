package com.kadhafi.aetherhop.data.backup

import android.content.Context
import com.kadhafi.aetherhop.core.util.CryptoManager
import com.kadhafi.aetherhop.core.util.EncryptedEnvelope
import com.kadhafi.aetherhop.data.local.AppDatabase
import com.kadhafi.aetherhop.data.local.entity.MessageEntity
import com.kadhafi.aetherhop.data.local.entity.PeerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class DatabaseBackupSchema(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<MessageBackupSchema>,
    val peers: List<PeerBackupSchema>,
    val hmacSignature: String = ""
)

@Serializable
data class MessageBackupSchema(
    val id: String,
    val peerId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
    val statusName: String
)

@Serializable
data class PeerBackupSchema(
    val id: String,
    val name: String,
    val address: String,
    val lastSeenTimestamp: Long
)

class MeshBackupManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exportEncryptedBackup(outputStream: OutputStream, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(appContext)
            val messageEntities = db.messageDao().getAllMessages().first()
            val peerEntities = db.peerDao().getAllPeers().first()

            val backupData = DatabaseBackupSchema(
                messages = messageEntities.map {
                    MessageBackupSchema(it.id, it.peerId, it.senderId, it.senderName, it.text, it.timestamp, it.isMine, it.status.name)
                },
                peers = peerEntities.map {
                    PeerBackupSchema(it.id, it.name, it.address, it.lastSeenTimestamp)
                }
            )

            val plainJson = json.encodeToString(backupData)
            val keySeed = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = CryptoManager.generateSecretKey(keySeed)

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(keySeed, "HmacSHA256"))
            val hmacBytes = mac.doFinal(plainJson.toByteArray(Charsets.UTF_8))
            val signedBackupData = backupData.copy(hmacSignature = android.util.Base64.encodeToString(hmacBytes, android.util.Base64.NO_WRAP))

            val signedPlainJson = json.encodeToString(signedBackupData)
            val envelope = CryptoManager.encrypt(signedPlainJson, secretKey)
            val envelopeJson = json.encodeToString(envelope)

            outputStream.write(envelopeJson.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()
            true
        } catch (e: Exception) {
            android.util.Log.e("MeshBackupManager", "Export backup failed", e)
            false
        }
    }

    suspend fun importEncryptedBackup(inputStream: InputStream, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val envelopeJson = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val envelope = json.decodeFromString<EncryptedEnvelope>(envelopeJson)

            val keySeed = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = CryptoManager.generateSecretKey(keySeed)

            val plainJson = CryptoManager.decrypt(envelope, secretKey)
            val backupData = json.decodeFromString<DatabaseBackupSchema>(plainJson)

            val db = AppDatabase.getDatabase(appContext)
            backupData.peers.forEach { p ->
                db.peerDao().insertPeer(PeerEntity(p.id, p.name, p.address, p.lastSeenTimestamp))
            }
            backupData.messages.forEach { m ->
                db.messageDao().insertMessage(
                    MessageEntity(
                        id = m.id,
                        peerId = m.peerId,
                        senderId = m.senderId,
                        senderName = m.senderName,
                        text = m.text,
                        timestamp = m.timestamp,
                        isMine = m.isMine,
                        status = try { enumValueOf(m.statusName) } catch (_: Exception) { com.kadhafi.aetherhop.domain.model.MessageStatus.SENT }
                    )
                )
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("MeshBackupManager", "Import backup failed", e)
            false
        }
    }
}
