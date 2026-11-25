package com.example.mictospeaker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mictospeaker.audio.OutputRoute
import com.example.mictospeaker.data.OutputDeviceItem
import com.example.mictospeaker.ui.theme.*
import com.example.mictospeaker.ui.viewmodel.MicUiState
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MicUiState,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleStart: () -> Unit,
    onPttPressChange: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onTogglePtt: () -> Unit,
    onToggleSaveWav: () -> Unit,
    onGainChange: (Float) -> Unit,
    onRouteChange: (OutputRoute) -> Unit,
    onSelectDevice: (OutputDeviceItem) -> Unit,
    onRefreshDevices: () -> Unit
) {
    var showDeviceSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Mic to Speaker - NFL", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!state.hasPermissions) {
                Surface(
                    onClick = onRequestPermissions,
                    shape = CircleShape,
                    color = PillSelected,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant permissions", color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (state.selectedDevice != null) "Ready on ${state.selectedDevice.name}"
                else "Choose an output device to begin",
                color = TextSecondary,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Output devices",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // Dropdown Device Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .clickable { showDeviceSheet = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bluetooth, null, tint = MintAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = state.selectedDevice?.name ?: "Choose device",
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSecondary)
            }

            Spacer(Modifier.height(28.dp))

            // 30-bar Real-time Visualizer
            AudioVisualizer(
                rmsLevel = if (state.isStreaming) state.rmsLevel else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )

            Spacer(Modifier.weight(1f))

            // Tombol Start / Hold-to-Talk Utama
            val activeHighlight = state.isStreaming && (!state.isPttMode || state.isPttPressed)
            Box(
                modifier = Modifier
                    .size(164.dp)
                    .clip(CircleShape)
                    .background(if (activeHighlight) MintAccent else MintAccent.copy(alpha = 0.88f))
                    .pointerInput(state.isPttMode) {
                        detectTapGestures(
                            onPress = {
                                if (state.isPttMode) {
                                    onPttPressChange(true)
                                    tryAwaitRelease()
                                    onPttPressChange(false)
                                }
                            },
                            onTap = {
                                if (!state.isPttMode) onToggleStart()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.config.isMuted && state.isStreaming) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mic Toggle",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    state.isPttMode && state.isPttPressed -> "Speaking..."
                    state.isPttMode -> "Hold to Talk"
                    state.isStreaming -> "Stop"
                    else -> "Start"
                },
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.weight(1f))

            // Baris Kontrol Cepat: Mute | PTT | Save WAV
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickChipButton(
                    icon = Icons.Default.VolumeOff,
                    label = "Mute",
                    selected = state.config.isMuted,
                    onClick = onToggleMute,
                    modifier = Modifier.weight(1f)
                )
                QuickChipButton(
                    icon = Icons.Default.TouchApp,
                    label = "PTT",
                    selected = state.isPttMode,
                    onClick = onTogglePtt,
                    modifier = Modifier.weight(1f)
                )
                QuickChipButton(
                    icon = Icons.Default.FiberManualRecord,
                    label = if (state.isRecordingWav) "Stop WAV" else "Save WAV",
                    selected = state.isRecordingWav,
                    iconTint = if (state.isRecordingWav) Color(0xFFFF6B6B) else MintAccent,
                    onClick = onToggleSaveWav,
                    modifier = Modifier.weight(1.25f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Quick Gain Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardDark)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tune, null, tint = MintAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Gain ${"%.1f".format(state.config.gain)}x",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.width(72.dp)
                )
                Slider(
                    value = state.config.gain,
                    onValueChange = onGainChange,
                    valueRange = 0f..3f,
                    colors = SliderDefaults.colors(
                        thumbColor = MintAccent,
                        activeTrackColor = MintAccent,
                        inactiveTrackColor = PillSelected
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDeviceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDeviceSheet = false },
            containerColor = SheetDark
        ) {
            OutputDeviceSheetContent(
                selectedRoute = state.config.outputRoute,
                devices = state.devices,
                selectedDeviceId = state.selectedDevice?.id,
                onRouteChange = onRouteChange,
                onSelectDevice = {
                    onSelectDevice(it)
                    showDeviceSheet = false
                },
                onRefresh = onRefreshDevices
            )
        }
    }
}

@Composable
private fun QuickChipButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    iconTint: Color = if (selected) MintAccent else TextSecondary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PillSelected else Color.Transparent)
            .border(1.dp, if (selected) MintAccent else Color(0xFF323534), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) MintAccent else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun AudioVisualizer(rmsLevel: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val bars = 30
        val barWidth = 4.dp.toPx()
        val gap = (size.width - bars * barWidth) / (bars - 1)
        val center = bars / 2f

        for (i in 0 until bars) {
            val distance = abs(i - center) / center
            val weight = (1f - distance * 0.7f).coerceAtLeast(0.2f)
            val barHeight = (size.height * (0.18f + rmsLevel * 0.82f * weight))
                .coerceIn(6.dp.toPx(), size.height)

            val x = i * (barWidth + gap)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = if (rmsLevel > 0.02f) MintAccent.copy(alpha = 0.4f + weight * 0.6f)
                else Color(0xFF343836),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
fun OutputDeviceSheetContent(
    selectedRoute: OutputRoute,
    devices: List<OutputDeviceItem>,
    selectedDeviceId: String?,
    onRouteChange: (OutputRoute) -> Unit,
    onSelectDevice: (OutputDeviceItem) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card Output Route (A2DP vs SCO)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(16.dp)
        ) {
            Text("Output route", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RouteChip(
                    label = "Music (A2DP)",
                    selected = selectedRoute == OutputRoute.MUSIC_A2DP,
                    onClick = { onRouteChange(OutputRoute.MUSIC_A2DP) }
                )
                RouteChip(
                    label = "Voice (SCO)",
                    selected = selectedRoute == OutputRoute.VOICE_SCO,
                    onClick = { onRouteChange(OutputRoute.VOICE_SCO) }
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (selectedRoute == OutputRoute.MUSIC_A2DP)
                    "For speakers and stereo headphones."
                else
                    "For hands-free headsets.",
                color = TextSecondary,
                fontSize = 13.sp
            )
            if (selectedRoute == OutputRoute.VOICE_SCO) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SCO may take a few seconds.",
                    color = Color(0xFFD99CB5),
                    fontSize = 12.sp
                )
            }
        }

        // Card Daftar Perangkat Output
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, null, tint = MintAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Output devices", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                }
            }

            val activeDevices = devices.filter { it.isConnected }
            if (activeDevices.isEmpty()) {
                Text(
                    "Pair a Bluetooth device or plug in audio, then refresh.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            } else {
                activeDevices.forEach { dev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDevice(dev) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = dev.id == selectedDeviceId,
                            onClick = { onSelectDevice(dev) },
                            colors = RadioButtonDefaults.colors(selectedColor = MintAccent)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(dev.name, color = TextPrimary, fontSize = 15.sp)
                            Text(dev.category.label, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PillSelected else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else Color(0xFF3E3B46), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(Icons.Default.Check, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = TextPrimary, fontSize = 13.sp)
    }
}
