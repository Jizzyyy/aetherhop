package com.kadhafi.aetherhop.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PerMessageRatchetIntegrityTest {

    @Test
    fun testLongMultiStepRatchetChainIntegrity() {
        val rootSeed = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val senderRatchet = SymmetricKeyRatchet(rootSeed.copyOf())
        val receiverRatchet = SymmetricKeyRatchet(rootSeed.copyOf())

        val totalSteps = 25
        val envelopes = mutableListOf<EncryptedEnvelope>()
        val originalMessages = mutableListOf<String>()

        // Generate 25 sequential messages
        for (i in 0 until totalSteps) {
            val text = "Tactical Mission Packet #$i payload verification"
            originalMessages.add(text)
            val env = CryptoManager.encryptWithRatchet(text, senderRatchet)
            assertEquals(i.toLong(), env.ratchetStep)
            envelopes.add(env)
        }

        // Receiver decrypts in perfect sequence
        for (i in 0 until totalSteps) {
            val decrypted = CryptoManager.decryptWithRatchet(envelopes[i], receiverRatchet)
            assertEquals(originalMessages[i], decrypted)
        }

        assertEquals(totalSteps.toLong(), senderRatchet.getCurrentStep())
        assertEquals(totalSteps.toLong(), receiverRatchet.getCurrentStep())
        assertEquals(0, receiverRatchet.getSkippedKeyCount())
    }

    @Test
    fun testInterleavedMultiStepOutboxDelivery() {
        val rootSeed = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val sender = SymmetricKeyRatchet(rootSeed.copyOf())
        val receiver = SymmetricKeyRatchet(rootSeed.copyOf())

        val envs = (0..9).map { idx ->
            CryptoManager.encryptWithRatchet("Step $idx", sender)
        }

        // Deliver even steps first: 0, 2, 4, 6, 8
        for (i in 0..8 step 2) {
            val dec = CryptoManager.decryptWithRatchet(envs[i], receiver)
            assertEquals("Step $i", dec)
        }

        // Then deliver odd steps: 1, 3, 5, 7, 9
        for (i in 1..9 step 2) {
            val dec = CryptoManager.decryptWithRatchet(envs[i], receiver)
            assertEquals("Step $i", dec)
        }

        // All skipped keys must now be consumed
        assertEquals(0, receiver.getSkippedKeyCount())
    }
}
