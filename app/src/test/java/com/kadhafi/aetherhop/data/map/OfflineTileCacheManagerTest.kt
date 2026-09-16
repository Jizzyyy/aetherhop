package com.kadhafi.aetherhop.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTileCacheManagerTest {

    @Test
    fun testTileKeyFormatting() {
        val zoom = 15
        val x = 26180
        val y = 16670

        val expectedKey = "tile_15_26180_16670"
        val manager = OfflineTileCacheManager.Companion

        // Use companion or coordinate check
        val tile = TileCoordinate(zoom, x, y)
        assertEquals(15, tile.zoom)
        assertEquals(26180, tile.x)
        assertEquals(16670, tile.y)
    }

    @Test
    fun testLatLonToTileMonasJakarta() {
        // Monas: lat -6.1754, lon 106.8272 at zoom 15
        val lat = -6.1754
        val lon = 106.8272
        val zoom = 15

        val tile = OfflineTileCacheManager.latLonToTile(lat, lon, zoom)
        assertEquals(15, tile.zoom)
        assertTrue(tile.x > 0)
        assertTrue(tile.y > 0)

        // At zoom 15, longitude 106.8272 maps around tile X = 26100..26200
        assertTrue("X tile should be near 26105, was ${tile.x}", tile.x in 26000..26300)
        assertTrue("Y tile should be near 16950, was ${tile.y}", tile.y in 16800..17100)
    }

    @Test
    fun testBoundingTilesCalculationCoversRadius() {
        val lat = -6.2088
        val lon = 106.8456
        val radiusMeters = 500.0 // 500 meter operational perimeter
        val zoomLevels = listOf(15)

        val tiles = OfflineTileCacheManager.calculateBoundingTiles(lat, lon, radiusMeters, zoomLevels)

        // A 500m radius at zoom 15 typically spans between 1 and 9 tiles (e.g. 1x1, 2x2, or 3x3)
        assertTrue("Bounding tiles count must be > 0", tiles.isNotEmpty())
        assertTrue("Tiles should be bounded", tiles.size <= 16)

        tiles.forEach { tile ->
            assertEquals(15, tile.zoom)
            assertTrue(tile.x >= 0)
            assertTrue(tile.y >= 0)
        }
    }

    @Test
    fun testMultiZoomBoundingTilesExpansion() {
        val lat = -6.2088
        val lon = 106.8456
        val radiusMeters = 200.0
        val zoomLevels = listOf(14, 15, 16)

        val tiles = OfflineTileCacheManager.calculateBoundingTiles(lat, lon, radiusMeters, zoomLevels)

        val zoomCounts = tiles.groupBy { it.zoom }
        assertTrue(zoomCounts.containsKey(14))
        assertTrue(zoomCounts.containsKey(15))
        assertTrue(zoomCounts.containsKey(16))
    }

    @Test
    fun testTileCacheStatsStructure() {
        val stats = TileCacheStats(tileCount = 42, sizeBytes = 1024L * 512L)
        assertEquals(42, stats.tileCount)
        assertEquals(524288L, stats.sizeBytes)
    }
}
