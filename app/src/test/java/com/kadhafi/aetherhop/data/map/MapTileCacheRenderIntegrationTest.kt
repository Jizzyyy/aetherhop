package com.kadhafi.aetherhop.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTileCacheRenderIntegrationTest {

    @Test
    fun testCenterTileCoordinateAcrossZoomLevels() {
        val lat = -6.2088
        val lon = 106.8456

        val tileZ14 = OfflineTileCacheManager.latLonToTile(lat, lon, 14)
        assertEquals(14, tileZ14.zoom)

        val tileZ15 = OfflineTileCacheManager.latLonToTile(lat, lon, 15)
        assertEquals(15, tileZ15.zoom)

        val tileZ16 = OfflineTileCacheManager.latLonToTile(lat, lon, 16)
        assertEquals(16, tileZ16.zoom)

        // As zoom increases by 1, tile indices approximately double
        assertEquals((tileZ14.x * 2).toDouble(), tileZ15.x.toDouble(), 2.0)
        assertEquals((tileZ15.x * 2).toDouble(), tileZ16.x.toDouble(), 2.0)
    }

    @Test
    fun testBoundingBoxScaleWithRadius() {
        val lat = -6.2088
        val lon = 106.8456

        // 100m radius -> should be small 1x1 or 2x2
        val tiles100m = OfflineTileCacheManager.calculateBoundingTiles(lat, lon, 100.0, listOf(15))
        assertTrue("100m radius should require <= 4 tiles", tiles100m.size in 1..4)

        // 1500m radius -> expands to more tiles
        val tiles1500m = OfflineTileCacheManager.calculateBoundingTiles(lat, lon, 1500.0, listOf(15))
        assertTrue("1500m radius should contain more tiles than 100m", tiles1500m.size > tiles100m.size)
    }

    @Test
    fun testCanvasTileCenteringOffset() {
        val canvasWidth = 800f
        val canvasHeight = 1200f
        val panX = 50f
        val panY = -30f
        val zoom = 1.5f

        val center = Pair(canvasWidth / 2 + panX, canvasHeight / 2 + panY)
        assertEquals(450f, center.first, 0.001f)
        assertEquals(570f, center.second, 0.001f)

        val baseTileSize = 256f
        val drawSize = baseTileSize * zoom // 384f
        val topLeftX = center.first - drawSize / 2
        val topLeftY = center.second - drawSize / 2

        assertEquals(258f, topLeftX, 0.001f)
        assertEquals(378f, topLeftY, 0.001f)
    }
}
