package app.neara.android.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Default Clean Light Theme Palette
val BgDark = Color(0xFFF8FAFC)       // Soft clean canvas
val BgBottomNav = Color(0xFFFFFFFF)  // Navigation bar
val BgCard = Color(0xFFFFFFFF)       // Crisp white cards
val BgSurface = Color(0xFFF1F5F9)    // Inputs and secondary surfaces
val BorderSubtle = Color(0xFFE2E8F0) // Clean slate border

val TextPrimary = Color(0xFF0F172A)   // Deep slate for high contrast
val TextSecondary = Color(0xFF475569) // Mid slate for subheadings
val TextMuted = Color(0xFF64748B)     // Light slate for captions

val AccentEmerald = Color(0xFF059669) // Modern vibrant emerald
val AccentCyan = Color(0xFF0284C7)    // Sky/Cyan accent
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
fun NearaMobileTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = BgCard.toArgb()
                window.navigationBarColor = BgBottomNav.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}


