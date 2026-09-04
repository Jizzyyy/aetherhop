package com.kadhafi.aetherhop.core.util

import java.util.Arrays

object SecureMemoryZeroizer {

    fun zeroize(byteArray: ByteArray?) {
        if (byteArray != null) {
            Arrays.fill(byteArray, 0.toByte())
        }
    }

    fun zeroize(charArray: CharArray?) {
        if (charArray != null) {
            Arrays.fill(charArray, '\u0000')
        }
    }
}
