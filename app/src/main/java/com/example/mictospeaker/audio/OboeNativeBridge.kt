package com.example.mictospeaker.audio

class OboeNativeBridge {
    private var nativeHandle: Long = nativeCreate()

    fun start(
        outputDeviceId: Int,
        inputDeviceId: Int,
        latencyMultiplier: Int,
        isScoRoute: Boolean,
        echoSuppression: Boolean
    ): Boolean = nativeStart(
        nativeHandle, outputDeviceId, inputDeviceId,
        latencyMultiplier, isScoRoute, echoSuppression
    )

    fun stop() = nativeStop(nativeHandle)

    fun updateParams(gain: Float, noiseGate: Float, presetIndex: Int, isMuted: Boolean) {
        nativeUpdateParams(nativeHandle, gain, noiseGate, presetIndex, isMuted)
    }

    fun setRecording(enabled: Boolean) = nativeSetRecording(nativeHandle, enabled)
    fun getRms(): Float = nativeGetRms(nativeHandle)
    fun getSampleRate(): Int = nativeGetSampleRate(nativeHandle)
    fun drainPcm(buffer: ShortArray): Int = nativeDrainPcm(nativeHandle, buffer)

    fun release() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(
        handle: Long, outputDeviceId: Int, inputDeviceId: Int,
        latencyMultiplier: Int, isScoRoute: Boolean, echoSuppression: Boolean
    ): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeUpdateParams(
        handle: Long, gain: Float, noiseGate: Float, presetIndex: Int, isMuted: Boolean
    )
    private external fun nativeSetRecording(handle: Long, enabled: Boolean)
    private external fun nativeGetRms(handle: Long): Float
    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeDrainPcm(handle: Long, dest: ShortArray): Int

    companion object {
        init {
            System.loadLibrary("mic_oboe_engine")
        }
    }
}
