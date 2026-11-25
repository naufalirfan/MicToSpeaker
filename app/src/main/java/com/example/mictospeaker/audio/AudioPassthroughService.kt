package com.example.mictospeaker.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mictospeaker.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPassthroughService : Service() {

    private val binder = LocalBinder()
    private lateinit var engine: AudioEngine
    private lateinit var wavRecorder: WavRecorder

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel = _rmsLevel.asStateFlow()

    private val _isRecordingWav = MutableStateFlow(false)
    val isRecordingWav = _isRecordingWav.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): AudioPassthroughService = this@AudioPassthroughService
    }

    override fun onCreate() {
        super.onCreate()
        engine = AudioEngine(this)
        wavRecorder = WavRecorder(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun startPassthrough(config: AudioConfig): Boolean {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val started = engine.start(
            initialConfig = config,
            onRmsUpdate = { level -> _rmsLevel.value = level },
            onPcmChunk = { pcm, count -> wavRecorder.writePcm(pcm, count) }
        )

        if (!started) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            _isStreaming.value = false
            return false
        }

        _isStreaming.value = true
        return true
    }

    fun updateLiveConfig(config: AudioConfig) {
        engine.updateConfig(config)
    }

    fun startWavRecording() {
        if (wavRecorder.isRecording) return
        val sampleRate = engine.getActiveSampleRate()
        wavRecorder.start(sampleRate)
        engine.setRecordingEnabled(true)
        _isRecordingWav.value = true
    }

    fun stopAndSaveWavRecording(): String? {
        if (!wavRecorder.isRecording) return null
        engine.setRecordingEnabled(false)
        _isRecordingWav.value = false
        return wavRecorder.stopAndSave()
    }

    fun stopPassthrough(): String? {
        val savedWav = stopAndSaveWavRecording()
        engine.stop()
        _isStreaming.value = false
        _rmsLevel.value = 0f
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return savedWav
    }

    override fun onDestroy() {
        stopAndSaveWavRecording()
        engine.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mic to Speaker Active",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mic to Speaker aktif")
            .setContentText("Meneruskan suara mikrofon ke output audio")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "mic_passthrough_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
