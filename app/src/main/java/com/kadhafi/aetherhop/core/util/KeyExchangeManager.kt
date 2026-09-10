package com.kadhafi.aetherhop.core.util

import java.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey

object KeyExchangeManager {
    private const val EC_ALGORITHM = "EC"

    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM)
        keyPairGenerator.initialize(256)
        return keyPairGenerator.generateKeyPair()
    }

    fun publicKeyToBase64(publicKey: PublicKey): String {
        return Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    fun base64ToPublicKey(base64: String): PublicKey {
        val bytes = Base64.getDecoder().decode(base64)
        val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
        return keyFactory.generatePublic(X509EncodedKeySpec(bytes))
    }

    fun generateSharedSecret(ourKeyPair: KeyPair, peerPublicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ourKeyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecretBytes = keyAgreement.generateSecret()
        return CryptoManager.generateSecretKey(sharedSecretBytes)
    }
}
