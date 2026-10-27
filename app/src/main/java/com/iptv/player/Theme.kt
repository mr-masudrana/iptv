package com.iptv.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Broadcast-desk palette: midnight base, slate panels, signal-amber accent, tally red for "live".
val TallyRed = Color(0xFFFF4D5E)
val HeartPink = Color(0xFFFF5C7A)
val LogoTile = Color(0xFF0E1527)

private val Colors = darkColorScheme(
    primary = Color(0xFFFFB547),
    onPrimary = Color(0xFF1A1200),
    secondary = Color(0xFF7FD6C2),
    background = Color(0xFF0A1020),
    onBackground = Color(0xFFE9EDF7),
    surface = Color(0xFF121A2E),
    onSurface = Color(0xFFE9EDF7),
    surfaceVariant = Color(0xFF1A2440),
    onSurfaceVariant = Color(0xFF98A3BF),
    outline = Color(0xFF2A3556),
    error = Color(0xFFFF6B7A)
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}
