# Pertahankan metode JNI bridge agar tidak dihapus R8 saat release build
-keep class com.example.mictospeaker.audio.OboeNativeBridge {
    native <methods>;
}
