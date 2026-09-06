package com.kadhafi.aetherhop.core.audio

import android.media.AudioManager
import android.media.ToneGenerator

object TacticalSoundManager {
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
        } catch (_: Exception) {}
    }

    fun playTransmitBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
        } catch (_: Exception) {}
    }

    fun playAckChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 90)
        } catch (_: Exception) {}
    }
}
