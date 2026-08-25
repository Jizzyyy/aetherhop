package com.kadhafi.aetherhop.data.network

import com.kadhafi.aetherhop.domain.model.MeshPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.Socket

class P2pSocketServer(private val port: Int = 8888) {
    private var serverSocket: ServerSocket? = null

    fun startServer(): Flow<MeshPacket> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)
                while (!isClosed) {
                    val socket: Socket = serverSocket?.accept() ?: break
                    try {
                        val packet = PacketSerializer.readPacket(socket.getInputStream())
                        trySend(packet)
                    } catch (_: Exception) {
                    } finally {
                        socket.close()
                    }
                }
            } catch (_: Exception) {
            } finally {
                close()
            }
        }

        awaitClose {
            stopServer()
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}
    }
}
