package com.kadhafi.aetherhop.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class SymmetricKeyRatchetTest {

    @Test
    fun testStepProgressionDerivesUniqueKeys() {
        val rootKey = ByteArray(32) { it.toByte() }
        val ratchet = SymmetricKeyRatchet(rootKey)

        val (step0, key0) = ratchet.stepForward()
        val (step1, key1) = ratchet.stepForward()
        val (step2, key2) = ratchet.stepForward()

        assertEquals(0L, step0)
        assertEquals(1L, step1)
        assertEquals(2L, step2)

        assertNotEquals(key0.encoded.toList(), key1.encoded.toList())
        assertNotEquals(key1.encoded.toList(), key2.encoded.toList())
        assertNotEquals(key0.encoded.toList(), key2.encoded.toList())
        assertEquals(3L, ratchet.getCurrentStep())
    }

    @Test
    fun testSynchronizedSenderAndReceiverRatchet() {
        val sharedSecret = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val senderRatchet = SymmetricKeyRatchet(sharedSecret.copyOf())
        val receiverRatchet = SymmetricKeyRatchet(sharedSecret.copyOf())

        // Message 1
        val message1 = "Tactical message 1 - Status Green"
        val envelope1 = CryptoManager.encryptWithRatchet(message1, senderRatchet)
        assertEquals(0L, envelope1.ratchetStep)

        val decrypted1 = CryptoManager.decryptWithRatchet(envelope1, receiverRatchet)
        assertEquals(message1, decrypted1)

        // Message 2
        val message2 = "Tactical message 2 - Move to extraction"
        val envelope2 = CryptoManager.encryptWithRatchet(message2, senderRatchet)
        assertEquals(1L, envelope2.ratchetStep)

        val decrypted2 = CryptoManager.decryptWithRatchet(envelope2, receiverRatchet)
        assertEquals(message2, decrypted2)
    }

    @Test
    fun testOutOfOrderMessageArrivalWithSkippedKeysBuffer() {
        val sharedSecret = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val senderRatchet = SymmetricKeyRatchet(sharedSecret.copyOf())
        val receiverRatchet = SymmetricKeyRatchet(sharedSecret.copyOf())

        val msg0 = "Message 0"
        val msg1 = "Message 1 (Delayed over mesh)"
        val msg2 = "Message 2 (Arrives early)"

        val env0 = CryptoManager.encryptWithRatchet(msg0, senderRatchet)
        val env1 = CryptoManager.encryptWithRatchet(msg1, senderRatchet)
        val env2 = CryptoManager.encryptWithRatchet(msg2, senderRatchet)

        // Receiver gets msg0
        val dec0 = CryptoManager.decryptWithRatchet(env0, receiverRatchet)
        assertEquals(msg0, dec0)

        // Receiver gets msg2 first (skipping msg1)
        val dec2 = CryptoManager.decryptWithRatchet(env2, receiverRatchet)
        assertEquals(msg2, dec2)
        assertTrue(receiverRatchet.hasSkippedKey(1L))
        assertEquals(1, receiverRatchet.getSkippedKeyCount())

        // Now delayed msg1 finally arrives and should successfully decrypt
        val dec1 = CryptoManager.decryptWithRatchet(env1, receiverRatchet)
        assertEquals(msg1, dec1)
        assertFalse(receiverRatchet.hasSkippedKey(1L))
        assertEquals(0, receiverRatchet.getSkippedKeyCount())
    }

    @Test
    fun testZeroizationWipesRatchetState() {
        val rootKey = ByteArray(32) { 0x5A.toByte() }
        val ratchet = SymmetricKeyRatchet(rootKey)

        ratchet.stepForward()
        ratchet.stepForward()
        assertTrue(ratchet.getCurrentStep() > 0)

        ratchet.zeroize()
        assertEquals(0L, ratchet.getCurrentStep())
        assertEquals(0, ratchet.getSkippedKeyCount())
    }
}
