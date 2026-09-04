package com.kadhafi.aetherhop.core.audio

object AdpcmCodec {
    private val INDEX_TABLE = intArrayOf(
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8
    )

    private val STEP_SIZE_TABLE = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    fun encodePcmToAdpcm(pcm: ByteArray): ByteArray {
        val out = ByteArray(pcm.size / 4)
        var predictor = 0
        var stepIndex = 0

        var outIdx = 0
        var i = 0
        while (i < pcm.size - 3) {
            val sample1 = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            val sample2 = (pcm[i + 2].toInt() and 0xFF) or (pcm[i + 3].toInt() shl 8)

            val nibble1 = encodeSample(sample1.toShort(), predictor, stepIndex).also {
                predictor = it.first
                stepIndex = it.second
            }.third

            val nibble2 = encodeSample(sample2.toShort(), predictor, stepIndex).also {
                predictor = it.first
                stepIndex = it.second
            }.third

            out[outIdx++] = ((nibble2 shl 4) or (nibble1 and 0x0F)).toByte()
            i += 4
        }
        return out
    }

    fun decodeAdpcmToPcm(adpcm: ByteArray): ByteArray {
        val out = ByteArray(adpcm.size * 4)
        var predictor = 0
        var stepIndex = 0
        var outIdx = 0

        for (byte in adpcm) {
            val nibble1 = byte.toInt() and 0x0F
            val nibble2 = (byte.toInt() shr 4) and 0x0F

            val s1 = decodeSample(nibble1, predictor, stepIndex).also {
                predictor = it.first
                stepIndex = it.second
            }.third

            val s2 = decodeSample(nibble2, predictor, stepIndex).also {
                predictor = it.first
                stepIndex = it.second
            }.third

            out[outIdx++] = (s1.toInt() and 0xFF).toByte()
            out[outIdx++] = ((s1.toInt() shr 8) and 0xFF).toByte()
            out[outIdx++] = (s2.toInt() and 0xFF).toByte()
            out[outIdx++] = ((s2.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun encodeSample(sample: Short, prevPredictor: Int, prevIndex: Int): Triple<Int, Int, Int> {
        var stepIndex = prevIndex
        val step = STEP_SIZE_TABLE[stepIndex]
        var diff = sample - prevPredictor
        var sign = 0
        if (diff < 0) {
            sign = 8
            diff = -diff
        }

        var delta = 0
        var vpdiff = step shr 3

        if (diff >= step) {
            delta = delta or 4
            diff -= step
            vpdiff += step
        }
        val step2 = step shr 1
        if (diff >= step2) {
            delta = delta or 2
            diff -= step2
            vpdiff += step2
        }
        val step4 = step shr 2
        if (diff >= step4) {
            delta = delta or 1
            vpdiff += step4
        }

        var predictor = if (sign != 0) prevPredictor - vpdiff else prevPredictor + vpdiff
        predictor = predictor.coerceIn(-32768, 32767)

        val nibble = delta or sign
        stepIndex = (stepIndex + INDEX_TABLE[nibble]).coerceIn(0, STEP_SIZE_TABLE.size - 1)

        return Triple(predictor, stepIndex, nibble)
    }

    private fun decodeSample(nibble: Int, prevPredictor: Int, prevIndex: Int): Triple<Int, Int, Short> {
        var stepIndex = prevIndex
        val step = STEP_SIZE_TABLE[stepIndex]
        val sign = nibble and 8
        val delta = nibble and 7

        var vpdiff = step shr 3
        if ((delta and 4) != 0) vpdiff += step
        if ((delta and 2) != 0) vpdiff += step shr 1
        if ((delta and 1) != 0) vpdiff += step shr 2

        var predictor = if (sign != 0) prevPredictor - vpdiff else prevPredictor + vpdiff
        predictor = predictor.coerceIn(-32768, 32767)

        stepIndex = (stepIndex + INDEX_TABLE[nibble]).coerceIn(0, STEP_SIZE_TABLE.size - 1)
        return Triple(predictor, stepIndex, predictor.toShort())
    }
}
