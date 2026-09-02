package com.kadhafi.aetherhop.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File
import java.io.FileInputStream

class AudioRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0

    @Suppress("DEPRECATION")
    fun startRecording(): Boolean {
        return try {
            outputFile = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(32000)
                setAudioSamplingRate(22050)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }
            startTimeMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            android.util.Log.e("AudioRecorderManager", "Failed to start recording", e)
            stopRecording()
            false
        }
    }

    fun stopRecording(): RecordResult? {
        val durationMs = System.currentTimeMillis() - startTimeMs
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null

            val file = outputFile
            if (file != null && file.exists() && durationMs > 500) {
                val bytes = FileInputStream(file).use { it.readBytes() }
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                file.delete()
                RecordResult(
                    audioBase64 = base64,
                    durationMs = durationMs
                )
            } else {
                file?.delete()
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioRecorderManager", "Failed to stop recording", e)
            recorder?.release()
            recorder = null
            outputFile?.delete()
            null
        }
    }

    data class RecordResult(
        val audioBase64: String,
        val durationMs: Long
    )
}
