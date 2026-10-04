@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.neara.android.ui




import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import app.neara.android.AndroidAppState
import app.neara.android.AndroidTab
import app.neara.android.ConversationItem
import app.neara.core.model.*
import app.neara.discovery.DiscoveredGroup
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AndroidNearbyScreen(appState: AndroidAppState) {
    val peers by appState.discoveredPeers.collectAsState()
    val nearbyGroups by appState.nearbyGroups.collectAsState()
    val isVisible by appState.isVisible.collectAsState()
    val displayPeers = remember(peers) {
        peers
            .filter { it.connectionState != ConnectionState.DISCONNECTED }
            .distinctBy { it.ipAddress ?: it.peerId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Professional Header Status & Visibility Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isVisible) AccentEmerald.copy(alpha = 0.12f) else BgSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isVisible) Icons.Default.WifiTethering else Icons.Default.WifiTetheringOff,
                        contentDescription = null,
                        tint = if (isVisible) AccentEmerald else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isVisible) "Device Visible" else "Stealth Mode",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isVisible) AccentEmerald else TextMuted)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "LAN • ${appState.localIpAddress} : ${appState.localPort}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            NearaSwitch(
                checked = isVisible,
                onCheckedChange = { appState.toggleVisibility() }
            )
        }

        // Section Title with Live Count Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Nearby Devices",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (displayPeers.isNotEmpty()) AccentEmerald.copy(alpha = 0.15f) else BgSurface)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${displayPeers.size}",
                        color = if (displayPeers.isNotEmpty()) AccentEmerald else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald)
                )
                Spacer(Modifier.width(5.dp))
                Text("Auto-discovering", color = TextMuted, fontSize = 11.sp)
            }
        }

        // Content Area: Empty Radar State or Discovered Peers List
        if (displayPeers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RadarScanningVisual()

                Spacer(Modifier.height(20.dp))

                Text(
                    "Scanning Local Network",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Nearby devices running Neara on this Wi-Fi or hotspot will appear here automatically.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayPeers) { peer ->
                    AndroidPeerCard(peer = peer, onChatClick = { appState.selectConversation(peer) })
                }

                if (nearbyGroups.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nearby Groups & Channels", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentIndigo.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("${nearbyGroups.size}", color = AccentIndigo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(nearbyGroups) { group ->
                        AndroidDiscoveredGroupCard(
                            group = group,
                            onJoin = { appState.joinDiscoveredGroup(group) }
                        )
                    }
                }
            }
        }

        // Bottom Network Navigation Quick Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .clickable { appState.setTab(AndroidTab.NETWORKS) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentIndigo, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Looking for a group channel?", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Create or join with QR code in Networks", color = TextSecondary, fontSize = 10.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun RadarScanningVisual() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.size(130.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(120.dp * pulseScale)
                .clip(CircleShape)
                .background(AccentEmerald.copy(alpha = pulseAlpha))
        )
        // Middle ring
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(AccentEmerald.copy(alpha = 0.05f))
                .border(1.dp, AccentEmerald.copy(alpha = 0.18f), CircleShape)
        )
        // Center icon circle
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(AccentEmerald.copy(alpha = 0.12f))
                .border(1.5.dp, AccentEmerald, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Radar,
                contentDescription = null,
                tint = AccentEmerald,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
fun AndroidPeerCard(peer: Peer, onChatClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .clickable { onChatClick() }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(AccentEmerald.copy(alpha = 0.12f))
                    .border(1.5.dp, AccentEmerald.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(peer.displayName.take(1).uppercase(), color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(peer.displayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("${peer.ipAddress ?: "Direct"} • ${peer.peerId.take(12)}", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        Button(
            onClick = onChatClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text("Message", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AndroidDiscoveredGroupCard(
    group: app.neara.discovery.DiscoveredGroup,
    onJoin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable { onJoin() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(AccentIndigo.copy(alpha = 0.15f))
                    .border(1.dp, AccentIndigo.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Hub, contentDescription = null, tint = AccentIndigo, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(group.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Hosted by ${group.hostDisplayName} • ${group.type.name}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Button(
            onClick = onJoin,
            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text("Join & Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
