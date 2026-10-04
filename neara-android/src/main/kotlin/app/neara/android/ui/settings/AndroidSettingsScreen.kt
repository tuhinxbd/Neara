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
    val context = LocalContext.current
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showPurgeQueueDialog by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }
    var showProfileModal by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var newDisplayName by remember(appState.localPeer.displayName) { mutableStateOf(appState.localPeer.displayName) }

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

        // Section 0: User Profile & Cryptographic Identity
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showProfileModal = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald.copy(alpha = 0.18f))
                            .border(1.5.dp, AccentEmerald, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            appState.localPeer.displayName.take(1).uppercase(),
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                appState.localPeer.displayName,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Online", color = AccentEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "ID: ${appState.localPeerId.take(16)}...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Profile, Keys & Cryptography",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Profile Details",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
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

    // Full Profile & Cryptography Modal
    if (showProfileModal) {
        Dialog(
            onDismissRequest = { showProfileModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = BgDark
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showProfileModal = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Profile & Cryptography",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Profile Identity Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(AccentEmerald.copy(alpha = 0.15f))
                                .border(2.dp, AccentEmerald, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                appState.localPeer.displayName.take(1).uppercase(),
                                color = AccentEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                appState.localPeer.displayName,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { showEditNameDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = AccentEmerald, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("Peer ID: ${appState.localPeerId}", color = TextSecondary, fontSize = 12.sp)

                        Spacer(Modifier.height(16.dp))

                        // QR Code representation
                        val qrBitmap = remember(appState.localPeer.publicKeyHex) {
                            try {
                                generateAndroidQrBitmap("neara://${appState.localPeerId}:${appState.localPeer.publicKeyHex}")
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Identity QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Scan to verify cryptographic identity", color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Public Key & Endpoints
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Text("Cryptographic Details", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))

                        // Ed25519 Public Key
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ed25519 Signing Public Key:", color = TextMuted, fontSize = 11.sp)
                                Text(
                                    appState.localPeer.publicKeyHex,
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("Neara Public Key", appState.localPeer.publicKeyHex))
                                    Toast.makeText(context, "Public key copied!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = AccentEmerald, modifier = Modifier.size(18.dp))
                            }
                        }

                        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 12.dp))

                        Text("Transport Endpoints", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("• Local IP: ${appState.localIpAddress}", color = TextSecondary, fontSize = 12.sp)
                        Text("• TCP Port: ${appState.localPort}", color = TextSecondary, fontSize = 12.sp)
                        Text("• UDP Discovery: 239.255.60.60:45780", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Edit Display Name Dialog
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Display Name", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newDisplayName,
                    onValueChange = { newDisplayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = BorderSubtle
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newDisplayName.trim()
                        if (trimmed.isNotEmpty()) {
                            appState.localPeer = appState.localPeer.copy(displayName = trimmed)
                            context.getSharedPreferences("neara_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("display_name", trimmed)
                                .apply()
                            appState.refreshConversations()
                        }
                        showEditNameDialog = false
                    }
                ) {
                    Text("Save", color = AccentEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = BgCard
        )
    }
}
