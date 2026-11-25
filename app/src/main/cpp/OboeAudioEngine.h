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

    // Filter state (Voice Clarity HPF & Noise Gate envelope)
    float mPrevInput = 0.0f;
    float mPrevOutput = 0.0f;
    float mGateEnvelope = 0.0f;

    // Lock-free atomic parameters
    std::atomic<float> mGain{1.0f};
    std::atomic<float> mNoiseGate{0.0f};
    std::atomic<int32_t> mPreset{0}; // 0=Natural, 1=VoiceClarity, 2=KaraokeEcho
    std::atomic<bool> mIsMuted{false};
    std::atomic<bool> mIsRecording{false};
    std::atomic<float> mLatestRms{0.0f};
    std::atomic<int32_t> mSampleRate{48000};
    int32_t mOutputChannels = 1;

    // Lock-free SPSC Ring Buffer untuk PCM WAV (kapasitas ~1.3 detik @ 48kHz)
    static constexpr size_t kRingSize = 65536;
    static constexpr size_t kRingMask = kRingSize - 1;
    std::vector<int16_t> mPcmRing;
    std::atomic<size_t> mRingWrite{0};
    std::atomic<size_t> mRingRead{0};

    void pushPcmSample(int16_t sample);
};
