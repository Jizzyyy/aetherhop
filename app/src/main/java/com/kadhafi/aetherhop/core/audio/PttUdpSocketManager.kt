package com.kadhafi.aetherhop.core.audio

import com.kadhafi.aetherhop.core.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class UdpAudioDatagram(
    val sessionId: String,
    val sequenceNumber: Long,
    val audioData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UdpAudioDatagram
        if (sequenceNumber != other.sequenceNumber) return false
        if (sessionId != other.sessionId) return false
        if (!audioData.contentEquals(other.audioData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + audioData.contentHashCode()
        return result
    }
}

class PttUdpSocketManager(private val port: Int = Constants.PTT_UDP_PORT) {

    companion object {
        const val MAGIC_BYTE_1: Byte = 0xAE.toByte()
        const val MAGIC_BYTE_2: Byte = 0x48.toByte()

        fun serialize(datagram: UdpAudioDatagram): ByteArray {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            dos.writeByte(MAGIC_BYTE_1.toInt())
            dos.writeByte(MAGIC_BYTE_2.toInt())
            dos.writeLong(datagram.sequenceNumber)
            val sessionBytes = datagram.sessionId.toByteArray(Charsets.UTF_8)
            dos.writeShort(sessionBytes.size)
            dos.write(sessionBytes)
            dos.writeInt(datagram.audioData.size)
            dos.write(datagram.audioData)
            dos.flush()
            return baos.toByteArray()
        }

        fun deserialize(bytes: ByteArray, length: Int = bytes.size): UdpAudioDatagram? {
            if (length < 16) return null
            return try {
                val bais = ByteArrayInputStream(bytes, 0, length)
                val dis = DataInputStream(bais)
                val b1 = dis.readByte()
                val b2 = dis.readByte()
                if (b1 != MAGIC_BYTE_1 || b2 != MAGIC_BYTE_2) return null
                val seq = dis.readLong()
                val sessionLen = dis.readShort().toInt()
                if (sessionLen < 0 || sessionLen > 256) return null
                val sessionBytes = ByteArray(sessionLen)
                dis.readFully(sessionBytes)
                val sessionId = String(sessionBytes, Charsets.UTF_8)
                val audioLen = dis.readInt()
                if (audioLen < 0 || audioLen > 65536) return null
                val audioData = ByteArray(audioLen)
                dis.readFully(audioData)
                UdpAudioDatagram(sessionId, seq, audioData)
            } catch (_: Exception) {
                null
            }
        }
    }

    @Volatile
    private var listeningSocket: DatagramSocket? = null

    fun sendUdpAudioFrame(targetIp: String, sessionId: String, sequenceNumber: Long, audioData: ByteArray) {
        try {
            val bytes = serialize(UdpAudioDatagram(sessionId, sequenceNumber, audioData))
            val address = InetAddress.getByName(targetIp)
            val packet = DatagramPacket(bytes, bytes.size, address, port)
            DatagramSocket().use { socket ->
                socket.send(packet)
            }
        } catch (_: Exception) {}
    }

    fun startListening(): Flow<UdpAudioDatagram> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                val socket = DatagramSocket(port)
                listeningSocket = socket
                socket.soTimeout = 10000

                while (!isClosedForSend) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    } catch (_: Exception) {
                        break
                    }
                    val datagram = deserialize(packet.data, packet.length)
                    if (datagram != null) {
                        trySend(datagram)
                    }
                }
            } catch (_: Exception) {
            } finally {
                stopListening()
            }
        }

        awaitClose {
            job.cancel()
            stopListening()
        }
    }

    fun stopListening() {
        try {
            listeningSocket?.close()
            listeningSocket = null
        } catch (_: Exception) {}
    }
}
