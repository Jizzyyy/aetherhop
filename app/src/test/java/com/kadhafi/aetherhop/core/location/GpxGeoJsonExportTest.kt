package com.kadhafi.aetherhop.core.location

import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxGeoJsonExportTest {

    private val sampleWaypoints = listOf(
        TacticalWaypointEntity(
            id = "wp_alpha",
            label = "FOB Alpha & Ops",
            latitude = -6.2088,
            longitude = 106.8456,
            type = "CAMP",
            createdTimestamp = 1788500000000L
        ),
        TacticalWaypointEntity(
            id = "wp_hazard",
            label = "Chemical Hazard Area",
            latitude = -6.2150,
            longitude = 106.8500,
            type = "HAZARD",
            createdTimestamp = 1788501000000L
        )
    )

    private val sampleBreadcrumbs = listOf(
        BreadcrumbPoint(-6.2088, 106.8456, 1788500000000L),
        BreadcrumbPoint(-6.2100, 106.8470, 1788500500000L),
        BreadcrumbPoint(-6.2150, 106.8500, 1788501000000L)
    )

    @Test
    fun testGpxExportHeaderAndTags() {
        val gpx = GpxExporter.exportToGpx(
            trackName = "Operation Nightfall",
            waypoints = sampleWaypoints,
            breadcrumbs = sampleBreadcrumbs
        )

        assertNotNull(gpx)
        assertTrue("Must have XML declaration", gpx.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue("Must declare GPX 1.1 namespace", gpx.contains("xmlns=\"http://www.topografix.com/GPX/1/1\""))
        assertTrue("Must escape special characters in name", gpx.contains("<name>Operation Nightfall</name>"))
        assertTrue("Must escape ampersand in waypoint label", gpx.contains("<name>FOB Alpha &amp; Ops</name>"))
        assertTrue("Must include waypoint latitude and longitude", gpx.contains("<wpt lat=\"-6.208800\" lon=\"106.845600\">"))
        assertTrue("Must format waypoint type", gpx.contains("<type>HAZARD</type>"))

        // Track validation
        assertTrue("Must include trk tag", gpx.contains("<trk>"))
        assertTrue("Must include trkseg tag", gpx.contains("<trkseg>"))
        assertTrue("Must contain trkpt elements", gpx.contains("<trkpt lat=\"-6.210000\" lon=\"106.847000\">"))
        assertTrue("Must close gpx root", gpx.endsWith("</gpx>\n") || gpx.endsWith("</gpx>"))
    }

    @Test
    fun testGpxExportWithoutBreadcrumbs() {
        val gpx = GpxExporter.exportToGpx(
            trackName = "Waypoints Only",
            waypoints = sampleWaypoints,
            breadcrumbs = emptyList()
        )

        assertTrue(gpx.contains("<wpt lat=\"-6.208800\""))
        assertTrue(!gpx.contains("<trk>"))
    }

    @Test
    fun testGeoJsonExportStructureAndRfcCompliance() {
        val geoJson = GeoJsonExporter.exportToGeoJson(
            trackName = "Patrol Sector 9",
            waypoints = sampleWaypoints,
            breadcrumbs = sampleBreadcrumbs
        )

        assertNotNull(geoJson)
        val root = Json.parseToJsonElement(geoJson).jsonObject

        assertEquals("FeatureCollection", root["type"]?.jsonPrimitive?.content)
        assertEquals("Patrol Sector 9", root["name"]?.jsonPrimitive?.content)

        val features = root["features"]?.jsonArray
        assertNotNull(features)
        // 2 waypoints + 1 breadcrumb track linestring = 3 features
        assertEquals(3, features?.size)

        // Validate first waypoint Point feature
        val wpFeature1 = features?.get(0)?.jsonObject
        assertEquals("Feature", wpFeature1?.get("type")?.jsonPrimitive?.content)
        assertEquals("wp_alpha", wpFeature1?.get("id")?.jsonPrimitive?.content)

        val geom1 = wpFeature1?.get("geometry")?.jsonObject
        assertEquals("Point", geom1?.get("type")?.jsonPrimitive?.content)
        val coords1 = geom1?.get("coordinates")?.jsonArray
        // RFC 7946: GeoJSON is [longitude, latitude]
        assertEquals(106.8456, coords1?.get(0)?.jsonPrimitive?.content?.toDouble() ?: 0.0, 0.0001)
        assertEquals(-6.2088, coords1?.get(1)?.jsonPrimitive?.content?.toDouble() ?: 0.0, 0.0001)

        // Validate LineString feature
        val trackFeature = features?.get(2)?.jsonObject
        val trackGeom = trackFeature?.get("geometry")?.jsonObject
        assertEquals("LineString", trackGeom?.get("type")?.jsonPrimitive?.content)
        val trackCoords = trackGeom?.get("coordinates")?.jsonArray
        assertEquals(3, trackCoords?.size)
    }
}
