#include <jni.h>
#include "OboeAudioEngine.h"

static OboeAudioEngine* asEngine(jlong handle) {
    return reinterpret_cast<OboeAudioEngine*>(handle);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new OboeAudioEngine());
}

JNIEXPORT void JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete asEngine(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeStart(
    JNIEnv*, jobject, jlong handle,
    jint outputDeviceId, jint inputDeviceId,
    jint latencyMultiplier, jboolean isScoRoute, jboolean isBluetooth, jboolean echoSuppression
) {
    auto* engine = asEngine(handle);
    if (!engine) return JNI_FALSE;
    return engine->start(
        outputDeviceId, inputDeviceId, latencyMultiplier,
        isScoRoute == JNI_TRUE, isBluetooth == JNI_TRUE, echoSuppression == JNI_TRUE
    ) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeStop(JNIEnv*, jobject, jlong handle) {
    if (auto* engine = asEngine(handle)) engine->stop();
}

JNIEXPORT void JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeUpdateParams(
    JNIEnv*, jobject, jlong handle,
    jfloat gain, jfloat noiseGate, jint presetIndex, jboolean isMuted
) {
    if (auto* engine = asEngine(handle)) {
        engine->updateLiveParams(gain, noiseGate, presetIndex, isMuted == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeSetRecording(
    JNIEnv*, jobject, jlong handle, jboolean enabled
) {
    if (auto* engine = asEngine(handle)) {
        engine->setRecordingEnabled(enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeSetEq(
    JNIEnv*, jobject, jlong handle, jfloat bassDb, jfloat midDb, jfloat trebleDb
) {
    if (auto* engine = asEngine(handle)) {
        engine->setEqGains(bassDb, midDb, trebleDb);
    }
}

JNIEXPORT void JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeSetLimiterThreshold(
    JNIEnv*, jobject, jlong handle, jfloat threshold
) {
    if (auto* engine = asEngine(handle)) {
        engine->setLimiterThreshold(threshold);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeGetRms(JNIEnv*, jobject, jlong handle) {
    auto* engine = asEngine(handle);
    return engine ? engine->getLatestRms() : 0.0f;
}

JNIEXPORT jint JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeGetSampleRate(JNIEnv*, jobject, jlong handle) {
    auto* engine = asEngine(handle);
    return engine ? engine->getSampleRate() : 48000;
}

JNIEXPORT jint JNICALL
Java_com_example_mictospeaker_audio_OboeNativeBridge_nativeDrainPcm(
    JNIEnv* env, jobject, jlong handle, jshortArray destArray
) {
    auto* engine = asEngine(handle);
    if (!engine || !destArray) return 0;

    const jsize maxSamples = env->GetArrayLength(destArray);
    jshort* elements = env->GetShortArrayElements(destArray, nullptr);
    if (!elements) return 0;

    const jint drained = engine->drainRecordedPcm(elements, maxSamples);
    env->ReleaseShortArrayElements(destArray, elements, 0);
    return drained;
}

} // extern "C"
