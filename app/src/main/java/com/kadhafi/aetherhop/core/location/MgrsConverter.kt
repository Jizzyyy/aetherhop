package com.kadhafi.aetherhop.core.location

import kotlin.math.floor

data class MgrsCoordinate(
    val zoneNumber: Int,
    val zoneLetter: Char,
    val square100kId: String,
    val eastingMeter: Int,
    val northingMeter: Int
) {
    fun toFormatted10DigitString(): String {
        return String.format(
            java.util.Locale.US,
            "%d%c %s %05d %05d",
            zoneNumber,
            zoneLetter,
            square100kId,
            eastingMeter % 100000,
            northingMeter % 100000
        )
    }

    fun toCompactString(): String {
        return String.format(
            java.util.Locale.US,
            "%d%c%s%05d%05d",
            zoneNumber,
            zoneLetter,
            square100kId,
            eastingMeter % 100000,
            northingMeter % 100000
        )
    }
}

object MgrsConverter {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ" // 24 letters skipping I, O
    private const val ROW_ALPHABET = "ABCDEFGHJKLMNPQRSTUV" // 20 letters skipping I, O

    private fun get100kSet(zoneNumber: Int): Int {
        return ((zoneNumber - 1) % 3) + 1
    }

    private fun get100kColumnLetter(set: Int, easting: Double): Char {
        val colIndex = (floor(easting / 100000.0).toInt() - 1) % 8
        val baseIndex = when (set) {
            1 -> 0 // A-H
            2 -> 8 // J-R
            3 -> 16 // S-Z
            else -> 0
        }
        val targetIdx = (baseIndex + colIndex) % ALPHABET.length
        return ALPHABET[targetIdx]
    }

    private fun get100kRowLetter(zoneNumber: Int, northing: Double): Char {
        val rowIndex = floor(northing / 100000.0).toInt() % 20
        // Even zones start at row F (offset 5 in 0-indexed ROW_ALPHABET)
        val offset = if (zoneNumber % 2 == 0) 5 else 0
        val targetIdx = (rowIndex + offset) % ROW_ALPHABET.length
        return ROW_ALPHABET[targetIdx]
    }

    fun toMgrs(latitude: Double, longitude: Double): MgrsCoordinate {
        val utm = UtmConverter.toUtm(latitude, longitude)
        val set = get100kSet(utm.zoneNumber)
        val col = get100kColumnLetter(set, utm.easting)
        val row = get100kRowLetter(utm.zoneNumber, utm.northing)
        val squareId = "$col$row"

        val easting5 = (utm.easting % 100000.0).toInt()
        val northing5 = (utm.northing % 100000.0).toInt()

        return MgrsCoordinate(
            zoneNumber = utm.zoneNumber,
            zoneLetter = utm.zoneLetter,
            square100kId = squareId,
            eastingMeter = easting5,
            northingMeter = northing5
        )
    }
}
