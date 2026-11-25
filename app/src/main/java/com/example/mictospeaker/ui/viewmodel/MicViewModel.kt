package com.example.mictospeaker.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mictospeaker.audio.AudioConfig
import com.example.mictospeaker.audio.AudioPassthroughService
import com.example.mictospeaker.audio.LatencyMode
import com.example.mictospeaker.audio.OutputRoute
import com.example.mictospeaker.audio.SoundPreset
import com.example.mictospeaker.data.AudioDeviceRepository
import com.example.mictospeaker.data.OutputDeviceItem
import com.example.mictospeaker.data.PersistedSettings
import com.example.mictospeaker.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MicUiState(
    val hasPermissions: Boolean = false,
    val isStreaming: Boolean = false,
    val isRecordingWav: Boolean = false,
    val isPttMode: Boolean = false,
    val isPttPressed: Boolean = false,
    val showWiredAndUsb: Boolean = true,
    val rmsLevel: Float = 0f,
    val config: AudioConfig = AudioConfig(),
    val devices: List<OutputDeviceItem> = emptyList(),
    val selectedDevice: OutputDeviceItem? = null,
    val statusMessage: String? = null
)

class MicViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceRepo = AudioDeviceRepository(application)
    private val settingsRepo = SettingsRepository(application)

    private var service: AudioPassthroughService? = null
    private var isBound = false
    private var pendingStart = false

    private val _uiState = MutableStateFlow(MicUiState())
    val uiState: StateFlow<MicUiState> = _uiState.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as AudioPassthroughService.LocalBinder).getService()
            service = svc
            isBound = true

            viewModelScope.launch {
                svc.isStreaming.collect { active ->
                    _uiState.update { it.copy(isStreaming = active) }
                }
            }
            viewModelScope.launch {
                svc.rmsLevel.collect { rms ->
                    _uiState.update { it.copy(rmsLevel = rms) }
                }
            }
            viewModelScope.launch {
                svc.isRecordingWav.collect { rec ->
                    _uiState.update { it.copy(isRecordingWav = rec) }
                }
            }

            if (pendingStart) {
                pendingStart = false
                startStreamingWithCurrentConfig()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }

    init {
        deviceRepo.startObserving()
        bindAudioService()

        // Muat preferensi permanen dari DataStore
        viewModelScope.launch {
            val saved = settingsRepo.settingsFlow.first()
            deviceRepo.setIncludeWiredAndUsb(saved.showWiredAndUsb)
            _uiState.update { state ->
                state.copy(
                    isPttMode = saved.isPttMode,
                    showWiredAndUsb = saved.showWiredAndUsb,
                    config = state.config.copy(
                        gain = saved.gain,
                        noiseGateThreshold = saved.noiseGateThreshold,
                        preset = saved.preset,
                        latencyMode = saved.latencyMode,
                        outputRoute = saved.outputRoute,
                        usePhoneMicInSco = saved.usePhoneMicInSco,
                        echoNoiseSuppression = saved.echoNoiseSuppression,
                        isMuted = saved.isPttMode
                    )
                )
            }
        }

        // Pantau perangkat audio secara real-time
        viewModelScope.launch {
            deviceRepo.devices.collect { list ->
                _uiState.update { state ->
                    val stillValid = list.find { it.id == state.selectedDevice?.id && it.isConnected }
                    val autoSelected = stillValid ?: list.firstOrNull { it.isConnected }
                    state.copy(
                        devices = list,
                        selectedDevice = autoSelected,
                        config = state.config.copy(preferredOutputDevice = autoSelected?.audioDeviceInfo)
                    )
                }
            }
        }
    }

    private fun bindAudioService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AudioPassthroughService::class.java)
        ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun setPermissionsGranted(granted: Boolean) {
        _uiState.update { it.copy(hasPermissions = granted) }
        if (granted) deviceRepo.refreshDevices()
    }

    fun refreshDevices() = deviceRepo.refreshDevices()

    fun toggleStreaming() {
        val state = _uiState.value
        if (state.isStreaming) {
            viewModelScope.launch {
                val savedWav = withContext(Dispatchers.IO) { service?.stopPassthrough() }
                if (savedWav != null) {
                    _uiState.update { it.copy(statusMessage = "Rekaman tersimpan: $savedWav") }
                }
            }
        } else {
            startStreamingWithCurrentConfig()
        }
    }

    private fun startStreamingWithCurrentConfig() {
        val svc = service
        if (svc == null) {
            pendingStart = true
            bindAudioService()
            return
        }

        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AudioPassthroughService::class.java)
        ContextCompat.startForegroundService(ctx, intent)

        val current = _uiState.value
        val effectiveConfig = current.config.copy(
            isMuted = if (current.isPttMode) !current.isPttPressed else current.config.isMuted
        )
        val ok = svc.startPassthrough(effectiveConfig)
        if (!ok) {
            _uiState.update { it.copy(statusMessage = "Gagal memulai audio engine. Coba refresh device atau ganti rute.") }
        }
    }

    fun setPttPressed(pressed: Boolean) {
        val state = _uiState.value
        if (!state.isPttMode) return
        _uiState.update { it.copy(isPttPressed = pressed) }

        if (pressed && !state.isStreaming) {
            startStreamingWithCurrentConfig()
        } else if (state.isStreaming) {
            service?.updateLiveConfig(state.config.copy(isMuted = !pressed))
        }
    }

    fun togglePttMode() {
        _uiState.update { state ->
            val nextPtt = !state.isPttMode
            val nextMuted = nextPtt
            val nextConfig = state.config.copy(isMuted = nextMuted)
            service?.updateLiveConfig(nextConfig)
            state.copy(isPttMode = nextPtt, isPttPressed = false, config = nextConfig)
        }
        persistCurrentSettings()
    }

    fun toggleMute() {
        updateLiveConfig(persist = false) { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleSaveWav() {
        val state = _uiState.value
        if (!state.isStreaming) {
            _uiState.update { it.copy(statusMessage = "Mulai mikrofon terlebih dahulu untuk merekam WAV.") }
            return
        }
        val svc = service ?: return

        if (state.isRecordingWav) {
            viewModelScope.launch {
                val savedName = withContext(Dispatchers.IO) { svc.stopAndSaveWavRecording() }
                _uiState.update {
                    it.copy(
                        statusMessage = if (savedName != null) "Tersimpan: $savedName"
                        else "Gagal menyimpan file WAV."
                    )
                }
            }
        } else {
            svc.startWavRecording()
            _uiState.update { it.copy(statusMessage = "Merekam audio WAV...") }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun setGain(gain: Float) = updateLiveConfig { it.copy(gain = gain.coerceIn(0f, 3f)) }

    fun setNoiseGate(threshold: Float) = updateLiveConfig { it.copy(noiseGateThreshold = threshold.coerceIn(0f, 0.25f)) }

    fun setSoundPreset(preset: SoundPreset) = updateLiveConfig { it.copy(preset = preset) }

    fun setLatencyMode(mode: LatencyMode) = updateConfigWithRestart { it.copy(latencyMode = mode) }

    fun setOutputRoute(route: OutputRoute) = updateConfigWithRestart { it.copy(outputRoute = route) }

    fun setUsePhoneMicInSco(enabled: Boolean) = updateConfigWithRestart { it.copy(usePhoneMicInSco = enabled) }

    fun setEchoSuppression(enabled: Boolean) = updateConfigWithRestart { it.copy(echoNoiseSuppression = enabled) }

    fun setShowWiredAndUsb(enabled: Boolean) {
        _uiState.update { it.copy(showWiredAndUsb = enabled) }
        deviceRepo.setIncludeWiredAndUsb(enabled)
        persistCurrentSettings()
    }

    fun selectDevice(device: OutputDeviceItem) {
        _uiState.update { it.copy(selectedDevice = device) }
        updateConfigWithRestart(persist = false) { it.copy(preferredOutputDevice = device.audioDeviceInfo) }
    }

    private fun updateLiveConfig(
        persist: Boolean = true,
        transform: (AudioConfig) -> AudioConfig
    ) {
        _uiState.update { state ->
            val updated = transform(state.config)
            service?.updateLiveConfig(updated)
            state.copy(config = updated)
        }
        if (persist) persistCurrentSettings()
    }

    private fun updateConfigWithRestart(
        persist: Boolean = true,
        transform: (AudioConfig) -> AudioConfig
    ) {
        val wasStreaming = _uiState.value.isStreaming
        _uiState.update { state ->
            state.copy(config = transform(state.config))
        }
        if (persist) persistCurrentSettings()

        if (wasStreaming) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { service?.stopPassthrough() }
                startStreamingWithCurrentConfig()
            }
        }
    }

    private fun persistCurrentSettings() {
        val s = _uiState.value
        viewModelScope.launch {
            settingsRepo.save(
                PersistedSettings(
                    gain = s.config.gain,
                    noiseGateThreshold = s.config.noiseGateThreshold,
                    preset = s.config.preset,
                    latencyMode = s.config.latencyMode,
                    outputRoute = s.config.outputRoute,
                    usePhoneMicInSco = s.config.usePhoneMicInSco,
                    echoNoiseSuppression = s.config.echoNoiseSuppression,
                    isPttMode = s.isPttMode,
                    showWiredAndUsb = s.showWiredAndUsb
                )
            )
        }
    }

    override fun onCleared() {
        deviceRepo.stopObserving()
        if (isBound) {
            getApplication<Application>().unbindService(connection)
            isBound = false
        }
        super.onCleared()
    }
}
