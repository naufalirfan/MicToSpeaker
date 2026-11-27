package com.example.mictospeaker.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Obsidian violet-black background & surfaces
val BgDark = Color(0xFF0C0A14)
val SurfaceDark = Color(0xFF151024)
val SheetDark = Color(0xFF130E20)
val CardDark = Color(0xFF1A142C)
val CardBorder = Color(0xFF2C2248)
val PillSelected = Color(0xFF38265E)
val DividerDark = Color(0xFF251C3B)

// Neon & vibrant purple/magenta accents
val NeonPurple = Color(0xFFA855F7)
val NeonPink = Color(0xFFEC4899)
val ElectricViolet = Color(0xFF8B5CF6)
val DeepViolet = Color(0xFF581C87)
val CyanGlow = Color(0xFF38BDF8)

// Gradients
val PrimaryGradient = Brush.horizontalGradient(
    listOf(Color(0xFF8B5CF6), Color(0xFFC026D3), Color(0xFFEC4899))
)
val MicActiveGradient = Brush.linearGradient(
    listOf(Color(0xFFEC4899), Color(0xFF9333EA))
)
val MicIdleGradient = Brush.linearGradient(
    listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))
)
val VisualizerGradient = Brush.horizontalGradient(
    listOf(Color(0xFFEC4899), Color(0xFFA855F7), Color(0xFF6366F1), Color(0xFF38BDF8))
)

// Backward-compatible alias
val MintAccent = NeonPurple
val OnMint = Color(0xFFFFFFFF)

// Typography
val TextPrimary = Color(0xFFF5F3F8)
val TextSecondary = Color(0xFFA59FB5)
val TextMuted = Color(0xFF706984)
