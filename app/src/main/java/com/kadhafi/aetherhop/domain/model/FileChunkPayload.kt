package com.kadhafi.aetherhop.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FileChunkPayload(
    val fileId: String,
    val fileName: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val dataBase64: String,
    val checksum: String
)
