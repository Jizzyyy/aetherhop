package com.kadhafi.aetherhop.data.map

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class OfflineTileCacheManager(context: Context) {
    private val appContext = context.applicationContext
    private val tilesDir = File(appContext.filesDir, "offline_map_tiles").apply {
        if (!exists()) mkdirs()
    }
    private val cachedTileKeys = ConcurrentHashMap.newKeySet<String>()

    init {
        tilesDir.listFiles()?.forEach { file ->
            cachedTileKeys.add(file.nameWithoutExtension)
        }
    }

    fun getTileKey(zoom: Int, x: Int, y: Int): String = "tile_${zoom}_${x}_${y}"

    fun hasTile(zoom: Int, x: Int, y: Int): Boolean {
        val key = getTileKey(zoom, x, y)
        return cachedTileKeys.contains(key) || File(tilesDir, "$key.png").exists()
    }

    fun getTileFile(zoom: Int, x: Int, y: Int): File? {
        val key = getTileKey(zoom, x, y)
        val file = File(tilesDir, "$key.png")
        return if (file.exists()) file else null
    }

    fun saveTile(zoom: Int, x: Int, y: Int, inputStream: InputStream): Boolean {
        return try {
            val key = getTileKey(zoom, x, y)
            val file = File(tilesDir, "$key.png")
            FileOutputStream(file).use { out ->
                inputStream.copyTo(out)
            }
            cachedTileKeys.add(key)
            true
        } catch (e: Exception) {
            android.util.Log.e("OfflineTileCacheManager", "Error saving offline tile", e)
            false
        }
    }

    fun getCachedTilesCount(): Int = cachedTileKeys.size

    fun clearCache() {
        tilesDir.deleteRecursively()
        tilesDir.mkdirs()
        cachedTileKeys.clear()
    }
}
