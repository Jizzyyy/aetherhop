package com.kadhafi.aetherhop.data.network

import com.kadhafi.aetherhop.core.util.Base64Compat
import com.kadhafi.aetherhop.domain.model.MeshPacket
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun MeshPacket.withOptimalCompression(): MeshPacket {
    return if (!isCompressed && PayloadCompressionManager.shouldCompress(payload)) {
        val compressed = PayloadCompressionManager.compressString(payload)
        if (compressed.length < payload.length) {
            copy(payload = compressed, isCompressed = true)
        } else {
            this
        }
    } else {
        this
    }
}

fun MeshPacket.withDecompression(): MeshPacket {
    return if (isCompressed) {
        try {
            val decompressed = PayloadCompressionManager.decompressString(payload)
            copy(payload = decompressed, isCompressed = false)
        } catch (_: Exception) {
            this
        }
    } else {
        this
    }
}

object PayloadCompressionManager {
    const val COMPRESSION_THRESHOLD_BYTES = 256

    private val totalRawBytesTracked = AtomicLong(0)
    private val totalCompressedBytesTracked = AtomicLong(0)

    fun compressString(input: String): String {
        val rawBytes = input.toByteArray(Charsets.UTF_8)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(rawBytes)
        }
        val compressedBytes = baos.toByteArray()

        totalRawBytesTracked.addAndGet(rawBytes.size.toLong())
        totalCompressedBytesTracked.addAndGet(compressedBytes.size.toLong())

        return Base64Compat.encodeToString(compressedBytes)
    }

    fun decompressString(compressedBase64: String): String {
        val compressedBytes = Base64Compat.decode(compressedBase64)
        val bais = ByteArrayInputStream(compressedBytes)
        val baos = ByteArrayOutputStream()
        GZIPInputStream(bais).use { gzip ->
            gzip.copyTo(baos)
        }
        return baos.toString(Charsets.UTF_8.name())
    }

    fun shouldCompress(payload: String): Boolean {
        return payload.length >= COMPRESSION_THRESHOLD_BYTES
    }

    fun getCumulativeSavingsBytes(): Long {
        val raw = totalRawBytesTracked.get()
        val comp = totalCompressedBytesTracked.get()
        return (raw - comp).coerceAtLeast(0L)
    }

    fun getCumulativeRawBytes(): Long = totalRawBytesTracked.get()

    fun getCumulativeCompressedBytes(): Long = totalCompressedBytesTracked.get()

    fun resetStats() {
        totalRawBytesTracked.set(0)
        totalCompressedBytesTracked.set(0)
    }
}
