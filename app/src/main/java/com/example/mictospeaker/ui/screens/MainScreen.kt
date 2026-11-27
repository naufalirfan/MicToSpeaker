package com.example.mictospeaker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mictospeaker.audio.OutputRoute
import com.example.mictospeaker.audio.SoundPreset
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
    onPresetChange: (SoundPreset) -> Unit,
    onRouteChange: (OutputRoute) -> Unit,
    onSelectDevice: (OutputDeviceItem) -> Unit,
    onRefreshDevices: () -> Unit,
    onExit: () -> Unit = {},
    onMinimize: () -> Unit = {}
) {
    var showDeviceSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text("Mic to Speaker", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF1C152E).copy(alpha = 0.85f))
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = TextSecondary)
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF1C152E).copy(alpha = 0.85f))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = onExit,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF2E121E).copy(alpha = 0.85f))
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Exit", tint = Color(0xFFFF5277))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF381B5E).copy(alpha = 0.35f),
                            Color(0xFF1B0E33).copy(alpha = 0.15f),
                            BgDark
                        ),
                        center = Offset(300f, 160f),
                        radius = 850f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!state.hasPermissions) {
                    Surface(
                        onClick = onRequestPermissions,
                        shape = CircleShape,
                        color = PillSelected,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .border(1.dp, CardBorder, CircleShape)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = NeonPink, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Grant permissions", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    text = if (state.selectedDevice != null) "Ready on ${state.selectedDevice.name}"
                    else "Choose an output device to begin",
                    color = TextSecondary,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Output devices",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // Dropdown Device Selector (Card styling with subtle purple border)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardDark)
                        .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                        .clickable { showDeviceSheet = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PillSelected),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bluetooth, null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.selectedDevice?.name ?: "Choose device",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = state.selectedDevice?.category?.label ?: "Tap to select audio route",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = TextSecondary)
                }

                Spacer(Modifier.height(24.dp))

                // Real-time Neon Wave Visualizer
                AudioVisualizer(
                    rmsLevel = if (state.isStreaming) state.rmsLevel else 0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )

                Spacer(Modifier.height(18.dp))

                // Pemilih Efek Suara Cepat (Voice Effects)
                Text(
                    text = "Voice Effect: ${state.config.preset.iconEmoji} ${state.config.preset.label}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoundPreset.entries.forEach { p ->
                        RouteChip(
                            label = "${p.iconEmoji} ${p.label}",
                            selected = state.config.preset == p,
                            onClick = { onPresetChange(p) }
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Tombol Start / Hold-to-Talk Utama (Neon Glow Circle)
                val activeHighlight = state.isStreaming && (!state.isPttMode || state.isPttPressed)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    // Outer ambient radial glow halo
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(
                                if (activeHighlight) Brush.radialGradient(
                                    listOf(Color(0xFFEC4899).copy(alpha = 0.45f), Color.Transparent)
                                ) else Brush.radialGradient(
                                    listOf(Color(0xFFA855F7).copy(alpha = 0.22f), Color.Transparent)
                                )
                            )
                    )

                    // Main circular button
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(if (activeHighlight) MicActiveGradient else MicIdleGradient)
                            .border(
                                2.dp,
                                if (activeHighlight) Color(0xFFF472B6) else Color(0xFFA855F7).copy(alpha = 0.65f),
                                CircleShape
                            )
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
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        state.isPttMode && state.isPttPressed -> "Speaking..."
                        state.isPttMode -> "Hold to Talk"
                        state.isStreaming -> "Stop"
                        else -> "Start"
                    },
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(18.dp))

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
                        iconTint = if (state.isRecordingWav) Color(0xFFFF4D4D) else NeonPurple,
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
                        .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PillSelected),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tune, null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Gain ${"%.1f".format(state.config.gain)}x",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(76.dp)
                    )
                    Slider(
                        value = state.config.gain,
                        onValueChange = onGainChange,
                        valueRange = 0f..3f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonPurple,
                            activeTrackColor = NeonPurple,
                            inactiveTrackColor = Color(0xFF261F38)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
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
    iconTint: Color = if (selected) Color.White else TextSecondary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgModifier = if (selected) {
        Modifier
            .background(PrimaryGradient)
            .border(1.dp, Color(0xFFE879F9).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
    } else {
        Modifier
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (selected) Color.White else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun AudioVisualizer(rmsLevel: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val bars = 32
        val barWidth = 3.5.dp.toPx()
        val gap = (size.width - bars * barWidth) / (bars - 1)
        val center = bars / 2f

        for (i in 0 until bars) {
            val distance = abs(i - center) / center
            val weight = (1f - distance * 0.68f).coerceAtLeast(0.2f)
            val barHeight = (size.height * (0.16f + rmsLevel * 0.84f * weight))
                .coerceIn(5.dp.toPx(), size.height)

            val x = i * (barWidth + gap)
            val y = (size.height - barHeight) / 2f

            drawRoundRect(
                brush = if (rmsLevel > 0.02f) VisualizerGradient
                else Brush.linearGradient(listOf(Color(0xFF261F38), Color(0xFF261F38))),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                alpha = if (rmsLevel > 0.02f) (0.55f + weight * 0.45f) else 0.45f
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
                .clip(RoundedCornerShape(18.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text("Output route", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
                    color = Color(0xFFE879F9),
                    fontSize = 12.sp
                )
            }
        }

        // Card Daftar Perangkat Output
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardDark)
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PillSelected),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bluetooth, null, tint = NeonPurple, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Output devices", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                }
            }

            val activeDevices = devices.filter { it.isConnected }
            if (activeDevices.isEmpty()) {
                Spacer(Modifier.height(8.dp))
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
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectDevice(dev) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = dev.id == selectedDeviceId,
                            onClick = { onSelectDevice(dev) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = NeonPurple,
                                unselectedColor = TextMuted
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(dev.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(dev.category.label, color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgModifier = if (selected) {
        Modifier
            .background(PrimaryGradient)
            .border(1.dp, Color(0xFFE879F9).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    } else {
        Modifier
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(bgModifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (selected) Color.White else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
