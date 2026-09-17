package com.kadhafi.aetherhop.core.location

import com.kadhafi.aetherhop.data.local.entity.TacticalWaypointEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    private fun formatIsoTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun exportToGpx(
        trackName: String = "AetherHop Tactical Track",
        waypoints: List<TacticalWaypointEntity> = emptyList(),
        breadcrumbs: List<BreadcrumbPoint> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<gpx version=\"1.1\" creator=\"AetherHop Tactical Mesh\"")
        sb.appendLine("     xmlns=\"http://www.topografix.com/GPX/1/1\"")
        sb.appendLine("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
        sb.appendLine("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>${escapeXml(trackName)}</name>")
        sb.appendLine("    <time>${formatIsoTime(System.currentTimeMillis())}</time>")
        sb.appendLine("  </metadata>")

        // Export Waypoints
        waypoints.forEach { wp ->
            sb.appendLine(
                String.format(
                    Locale.US,
                    "  <wpt lat=\"%.6f\" lon=\"%.6f\">",
                    wp.latitude,
                    wp.longitude
                )
            )
            sb.appendLine("    <name>${escapeXml(wp.label)}</name>")
            sb.appendLine("    <type>${escapeXml(wp.type)}</type>")
            sb.appendLine("    <time>${formatIsoTime(wp.createdTimestamp)}</time>")
            sb.appendLine("  </wpt>")
        }

        // Export Breadcrumbs Track
        if (breadcrumbs.isNotEmpty()) {
            sb.appendLine("  <trk>")
            sb.appendLine("    <name>${escapeXml(trackName)}</name>")
            sb.appendLine("    <trkseg>")
            breadcrumbs.forEach { pt ->
                sb.appendLine(
                    String.format(
                        Locale.US,
                        "      <trkpt lat=\"%.6f\" lon=\"%.6f\">",
                        pt.latitude,
                        pt.longitude
                    )
                )
                sb.appendLine("        <time>${formatIsoTime(pt.timestamp)}</time>")
                sb.appendLine("      </trkpt>")
            }
            sb.appendLine("    </trkseg>")
            sb.appendLine("  </trk>")
        }

        sb.appendLine("</gpx>")
        return sb.toString()
    }
}
