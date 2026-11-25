package com.example.mictospeaker.audio

import android.media.AudioDeviceInfo

enum class OutputRoute { MUSIC_A2DP, VOICE_SCO }

enum class SoundPreset(val label: String, val iconEmoji: String) {
    NATURAL("Normal", "🎤"),
    COWO("Cowo", "👨"),
    CEWE("Cewe", "👩"),
    LUCU("Lucu", "🐹"),
    IMUT("Imut", "🌸"),
    JOKOWI("Jokowi", "🇮🇩"),
    VOICE_CLARITY("Clarity", "🎙️"),
    KARAOKE_ECHO("Echo", "🎶"),
    MEGAPHONE("Toa", "📢"),
    ROBOT("Robot", "🤖"),
    RADIO_HT("HT Radio", "📻"),
    BROADCAST_DJ("Penyiar DJ", "🎧"),
    MONSTER("Monster", "👻"),
    HELIUM("Helium", "🎈"),
    HALL_MASJID("Masjid", "🏛️"),
    KAKEK("Kakek", "👴"),
    VINTAGE_PHONE("Telepon", "📱")
}

enum class LatencyMode(val bufferMultiplier: Int) {
    LOW_LATENCY(1),
    BALANCED(2),
    STABLE(4)
}

data class AudioConfig(
    val gain: Float = 1.0f,               // 0.0f .. 3.0f
    val noiseGateThreshold: Float = 0.0f, // 0.0f (Off) .. 0.25f
    val preset: SoundPreset = SoundPreset.NATURAL,
    val latencyMode: LatencyMode = LatencyMode.BALANCED,
    val outputRoute: OutputRoute = OutputRoute.MUSIC_A2DP,
    val usePhoneMicInSco: Boolean = true,
    val echoNoiseSuppression: Boolean = true,
    val isMuted: Boolean = false,
    val preferredOutputDevice: AudioDeviceInfo? = null
)
