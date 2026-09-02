package com.kadhafi.aetherhop.core.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playingVoiceId = MutableStateFlow<String?>(null)
    val playingVoiceId: StateFlow<String?> = _playingVoiceId.asStateFlow()

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
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerManager", "Failed to play voice note", e)
            stopPlayback()
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _playingVoiceId.value = null
    }
}
