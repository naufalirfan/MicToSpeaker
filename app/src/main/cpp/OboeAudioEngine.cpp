#include "OboeAudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>

#define LOG_TAG "OboeAudioEngine"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static constexpr float kPi = 3.14159265358979323846f;

OboeAudioEngine::OboeAudioEngine()
    : mPitchRing(kPitchRingSize, 0.0f),
      mPcmRing(kRingSize, 0) {}

OboeAudioEngine::~OboeAudioEngine() {
    stop();
}

void OboeAudioEngine::setupJokowiFilter(int32_t sampleRate) {
    if (sampleRate <= 0) sampleRate = 48000;
    // Karakter khas suara Pak Jokowi: Resonansi mid-nasal di ~1150 Hz dengan Q sedang & dorongan +6dB
    float f0 = 1150.0f;
    float Q = 2.0f;
    float gainDb = 6.0f;
    float A = std::pow(10.0f, gainDb / 40.0f);
    float w0 = 2.0f * kPi * f0 / static_cast<float>(sampleRate);
    float alpha = std::sin(w0) / (2.0f * Q);

    float b0 = 1.0f + (alpha * A);
    float b1 = -2.0f * std::cos(w0);
    float b2 = 1.0f - (alpha * A);
    float a0 = 1.0f + (alpha / A);
    float a1 = -2.0f * std::cos(w0);
    float a2 = 1.0f - (alpha / A);

    mJokowiB0 = b0 / a0;
    mJokowiB1 = b1 / a0;
    mJokowiB2 = b2 / a0;
    mJokowiA1 = a1 / a0;
    mJokowiA2 = a2 / a0;

    mJokowiX1 = 0.0f;
    mJokowiX2 = 0.0f;
    mJokowiY1 = 0.0f;
    mJokowiY2 = 0.0f;
}

inline float OboeAudioEngine::processJokowiFilter(float inSample) {
    float outSample = (mJokowiB0 * inSample) + (mJokowiB1 * mJokowiX1) + (mJokowiB2 * mJokowiX2)
                      - (mJokowiA1 * mJokowiY1) - (mJokowiA2 * mJokowiY2);
    mJokowiX2 = mJokowiX1;
    mJokowiX1 = inSample;
    mJokowiY2 = mJokowiY1;
    mJokowiY1 = outSample;
    return outSample;
}

float OboeAudioEngine::processPitchShift(float inSample, float pitchRatio) {
    if (std::abs(pitchRatio - 1.0f) < 0.01f) {
        return inSample;
    }

    // Tulis sampel ke circular ring buffer
    mPitchRing[mPitchWritePos] = inSample;
    size_t currentWrite = mPitchWritePos;
    mPitchWritePos = (mPitchWritePos + 1) & kPitchMask;

    // Majukan phase delay dengan rate = (1.0f - pitchRatio)
    float rate = 1.0f - pitchRatio;
    mPitchPhase1 += rate;
    if (mPitchPhase1 >= kPitchWindow) mPitchPhase1 -= kPitchWindow;
    if (mPitchPhase1 < 0.0f) mPitchPhase1 += kPitchWindow;

    float phase2 = mPitchPhase1 + (kPitchWindow * 0.5f);
    if (phase2 >= kPitchWindow) phase2 -= kPitchWindow;

    // Hanning crossfade window
    float norm1 = mPitchPhase1 / kPitchWindow;
    float w1 = 0.5f * (1.0f - std::cos(norm1 * 2.0f * kPi));
    float w2 = 1.0f - w1;

    // Hitung posisi baca fractional
    float r1 = static_cast<float>(currentWrite) - mPitchPhase1;
    while (r1 < 0.0f) r1 += static_cast<float>(kPitchRingSize);
    while (r1 >= static_cast<float>(kPitchRingSize)) r1 -= static_cast<float>(kPitchRingSize);

    float r2 = static_cast<float>(currentWrite) - phase2;
    while (r2 < 0.0f) r2 += static_cast<float>(kPitchRingSize);
    while (r2 >= static_cast<float>(kPitchRingSize)) r2 -= static_cast<float>(kPitchRingSize);

    // Linear interpolation head 1
    size_t idx1_0 = static_cast<size_t>(r1) & kPitchMask;
    size_t idx1_1 = (idx1_0 + 1) & kPitchMask;
    float frac1 = r1 - std::floor(r1);
    float s1 = mPitchRing[idx1_0] * (1.0f - frac1) + mPitchRing[idx1_1] * frac1;

    // Linear interpolation head 2
    size_t idx2_0 = static_cast<size_t>(r2) & kPitchMask;
    size_t idx2_1 = (idx2_0 + 1) & kPitchMask;
    float frac2 = r2 - std::floor(r2);
    float s2 = mPitchRing[idx2_0] * (1.0f - frac2) + mPitchRing[idx2_1] * frac2;

    return (w1 * s1) + (w2 * s2);
}

