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
