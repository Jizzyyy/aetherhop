package com.kadhafi.aetherhop.core.util

import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class SymmetricKeyRatchet(initialKeyBytes: ByteArray) {

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val MESSAGE_KEY_CONSTANT = "AetherHop-MessageKey-v1".toByteArray(Charsets.UTF_8)
        private val CHAIN_KEY_CONSTANT = "AetherHop-ChainKeyAdvance-v1".toByteArray(Charsets.UTF_8)
        private const val MAX_SKIPPED_KEYS = 50

        fun deriveHmacSha256(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
            return mac.doFinal(data)
        }
    }

    private val lock = Any()
    @Volatile
    private var currentChainKey: ByteArray = initialKeyBytes.copyOf(32)
    @Volatile
    private var currentStep: Long = 0L

    private val skippedMessageKeys = ConcurrentHashMap<Long, ByteArray>()

    fun getCurrentStep(): Long = currentStep

    fun stepForward(): Pair<Long, SecretKey> = synchronized(lock) {
        val thisStep = currentStep
        val mkBytes = deriveHmacSha256(currentChainKey, MESSAGE_KEY_CONSTANT)
        val nextCkBytes = deriveHmacSha256(currentChainKey, CHAIN_KEY_CONSTANT)

        SecureMemoryZeroizer.zeroize(currentChainKey)
        currentChainKey = nextCkBytes
        currentStep = thisStep + 1

        val secretKey = SecretKeySpec(mkBytes, "AES")
        Pair(thisStep, secretKey)
    }

    fun getKeyForStep(targetStep: Long): SecretKey? = synchronized(lock) {
        val skipped = skippedMessageKeys.remove(targetStep)
        if (skipped != null) {
            val key = SecretKeySpec(skipped, "AES")
            SecureMemoryZeroizer.zeroize(skipped)
            return key
        }

        if (targetStep < currentStep) {
            // Already stepped past and skipped key no longer exists
            return null
        }

        // Catch up to targetStep, caching intermediate skipped message keys
        while (currentStep < targetStep) {
            val stepIdx = currentStep
            val mkBytes = deriveHmacSha256(currentChainKey, MESSAGE_KEY_CONSTANT)
            val nextCkBytes = deriveHmacSha256(currentChainKey, CHAIN_KEY_CONSTANT)

            SecureMemoryZeroizer.zeroize(currentChainKey)
            currentChainKey = nextCkBytes
            currentStep = stepIdx + 1

            if (skippedMessageKeys.size < MAX_SKIPPED_KEYS) {
                skippedMessageKeys[stepIdx] = mkBytes
            } else {
                SecureMemoryZeroizer.zeroize(mkBytes)
            }
        }

        // Now currentStep == targetStep
        val thisStep = currentStep
        val mkBytes = deriveHmacSha256(currentChainKey, MESSAGE_KEY_CONSTANT)
        val nextCkBytes = deriveHmacSha256(currentChainKey, CHAIN_KEY_CONSTANT)

        SecureMemoryZeroizer.zeroize(currentChainKey)
        currentChainKey = nextCkBytes
        currentStep = thisStep + 1

        SecretKeySpec(mkBytes, "AES")
    }

    fun zeroize() = synchronized(lock) {
        SecureMemoryZeroizer.zeroize(currentChainKey)
        skippedMessageKeys.values.forEach { SecureMemoryZeroizer.zeroize(it) }
        skippedMessageKeys.clear()
        currentStep = 0L
    }
}
