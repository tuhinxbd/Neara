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
fun AndroidSettingsScreen(appState: AndroidAppState) {
    val isVisible by appState.isVisible.collectAsState()
    val meshRelay by appState.meshRelayEnabled.collectAsState()
    val autoAccept by appState.autoAcceptFiles.collectAsState()
    val offlineCount by appState.offlinePendingCount.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showPurgeQueueDialog by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Settings & Protocols", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Manage networking, mesh routing & storage", color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        if (statusFeedback != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentEmerald.copy(alpha = 0.15f))
                        .border(1.dp, AccentEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(statusFeedback ?: "", color = AccentEmerald, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Section 1: Local Network & Discovery
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiTethering, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Network & Discovery", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Visible to Nearby Peers", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(if (isVisible) "Broadcasting discovery beacons" else "Stealth mode active", color = TextSecondary, fontSize = 11.sp)
                    }
                    NearaSwitch(
                        checked = isVisible,
                        onCheckedChange = { appState.toggleVisibility(it) }
                    )
                }

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Multicast Discovery:", color = TextMuted, fontSize = 12.sp)
                    Text("239.255.60.60 : 45780 (UDP)", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Local TCP Transport:", color = TextMuted, fontSize = 12.sp)
                    Text("${appState.localIpAddress} : ${appState.localPort}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Section 2: Mesh Routing
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mesh Routing & Relay", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Packet Relay Node", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Forward mesh frames for multi-hop peers", color = TextSecondary, fontSize = 11.sp)
                    }
                    NearaSwitch(
                        checked = meshRelay,
                        onCheckedChange = { appState.toggleMeshRelay(it) },
                        activeColor = AccentCyan
                    )
                }

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Max Hop Limit (TTL):", color = TextMuted, fontSize = 12.sp)
                    Text("5 Hops (Loop-Suppressed)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Section 3: Storage & Offline Queue
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Storage & Offline Queue", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto-Accept Small Files", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Accept transfers under 10MB automatically", color = TextSecondary, fontSize = 11.sp)
                    }
                    NearaSwitch(
                        checked = autoAccept,
                        onCheckedChange = { appState.toggleAutoAcceptFiles(it) }
                    )
                }

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Offline Message Queue", color = TextPrimary, fontSize = 13.sp)
                        Text("$offlineCount messages waiting for peers", color = TextSecondary, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { showPurgeQueueDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                    ) {
                        Text("Purge", fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showClearHistoryDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear All Local Messages", fontSize = 12.sp)
                }
            }
        }

        // Section 4: About Neara & Developer
        item {
            val context = LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("About Neara", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(Modifier.height(8.dp))
                Text("Neara v1.0.0", color = AccentEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Pure offline-first nearby communication & mesh routing over Wi-Fi Direct, Hotspot & LAN without cloud or internet.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 12.dp))

                // Developer info as clean, simple text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Developer", color = TextSecondary, fontSize = 13.sp)
                    Text("Tuhinx", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GitHub", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        "tuhinxbd",
                        color = AccentEmerald,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tuhinxbd")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Facebook", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        "tuhinxbd",
                        color = AccentEmerald,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://facebook.com/tuhinxbd")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Chat History?", color = TextPrimary) },
            text = { Text("All local conversation history will be permanently deleted.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        appState.clearChatHistory()
                        showClearHistoryDialog = false
                        statusFeedback = "Chat history cleared successfully"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }

    if (showPurgeQueueDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeQueueDialog = false },
            title = { Text("Purge Offline Queue?", color = TextPrimary) },
            text = { Text("All pending messages waiting for offline peers will be discarded.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        appState.purgeOfflineQueue()
                        showPurgeQueueDialog = false
                        statusFeedback = "Offline queue purged"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Purge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeQueueDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }
}
