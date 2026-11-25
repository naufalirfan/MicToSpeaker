#include "OboeAudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>

#define LOG_TAG "OboeAudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

OboeAudioEngine::OboeAudioEngine() : mPcmRing(kRingSize, 0) {}

OboeAudioEngine::~OboeAudioEngine() {
    stop();
}

bool OboeAudioEngine::start(
    int32_t outputDeviceId,
    int32_t inputDeviceId,
    int32_t latencyMultiplier,
    bool isScoRoute,
    bool echoNoiseSuppression
) {
    stop();

    // 1. Buka Output Stream terlebih dahulu untuk menentukan sample rate native perangkat
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(latencyMultiplier == 1
            ? oboe::SharingMode::Exclusive
            : oboe::SharingMode::Shared)
        ->setUsage(isScoRoute
            ? oboe::Usage::VoiceCommunication
            : oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Speech)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    if (outputDeviceId > 0) {
        outBuilder.setDeviceId(outputDeviceId);
    }

    oboe::Result outRes = outBuilder.openStream(mOutputStream);
    if (outRes != oboe::Result::OK || !mOutputStream) {
        LOGE("Gagal membuka Output Stream: %s", oboe::convertToText(outRes));
        return false;
    }

    int32_t sampleRate = mOutputStream->getSampleRate();
    mSampleRate.store(sampleRate, std::memory_order_relaxed);
    mOutputChannels = mOutputStream->getChannelCount();

    // Atur ukuran buffer sesuai LatencyMode (1=LowLatency, 2=Balanced, 4=Stable)
    int32_t burst = mOutputStream->getFramesPerBurst();
    int32_t targetBuffer = std::min(burst * latencyMultiplier, mOutputStream->getBufferCapacityInFrames());
    mOutputStream->setBufferSizeInFrames(targetBuffer);

    // 2. Buka Input Stream dengan sample rate yang sama (resampling otomatis oleh Oboe jika perlu)
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(sampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(latencyMultiplier == 1
            ? oboe::SharingMode::Exclusive
            : oboe::SharingMode::Shared)
        ->setInputPreset(echoNoiseSuppression
            ? oboe::InputPreset::VoiceCommunication
            : oboe::InputPreset::Generic);

    if (inputDeviceId > 0) {
        inBuilder.setDeviceId(inputDeviceId);
    }

    oboe::Result inRes = inBuilder.openStream(mInputStream);
    if (inRes != oboe::Result::OK || !mInputStream) {
        LOGE("Gagal membuka Input Stream: %s", oboe::convertToText(inRes));
        stop();
        return false;
    }

    // 3. Pre-alokasi seluruh buffer DSP sebelum stream berjalan (zero allocation di audio thread)
    int32_t maxFrames = std::max(mOutputStream->getBufferCapacityInFrames(), 4096);
    mInputBuffer.assign(static_cast<size_t>(maxFrames), 0.0f);

    size_t echoSamples = static_cast<size_t>(sampleRate * 0.18f); // 180 ms delay
    mEchoBuffer.assign(std::max<size_t>(echoSamples, 1), 0.0f);
    mEchoIndex = 0;
    mPrevInput = 0.0f;
    mPrevOutput = 0.0f;
    mGateEnvelope = 0.0f;

    mRingWrite.store(0, std::memory_order_relaxed);
    mRingRead.store(0, std::memory_order_relaxed);

    // 4. Mulai Input lebih dulu, lalu Output
    if (mInputStream->requestStart() != oboe::Result::OK) {
        stop();
        return false;
    }

    if (mOutputStream->requestStart() != oboe::Result::OK) {
        stop();
        return false;
    }

    LOGI("Oboe Full-Duplex aktif @ %d Hz | OutCh: %d | Burst: %d", sampleRate, mOutputChannels, burst);
    return true;
}

void OboeAudioEngine::stop() {
    if (mOutputStream) {
        mOutputStream->requestStop();
        mOutputStream->close();
        mOutputStream.reset();
    }
    if (mInputStream) {
        mInputStream->requestStop();
        mInputStream->close();
        mInputStream.reset();
    }
    mLatestRms.store(0.0f, std::memory_order_relaxed);
}

void OboeAudioEngine::updateLiveParams(
    float gain,
    float noiseGateThreshold,
    int32_t presetIndex,
    bool isMuted
) {
    mGain.store(gain, std::memory_order_relaxed);
    mNoiseGate.store(noiseGateThreshold, std::memory_order_relaxed);
    mPreset.store(presetIndex, std::memory_order_relaxed);
    mIsMuted.store(isMuted, std::memory_order_relaxed);
}

void OboeAudioEngine::setRecordingEnabled(bool enabled) {
    if (enabled) {
        mRingRead.store(mRingWrite.load(std::memory_order_acquire), std::memory_order_release);
    }
    mIsRecording.store(enabled, std::memory_order_release);
}

float OboeAudioEngine::getLatestRms() const {
    return mLatestRms.load(std::memory_order_relaxed);
}

int32_t OboeAudioEngine::getSampleRate() const {
    return mSampleRate.load(std::memory_order_relaxed);
}

inline void OboeAudioEngine::pushPcmSample(int16_t sample) {
    size_t write = mRingWrite.load(std::memory_order_relaxed);
    size_t next = (write + 1) & kRingMask;
    if (next != mRingRead.load(std::memory_order_acquire)) {
        mPcmRing[write] = sample;
        mRingWrite.store(next, std::memory_order_release);
    }
}

int32_t OboeAudioEngine::drainRecordedPcm(int16_t* dest, int32_t maxSamples) {
    size_t read = mRingRead.load(std::memory_order_relaxed);
    size_t write = mRingWrite.load(std::memory_order_acquire);
    int32_t count = 0;

    while (read != write && count < maxSamples) {
        dest[count++] = mPcmRing[read];
        read = (read + 1) & kRingMask;
    }

    mRingRead.store(read, std::memory_order_release);
    return count;
}

oboe::DataCallbackResult OboeAudioEngine::onAudioReady(
    oboe::AudioStream* /*audioStream*/,
    void* audioData,
    int32_t numFrames
) {
    auto* out = static_cast<float*>(audioData);
    const int32_t framesToProcess = std::min<int32_t>(numFrames, static_cast<int32_t>(mInputBuffer.size()));

    // Non-blocking read dari mikrofon
    int32_t framesRead = 0;
    if (mInputStream) {
        auto result = mInputStream->read(mInputBuffer.data(), framesToProcess, 0);
        if (result) {
            framesRead = result.value();
        }
    }

    // Jika input belum penuh, isi sisanya dengan silence agar tidak bunyi noise
    for (int32_t i = framesRead; i < framesToProcess; ++i) {
        mInputBuffer[i] = 0.0f;
    }

    // Hitung RMS frame
    double sumSq = 0.0;
    for (int32_t i = 0; i < framesToProcess; ++i) {
        double s = mInputBuffer[i];
        sumSq += s * s;
    }
    const float rms = (framesToProcess > 0)
        ? static_cast<float>(std::sqrt(sumSq / framesToProcess))
        : 0.0f;

    const float gain = mGain.load(std::memory_order_relaxed);
    const float gateThresh = mNoiseGate.load(std::memory_order_relaxed);
    const int32_t preset = mPreset.load(std::memory_order_relaxed);
    const bool muted = mIsMuted.load(std::memory_order_relaxed);
    const bool recording = mIsRecording.load(std::memory_order_relaxed);

    const float gateTarget = (muted || (gateThresh > 0.0f && rms < gateThresh)) ? 0.0f : 1.0f;
    const size_t echoSize = mEchoBuffer.size();

    for (int32_t i = 0; i < framesToProcess; ++i) {
        const float coeff = (gateTarget > mGateEnvelope) ? 0.15f : 0.01f;
        mGateEnvelope += (gateTarget - mGateEnvelope) * coeff;

        float sample = mInputBuffer[i] * mGateEnvelope;

        if (preset == 1) {
            // Voice Clarity: High-pass filter (~120 Hz) + presence boost
            const float hp = 0.985f * (mPrevOutput + sample - mPrevInput);
            mPrevInput = sample;
            mPrevOutput = hp;
            sample = hp * 1.25f;
        } else if (preset == 2) {
            // Karaoke Echo
            const float delayed = mEchoBuffer[mEchoIndex];
            const float mixed = sample + (delayed * 0.38f);
            mEchoBuffer[mEchoIndex] = mixed * 0.65f;
            mEchoIndex = (mEchoIndex + 1) % echoSize;
            sample = mixed;
        }

        // Gain + Soft Clipping
        const float outSample = std::tanh(sample * gain);

        // Tulis ke buffer output (otomatis handle mono atau stereo)
        if (mOutputChannels == 1) {
            out[i] = outSample;
        } else {
            for (int32_t ch = 0; ch < mOutputChannels; ++ch) {
                out[i * mOutputChannels + ch] = outSample;
            }
        }

        // Dorong ke SPSC ring buffer jika rekam WAV aktif
        if (recording) {
            const float clamped = std::clamp(outSample, -1.0f, 1.0f);
            pushPcmSample(static_cast<int16_t>(clamped * 32767.0f));
        }
    }

    // Jika numFrames melebihi mInputBuffer (jarang terjadi), nol-kan sisanya
    for (int32_t i = framesToProcess * mOutputChannels; i < numFrames * mOutputChannels; ++i) {
        out[i] = 0.0f;
    }

    mLatestRms.store(muted ? 0.0f : std::clamp(rms * gain, 0.0f, 1.0f), std::memory_order_relaxed);
    return oboe::DataCallbackResult::Continue;
}

void OboeAudioEngine::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGE("Oboe stream terputus: %s", oboe::convertToText(error));
}
