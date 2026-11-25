cmake_minimum_required(VERSION 3.22.1)
project("mic_oboe_engine")

find_package(oboe REQUIRED CONFIG)

add_library(mic_oboe_engine SHARED
    OboeAudioEngine.cpp
    jni_bridge.cpp
)

target_link_libraries(mic_oboe_engine
    oboe::oboe
    log
)
