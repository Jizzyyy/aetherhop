package com.kadhafi.aetherhop.data.network

import com.kadhafi.aetherhop.core.util.Constants
import com.kadhafi.aetherhop.domain.model.MeshPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class P2pSocketClient {
    suspend fun sendPacket(
        hostAddress: String,
        packet: MeshPacket,
        port: Int = Constants.SOCKET_PORT,
        timeoutMs: Int = 5000
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(hostAddress, port), timeoutMs)
            PacketSerializer.writePacket(socket.getOutputStream(), packet)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }
}
