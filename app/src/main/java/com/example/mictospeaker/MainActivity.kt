package com.example.mictospeaker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mictospeaker.ui.screens.MainScreen
import com.example.mictospeaker.ui.screens.SettingsScreen
import com.example.mictospeaker.ui.theme.*
import com.example.mictospeaker.ui.viewmodel.MicViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MicViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.setPermissionsGranted(hasCorePermissions())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(BgDark.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(BgDark.toArgb())
        )

        viewModel.setPermissionsGranted(hasCorePermissions())

        setContent {
            val darkScheme = darkColorScheme(
                primary = MintAccent,
                onPrimary = OnMint,
                background = BgDark,
                surface = CardDark,
                onBackground = TextPrimary,
                onSurface = TextPrimary
            )

            MaterialTheme(colorScheme = darkScheme) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                var showSettings by remember { mutableStateOf(false) }

                BackHandler(enabled = showSettings) {
                    showSettings = false
                }

                LaunchedEffect(state.statusMessage) {
                    state.statusMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearStatusMessage()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (showSettings) {
                        SettingsScreen(
                            state = state,
                            onBack = { showSettings = false },
                            onRefreshDevices = viewModel::refreshDevices,
                            onGainChange = viewModel::setGain,
                            onNoiseGateChange = viewModel::setNoiseGate,
                            onPresetChange = viewModel::setSoundPreset,
                            onLatencyChange = viewModel::setLatencyMode,
                            onRouteChange = viewModel::setOutputRoute,
                            onToggleWiredUsb = viewModel::setShowWiredAndUsb,
                            onSelectDevice = viewModel::selectDevice,
                            onTogglePtt = viewModel::togglePttMode,
                            onToggleUsePhoneMic = viewModel::setUsePhoneMicInSco,
                            onToggleEchoSuppression = viewModel::setEchoSuppression
                        )
                    } else {
                        MainScreen(
                            state = state,
                            onRequestPermissions = ::requestRequiredPermissions,
                            onOpenSettings = { showSettings = true },
                            onToggleStart = {
                                if (!hasCorePermissions()) {
                                    requestRequiredPermissions()
                                } else {
                                    viewModel.toggleStreaming()
                                }
                            },
                            onPttPressChange = { pressed ->
                                if (pressed && !hasCorePermissions()) {
                                    requestRequiredPermissions()
                                } else {
                                    viewModel.setPttPressed(pressed)
                                }
                            },
                            onToggleMute = viewModel::toggleMute,
                            onTogglePtt = viewModel::togglePttMode,
                            onToggleSaveWav = viewModel::toggleSaveWav,
                            onGainChange = viewModel::setGain,
                            onRouteChange = viewModel::setOutputRoute,
                            onSelectDevice = viewModel::selectDevice,
                            onRefreshDevices = viewModel::refreshDevices
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setPermissionsGranted(hasCorePermissions())
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.toTypedArray()
    }

    private fun hasCorePermissions(): Boolean {
        val hasMic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasBt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return hasMic && hasBt
    }

    private fun requestRequiredPermissions() {
        permissionLauncher.launch(requiredPermissions())
    }
}
