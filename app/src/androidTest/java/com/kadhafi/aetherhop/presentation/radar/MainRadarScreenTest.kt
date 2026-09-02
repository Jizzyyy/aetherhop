package com.kadhafi.aetherhop.presentation.radar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kadhafi.aetherhop.domain.model.PeerNode
import org.junit.Rule
import org.junit.Test

class MainRadarScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRadarScreenTitleAndPeerCountRenders() {
        val testPeers = listOf(
            PeerNode(id = "p1", name = "Test Node Alpha", address = "192.168.49.2")
        )

        composeTestRule.setContent {
            MainRadarScreen(
                peers = testPeers,
                isScanning = true,
                isBluetoothEnabled = true
            )
        }

        composeTestRule.onNodeWithText("AetherHop").assertExists()
        composeTestRule.onNodeWithText("Test Node Alpha").assertExists()
    }
}
