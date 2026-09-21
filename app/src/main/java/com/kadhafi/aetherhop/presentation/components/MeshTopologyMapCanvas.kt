package com.kadhafi.aetherhop.presentation.components

import android.location.Location
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kadhafi.aetherhop.core.location.BreadcrumbPoint
import com.kadhafi.aetherhop.core.location.DeadReckoningState
import com.kadhafi.aetherhop.core.location.GeodesicCalculator
import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import com.kadhafi.aetherhop.data.map.OfflineTileCacheManager
import com.kadhafi.aetherhop.domain.model.PeerNode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MeshTopologyMapCanvas(
    peers: List<PeerNode> = emptyList(),
    breadcrumbs: List<BreadcrumbPoint> = emptyList(),
    waypoints: List<TacticalWaypointEntity> = emptyList(),
    selectedWaypointId: String? = null,
    azimuthDegrees: Float = 0f,
    currentLocation: Location? = null,
    deadReckoningState: DeadReckoningState? = null,
    coordinateFormat: com.kadhafi.aetherhop.core.location.CoordinateFormat = com.kadhafi.aetherhop.core.location.CoordinateFormat.DECIMAL,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    val isTacticalRed = primaryColor == Color(0xFFFF1A35)
    val isNvgGreen = primaryColor == Color(0xFF00FF41)
    val isNightVision = isTacticalRed || isNvgGreen
    val highlightColor = if (isNightVision) primaryColor else Color(0xFF00E5FF)

    var isNorthUp by remember { mutableStateOf(false) }
    val effectiveAzimuth = if (isNorthUp) 0f else azimuthDegrees

    val infiniteTransition = rememberInfiniteTransition(label = "hazard_pulse")
    val hazardPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hazard_pulse_alpha"
    )

    val ringTextPaint = remember(primaryColor) {
        android.graphics.Paint().apply {
            color = primaryColor.toArgb()
            textSize = 26f
            alpha = 150
            isAntiAlias = true
        }
    }

    val mappedPeers = remember(peers, effectiveAzimuth) {
        peers.map { peer ->
            val normDist = if (peer.distanceMeters <= 0) 0.5f else (peer.distanceMeters.toFloat() / 20f).coerceIn(0.1f, 0.95f)
            val baseAngleDeg = (peer.id.hashCode() % 360).toFloat()
            val adjustedAngleDeg = (baseAngleDeg - effectiveAzimuth + 360f) % 360f
            val peerAngleRad = Math.toRadians(adjustedAngleDeg.toDouble())

            val dotColor = if (isTacticalRed) {
                when {
                    peer.rssi > -60 -> Color(0xFFFF1A35)
                    peer.rssi > -80 -> Color(0xFFCC0029)
                    else -> Color(0xFF800014)
                }
            } else if (isNvgGreen) {
                when {
                    peer.rssi > -60 -> Color(0xFF00FF41)
                    peer.rssi > -80 -> Color(0xFF00B32D)
                    else -> Color(0xFF00661A)
                }
            } else {
                when {
                    peer.rssi > -60 -> Color(0xFF00E676)
                    peer.rssi > -80 -> Color(0xFFFFD600)
                    else -> Color(0xFFFF5252)
                }
            }

            CalculatedMapNode(
                id = peer.id,
                name = peer.name,
                normDist = normDist,
                angleRad = peerAngleRad,
                dotColor = dotColor
            )
        }
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var cursorOffset by remember { mutableStateOf<Offset?>(null) }

    val isDrActive = deadReckoningState?.isDeadReckoningActive == true
    val originLat = if (isDrActive) deadReckoningState?.currentLatitude ?: -6.2088 else (currentLocation?.latitude ?: -6.2088)
    val originLon = if (isDrActive) deadReckoningState?.currentLongitude ?: 106.8456 else (currentLocation?.longitude ?: 106.8456)

    val context = LocalContext.current
    val tileCacheManager = remember(context) { OfflineTileCacheManager(context) }

    val cachedTileBitmap = remember(originLat, originLon) {
        val centerTile = OfflineTileCacheManager.latLonToTile(originLat, originLon, 15)
        val tileFile = tileCacheManager.getTileFile(centerTile.zoom, centerTile.x, centerTile.y)
        if (tileFile != null && tileFile.exists()) {
            try {
                android.graphics.BitmapFactory.decodeFile(tileFile.absolutePath)?.asImageBitmap()
            } catch (_: Exception) { null }
        } else null
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(0.5f, 5.0f)
                    panOffset += pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                        cursorOffset = null
                    },
                    onTap = { tapPos ->
                        cursorOffset = if (cursorOffset == null) tapPos else null
                    }
                )
            }
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val centerPx = Offset(widthPx / 2 + panOffset.x, heightPx / 2 + panOffset.y)
        val maxRadiusPx = min(widthPx, heightPx) / 2 * 0.85f * zoomScale

        val cursorInfo = remember(cursorOffset, centerPx, maxRadiusPx, effectiveAzimuth, originLat, originLon, coordinateFormat) {
            cursorOffset?.let { cPos ->
                val dx = cPos.x - centerPx.x
                val dy = cPos.y - centerPx.y
                val distPx = sqrt(dx * dx + dy * dy)
                val distM = if (maxRadiusPx > 0) (distPx / maxRadiusPx) * 200.0 else 0.0
                val angleRad = atan2(dy.toDouble(), dx.toDouble())
                val screenAngleDeg = (Math.toDegrees(angleRad) + 360.0) % 360.0
                val cursorBearing = (screenAngleDeg + effectiveAzimuth) % 360.0

                val dLat = (distM * cos(Math.toRadians(cursorBearing))) / 111320.0
                val dLon = (distM * sin(Math.toRadians(cursorBearing))) / (111320.0 * cos(Math.toRadians(originLat)).coerceAtLeast(0.01))
                val cursorLat = originLat + dLat
                val cursorLon = originLon + dLon
                val coordStr = com.kadhafi.aetherhop.core.location.CoordinateFormatManager.formatCoordinates(cursorLat, cursorLon, coordinateFormat)
                Triple(coordStr, distM, cursorBearing)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = centerPx
            val maxRadius = maxRadiusPx

            // Draw offline raster tile background layer if cached
            if (cachedTileBitmap != null) {
                val tileDrawSize = 256.dp.toPx() * zoomScale
                val tileTopLeft = Offset(center.x - tileDrawSize / 2, center.y - tileDrawSize / 2)
                drawImage(
                    image = cachedTileBitmap,
                    dstOffset = IntOffset(tileTopLeft.x.toInt(), tileTopLeft.y.toInt()),
                    dstSize = IntSize(tileDrawSize.toInt(), tileDrawSize.toInt()),
                    alpha = if (isNightVision) 0.35f else 0.65f
                )
            }

            // Compass cardinal rings and tactical grid lines with distance scale labels
            val ringLabels = listOf("25m", "50m", "100m", "200m")
            for (i in 1..4) {
                val ringRadius = maxRadius * (i / 4f)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.08f * i),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1.2f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    ringLabels[i - 1],
                    center.x + 8f,
                    center.y - ringRadius + 22f,
                    ringTextPaint
                )
            }

            // Tactical Crosshair Axes (N-S, E-W)
            drawLine(
                color = primaryColor.copy(alpha = 0.25f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.25f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1f
            )

            // Draw tactical waypoints using geodesic calculation if valid GPS or hash angle
            waypoints.forEachIndexed { index, wp ->
                val wpDist = maxRadius * 0.7f
                val bearingDeg = GeodesicCalculator.calculateForwardBearingDegrees(originLat, originLon, wp.latitude, wp.longitude)
                val adjustedBearing = (bearingDeg - effectiveAzimuth + 360.0) % 360.0
                val wpAngleRad = Math.toRadians(adjustedBearing)

                val wpX = center.x + (wpDist * cos(wpAngleRad)).toFloat()
                val wpY = center.y + (wpDist * sin(wpAngleRad)).toFloat()
                val wpColor = if (isNightVision) {
                    primaryColor
                } else {
                    when (wp.type) {
                        "MEDICAL" -> Color(0xFFFF1744)
                        "HAZARD" -> Color(0xFFFFD600)
                        "RENDEZVOUS" -> Color(0xFF00E5FF)
                        else -> Color(0xFF00E676)
                    }
                }

                val isSelected = wp.id == selectedWaypointId
                val wpRadius = if (isSelected) 9.dp.toPx() else 7.dp.toPx()

                drawCircle(
                    color = wpColor,
                    radius = wpRadius,
                    center = Offset(wpX, wpY)
                )
                drawCircle(
                    color = wpColor.copy(alpha = if (isSelected) 0.5f else 0.3f),
                    radius = if (isSelected) 22.dp.toPx() else 14.dp.toPx(),
                    center = Offset(wpX, wpY),
                    style = if (isSelected) Stroke(width = 2.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill
                )

                // Pulsing hazard boundary exclusion ring
                if (wp.type == "HAZARD") {
                    val hazardBoundaryRadius = 28.dp.toPx()
                    drawCircle(
                        color = Color(0xFFFF1744).copy(alpha = hazardPulseAlpha * 0.15f),
                        radius = hazardBoundaryRadius,
                        center = Offset(wpX, wpY)
                    )
                    drawCircle(
                        color = Color(0xFFFF1744).copy(alpha = hazardPulseAlpha),
                        radius = hazardBoundaryRadius,
                        center = Offset(wpX, wpY),
                        style = Stroke(width = 1.8f)
                    )
                }

                // If this waypoint is locked, draw navigation guidance needle from center
                if (isSelected) {
                    val needleLength = maxRadius * 0.5f
                    val needleEnd = Offset(
                        center.x + (needleLength * cos(wpAngleRad)).toFloat(),
                        center.y + (needleLength * sin(wpAngleRad)).toFloat()
                    )
                    drawLine(
                        color = highlightColor,
                        start = center,
                        end = needleEnd,
                        strokeWidth = 3f
                    )
                    // Arrow head pointing to target
                    val arrowAngle1 = wpAngleRad + Math.toRadians(150.0)
                    val arrowAngle2 = wpAngleRad - Math.toRadians(150.0)
                    val arrowHeadSize = 16f
                    drawLine(
                        color = highlightColor,
                        start = needleEnd,
                        end = Offset(
                            needleEnd.x + (arrowHeadSize * cos(arrowAngle1)).toFloat(),
                            needleEnd.y + (arrowHeadSize * sin(arrowAngle1)).toFloat()
                        ),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = highlightColor,
                        start = needleEnd,
                        end = Offset(
                            needleEnd.x + (arrowHeadSize * cos(arrowAngle2)).toFloat(),
                            needleEnd.y + (arrowHeadSize * sin(arrowAngle2)).toFloat()
                        ),
                        strokeWidth = 3f
                    )
                }
            }

            // Draw live GPS accuracy circle if available
            val accuracyMeters = if (currentLocation != null && currentLocation.hasAccuracy()) currentLocation.accuracy else 0f
            if (accuracyMeters > 0f) {
                val accuracyRadiusPx = (accuracyMeters / 50f * maxRadius).coerceIn(12.dp.toPx(), maxRadius)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.12f),
                    radius = accuracyRadiusPx,
                    center = center
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    radius = accuracyRadiusPx,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            // Draw center self-node
            drawCircle(
                color = primaryColor,
                radius = 8.dp.toPx(),
                center = center
            )

            // Draw Dead Reckoning estimated drift radius if active
            if (isDrActive) {
                val driftRadiusPx = (deadReckoningState!!.estimatedDriftRadiusMeters / 50f * maxRadius).coerceIn(16.dp.toPx(), maxRadius)
                drawCircle(
                    color = Color(0xFFFFD600).copy(alpha = 0.12f),
                    radius = driftRadiusPx,
                    center = center
                )
                drawCircle(
                    color = Color(0xFFFFD600).copy(alpha = 0.5f),
                    radius = driftRadiusPx,
                    center = center,
                    style = Stroke(width = 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )
            }

            // Draw movement breadcrumb dots
            breadcrumbs.takeLast(10).forEachIndexed { idx, point ->
                val alpha = (idx + 1) / 10f * 0.5f
                val offsetPx = (idx + 1) * 6.dp.toPx()
                drawCircle(
                    color = primaryColor.copy(alpha = alpha),
                    radius = 3.dp.toPx(),
                    center = Offset(center.x - offsetPx, center.y + offsetPx)
                )
            }

            // Draw links and peer nodes
            mappedPeers.forEach { node ->
                val peerRadius = maxRadius * node.normDist
                val peerX = center.x + (peerRadius * cos(node.angleRad)).toFloat()
                val peerY = center.y + (peerRadius * sin(node.angleRad)).toFloat()
                val peerOffset = Offset(peerX, peerY)

                // Mesh topology link vector
                drawLine(
                    color = node.dotColor.copy(alpha = 0.4f),
                    start = center,
                    end = peerOffset,
                    strokeWidth = 1.5f
                )

                // Peer node circle
                drawCircle(
                    color = node.dotColor,
                    radius = 6.dp.toPx(),
                    center = peerOffset
                )
            }

            // Draw dynamic coordinate crosshair under touch cursor
            cursorOffset?.let { cOffset ->
                val crosshairLength = 20.dp.toPx()
                drawLine(
                    color = highlightColor.copy(alpha = 0.8f),
                    start = Offset(cOffset.x - crosshairLength, cOffset.y),
                    end = Offset(cOffset.x + crosshairLength, cOffset.y),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = highlightColor.copy(alpha = 0.8f),
                    start = Offset(cOffset.x, cOffset.y - crosshairLength),
                    end = Offset(cOffset.x, cOffset.y + crosshairLength),
                    strokeWidth = 1.5f
                )
                drawCircle(
                    color = highlightColor,
                    radius = 6.dp.toPx(),
                    center = cOffset,
                    style = Stroke(width = 1.5f)
                )
            }
        }

        // Tactical HUD Navigation Overlay
        val lockedWaypoint = remember(waypoints, selectedWaypointId) {
            waypoints.find { it.id == selectedWaypointId }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TAC HUD",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AZ: ${effectiveAzimuth.toInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (currentLocation != null && currentLocation.hasAltitude()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ALT: ${currentLocation.altitude.toInt()}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (currentLocation != null && currentLocation.hasSpeed() && currentLocation.speed > 0f) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SPD: ${(currentLocation.speed * 3.6f).toInt()}km/h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isDrActive) {
                    Surface(
                        color = Color(0xFFFFD600).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "DR MODE: ${deadReckoningState!!.accumulatedSteps} Langkah • ±${deadReckoningState.estimatedDriftRadiusMeters.toInt()}m Drift",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD600),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                val posStr = com.kadhafi.aetherhop.core.location.CoordinateFormatManager.formatCoordinates(
                    originLat,
                    originLon,
                    coordinateFormat
                )
                Text(
                    text = "POS: $posStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                cursorInfo?.let { (cStr, dM, cBrg) ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "RETICLE: $cStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFD600),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "DST: ${GeodesicCalculator.formatDistance(dM)} • BRG: ${cBrg.toInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (lockedWaypoint != null) {
                    val distM = GeodesicCalculator.calculateDistanceMeters(originLat, originLon, lockedWaypoint.latitude, lockedWaypoint.longitude)
                    val brg = GeodesicCalculator.calculateForwardBearingDegrees(originLat, originLon, lockedWaypoint.latitude, lockedWaypoint.longitude)
                    val targetPosStr = com.kadhafi.aetherhop.core.location.CoordinateFormatManager.formatCoordinates(
                        lockedWaypoint.latitude,
                        lockedWaypoint.longitude,
                        coordinateFormat
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "TARGET: ${lockedWaypoint.label} (${lockedWaypoint.type})",
                        style = MaterialTheme.typography.labelSmall,
                        color = highlightColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "LOC: $targetPosStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "DST: ${GeodesicCalculator.formatDistance(distM)} • BRG: ${brg.toInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Compass / North-Reset Floating Toggle
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clickable {
                    isNorthUp = !isNorthUp
                    panOffset = Offset.Zero
                    zoomScale = 1f
                },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "North Reset",
                    tint = if (isNorthUp) Color(0xFF00E5FF) else primaryColor,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (isNorthUp) 0f else -azimuthDegrees)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isNorthUp) "NORTH-UP" else "HEAD-UP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isNorthUp) Color(0xFF00E5FF) else primaryColor
                )
            }
        }
    }
}

private data class CalculatedMapNode(
    val id: String,
    val name: String,
    val normDist: Float,
    val angleRad: Double,
    val dotColor: Color
)
