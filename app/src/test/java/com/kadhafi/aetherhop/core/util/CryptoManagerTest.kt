package com.kadhafi.aetherhop.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class CryptoManagerTest {

    @Test
    fun testGenerateSecretKeyReturns32BytesKey() {
        val seed = "test_seed_bytes_1234567890_32bytes_long".toByteArray()
        val secretKey = CryptoManager.generateSecretKey(seed)

        assertNotNull(secretKey)
        assertEquals("AES", secretKey.algorithm)
        assertEquals(32, secretKey.encoded.size)
    }

    @Test
    fun testEcdhKeyExchangeRoundtrip() {
        val aliceKeyPair = KeyExchangeManager.generateKeyPair()
        val bobKeyPair = KeyExchangeManager.generateKeyPair()

        val alicePubBase64 = KeyExchangeManager.publicKeyToBase64(aliceKeyPair.public)
        val bobPubBase64 = KeyExchangeManager.publicKeyToBase64(bobKeyPair.public)

        val aliceDecodedBobPub = KeyExchangeManager.base64ToPublicKey(bobPubBase64)
        val bobDecodedAlicePub = KeyExchangeManager.base64ToPublicKey(alicePubBase64)

        val aliceSharedSecret = KeyExchangeManager.generateSharedSecret(aliceKeyPair, aliceDecodedBobPub)
        val bobSharedSecret = KeyExchangeManager.generateSharedSecret(bobKeyPair, bobDecodedAlicePub)

        assertEquals(aliceSharedSecret.encoded.contentToString(), bobSharedSecret.encoded.contentToString())
    }
}
