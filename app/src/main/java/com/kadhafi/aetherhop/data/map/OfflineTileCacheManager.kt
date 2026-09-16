package com.kadhafi.aetherhop.data.map

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

data class TileCoordinate(
    val zoom: Int,
    val x: Int,
    val y: Int
)

data class TileCacheStats(
    val tileCount: Int,
    val sizeBytes: Long
)

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

    companion object {
        fun latLonToTile(lat: Double, lon: Double, zoom: Int): TileCoordinate {
            val n = 2.0.pow(zoom)
            val x = floor((lon + 180.0) / 360.0 * n).toInt()
            val latRad = Math.toRadians(lat)
            val y = floor((1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n).toInt()
            return TileCoordinate(zoom, x.coerceAtLeast(0), y.coerceAtLeast(0))
        }

        fun calculateBoundingTiles(
            centerLat: Double,
            centerLon: Double,
            radiusMeters: Double,
            zoomLevels: List<Int> = listOf(14, 15)
        ): List<TileCoordinate> {
            val dLat = radiusMeters / 111320.0
            val dLon = radiusMeters / (111320.0 * cos(Math.toRadians(centerLat)).coerceAtLeast(0.01))

            val minLat = centerLat - dLat
            val maxLat = centerLat + dLat
            val minLon = centerLon - dLon
            val maxLon = centerLon + dLon

            val result = mutableListOf<TileCoordinate>()
            for (z in zoomLevels) {
                val nw = latLonToTile(maxLat, minLon, z)
                val se = latLonToTile(minLat, maxLon, z)

                val minX = minOf(nw.x, se.x)
                val maxX = maxOf(nw.x, se.x)
                val minY = minOf(nw.y, se.y)
                val maxY = maxOf(nw.y, se.y)

                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        result.add(TileCoordinate(z, x, y))
                    }
                }
            }
            return result
        }
    }

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

    fun getCacheSizeBytes(): Long {
        return tilesDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getCacheStats(): TileCacheStats {
        return TileCacheStats(
            tileCount = getCachedTilesCount(),
            sizeBytes = getCacheSizeBytes()
        )
    }

    fun clearCache() {
        tilesDir.deleteRecursively()
        tilesDir.mkdirs()
        cachedTileKeys.clear()
    }
}
