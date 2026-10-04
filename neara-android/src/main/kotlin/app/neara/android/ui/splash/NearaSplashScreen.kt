@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.neara.android.ui


import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

@Composable
fun NearaSplashScreen(
    onSplashFinished: () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color(0xFF060911).toArgb()
                window.navigationBarColor = Color(0xFF04070D).toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }
    // Stage state for animated status text
    var statusText by remember { mutableStateOf("Initializing offline mesh...") }

    // Start auto timer for splash duration
    LaunchedEffect(Unit) {
        delay(600)
        statusText = "Scanning local Wi-Fi & P2P radio..."
        delay(700)
        statusText = "Ready • Encrypted local network"
        delay(600)
        onSplashFinished()
    }

    // Infinite ripple animations
    val infiniteTransition = rememberInfiniteTransition(label = "splashRipple")

    // Ripple 1
    val ripple1Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1Scale"
    )
    val ripple1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1Alpha"
    )

    // Ripple 2 (offset)
    val ripple2Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2Scale"
    )
    val ripple2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2Alpha"
    )

    // Center icon gentle breathing scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "centerPulse"
    )

    // Deep Cyber-Slate Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF060911),
                        Color(0xFF0B1120),
                        Color(0xFF04070D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Background Ambient Mesh Glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.44f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x33059669),
                        Color(0x1A0284C7),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width * 0.75f
                ),
                center = center,
                radius = size.width * 0.75f
            )
        }

        // Center Content Container
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 60.dp)
        ) {
            // Animated Logo with Radar Waves
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Expanding Ring 1
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(ripple1Scale)
                        .clip(CircleShape)
                        .background(Color(0xFF059669).copy(alpha = ripple1Alpha * 0.25f))
                )

                // Expanding Ring 2
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(ripple2Scale)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7).copy(alpha = ripple2Alpha * 0.25f))
                )

                // Central Icon Container
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .scale(pulseScale)
                        .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = Color(0xFF059669), ambientColor = Color(0xFF0284C7))
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF059669),
                                    Color(0xFF0284C7)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = "Neara Logo",
                        tint = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // App Brand Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Neara",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }

            Spacer(Modifier.height(6.dp))

            // Tagline
            Text(
                text = "OFFLINE MESH NETWORK",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.5.sp
            )

            Spacer(Modifier.height(32.dp))

            // Micro-Status Pill with pulsing green dot
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusText,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Bottom Footer (Security & Privacy Badges)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Pure Offline • End-to-End Encrypted",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "v1.0.0",
                color = Color(0xFF475569),
                fontSize = 10.sp
            )
        }
    }
}
