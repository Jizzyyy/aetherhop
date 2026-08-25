package com.kadhafi.aetherhop.data.network

import com.kadhafi.aetherhop.domain.model.MeshPacket
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object PacketSerializer {
    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_PACKET_BYTES = 5 * 1024 * 1024 // 5 MB safety limit

    fun writePacket(output: OutputStream, packet: MeshPacket) {
        val jsonString = json.encodeToString(packet)
        val bytes = jsonString.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_PACKET_BYTES) {
            throw IOException("Packet size exceeds maximum limit of 5MB: ${bytes.size} bytes")
        }
        val dataOut = DataOutputStream(output)
        dataOut.writeInt(bytes.size)
        dataOut.write(bytes)
        dataOut.flush()
    }

    fun readPacket(input: InputStream): MeshPacket {
        val dataIn = DataInputStream(input)
        val length = dataIn.readInt()
        if (length <= 0 || length > MAX_PACKET_BYTES) {
            throw IOException("Invalid packet length: $length bytes (max allowed: 5MB)")
        }
        val bytes = ByteArray(length)
        dataIn.readFully(bytes)
        val jsonString = String(bytes, Charsets.UTF_8)
        return json.decodeFromString(jsonString)
    }
}
