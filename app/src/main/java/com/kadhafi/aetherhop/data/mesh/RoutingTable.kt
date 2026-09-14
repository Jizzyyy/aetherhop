package com.kadhafi.aetherhop.data.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class RouteEntry(
    val destinationId: String,
    val nextHopIp: String,
    val hops: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)

class RoutingTable {
    private val routes = ConcurrentHashMap<String, RouteEntry>()
    private val _routesFlow = MutableStateFlow<List<RouteEntry>>(emptyList())
    val routesFlow: StateFlow<List<RouteEntry>> = _routesFlow.asStateFlow()

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
            _routesFlow.value = routes.values.toList()
        }
    }

    fun getNextHopIp(destinationId: String): String? {
        return routes[destinationId]?.nextHopIp
    }

    fun removeStaleRoutes(maxAgeMs: Long = 60000) {
        val now = System.currentTimeMillis()
        val removed = routes.entries.removeIf { now - it.value.lastUpdated > maxAgeMs }
        if (removed) {
            _routesFlow.value = routes.values.toList()
        }
    }

    fun getAllRoutes(): List<RouteEntry> = routes.values.toList()
}
