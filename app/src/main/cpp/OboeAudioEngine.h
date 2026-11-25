#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include <vector>
#include <thread>
#include <cstdint>

class OboeAudioEngine : public oboe::AudioStreamDataCallback,
                        public oboe::AudioStreamErrorCallback {
public:
    OboeAudioEngine();
    ~OboeAudioEngine() override;

    bool start(
        int32_t outputDeviceId,
        int32_t inputDeviceId,
        int32_t latencyMultiplier,
        bool isScoRoute,
        bool isBluetooth,
        bool echoNoiseSuppression
    );
    void stop();

    void updateLiveParams(
        float gain,
        float noiseGateThreshold,
        int32_t presetIndex,
        bool isMuted
    );
    void setEqGains(float bassDb, float midDb, float trebleDb);
    void setLimiterThreshold(float threshold);

    void setRecordingEnabled(bool enabled);
    float getLatestRms() const;
    int32_t getSampleRate() const;

    // Drain PCM 16-bit dari lock-free ring buffer untuk ditulis ke WavRecorder
    int32_t drainRecordedPcm(int16_t* dest, int32_t maxSamples);

    // Oboe callbacks
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        int32_t numFrames
    ) override;

    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::vector<float> mInputBuffer;
    std::vector<float> mEchoBuffer;
    size_t mEchoIndex = 0;

    // Jitter Ring Buffer antara thread capture mic dan output callback
    static constexpr size_t kJitterRingSize = 32768; // ~680ms @ 48kHz
    static constexpr size_t kJitterMask = kJitterRingSize - 1;
    std::vector<float> mJitterRing;
    std::atomic<size_t> mJitterWrite{0};
    std::atomic<size_t> mJitterRead{0};
    std::atomic<bool> mJitterPrimed{false};

    // Dedicated Capture Thread untuk microphone
    std::atomic<bool> mIsCapturing{false};
    std::thread mCaptureThread;

    // Pitch shifter state (Dual delay-line with overlapping crossfade)
    static constexpr size_t kPitchRingSize = 4096;
    static constexpr size_t kPitchMask = kPitchRingSize - 1;
    static constexpr float kPitchWindow = 1024.0f; // ~21ms at 48kHz
    std::vector<float> mPitchRing;
    size_t mPitchWritePos = 0;
    float mPitchPhase1 = 0.0f;

    // Imut soft ambiance delay line
    std::vector<float> mImutEchoBuffer;
    size_t mImutEchoIndex = 0;

    // Filter state (Voice Clarity HPF & Noise Gate envelope)
    float mPrevInput = 0.0f;
    float mPrevOutput = 0.0f;
    float mGateEnvelope = 0.0f;

    // Filter states for Cowo & Cewe
    float mPrevCowo = 0.0f;
    float mPrevCeweIn = 0.0f;
    float mPrevCeweOut = 0.0f;

    // RBJ Biquad Filter untuk real-time formant & acoustic modeling
    struct BiquadFilter {
        float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
        float a1 = 0.0f, a2 = 0.0f;
        float x1 = 0.0f, x2 = 0.0f;
        float y1 = 0.0f, y2 = 0.0f;

        inline float process(float in) {
            float out = (b0 * in) + (b1 * x1) + (b2 * x2) - (a1 * y1) - (a2 * y2);
            x2 = x1;
            x1 = in;
            y2 = y1;
            y1 = out;
            return out;
        }

        void reset() {
            x1 = x2 = y1 = y2 = 0.0f;
        }

        void setPeaking(float f0, float Q, float gainDb, float fs) {
            if (fs <= 0.0f) fs = 48000.0f;
            float A = std::pow(10.0f, gainDb / 40.0f);
            float w0 = 2.0f * 3.14159265358979323846f * f0 / fs;
            float alpha = std::sin(w0) / (2.0f * Q);
            float a0 = 1.0f + (alpha / A);
            b0 = (1.0f + (alpha * A)) / a0;
            b1 = (-2.0f * std::cos(w0)) / a0;
            b2 = (1.0f - (alpha * A)) / a0;
            a1 = (-2.0f * std::cos(w0)) / a0;
            a2 = (1.0f - (alpha / A)) / a0;
        }

        void setHPF(float f0, float Q, float fs) {
            if (fs <= 0.0f) fs = 48000.0f;
            float w0 = 2.0f * 3.14159265358979323846f * f0 / fs;
            float alpha = std::sin(w0) / (2.0f * Q);
            float a0 = 1.0f + alpha;
            b0 = ((1.0f + std::cos(w0)) * 0.5f) / a0;
            b1 = (-(1.0f + std::cos(w0))) / a0;
            b2 = ((1.0f + std::cos(w0)) * 0.5f) / a0;
            a1 = (-2.0f * std::cos(w0)) / a0;
            a2 = (1.0f - alpha) / a0;
        }

        void setLPF(float f0, float Q, float fs) {
            if (fs <= 0.0f) fs = 48000.0f;
            float w0 = 2.0f * 3.14159265358979323846f * f0 / fs;
            float alpha = std::sin(w0) / (2.0f * Q);
            float a0 = 1.0f + alpha;
            b0 = ((1.0f - std::cos(w0)) * 0.5f) / a0;
            b1 = (1.0f - std::cos(w0)) / a0;
            b2 = ((1.0f - std::cos(w0)) * 0.5f) / a0;
            a1 = (-2.0f * std::cos(w0)) / a0;
            a2 = (1.0f - alpha) / a0;
        }

        void setLowShelf(float freq, float q, float gainDb, float sampleRate) {
            if (sampleRate <= 0.0f) sampleRate = 48000.0f;
            float A = std::pow(10.0f, gainDb / 40.0f);
            float w0 = 2.0f * 3.14159265358979323846f * freq / sampleRate;
            float cosW0 = std::cos(w0);
            float sinW0 = std::sin(w0);
            float alpha = sinW0 / (2.0f * q);
            float twoSqrtAAlpha = 2.0f * std::sqrt(A) * alpha;

            float rawB0 = A * ((A + 1.0f) - (A - 1.0f) * cosW0 + twoSqrtAAlpha);
            float rawB1 = 2.0f * A * ((A - 1.0f) - (A + 1.0f) * cosW0);
            float rawB2 = A * ((A + 1.0f) - (A - 1.0f) * cosW0 - twoSqrtAAlpha);
            float rawA0 = (A + 1.0f) + (A - 1.0f) * cosW0 + twoSqrtAAlpha;
            float rawA1 = -2.0f * ((A - 1.0f) + (A + 1.0f) * cosW0);
            float rawA2 = (A + 1.0f) + (A - 1.0f) * cosW0 - twoSqrtAAlpha;

            b0 = rawB0 / rawA0;
            b1 = rawB1 / rawA0;
            b2 = rawB2 / rawA0;
            a1 = rawA1 / rawA0;
            a2 = rawA2 / rawA0;
        }

        void setHighShelf(float freq, float q, float gainDb, float sampleRate) {
            if (sampleRate <= 0.0f) sampleRate = 48000.0f;
            float A = std::pow(10.0f, gainDb / 40.0f);
            float w0 = 2.0f * 3.14159265358979323846f * freq / sampleRate;
            float cosW0 = std::cos(w0);
            float sinW0 = std::sin(w0);
            float alpha = sinW0 / (2.0f * q);
            float twoSqrtAAlpha = 2.0f * std::sqrt(A) * alpha;

            float rawB0 = A * ((A + 1.0f) + (A - 1.0f) * cosW0 + twoSqrtAAlpha);
            float rawB1 = -2.0f * A * ((A - 1.0f) + (A + 1.0f) * cosW0);
            float rawB2 = A * ((A + 1.0f) + (A - 1.0f) * cosW0 - twoSqrtAAlpha);
            float rawA0 = (A + 1.0f) - (A - 1.0f) * cosW0 + twoSqrtAAlpha;
            float rawA1 = 2.0f * ((A - 1.0f) - (A + 1.0f) * cosW0);
            float rawA2 = (A + 1.0f) - (A - 1.0f) * cosW0 - twoSqrtAAlpha;

            b0 = rawB0 / rawA0;
            b1 = rawB1 / rawA0;
            b2 = rawB2 / rawA0;
            a1 = rawA1 / rawA0;
            a2 = rawA2 / rawA0;
        }
    };

    // 3-Band Equalizer (LowShelf 200Hz, Peaking 1000Hz, HighShelf 4000Hz)
    class ThreeBandEQ {
    public:
        void init(float sampleRate) {
            mSampleRate = (sampleRate > 0.0f) ? sampleRate : 48000.0f;
            mBass.reset();
            mMid.reset();
            mTreble.reset();
            updateCoefficients();
        }
        void setGains(float bassDb, float midDb, float trebleDb) {
            mBassDb.store(std::clamp(bassDb, -12.0f, 12.0f), std::memory_order_relaxed);
            mMidDb.store(std::clamp(midDb, -12.0f, 12.0f), std::memory_order_relaxed);
            mTrebleDb.store(std::clamp(trebleDb, -12.0f, 12.0f), std::memory_order_relaxed);
            mDirty.store(true, std::memory_order_release);
        }
        void syncParameters() {
            if (mDirty.exchange(false, std::memory_order_acquire)) {
                updateCoefficients();
            }
        }
        inline float process(float sample) {
            sample = mBass.process(sample);
            sample = mMid.process(sample);
            sample = mTreble.process(sample);
            return sample;
        }
    private:
        void updateCoefficients() {
            mBass.setLowShelf(200.0f, 0.707f, mBassDb.load(std::memory_order_relaxed), mSampleRate);
            mMid.setPeaking(1000.0f, 0.707f, mMidDb.load(std::memory_order_relaxed), mSampleRate);
            mTreble.setHighShelf(4000.0f, 0.707f, mTrebleDb.load(std::memory_order_relaxed), mSampleRate);
        }
        float mSampleRate = 48000.0f;
        BiquadFilter mBass, mMid, mTreble;
        std::atomic<float> mBassDb{0.0f}, mMidDb{0.0f}, mTrebleDb{0.0f};
        std::atomic<bool> mDirty{false};
    };

    // Soft Limiter (Zero-Latency Anti-Clipping dari PDF)
    class SoftLimiter {
    public:
        void setThreshold(float threshold) {
            mThreshold.store(std::clamp(threshold, 0.1f, 0.99f), std::memory_order_relaxed);
        }
        inline float process(float x) {
            float thresh = mThreshold.load(std::memory_order_relaxed);
            float absX = std::fabs(x);
            if (absX <= thresh) {
                return x;
            }
            float sign = (x >= 0.0f) ? 1.0f : -1.0f;
            float headroom = 1.0f - thresh;
            float excess = (absX - thresh) / headroom;
            return sign * (thresh + headroom * std::tanh(excess));
        }
    private:
        std::atomic<float> mThreshold{0.8f};
    };

    // GoPay Soundbox Jokowi Acoustic & Formant Chain:
    // 1. Box Speaker HPF (potong sub-bass boomy di 240 Hz)
    // 2. Chest scoop (reduksi 500 Hz agar tidak terdengar suara cowo tebal standar)
    // 3. Nasal peak (karakter sengau khas Solo di 1220 Hz +9dB)
    // 4. Throat presence (artikulasi vokal di 2550 Hz +6dB)
    // 5. Box Speaker LPF (karakter speaker portabel di 4600 Hz)
    // 6. Cabinet acoustic reflection (~22ms delay buffer)
    BiquadFilter mJokowiHpf;
    BiquadFilter mJokowiDip;
    BiquadFilter mJokowiNasal;
    BiquadFilter mJokowiThroat;
    BiquadFilter mJokowiLpf;
    std::vector<float> mJokowiBoxBuffer;
    size_t mJokowiBoxIndex = 0;

    ThreeBandEQ mEq;
    SoftLimiter mSoftLimiter;
    BiquadFilter mMegaHpf;
    BiquadFilter mMegaLpf;
    float mRobotPhase = 0.0f;

    void setupJokowiFilter(int32_t sampleRate);
    float processPitchShift(float inSample, float pitchRatio);
    float processJokowiFilter(float inSample);

    // Lock-free atomic parameters
    std::atomic<float> mGain{1.0f};
    std::atomic<float> mNoiseGate{0.0f};
    std::atomic<int32_t> mPreset{0};
    std::atomic<bool> mIsMuted{false};
    std::atomic<bool> mIsRecording{false};
    std::atomic<float> mLatestRms{0.0f};
    std::atomic<int32_t> mSampleRate{48000};
    int32_t mOutputChannels = 1;

    // Lock-free SPSC Ring Buffer untuk PCM WAV
    static constexpr size_t kRingSize = 65536;
    static constexpr size_t kRingMask = kRingSize - 1;
    std::vector<int16_t> mPcmRing;
    std::atomic<size_t> mRingWrite{0};
    std::atomic<size_t> mRingRead{0};

    void pushPcmSample(int16_t sample);
};
