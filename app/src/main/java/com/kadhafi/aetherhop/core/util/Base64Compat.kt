package com.kadhafi.aetherhop.core.util

/**
 * Multiplatform-safe Base64 encoder/decoder.
 * Uses android.util.Base64 at runtime on Android (compatible down to API 24).
 * Falls back to java.util.Base64 when executed in standard JVM unit test environments.
 */
object Base64Compat {

    private val isAndroidRuntime: Boolean by lazy {
        try {
            Class.forName("android.util.Base64")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    fun encodeToString(bytes: ByteArray): String {
        return try {
            if (isAndroidRuntime) {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } else {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        } catch (_: UnsatisfiedLinkError) {
            // Unmocked Android framework in JVM local test runner
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Exception) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    fun decode(base64Str: String): ByteArray {
        return try {
            if (isAndroidRuntime) {
                android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP)
            } else {
                java.util.Base64.getDecoder().decode(base64Str)
            }
        } catch (_: UnsatisfiedLinkError) {
            // Unmocked Android framework in JVM local test runner
            java.util.Base64.getDecoder().decode(base64Str)
        } catch (_: Exception) {
            java.util.Base64.getDecoder().decode(base64Str)
        }
    }
}
