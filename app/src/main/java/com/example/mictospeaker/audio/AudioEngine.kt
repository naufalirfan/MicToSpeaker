package com.example.mictospeaker.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

class AudioEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bridge = OboeNativeBridge()
    private val isRunning = AtomicBoolean(false)
    private var pollingThread: Thread? = null

    fun getActiveSampleRate(): Int = bridge.getSampleRate()

    fun setRecordingEnabled(enabled: Boolean) {
        bridge.setRecording(enabled)
    }

    fun updateConfig(config: AudioConfig) {
        bridge.updateParams(
            gain = config.gain,
            noiseGate = config.noiseGateThreshold,
            presetIndex = config.preset.ordinal,
            isMuted = config.isMuted
        )
    }

    fun start(
        initialConfig: AudioConfig,
        onRmsUpdate: (Float) -> Unit,
        onPcmChunk: ((ShortArray, Int) -> Unit)? = null
    ): Boolean {
        if (!isRunning.compareAndSet(false, true)) return true

        val resolvedScoDevice = configureRouting(initialConfig)
        updateConfig(initialConfig)

        val isSco = initialConfig.outputRoute == OutputRoute.VOICE_SCO
        val outputDeviceId = when {
            isSco && resolvedScoDevice != null -> resolvedScoDevice.id
            !isSco && initialConfig.preferredOutputDevice != null -> initialConfig.preferredOutputDevice.id
            else -> 0
        }

        val inputDeviceId = if (isSco && initialConfig.usePhoneMicInSco) {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                ?.id ?: 0
        } else {
            0
        }

        val started = bridge.start(
            outputDeviceId = outputDeviceId,
            inputDeviceId = inputDeviceId,
            latencyMultiplier = initialConfig.latencyMode.bufferMultiplier,
            isScoRoute = isSco,
            echoSuppression = initialConfig.echoNoiseSuppression
        )

        if (!started) {
            isRunning.set(false)
            releaseRouting()
            return false
        }

        // Thread polling ringan (~30fps) untuk update visualizer & drain ring buffer WAV
        pollingThread = Thread({
            val pcmScratch = ShortArray(4096)
            while (isRunning.get()) {
                onRmsUpdate(bridge.getRms())

                if (onPcmChunk != null) {
                    var drained = bridge.drainPcm(pcmScratch)
                    while (drained > 0) {
                        onPcmChunk(pcmScratch, drained)
                        drained = bridge.drainPcm(pcmScratch)
                    }
                }

                try {
                    Thread.sleep(30L)
                } catch (_: InterruptedException) {
                    break
                }
            }
            onRmsUpdate(0f)
        }, "Oboe-UiWavPoller").apply { start() }

        return true
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        pollingThread?.interrupt()
        pollingThread?.join(300)
        pollingThread = null
        bridge.stop()
        releaseRouting()
    }

    fun release() {
        stop()
        bridge.release()
    }

    private fun configureRouting(config: AudioConfig): AudioDeviceInfo? {
        if (config.outputRoute == OutputRoute.VOICE_SCO) {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val commDevices = audioManager.availableCommunicationDevices
                val preferred = config.preferredOutputDevice
                val scoDevice = if (preferred != null && preferred.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    preferred
                } else {
                    commDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                }
                scoDevice?.let { audioManager.setCommunicationDevice(it) }
                return scoDevice
            } else {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            }
        } else {
            releaseRouting()
            return null
        }
    }

    private fun releaseRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
