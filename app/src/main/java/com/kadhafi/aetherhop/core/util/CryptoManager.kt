package com.kadhafi.aetherhop.core.util

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class EncryptedEnvelope(
    val ivBase64: String,
    val ciphertextBase64: String,
    val ratchetStep: Long = 0L
)

object CryptoManager {
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH_BYTES = 12
    private const val ECDSA_ALGORITHM = "SHA256withECDSA"

    fun generateSecretKey(seedBytes: ByteArray): SecretKey {
        val keyBytes = seedBytes.copyOf(32) // AES-256 requires 32 bytes
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String, secretKey: SecretKey): EncryptedEnvelope {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedEnvelope(
            ivBase64 = Base64Compat.encodeToString(iv),
            ciphertextBase64 = Base64Compat.encodeToString(cipherBytes),
            ratchetStep = 0L
        )
    }

    fun encryptWithRatchet(plainText: String, ratchet: SymmetricKeyRatchet): EncryptedEnvelope {
        val (step, messageKey) = ratchet.stepForward()
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, messageKey, spec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedEnvelope(
            ivBase64 = Base64Compat.encodeToString(iv),
            ciphertextBase64 = Base64Compat.encodeToString(cipherBytes),
            ratchetStep = step
        )
    }

    fun decrypt(envelope: EncryptedEnvelope, secretKey: SecretKey): String {
        val iv = Base64Compat.decode(envelope.ivBase64)
        val cipherBytes = Base64Compat.decode(envelope.ciphertextBase64)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun decryptWithRatchet(envelope: EncryptedEnvelope, ratchet: SymmetricKeyRatchet): String {
        val messageKey = ratchet.getKeyForStep(envelope.ratchetStep)
            ?: throw IllegalStateException("Key for ratchet step ${envelope.ratchetStep} could not be derived or was expired")
        return decrypt(envelope, messageKey)
    }

    fun signData(dataBytes: ByteArray, privateKey: java.security.PrivateKey): String {
        val signer = java.security.Signature.getInstance(ECDSA_ALGORITHM)
        signer.initSign(privateKey)
        signer.update(dataBytes)
        val signature = signer.sign()
        return Base64Compat.encodeToString(signature)
    }

    fun verifySignature(dataBytes: ByteArray, signatureBase64: String, publicKey: java.security.PublicKey): Boolean {
        if (signatureBase64.isBlank()) return false
        return try {
            val signature = Base64Compat.decode(signatureBase64)
            val verifier = java.security.Signature.getInstance(ECDSA_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(dataBytes)
            verifier.verify(signature)
        } catch (_: Exception) {
            false
        }
    }
}
