package com.example.mictospeaker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mictospeaker.audio.LatencyMode
import com.example.mictospeaker.audio.OutputRoute
import com.example.mictospeaker.audio.SoundPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "mic_to_speaker_settings")

data class PersistedSettings(
    val gain: Float = 1.0f,
    val noiseGateThreshold: Float = 0.0f,
    val preset: SoundPreset = SoundPreset.NATURAL,
    val latencyMode: LatencyMode = LatencyMode.BALANCED,
    val outputRoute: OutputRoute = OutputRoute.MUSIC_A2DP,
    val usePhoneMicInSco: Boolean = true,
    val echoNoiseSuppression: Boolean = true,
    val isPttMode: Boolean = false,
    val showWiredAndUsb: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val GAIN = floatPreferencesKey("gain")
        val NOISE_GATE = floatPreferencesKey("noise_gate")
        val PRESET = stringPreferencesKey("sound_preset")
        val LATENCY = stringPreferencesKey("latency_mode")
        val ROUTE = stringPreferencesKey("output_route")
        val USE_PHONE_MIC_SCO = booleanPreferencesKey("use_phone_mic_sco")
        val ECHO_SUPPRESSION = booleanPreferencesKey("echo_suppression")
        val PTT_MODE = booleanPreferencesKey("ptt_mode")
        val SHOW_WIRED_USB = booleanPreferencesKey("show_wired_usb")
    }

    val settingsFlow: Flow<PersistedSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            PersistedSettings(
                gain = prefs[Keys.GAIN] ?: 1.0f,
                noiseGateThreshold = prefs[Keys.NOISE_GATE] ?: 0.0f,
                preset = runCatching {
                    SoundPreset.valueOf(prefs[Keys.PRESET] ?: SoundPreset.NATURAL.name)
                }.getOrDefault(SoundPreset.NATURAL),
                latencyMode = runCatching {
                    LatencyMode.valueOf(prefs[Keys.LATENCY] ?: LatencyMode.BALANCED.name)
                }.getOrDefault(LatencyMode.BALANCED),
                outputRoute = runCatching {
                    OutputRoute.valueOf(prefs[Keys.ROUTE] ?: OutputRoute.MUSIC_A2DP.name)
                }.getOrDefault(OutputRoute.MUSIC_A2DP),
                usePhoneMicInSco = prefs[Keys.USE_PHONE_MIC_SCO] ?: true,
                echoNoiseSuppression = prefs[Keys.ECHO_SUPPRESSION] ?: true,
                isPttMode = prefs[Keys.PTT_MODE] ?: false,
                showWiredAndUsb = prefs[Keys.SHOW_WIRED_USB] ?: true
            )
        }

    suspend fun save(settings: PersistedSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GAIN] = settings.gain
            prefs[Keys.NOISE_GATE] = settings.noiseGateThreshold
            prefs[Keys.PRESET] = settings.preset.name
            prefs[Keys.LATENCY] = settings.latencyMode.name
            prefs[Keys.ROUTE] = settings.outputRoute.name
            prefs[Keys.USE_PHONE_MIC_SCO] = settings.usePhoneMicInSco
            prefs[Keys.ECHO_SUPPRESSION] = settings.echoNoiseSuppression
            prefs[Keys.PTT_MODE] = settings.isPttMode
            prefs[Keys.SHOW_WIRED_USB] = settings.showWiredAndUsb
        }
    }
}
