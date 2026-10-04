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
fun AndroidNetworksScreen(appState: AndroidAppState) {
    val rawNetworks by appState.activeNetworks.collectAsState()
    val rawNearbyGroups by appState.nearbyGroups.collectAsState()
    val networks = remember(rawNetworks) {
        rawNetworks.filter { !appState.isNetworkDismissed(it.networkId) }
    }
    val nearbyGroups = remember(rawNearbyGroups) {
        rawNearbyGroups.filter { !appState.isNetworkDismissed(it.networkId) }
    }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedQrUri by remember { mutableStateOf<String?>(null) }
    var networkToDeleteOrLeave by remember { mutableStateOf<Network?>(null) }
    var netName by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(BgDark).padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Local Networks", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Create", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Discovered Groups from nearby devices on local network
            if (nearbyGroups.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nearby Active Channels", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("My Hosted Networks", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (networks.isEmpty() && nearbyGroups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No networks created yet. Click '+ Create' to host a local channel.", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(networks) { net ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { appState.selectNetworkConversation(net) },
                                onLongClick = { networkToDeleteOrLeave = net }
                            )
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AccentIndigo.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Hub, contentDescription = null, tint = AccentIndigo, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(net.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.height(2.dp))
                                Text("${net.type.name} • ${net.members.size} members • Tap to chat, long press to manage", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { selectedQrUri = appState.networkManager.generateQrInvitationUri(net.networkId) }) {
                            Icon(Icons.Default.QrCode, contentDescription = "QR", tint = AccentCyan)
                        }
                    }
                }
            }
        }
    }

    if (networkToDeleteOrLeave != null) {
        val target = networkToDeleteOrLeave!!
        val isOwner = target.ownerId == appState.localPeerId
        AlertDialog(
            onDismissRequest = { networkToDeleteOrLeave = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentDanger.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = "Warning",
                        tint = AccentDanger,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    if (isOwner) "Delete Network Channel?" else "Leave Network Channel?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (isOwner)
                            "Warning: You are the host of \"${target.name}\". Deleting this channel will permanently remove it from your device and close it for all connected members."
                        else
                            "Warning: Are you sure you want to leave \"${target.name}\"? You will exit this channel and lose access to group updates.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "⚠️ This action cannot be undone.",
                        color = AccentDanger.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val netId = target.networkId
                        networkToDeleteOrLeave = null
                        if (isOwner) {
                            appState.deleteGroup(netId)
                        } else {
                            appState.leaveGroup(netId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDanger),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isOwner) "Delete Channel" else "Leave Channel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { networkToDeleteOrLeave = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Network", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = netName,
                        onValueChange = { netName = it },
                        label = { Text("Network Name") }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                        Text("Private (PIN protected)", color = TextPrimary, fontSize = 13.sp)
                    }
                    if (isPrivate) {
                        TextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (netName.isNotBlank()) {
                        val newNet = appState.createNetwork(
                            name = netName.trim(),
                            type = if (isPrivate) NetworkType.PRIVATE else NetworkType.PUBLIC,
                            pin = if (isPrivate) pin.trim() else null
                        )
                        netName = ""
                        pin = ""
                        showCreateDialog = false
                        appState.selectNetworkConversation(newNet)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)) {
                    Text("Create & Open Chat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = BgCard
        )
    }

    val currentQrUri = selectedQrUri
    if (currentQrUri != null) {
        val qrBitmap = remember(currentQrUri) { generateAndroidQrBitmap(currentQrUri) }
        AlertDialog(
            onDismissRequest = { selectedQrUri = null },
            title = { Text("Scan QR to Join", color = TextPrimary) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(8.dp)
                    ) {
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedQrUri = null }, colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)) {
                    Text("Close")
                }
            },
            containerColor = BgCard
        )
    }
}
