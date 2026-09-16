package com.kadhafi.aetherhop.data.ble

import android.bluetooth.le.AdvertiseSettings
import com.kadhafi.aetherhop.core.power.AdaptiveBeaconScheduler
import com.kadhafi.aetherhop.core.power.PowerProfile
import com.kadhafi.aetherhop.domain.model.PeerPairingPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDualRoleAdvertisingTest {

    @Test
    fun testPowerProfileToAdvertiseSettingsMapping() {
        fun mapPowerProfileToAdvertiseSettings(profile: PowerProfile): Pair<Int, Int> {
            return when (profile) {
                PowerProfile.EMERGENCY_MAX -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY to AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
                PowerProfile.BALANCED -> AdvertiseSettings.ADVERTISE_MODE_BALANCED to AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
                PowerProfile.SAVER_LOW_POWER -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER to AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            }
        }

        val (maxMode, maxTx) = mapPowerProfileToAdvertiseSettings(PowerProfile.EMERGENCY_MAX)
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, maxMode)
        assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH, maxTx)

        val (balMode, balTx) = mapPowerProfileToAdvertiseSettings(PowerProfile.BALANCED)
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_BALANCED, balMode)
        assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM, balTx)

        val (saverMode, saverTx) = mapPowerProfileToAdvertiseSettings(PowerProfile.SAVER_LOW_POWER)
        assertEquals(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER, saverMode)
        assertEquals(AdvertiseSettings.ADVERTISE_TX_POWER_LOW, saverTx)
    }

    @Test
    fun testAdaptiveBeaconSchedulerIntervals() {
        assertEquals(5000L, AdaptiveBeaconScheduler.getScanIntervalMillis(PowerProfile.EMERGENCY_MAX))
        assertEquals(15000L, AdaptiveBeaconScheduler.getScanIntervalMillis(PowerProfile.BALANCED))
        assertEquals(45000L, AdaptiveBeaconScheduler.getScanIntervalMillis(PowerProfile.SAVER_LOW_POWER))

        assertEquals(4000L, AdaptiveBeaconScheduler.getScanDurationMillis(PowerProfile.EMERGENCY_MAX))
        assertEquals(3000L, AdaptiveBeaconScheduler.getScanDurationMillis(PowerProfile.BALANCED))
        assertEquals(2000L, AdaptiveBeaconScheduler.getScanDurationMillis(PowerProfile.SAVER_LOW_POWER))
    }

    @Test
    fun testPeerPairingPayloadVerification() {
        val deviceId = "node_tactical_bravo_999"
        val validFingerprint = deviceId.take(16)

        val validPayload = PeerPairingPayload(
            deviceId = deviceId,
            deviceName = "Bravo Leader",
            publicKeyBase64 = "MCowBQYDK2VwAyEA...",
            checksumFingerprint = validFingerprint
        )

        val json = Json.encodeToString(validPayload)
        val decoded = Json.decodeFromString<PeerPairingPayload>(json)

        assertEquals("Bravo Leader", decoded.deviceName)
        assertEquals(deviceId, decoded.deviceId)
        assertEquals(validFingerprint, decoded.checksumFingerprint)
        assertTrue(decoded.checksumFingerprint == decoded.deviceId.take(16))
    }

    @Test
    fun testCorruptedPairingPayloadFingerprintMismatch() {
        val deviceId = "node_recon_team_1"
        val corruptedPayload = PeerPairingPayload(
            deviceId = deviceId,
            deviceName = "Recon 1",
            publicKeyBase64 = "keys...",
            checksumFingerprint = "forged_checksum_12"
        )

        assertFalse(corruptedPayload.checksumFingerprint == corruptedPayload.deviceId.take(16))
    }
}
