# Mic to Speaker (Android Native — Kotlin + Jetpack Compose + C++ Oboe)

Aplikasi Android native untuk meneruskan suara mikrofon ke speaker Bluetooth (A2DP / SCO), kabel AUX, atau USB Audio secara real-time dengan latensi sangat rendah menggunakan **Google Oboe (C++)**, penyimpanan permanen **Jetpack DataStore**, dan perekaman **WAV (16-bit PCM)**.

## Fitur Utama
- **Ultra-Low Latency Audio Engine (C++ Oboe):** Full-duplex real-time stream dengan DSP lock-free di audio thread (Mic Gain `0.0x–3.0x` + soft clipping `tanh`, Noise Gate berbasis RMS, Preset **Natural / Voice Clarity / Karaoke Echo**).
- **Latency Mode:** Pilihan **Low Latency** (1x burst), **Balanced** (2x burst), dan **Stable** (4x burst).
- **Output Routing & Device Detection:** Deteksi otomatis perangkat Bluetooth, AUX, dan USB secara real-time via `AudioDeviceCallback`, serta switch **Music (A2DP)** vs **Voice (SCO)** dan opsi **Use phone mic** di mode SCO.
- **Push-to-Talk (PTT) & Mute:** Mode tahan tombol untuk berbicara (*hold-to-speak*) atau tombol Start/Stop standar.
- **Save WAV:** Perekaman audio pasca-DSP menggunakan lock-free SPSC ring buffer, tersimpan otomatis di `Recordings/MicToSpeaker/` (Android 10+).
- **Background Foreground Service:** Tetap berjalan saat layar mati atau berpindah aplikasi, lengkap dengan shortcut pengaturan optimasi baterai.
- **Jetpack DataStore:** Seluruh pengaturan terakhir otomatis tersimpan.

## Persyaratan Build
1. **Android Studio** Ladybug (2024.2+) atau Gradle 8.10.2 + JDK 17.
2. **Android SDK 35** (Min SDK 26).
3. **NDK (Side by side)** (disarankan r26/r27) dan **CMake 3.22.1** terpasang melalui SDK Manager:
   ```bash
   sdkmanager "platforms;android-35" "ndk;27.0.12077973" "cmake;3.22.1"
   ```

## Cara Build APK
Buka folder `MicToSpeaker` di Android Studio lalu klik **Build > Build Bundle(s) / APK(s) > Build APK(s)**, atau melalui terminal (jika Gradle sudah terpasang di sistem / setelah Android Studio men-generate wrapper):
```bash
gradle assembleDebug
```
Output APK berada di:
`app/build/outputs/apk/debug/app-debug.apk`
