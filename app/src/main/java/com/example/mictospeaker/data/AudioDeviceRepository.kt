package com.example.mictospeaker.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DeviceCategory(val label: String) {
    BLUETOOTH("Bluetooth"),
    WIRED_AUX("AUX / Wired"),
    USB("USB Audio")
}

data class OutputDeviceItem(
    val id: String,
    val name: String,
    val category: DeviceCategory,
    val isConnected: Boolean,
    val audioDeviceInfo: AudioDeviceInfo? = null,
    val bluetoothAddress: String? = null
)

class AudioDeviceRepository(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val _devices = MutableStateFlow<List<OutputDeviceItem>>(emptyList())
    val devices: StateFlow<List<OutputDeviceItem>> = _devices.asStateFlow()

    private var includeWiredAndUsb = true

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }
    }

    fun startObserving() {
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        refreshDevices()
    }

    fun stopObserving() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    fun setIncludeWiredAndUsb(enabled: Boolean) {
        includeWiredAndUsb = enabled
        refreshDevices()
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        val result = mutableListOf<OutputDeviceItem>()
        val connectedBtNames = mutableSetOf<String>()

        // 1. Perangkat output aktif yang sedang terhubung (Bluetooth, AUX, USB)
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (info in outputs) {
            val category = mapDeviceCategory(info.type) ?: continue
            if (!includeWiredAndUsb && category != DeviceCategory.BLUETOOTH) continue

            val name = info.productName?.toString()?.takeIf { it.isNotBlank() }
                ?: defaultLabelFor(category)

            if (category == DeviceCategory.BLUETOOTH) {
                connectedBtNames.add(name)
            }

            result.add(
                OutputDeviceItem(
                    id = "active_${info.id}_${info.type}",
                    name = name,
                    category = category,
                    isConnected = true,
                    audioDeviceInfo = info
                )
            )
        }

        // 2. Perangkat Bluetooth yang sudah paired (tapi belum connect) untuk list di Settings
        if (hasBluetoothConnectPermission()) {
            val bonded = bluetoothManager?.adapter?.bondedDevices.orEmpty()
            for (bt in bonded) {
                val btName = bt.name ?: continue
                if (btName in connectedBtNames) continue
                result.add(
                    OutputDeviceItem(
                        id = "paired_${bt.address}",
                        name = btName,
                        category = DeviceCategory.BLUETOOTH,
                        isConnected = false,
                        bluetoothAddress = bt.address
                    )
                )
            }
        }

        _devices.value = result
            .distinctBy { "${it.name}_${it.category}_${it.isConnected}" }
            .sortedWith(compareByDescending<OutputDeviceItem> { it.isConnected }.thenBy { it.name })
    }

    @SuppressLint("InlinedApi")
    private fun mapDeviceCategory(type: Int): DeviceCategory? = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> DeviceCategory.BLUETOOTH

        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_LINE_ANALOG -> DeviceCategory.WIRED_AUX

        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> DeviceCategory.USB

        else -> null
    }

    private fun defaultLabelFor(category: DeviceCategory): String = when (category) {
        DeviceCategory.BLUETOOTH -> "Bluetooth Audio"
        DeviceCategory.WIRED_AUX -> "Wired Audio (AUX)"
        DeviceCategory.USB -> "USB Audio"
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
