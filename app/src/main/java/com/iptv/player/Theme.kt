package com.iptv.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light palette: cool paper background, white panels, blue accent, tally red for "live".
val TallyRed = Color(0xFFFF4D5E)
val HeartPink = Color(0xFFFF5C7A)
val LogoTile = Color(0xFFEEF1F7)

// Lighter blue for controls drawn over the (dark) video
val PlayerAccent = Color(0xFF5B9BFF)

private val Colors = lightColorScheme(
    primary = Color(0xFF1D5FE0),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF1F8F7A),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF0A1020),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A1020),
    surfaceVariant = Color(0xFFE8ECF4),
    onSurfaceVariant = Color(0xFF5A6480),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFD2D8E5),
    error = Color(0xFFD93A4C)
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}
