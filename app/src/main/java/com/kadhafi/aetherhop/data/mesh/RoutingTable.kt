package com.kadhafi.aetherhop.data.mesh

import java.util.concurrent.ConcurrentHashMap

data class RouteEntry(
    val destinationId: String,
    val nextHopIp: String,
    val hops: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)

class RoutingTable {
    private val routes = ConcurrentHashMap<String, RouteEntry>()

    fun updateRoute(destinationId: String, nextHopIp: String, hops: Int = 1) {
        if (destinationId.isBlank() || nextHopIp.isBlank()) return
        val existing = routes[destinationId]
        if (existing == null || hops <= existing.hops || (System.currentTimeMillis() - existing.lastUpdated > 30000)) {
            routes[destinationId] = RouteEntry(
                destinationId = destinationId,
                nextHopIp = nextHopIp,
                hops = hops,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun getNextHopIp(destinationId: String): String? {
        return routes[destinationId]?.nextHopIp
    }

    fun removeStaleRoutes(maxAgeMs: Long = 60000) {
        val now = System.currentTimeMillis()
        routes.entries.removeIf { now - it.value.lastUpdated > maxAgeMs }
    }

    fun getAllRoutes(): List<RouteEntry> = routes.values.toList()
}