bool OboeAudioEngine::start(
    int32_t outputDeviceId,
    int32_t inputDeviceId,
    int32_t latencyMultiplier,
    bool isScoRoute,
    bool echoNoiseSuppression
) {
    stop();

    // 1. Output Stream
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(isScoRoute ? oboe::ChannelCount::Mono : oboe::ChannelCount::Stereo)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setUsage(isScoRoute
            ? oboe::Usage::VoiceCommunication
            : oboe::Usage::Media)
        ->setContentType(isScoRoute
            ? oboe::ContentType::Speech
            : oboe::ContentType::Music)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    if (outputDeviceId > 0) {
        outBuilder.setDeviceId(outputDeviceId);
    }

    // Adaptive fallbacks
    oboe::Result outRes = outBuilder.openStream(mOutputStream);
    if (outRes != oboe::Result::OK) {
        LOGI("Fallback 1 Output: Hapus deviceId");
        outBuilder.setDeviceId(oboe::kUnspecified);
        outRes = outBuilder.openStream(mOutputStream);
    }
    if (outRes != oboe::Result::OK) {
        LOGI("Fallback 2 Output: ChannelCount Unspecified");
        outBuilder.setChannelCount(oboe::ChannelCount::Unspecified);
        outRes = outBuilder.openStream(mOutputStream);
    }
    if (outRes != oboe::Result::OK) {
        LOGI("Fallback 3 Output: PerformanceMode None");
        outBuilder.setPerformanceMode(oboe::PerformanceMode::None);
        outRes = outBuilder.openStream(mOutputStream);
    }

    if (outRes != oboe::Result::OK || !mOutputStream) {
        LOGE("Gagal membuka Output Stream: %s", oboe::convertToText(outRes));
        return false;
    }

    int32_t sampleRate = mOutputStream->getSampleRate();
    mSampleRate.store(sampleRate, std::memory_order_relaxed);
    mOutputChannels = mOutputStream->getChannelCount();

    int32_t burst = mOutputStream->getFramesPerBurst();
    if (burst <= 0) burst = 192;
    int32_t targetBuffer = std::min(burst * latencyMultiplier, mOutputStream->getBufferCapacityInFrames());
    if (targetBuffer > 0) {
        mOutputStream->setBufferSizeInFrames(targetBuffer);
    }

    // 2. Input Stream (Mikrofon)
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(sampleRate)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setInputPreset(echoNoiseSuppression
            ? oboe::InputPreset::VoiceCommunication
            : oboe::InputPreset::Generic)
        ->setErrorCallback(this);

    if (inputDeviceId > 0) {
        inBuilder.setDeviceId(inputDeviceId);
    }

    oboe::Result inRes = inBuilder.openStream(mInputStream);
    if (inRes != oboe::Result::OK) {
        LOGI("Fallback 1 Input: Coba native sample rate");
        inBuilder.setSampleRate(oboe::kUnspecified);
        inRes = inBuilder.openStream(mInputStream);
    }
    if (inRes != oboe::Result::OK) {
        LOGI("Fallback 2 Input: Hapus deviceId");
        inBuilder.setDeviceId(oboe::kUnspecified);
        inRes = inBuilder.openStream(mInputStream);
    }
    if (inRes != oboe::Result::OK) {
        LOGI("Fallback 3 Input: PerformanceMode None");
        inBuilder.setPerformanceMode(oboe::PerformanceMode::None);
        inRes = inBuilder.openStream(mInputStream);
    }

    if (inRes != oboe::Result::OK || !mInputStream) {
        LOGE("Gagal membuka Input Stream: %s", oboe::convertToText(inRes));
        stop();
        return false;
    }

    // 3. Pre-alokasi buffer DSP (Zero runtime allocations di audio thread)
    int32_t maxFrames = std::max(mOutputStream->getBufferCapacityInFrames(), 4096);
    mInputBuffer.assign(static_cast<size_t>(maxFrames), 0.0f);

    size_t echoSamples = static_cast<size_t>(sampleRate * 0.18f); // 180 ms delay untuk Karaoke
    mEchoBuffer.assign(std::max<size_t>(echoSamples, 1), 0.0f);
    mEchoIndex = 0;

    mPitchRing.assign(kPitchRingSize, 0.0f);
    mPitchWritePos = 0;
    mPitchPhase1 = 0.0f;

    size_t imutSamples = static_cast<size_t>(sampleRate * 0.035f); // 35 ms ambiance delay untuk Imut
    mImutEchoBuffer.assign(std::max<size_t>(imutSamples, 1), 0.0f);
    mImutEchoIndex = 0;

    mPrevInput = 0.0f;
    mPrevOutput = 0.0f;
    mGateEnvelope = 0.0f;
    mPrevCowo = 0.0f;
    mPrevCeweIn = 0.0f;
    mPrevCeweOut = 0.0f;

    setupJokowiFilter(sampleRate);

    mRingWrite.store(0, std::memory_order_relaxed);
    mRingRead.store(0, std::memory_order_relaxed);

    // 4. Start streams
    if (mInputStream->requestStart() != oboe::Result::OK) {
        LOGE("Gagal start Input Stream");
        stop();
        return false;
    }

    if (mOutputStream->requestStart() != oboe::Result::OK) {
        LOGE("Gagal start Output Stream");
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

    // Baca mikrofon dengan timeout 12ms agar data mikrofon stabil saat ada Bluetooth jitter
    int32_t framesRead = 0;
    if (mInputStream) {
        auto result = mInputStream->read(mInputBuffer.data(), framesToProcess, 12000000); // 12 ms
        if (result && result.value() > 0) {
            framesRead = result.value();
        }
    }

    // Jika input belum penuh, isi sisanya dengan silence agar tidak berderau
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

        // Pemrosesan Efek Suara (Voice Effects):
        // 0 = NATURAL (Normal)
        // 1 = COWO (Suara Cowok Bass Berat)
        // 2 = CEWE (Suara Cewek Nyaring Natural)
        // 3 = LUCU (Suara Kartun / Chipmunk Tinggi)
        // 4 = IMUT (Suara Imut Kawaii / Anime)
        // 5 = JOKOWI (Khas Suara Pak Jokowi - Nasal Mid-Range & Relaxed Tone)
        // 6 = VOICE_CLARITY (Kejernihan Suara / HPF)
        // 7 = KARAOKE_ECHO (Gema Karaoke)
        switch (preset) {
            case 1: { // COWO (Deep Male)
                float pitched = processPitchShift(sample, 0.76f);
                mPrevCowo += 0.38f * (pitched - mPrevCowo);
                sample = (pitched * 0.7f) + (mPrevCowo * 0.65f);
                break;
            }
            case 2: { // CEWE (Female)
                float pitched = processPitchShift(sample, 1.34f);
                float hp = 0.982f * (mPrevCeweOut + pitched - mPrevCeweIn);
                mPrevCeweIn = pitched;
                mPrevCeweOut = hp;
                sample = hp * 1.15f;
                break;
            }
            case 3: { // LUCU (Chipmunk / Cartoon)
                sample = processPitchShift(sample, 1.68f) * 1.1f;
                break;
            }
            case 4: { // IMUT (Cute / Anime)
                float pitched = processPitchShift(sample, 1.44f);
                if (!mImutEchoBuffer.empty()) {
                    float delayed = mImutEchoBuffer[mImutEchoIndex];
                    mImutEchoBuffer[mImutEchoIndex] = pitched + (delayed * 0.20f);
                    mImutEchoIndex = (mImutEchoIndex + 1) % mImutEchoBuffer.size();
                    sample = (pitched * 0.88f) + (delayed * 0.22f);
                } else {
                    sample = pitched;
                }
                break;
            }
            case 5: { // JOKOWI (Khas Pak Jokowi)
                float pitched = processPitchShift(sample, 0.93f);
                sample = processJokowiFilter(pitched) * 1.05f;
                break;
            }
            case 6: { // VOICE_CLARITY
                float hp = 0.985f * (mPrevOutput + sample - mPrevInput);
                mPrevInput = sample;
                mPrevOutput = hp;
                sample = hp * 1.25f;
                break;
            }
            case 7: { // KARAOKE_ECHO
                if (!mEchoBuffer.empty()) {
                    float delayed = mEchoBuffer[mEchoIndex];
                    float mixed = sample + (delayed * 0.38f);
                    mEchoBuffer[mEchoIndex] = mixed * 0.65f;
                    mEchoIndex = (mEchoIndex + 1) % echoSize;
                    sample = mixed;
                }
                break;
            }
            default: // NATURAL (Bypass)
                break;
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

        // Tulis ke SPSC ring buffer jika rekam WAV aktif
        if (recording) {
            const float clamped = std::clamp(outSample, -1.0f, 1.0f);
            pushPcmSample(static_cast<int16_t>(clamped * 32767.0f));
        }
    }

    // Jika numFrames melebihi mInputBuffer, nol-kan sisanya
    for (int32_t i = framesToProcess * mOutputChannels; i < numFrames * mOutputChannels; ++i) {
        out[i] = 0.0f;
    }

    mLatestRms.store(muted ? 0.0f : std::clamp(rms * gain, 0.0f, 1.0f), std::memory_order_relaxed);
    return oboe::DataCallbackResult::Continue;
}

void OboeAudioEngine::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGE("Oboe stream terputus: %s", oboe::convertToText(error));
}
