package app.neara.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Default Clean Light Theme Palette
val BgDark = Color(0xFFF8FAFC)       // Soft clean background
val BgSidebar = Color(0xFFFFFFFF)    // Crisp white sidebar
val BgCard = Color(0xFFFFFFFF)       // Crisp white cards
val BgSurface = Color(0xFFF1F5F9)    // Inputs and secondary surfaces
val BgCardHover = Color(0xFFF8FAFC)
val BorderSubtle = Color(0xFFE2E8F0) // Clean slate border

val TextPrimary = Color(0xFF0F172A)   // Deep slate
val TextSecondary = Color(0xFF475569) // Mid slate
val TextMuted = Color(0xFF64748B)     // Light slate

val AccentEmerald = Color(0xFF059669) // Modern vibrant emerald
val AccentCyan = Color(0xFF0284C7)    // Sky/Cyan
val AccentIndigo = Color(0xFF4F46E5)
val AccentPurple = Color(0xFF7C3AED)
val AccentDanger = Color(0xFFDC2626)
val AccentWarning = Color(0xFFD97706)

private val LightColorScheme = lightColorScheme(
    primary = AccentEmerald,
    secondary = AccentCyan,
    background = BgDark,
    surface = BgCard,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun NearaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
