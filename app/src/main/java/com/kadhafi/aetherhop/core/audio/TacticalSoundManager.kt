package com.kadhafi.aetherhop.core.audio

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.*

object TacticalSoundManager {
    private var toneGenerator: ToneGenerator? = null
    private var morseSosJob: Job? = null

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

    fun playPerimeterBreachAlarm() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 300)
        } catch (_: Exception) {}
    }

    fun playProximityAlert() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
        } catch (_: Exception) {}
    }

    fun playMorseSos(scope: CoroutineScope) {
        stopMorseSos()
        morseSosJob = scope.launch(Dispatchers.IO) {
            val shortBeep = 120
            val longBeep = 350
            val elementGap = 100L
            val letterGap = 300L

            while (isActive) {
                // S: ...
                repeat(3) {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, shortBeep)
                    delay(shortBeep + elementGap)
                }
                delay(letterGap)

                // O: ---
                repeat(3) {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, longBeep)
                    delay(longBeep + elementGap)
                }
                delay(letterGap)

                // S: ...
                repeat(3) {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, shortBeep)
                    delay(shortBeep + elementGap)
                }
                delay(1200L) // Gap between SOS words
            }
        }
    }

    fun stopMorseSos() {
        morseSosJob?.cancel()
        morseSosJob = null
    }
}
