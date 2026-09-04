package com.kadhafi.aetherhop.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class PttStreamManager(context: Context) {
    private val appContext = context.applicationContext
    private var audioTrack: AudioTrack? = null

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
                        val adpcmBytes = AdpcmCodec.encodePcmToAdpcm(frameBytes)
                        val base64 = Base64.encodeToString(adpcmBytes, Base64.NO_WRAP)
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

    fun playPttFrame(frameBase64: String) {
        try {
            val adpcmBytes = Base64.decode(frameBase64, Base64.NO_WRAP)
            val pcmBytes = AdpcmCodec.decodeAdpcmToPcm(adpcmBytes)
            if (audioTrack == null) {
                val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT)
                val trackBuffer = minBufferSize.coerceAtLeast(2048)
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(trackBuffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack?.play()
            }
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            android.util.Log.e("PttStreamManager", "Error playing PTT frame", e)
        }
    }

    fun stopPttPlayer() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
