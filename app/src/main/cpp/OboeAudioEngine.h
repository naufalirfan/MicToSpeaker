#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include <vector>
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

    // Jokowi Biquad Resonant Formant Filter
    float mJokowiB0 = 1.0f;
    float mJokowiB1 = 0.0f;
    float mJokowiB2 = 0.0f;
    float mJokowiA1 = 0.0f;
    float mJokowiA2 = 0.0f;
    float mJokowiX1 = 0.0f;
    float mJokowiX2 = 0.0f;
    float mJokowiY1 = 0.0f;
    float mJokowiY2 = 0.0f;

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
