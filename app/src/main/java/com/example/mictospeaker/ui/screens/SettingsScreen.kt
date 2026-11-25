package com.example.mictospeaker.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mictospeaker.audio.LatencyMode
import com.example.mictospeaker.audio.OutputRoute
import com.example.mictospeaker.audio.SoundPreset
import com.example.mictospeaker.data.OutputDeviceItem
import com.example.mictospeaker.ui.theme.*
import com.example.mictospeaker.ui.viewmodel.MicUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MicUiState,
    onBack: () -> Unit,
    onRefreshDevices: () -> Unit,
    onGainChange: (Float) -> Unit,
    onNoiseGateChange: (Float) -> Unit,
    onPresetChange: (SoundPreset) -> Unit,
    onLatencyChange: (LatencyMode) -> Unit,
    onRouteChange: (OutputRoute) -> Unit,
    onToggleWiredUsb: (Boolean) -> Unit,
    onSelectDevice: (OutputDeviceItem) -> Unit,
    onTogglePtt: () -> Unit,
    onToggleUsePhoneMic: (Boolean) -> Unit,
    onToggleEchoSuppression: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var activeDialogTitle by remember { mutableStateOf<String?>(null) }
    var activeDialogBody by remember { mutableStateOf("") }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshDevices) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Audio Controls Card
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, tint = MintAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Audio controls", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(14.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mic gain", color = TextPrimary)
                    Text("${"%.1f".format(state.config.gain)}x", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = state.config.gain,
                    onValueChange = onGainChange,
                    valueRange = 0f..3f,
                    colors = SliderDefaults.colors(
                        thumbColor = MintAccent,
                        activeTrackColor = MintAccent,
                        inactiveTrackColor = PillSelected
                    )
                )

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Noise gate", color = TextPrimary)
                    val gateLabel = if (state.config.noiseGateThreshold <= 0.005f) "Off"
                    else "${(state.config.noiseGateThreshold / 0.25f * 100).toInt()}%"
                    Text(gateLabel, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = state.config.noiseGateThreshold,
                    onValueChange = onNoiseGateChange,
                    valueRange = 0f..0.25f,
                    colors = SliderDefaults.colors(
                        thumbColor = MintAccent,
                        activeTrackColor = MintAccent,
                        inactiveTrackColor = PillSelected
                    )
                )
                Text("Silences sound below this level.", color = TextSecondary, fontSize = 12.sp)

                Spacer(Modifier.height(16.dp))
                Text("Sound preset / Efek Suara", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoundPreset.entries.forEach { p ->
                        RouteChip(
                            label = "${p.iconEmoji} ${p.label}",
                            selected = state.config.preset == p
                        ) {
                            onPresetChange(p)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Latency mode", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RouteChip("Low Latency", state.config.latencyMode == LatencyMode.LOW_LATENCY) { onLatencyChange(LatencyMode.LOW_LATENCY) }
                    RouteChip("Balanced", state.config.latencyMode == LatencyMode.BALANCED) { onLatencyChange(LatencyMode.BALANCED) }
                    RouteChip("Stable", state.config.latencyMode == LatencyMode.STABLE) { onLatencyChange(LatencyMode.STABLE) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Low Latency cuts delay. Stable reduces dropouts.", color = TextSecondary, fontSize = 12.sp)
            }

            // 2. Output Route Card
            SettingsCard {
                Text("Output route", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RouteChip("Music (A2DP)", state.config.outputRoute == OutputRoute.MUSIC_A2DP) { onRouteChange(OutputRoute.MUSIC_A2DP) }
                    RouteChip("Voice (SCO)", state.config.outputRoute == OutputRoute.VOICE_SCO) { onRouteChange(OutputRoute.VOICE_SCO) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.config.outputRoute == OutputRoute.MUSIC_A2DP)
                        "For speakers and stereo headphones."
                    else
                        "For hands-free headsets. SCO may take a few seconds.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            // 3. Output Devices Card
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, null, tint = MintAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Output devices", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onRefreshDevices) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "Wired & USB audio",
                    subtitle = "Show AUX and USB outputs.",
                    checked = state.showWiredAndUsb,
                    onCheckedChange = onToggleWiredUsb
                )
                HorizontalDivider(color = DividerDark, modifier = Modifier.padding(vertical = 8.dp))

                if (state.devices.isEmpty()) {
                    Text(
                        "Pair a Bluetooth device or plug in audio, then refresh.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    state.devices.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (dev.isConnected) onSelectDevice(dev)
                                    else context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = dev.id == state.selectedDevice?.id,
                                onClick = { if (dev.isConnected) onSelectDevice(dev) },
                                colors = RadioButtonDefaults.colors(selectedColor = MintAccent)
                            )
                            Icon(
                                imageVector = if (dev.category.name == "BLUETOOTH") Icons.Default.Bluetooth else Icons.Default.Headphones,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, color = TextPrimary, fontSize = 15.sp)
                                Text(
                                    if (dev.isConnected) "${dev.category.label} • Connected" else dev.category.label,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (!dev.isConnected) {
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }) {
                                    Text("Connect", color = MintAccent)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Behavior Toggles Card
            SettingsCard {
                ToggleRow(
                    title = "Push-to-Talk",
                    subtitle = "Hold to speak. Release to mute.",
                    checked = state.isPttMode,
                    onCheckedChange = { onTogglePtt() }
                )
                HorizontalDivider(color = DividerDark, modifier = Modifier.padding(vertical = 8.dp))
                ToggleRow(
                    title = "Use phone mic",
                    subtitle = "Use the built-in mic in Voice (SCO) mode.",
                    checked = state.config.usePhoneMicInSco,
                    onCheckedChange = onToggleUsePhoneMic
                )
                HorizontalDivider(color = DividerDark, modifier = Modifier.padding(vertical = 8.dp))
                ToggleRow(
                    title = "Echo / noise suppression",
                    subtitle = "Helps reduce feedback.",
                    checked = state.config.echoNoiseSuppression,
                    onCheckedChange = onToggleEchoSuppression
                )
            }

            // 5. Help & App Info Card
            SettingsCard {
                Text("Help & App Info", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))

                InfoActionRow(
                    icon = Icons.Default.HelpOutline,
                    title = "User Guide",
                    onClick = {
                        activeDialogTitle = "User Guide"
                        activeDialogBody = "1. Hubungkan speaker Bluetooth, AUX, atau USB audio.\n" +
                            "2. Pilih perangkat dari menu Output devices.\n" +
                            "3. Tekan Start (atau tahan tombol jika mode PTT aktif).\n" +
                            "4. Jauhkan HP dari speaker untuk mencegah feedback, atau aktifkan Echo / noise suppression dan Noise gate."
                    }
                )
                HorizontalDivider(color = DividerDark)

                InfoActionRow(
                    icon = Icons.Default.Security,
                    title = "Privacy Policy",
                    onClick = {
                        activeDialogTitle = "Privacy Policy"
                        activeDialogBody = "Seluruh pemrosesan audio dilakukan 100% secara lokal di perangkat Anda. Aplikasi tidak mengirim atau mengunggah rekaman suara ke server mana pun. File WAV hanya disimpan di penyimpanan lokal saat Anda menekan Save WAV."
                    }
                )
                HorizontalDivider(color = DividerDark)

                InfoActionRow(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Battery settings",
                    subtitle = "Review limits on background audio.",
                    onClick = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        runCatching { context.startActivity(intent) }.onFailure {
                            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(fallback)
                        }
                    }
                )
                HorizontalDivider(color = DividerDark)

                InfoActionRow(
                    icon = Icons.Default.Share,
                    title = "Share App",
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Mic to Speaker — Low-latency live microphone & WAV recorder.")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share App"))
                    }
                )
                HorizontalDivider(color = DividerDark)

                InfoActionRow(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "Version 1.0.0 • C++ Oboe Engine",
                    showChevron = false,
                    onClick = {}
                )
            }
        }
    }

    if (activeDialogTitle != null) {
        AlertDialog(
            onDismissRequest = { activeDialogTitle = null },
            containerColor = CardDark,
            title = { Text(activeDialogTitle ?: "", color = TextPrimary) },
            text = { Text(activeDialogBody, color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { activeDialogTitle = null }) {
                    Text("OK", color = MintAccent)
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardDark)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnMint,
                checkedTrackColor = MintAccent
            )
        )
    }
}

@Composable
private fun InfoActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = showChevron, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MintAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
        if (showChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
