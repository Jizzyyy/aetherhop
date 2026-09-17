package com.kadhafi.aetherhop.core.location

import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object GeoJsonExporter {

    private val json = Json { prettyPrint = true }

    fun exportToGeoJson(
        trackName: String = "AetherHop Tactical Features",
        waypoints: List<TacticalWaypointEntity> = emptyList(),
        breadcrumbs: List<BreadcrumbPoint> = emptyList()
    ): String {
        val root = buildJsonObject {
            put("type", "FeatureCollection")
            put("name", trackName)
            put(
                "features",
                buildJsonArray {
                    // Export Waypoints as Point Features
                    waypoints.forEach { wp ->
                        add(
                            buildJsonObject {
                                put("type", "Feature")
                                put("id", wp.id)
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("name", wp.label)
                                        put("type", wp.type)
                                        put("createdTimestamp", wp.createdTimestamp)
                                    }
                                )
                                put(
                                    "geometry",
                                    buildJsonObject {
                                        put("type", "Point")
                                        put(
                                            "coordinates",
                                            buildJsonArray {
                                                add(kotlinx.serialization.json.JsonPrimitive(wp.longitude))
                                                add(kotlinx.serialization.json.JsonPrimitive(wp.latitude))
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }

                    // Export Breadcrumbs Track as LineString Feature
                    if (breadcrumbs.isNotEmpty()) {
                        add(
                            buildJsonObject {
                                put("type", "Feature")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("name", "$trackName (Breadcrumb Path)")
                                        put("pointCount", breadcrumbs.size)
                                    }
                                )
                                put(
                                    "geometry",
                                    buildJsonObject {
                                        put("type", "LineString")
                                        put(
                                            "coordinates",
                                            buildJsonArray {
                                                breadcrumbs.forEach { pt ->
                                                    add(
                                                        buildJsonArray {
                                                            add(kotlinx.serialization.json.JsonPrimitive(pt.longitude))
                                                            add(kotlinx.serialization.json.JsonPrimitive(pt.latitude))
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }

        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
    }
}
