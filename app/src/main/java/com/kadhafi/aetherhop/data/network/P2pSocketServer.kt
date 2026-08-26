package com.kadhafi.aetherhop.data.network

import com.kadhafi.aetherhop.domain.model.MeshPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class P2pSocketServer(private val port: Int = 8888) {
    @Volatile
    private var serverSocket: ServerSocket? = null

    fun startServer(): Flow<MeshPacket> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    soTimeout = 10000 // 10 seconds socket read/accept timeout
                }
                while (!isClosedForSend) {
                    val socket: Socket = serverSocket?.accept() ?: break
                    // Spawn asynchronous worker coroutine to prevent slow clients from blocking accept loop
                    launch(Dispatchers.IO) {
                        try {
                            val packet = PacketSerializer.readPacket(socket.getInputStream())
                            trySend(packet)
                        } catch (_: IOException) {
                        } finally {
                            try {
                                socket.close()
                            } catch (_: IOException) {}
                        }
                    }
                }
            } catch (_: IOException) {
            } finally {
                stopServer()
            }
        }

        awaitClose {
            job.cancel()
            stopServer()
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: IOException) {}
    }
}
