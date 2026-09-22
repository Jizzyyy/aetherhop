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
    val audioData: ByteArray,
    val sampleRateHz: Int = 16000,
    val flags: Byte = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UdpAudioDatagram
        if (sequenceNumber != other.sequenceNumber) return false
        if (sessionId != other.sessionId) return false
        if (sampleRateHz != other.sampleRateHz) return false
        if (flags != other.flags) return false
        if (!audioData.contentEquals(other.audioData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + sampleRateHz.hashCode()
        result = 31 * result + flags.hashCode()
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
            dos.writeShort(datagram.sampleRateHz)
            dos.writeByte(datagram.flags.toInt())
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
            if (length < 19) return null
            return try {
                val bais = ByteArrayInputStream(bytes, 0, length)
                val dis = DataInputStream(bais)
                val b1 = dis.readByte()
                val b2 = dis.readByte()
                if (b1 != MAGIC_BYTE_1 || b2 != MAGIC_BYTE_2) return null
                val sampleRate = dis.readShort().toInt()
                val flags = dis.readByte()
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
                UdpAudioDatagram(sessionId, seq, audioData, sampleRate, flags)
            } catch (_: Exception) {
                null
            }
        }
    }

    @Volatile
    private var listeningSocket: DatagramSocket? = null
    private val lastSeqBySession = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastFrameBySession = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    fun handlePacketLossConcealment(datagram: UdpAudioDatagram): List<UdpAudioDatagram> {
        val lastSeq = lastSeqBySession[datagram.sessionId]
        val result = mutableListOf<UdpAudioDatagram>()

        if (lastSeq != null && datagram.sequenceNumber > lastSeq + 1) {
            val missingCount = (datagram.sequenceNumber - lastSeq - 1).coerceAtMost(3)
            val fallbackAudio = lastFrameBySession[datagram.sessionId] ?: ByteArray(datagram.audioData.size)
            // Generate attenuated concealment frames for missed packets
            for (i in 1..missingCount) {
                val concealedSeq = lastSeq + i
                val concealedAudio = ByteArray(fallbackAudio.size) { idx ->
                    // Apply attenuation to concealed frame
                    ((fallbackAudio[idx].toInt() * (missingCount - i + 1)) / (missingCount + 1)).toByte()
                }
                result.add(
                    UdpAudioDatagram(
                        sessionId = datagram.sessionId,
                        sequenceNumber = concealedSeq,
                        audioData = concealedAudio,
                        sampleRateHz = datagram.sampleRateHz,
                        flags = datagram.flags
                    )
                )
            }
        }

        lastSeqBySession[datagram.sessionId] = datagram.sequenceNumber
        lastFrameBySession[datagram.sessionId] = datagram.audioData
        result.add(datagram)
        return result
    }

    fun clearSession(sessionId: String) {
        lastSeqBySession.remove(sessionId)
        lastFrameBySession.remove(sessionId)
    }

    fun sendUdpAudioFrame(
        targetIp: String,
        sessionId: String,
        sequenceNumber: Long,
        audioData: ByteArray,
        sampleRateHz: Int = 16000,
        flags: Byte = 0
    ) {
        try {
            val bytes = serialize(UdpAudioDatagram(sessionId, sequenceNumber, audioData, sampleRateHz, flags))
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
                        val concealedFrames = handlePacketLossConcealment(datagram)
                        concealedFrames.forEach { trySend(it) }
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
