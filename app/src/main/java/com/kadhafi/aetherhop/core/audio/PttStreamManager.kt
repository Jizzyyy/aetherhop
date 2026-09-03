package com.kadhafi.aetherhop.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class PttStreamManager(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    fun startPttStream(): Flow<String> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize.coerceAtLeast(1280)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        val buffer = ByteArray(640) // 20ms audio frame chunks at 16kHz 16-bit
        audioRecord.startRecording()

        val recordingJob = launch(Dispatchers.IO) {
            try {
                while (!isClosedForSend) {
                    val readBytes = audioRecord.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        val frameBytes = if (readBytes == buffer.size) buffer else buffer.copyOf(readBytes)
                        val base64 = Base64.encodeToString(frameBytes, Base64.NO_WRAP)
                        trySend(base64)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PttStreamManager", "Error reading PTT audio frame", e)
            } finally {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (_: Exception) {}
            }
        }

        awaitClose {
            recordingJob.cancel()
            try {
                audioRecord.stop()
                audioRecord.release()
            } catch (_: Exception) {}
        }
    }
}
