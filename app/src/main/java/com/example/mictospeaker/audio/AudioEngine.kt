package com.example.mictospeaker.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class AudioEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bridge = OboeNativeBridge()
    private val isRunning = AtomicBoolean(false)
    private var pollingThread: Thread? = null
    private var focusRequest: AudioFocusRequest? = null

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

        // 1. Minta Audio Focus agar sistem Android tidak me-mute atau men-duck audio passthrough
        requestAudioFocus(initialConfig.outputRoute)

        val resolvedScoDevice = configureRouting(initialConfig)
        updateConfig(initialConfig)

        val preferred = initialConfig.preferredOutputDevice
        val isBluetooth = when {
            preferred != null -> isBluetoothDeviceType(preferred.type)
            else -> audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isBluetoothDeviceType(it.type) }
        }

        val isSco = initialConfig.outputRoute == OutputRoute.VOICE_SCO
        val outputDeviceId = when {
            isSco && resolvedScoDevice != null -> resolvedScoDevice.id
            !isSco && !isBluetooth && initialConfig.preferredOutputDevice != null -> initialConfig.preferredOutputDevice.id
            else -> 0
        }

        // Cari mikrofon bawaan ponsel (Internal Phone Mic)
        val builtinMic = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?: audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
                it.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
                it.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                it.type != AudioDeviceInfo.TYPE_BLE_HEADSET
            }

        // PENTING: Pada mode Musik/Speaker (A2DP / TWS / Aux / USB), profil A2DP adalah output-only (tidak ada mic uplink).
        // Jika inputDeviceId disetel ke 0, sistem Android default-nya mencoba merekam dari TWS Bluetooth yang menghasilkan SILENCE (Gada Suara).
        // Karena itu, input HARUS selalu diarahkan ke mikrofon bawaan ponsel (TYPE_BUILTIN_MIC), kecuali user sengaja pakai mode SCO dengan mic TWS.
        val inputDeviceId = when {
            isSco && !initialConfig.usePhoneMicInSco -> 0
            else -> builtinMic?.id ?: 0
        }

        Log.i("AudioEngine", "Starting Oboe: OutDev=$outputDeviceId, InDev=$inputDeviceId, isSco=$isSco, isBt=$isBluetooth, LatencyMult=${initialConfig.latencyMode.bufferMultiplier}")

        val started = bridge.start(
            outputDeviceId = outputDeviceId,
            inputDeviceId = inputDeviceId,
            latencyMultiplier = initialConfig.latencyMode.bufferMultiplier,
            isScoRoute = isSco,
            isBluetooth = isBluetooth,
            echoSuppression = initialConfig.echoNoiseSuppression
        )

        if (!started) {
            isRunning.set(false)
            releaseRouting()
            abandonAudioFocus()
            return false
        }

        // Thread polling (~30fps) untuk visualizer & drain ring buffer WAV
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
        abandonAudioFocus()
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
                val scoDevice = if (preferred != null && isBluetoothCommunicationType(preferred.type)) {
                    preferred
                } else {
                    commDevices.firstOrNull { isBluetoothCommunicationType(it.type) }
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

    private fun isBluetoothCommunicationType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
               type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
               (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
    }

    private fun isBluetoothDeviceType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
               type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
               type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
               type == AudioDeviceInfo.TYPE_HEARING_AID ||
               (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
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

    private fun requestAudioFocus(route: OutputRoute) {
        val usage = if (route == OutputRoute.VOICE_SCO) {
            AudioAttributes.USAGE_VOICE_COMMUNICATION
        } else {
            AudioAttributes.USAGE_MEDIA
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val contentType = if (route == OutputRoute.VOICE_SCO) {
                AudioAttributes.CONTENT_TYPE_SPEECH
            } else {
                AudioAttributes.CONTENT_TYPE_MUSIC
            }

            val attr = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()

            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { /* maintain continuous audio output */ }
                .build()

            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                if (route == OutputRoute.VOICE_SCO) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
