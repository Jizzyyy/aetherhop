package com.kadhafi.aetherhop.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playingVoiceId = MutableStateFlow<String?>(null)
    val playingVoiceId: StateFlow<String?> = _playingVoiceId.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    fun playVoiceNote(voiceId: String, audioBase64: String) {
        stopPlayback()
        try {
            val audioBytes = Base64.decode(audioBase64, Base64.NO_WRAP)
            val tempFile = File(context.cacheDir, "play_voice_$voiceId.m4a")
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                    tempFile.delete()
                }
                start()
            }
            _playingVoiceId.value = voiceId
            _isPlaying.value = true
            _playbackProgress.value = 0f

            progressJob?.cancel()
            progressJob = scope.launch {
                while (isActive && mediaPlayer?.isPlaying == true) {
                    val current = mediaPlayer?.currentPosition ?: 0
                    val duration = mediaPlayer?.duration ?: 1
                    if (duration > 0) {
                        _playbackProgress.value = (current.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    }
                    delay(50)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerManager", "Failed to play voice note", e)
            stopPlayback()
        }
    }

    fun seekToFraction(fraction: Float) {
        mediaPlayer?.let { player ->
            try {
                val targetMs = (player.duration * fraction.coerceIn(0f, 1f)).toInt()
                player.seekTo(targetMs)
                _playbackProgress.value = fraction.coerceIn(0f, 1f)
            } catch (_: Exception) {}
        }
    }

    fun stopPlayback() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _playingVoiceId.value = null
        _playbackProgress.value = 0f
    }
}
