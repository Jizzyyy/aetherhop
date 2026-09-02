package com.kadhafi.aetherhop.core.qr

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.charset.StandardCharsets

object QrCodeGenerator {

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val matrix = createSimpleMatrix(content, 25)

        val scale = size / matrix.size
        for (x in 0 until size) {
            for (y in 0 until size) {
                val matrixX = (x / scale).coerceIn(0, matrix.size - 1)
                val matrixY = (y / scale).coerceIn(0, matrix.size - 1)
                val isDark = matrix[matrixX][matrixY]
                bitmap.setPixel(x, y, if (isDark) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun createSimpleMatrix(data: String, gridDimension: Int): Array<BooleanArray> {
        val matrix = Array(gridDimension) { BooleanArray(gridDimension) }
        val bytes = data.toByteArray(StandardCharsets.UTF_8)

        // Draw finder patterns at corners
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, gridDimension - 7, 0)
        drawFinderPattern(matrix, 0, gridDimension - 7)

        // Encode payload bytes into inner matrix cells
        var byteIdx = 0
        for (i in 7 until gridDimension - 7) {
            for (j in 7 until gridDimension - 7) {
                if (byteIdx < bytes.size) {
                    val bit = (bytes[byteIdx % bytes.size].toInt() shr (j % 8)) and 1
                    matrix[i][j] = bit == 1
                } else {
                    matrix[i][j] = (i + j) % 2 == 0
                }
                byteIdx++
            }
        }
        return matrix
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startX: Int, startY: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isOuterBorder = r == 0 || r == 6 || c == 0 || c == 6
                val isInnerCenter = r in 2..4 && c in 2..4
                matrix[startX + r][startY + c] = isOuterBorder || isInnerCenter
            }
        }
    }
}
