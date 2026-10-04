package app.neara.desktop.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.neara.core.model.*
import app.neara.desktop.state.AppTab
import app.neara.desktop.state.NearaAppState
import app.neara.desktop.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NearbyScreen(appState: NearaAppState) {
    val peers by appState.discoveredPeers.collectAsState()
    val isVisible by appState.isVisible.collectAsState()
    val networks by appState.activeNetworks.collectAsState()
    val displayPeers = remember(peers) {
        peers
            .filter { it.connectionState != ConnectionState.DISCONNECTED }
            .distinctBy { it.ipAddress ?: it.peerId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
    ) {
        // Top Header with Local Mode Active & Visibility Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Local mode active",
                        color = AccentEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "LAN ${appState.localIpAddress} : ${appState.localPort}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            // Visibility Toggle Card
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                    .clickable { appState.toggleVisibility() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isVisible) "Visible to Peers" else "Invisible (Silent)",
                    color = if (isVisible) AccentEmerald else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = isVisible,
                    onCheckedChange = { appState.toggleVisibility() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentEmerald,
                        checkedTrackColor = AccentEmerald.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = BorderSubtle
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Nearby Users (${displayPeers.size})",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))

        if (displayPeers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard.copy(alpha = 0.5f))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AccentCyan,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Listening on UDP 239.255.60.60:45780...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        "Nearby devices on your Wi-Fi, hotspot, or LAN will appear here instantly.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayPeers) { peer ->
                    PeerCard(peer = peer, onChatClick = { appState.selectConversation(peer) })
                }
            }
        }
    }
}

@Composable
fun PeerCard(peer: Peer, onChatClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .clickable { onChatClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentIndigo.copy(alpha = 0.2f))
                    .border(1.5.dp, AccentIndigo, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    peer.displayName.take(1).uppercase(),
                    color = AccentIndigo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        peer.displayName,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            peer.proximityEstimate.name,
                            color = AccentEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${peer.ipAddress ?: "Direct"} : ${peer.port} • ID: ${peer.peerId}",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Button(
            onClick = onChatClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Chat", fontSize = 13.sp)
        }
    }
}

@Composable
fun ChatScreen(appState: NearaAppState) {
    val activePeer by appState.activeConversationPeer.collectAsState()
    val messages by appState.currentMessages.collectAsState()
    val offlinePending by appState.offlinePendingCount.collectAsState()
    val isPttActive by appState.isPttActive.collectAsState()
    var textInput by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Active Chat Area
        if (activePeer == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Select a nearby peer to start chatting", color = TextSecondary, fontSize = 15.sp)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Chat Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AccentCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                activePeer!!.displayName.take(1).uppercase(),
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                activePeer!!.displayName,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Connected • ${activePeer!!.ipAddress ?: "Direct"} : ${activePeer!!.port}",
                                color = AccentEmerald,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Push to talk button in header
                    Button(
                        onClick = {
                            if (isPttActive) appState.stopPtt() else appState.startPtt()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPttActive) AccentDanger else AccentIndigo
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            if (isPttActive) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPttActive) "Recording Audio..." else "Push To Talk", fontSize = 13.sp)
                    }
                }

                // Offline Queue Indicator Banner
                if (offlinePending > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentWarning.copy(alpha = 0.15f))
                            .border(1.dp, AccentWarning.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.HourglassTop, contentDescription = null, tint = AccentWarning, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$offlinePending message(s) queued locally. Will deliver when peer reconnects.",
                            color = AccentWarning,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Messages Stream
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        val isSelf = msg.senderId == appState.localPeerId
                        val senderName = appState.getSenderDisplayName(msg.senderId)
                        MessageBubble(msg = msg, isSelf = isSelf, senderName = senderName)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Type message (encrypted local-first)...", color = TextMuted, fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = {
                        if (textInput.isNotBlank()) {
                            appState.sendTextMessage(textInput.trim())
                            textInput = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = AccentEmerald)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    msg: ChatMessage,
    isSelf: Boolean,
    senderName: String = if (isSelf) "You" else "Peer"
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(msg.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isSelf) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AccentEmerald.copy(alpha = 0.15f))
                    .border(1.dp, AccentEmerald.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    senderName.take(1).uppercase(),
                    color = AccentEmerald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isSelf) 14.dp else 2.dp,
                        bottomEnd = if (isSelf) 2.dp else 14.dp
                    )
                )
                .background(if (isSelf) AccentEmerald.copy(alpha = 0.25f) else BgCard)
                .border(
                    1.dp,
                    if (isSelf) AccentEmerald.copy(alpha = 0.5f) else BorderSubtle,
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (!isSelf) {
                Text(
                    senderName,
                    color = AccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
            }

            Text(
                msg.payload,
                color = TextPrimary,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    timeStr,
                    color = TextMuted,
                    fontSize = 11.sp
                )
                if (isSelf) {
                    Spacer(Modifier.width(6.dp))
                    val statusText = when (msg.status) {
                        MessageStatus.PENDING -> "⏱ Pending"
                        MessageStatus.SENDING -> "⏳ Sending"
                        MessageStatus.SENT -> "✓ Sent"
                        MessageStatus.DELIVERED -> "✓✓ Delivered"
                        MessageStatus.READ -> "✓✓ Read"
                        MessageStatus.FAILED -> "⚠ Failed"
                    }
                    Text(
                        statusText,
                        color = if (msg.status == MessageStatus.DELIVERED) AccentEmerald else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun NetworksScreen(appState: NearaAppState) {
    val networks by appState.activeNetworks.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedQrInviteUri by remember { mutableStateOf<String?>(null) }

    var networkNameInput by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Local Networks", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Create public local channels or encrypted private networks with QR codes", color = TextSecondary, fontSize = 13.sp)
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create Network")
            }
        }

        Spacer(Modifier.height(20.dp))

        if (networks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No networks created yet. Click 'Create Network' to host a local channel.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(networks) { net ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(net.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (net.type == NetworkType.PUBLIC) AccentEmerald.copy(alpha = 0.2f) else AccentPurple.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(net.type.name, color = if (net.type == NetworkType.PUBLIC) AccentEmerald else AccentPurple, fontSize = 11.sp)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${net.members.size} members • Owner: ${net.ownerId}", color = TextMuted, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                selectedQrInviteUri = appState.networkManager.generateQrInvitationUri(net.networkId)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Invite QR")
                        }
                    }
                }
            }
        }
    }

    // Create Network Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Local Network", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = networkNameInput,
                        onValueChange = { networkNameInput = it },
                        label = { Text("Network Name (e.g. University Event)") },
                        colors = TextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                        Text("Private (Requires PIN & Approval)", color = TextPrimary)
                    }
                    if (isPrivate) {
                        TextField(
                            value = pinInput,
                            onValueChange = { pinInput = it },
                            label = { Text("Security PIN / Password") },
                            colors = TextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (networkNameInput.isNotBlank()) {
                            appState.networkManager.createNetwork(
                                name = networkNameInput.trim(),
                                type = if (isPrivate) NetworkType.PRIVATE else NetworkType.PUBLIC,
                                pin = if (isPrivate) pinInput.trim() else null
                            )
                            networkNameInput = ""
                            pinInput = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = BgCard
        )
    }

    // QR Code Modal Dialog
    if (selectedQrInviteUri != null) {
        val qrImage = remember(selectedQrInviteUri) {
            generateQrBitmap(selectedQrInviteUri!!)
        }
        AlertDialog(
            onDismissRequest = { selectedQrInviteUri = null },
            title = { Text("QR Network Invitation", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Nearby devices can scan this QR code to join without plaintext passwords", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = qrImage.toComposeImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedQrInviteUri = null }, colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)) {
                    Text("Close")
                }
            },
            containerColor = BgCard
        )
    }
}

@Composable
fun FilesScreen(appState: NearaAppState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
    ) {
        Text("File Sharing", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Chunked 64KB transfers over direct TCP connections with SHA-256 verification", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(54.dp))
                Spacer(Modifier.height(12.dp))
                Text("No active transfers", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Received files are saved to ~/Downloads/Neara/", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ProfileScreen(appState: NearaAppState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp)
    ) {
        Text("Device Profile & Security", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // Profile Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald.copy(alpha = 0.2f))
                        .border(2.dp, AccentEmerald, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Devices, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(appState.localPeer.displayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Peer ID: ${appState.localPeerId}", color = TextSecondary, fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 16.dp))

            Text("Cryptographic Identity (Ed25519 & X25519)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("Public Key Fingerprint:", color = TextMuted, fontSize = 12.sp)
            Text(appState.localPeer.publicKeyHex.take(48) + "...", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)

            HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 16.dp))

            Text("Network Interface", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("IP: ${appState.localIpAddress} • TCP Port: ${appState.localPort} • Discovery UDP: 45780", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
fun SettingsScreen(appState: NearaAppState) {
    val isVisible by appState.isVisible.collectAsState()
    val meshRelay by appState.meshRelayEnabled.collectAsState()
    val offlineCount by appState.offlinePendingCount.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showPurgeQueueDialog by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Settings & Protocols", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Configure discovery, mesh routing parameters & local storage", color = TextMuted, fontSize = 12.sp)
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
                    Text(statusFeedback ?: "", color = AccentEmerald, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 1. Network & Discovery
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiTethering, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Local Network & Discovery", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Visible to Nearby Peers", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(if (isVisible) "Broadcasting UDP beacons every 3s" else "Stealth mode active (Silent listener)", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isVisible,
                        onCheckedChange = { appState.toggleVisibility(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentEmerald,
                            checkedTrackColor = AccentEmerald.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BgSurface
                        )
                    )
                }

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("UDP Multicast Group:", color = TextMuted, fontSize = 13.sp)
                    Text("239.255.60.60 : 45780", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TCP Framing Transport:", color = TextMuted, fontSize = 13.sp)
                    Text("${appState.localIpAddress} : ${appState.localPort}", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 2. Mesh Routing
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mesh Routing & Packet Forwarding", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Mesh Relay Mode", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Forward mesh frames for multi-hop out-of-range peers", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = meshRelay,
                        onCheckedChange = { appState.toggleMeshRelay(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentCyan,
                            checkedTrackColor = AccentCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BgSurface
                        )
                    )
                }

                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Max TTL / Hop Limit:", color = TextMuted, fontSize = 13.sp)
                    Text("5 Hops (Loop Suppression Enabled)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 3. Storage & Offline Queue
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Storage & Offline Queue", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Offline Queue Status", color = TextPrimary, fontSize = 14.sp)
                        Text("$offlineCount pending message(s) waiting for peers", color = TextSecondary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { showPurgeQueueDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                    ) {
                        Text("Purge Queue", fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showClearHistoryDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear All Local Messages", fontSize = 13.sp)
                }
            }
        }

        // 4. About
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("About Neara Desktop", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(8.dp))
                Text("Neara v1.0.0 • Wire Magic 0x4E454152 (NEAR)", color = AccentEmerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("100% Peer-to-Peer • Zero Internet Required • Pure Local Communication", color = TextSecondary, fontSize = 12.sp)

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
                                if (java.awt.Desktop.isDesktopSupported()) {
                                    java.awt.Desktop.getDesktop().browse(java.net.URI("https://github.com/tuhinxbd"))
                                }
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
                                if (java.awt.Desktop.isDesktopSupported()) {
                                    java.awt.Desktop.getDesktop().browse(java.net.URI("https://facebook.com/tuhinxbd"))
                                }
                            } catch (e: Exception) {}
                        }
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Message History?", color = TextPrimary) },
            text = { Text("All local conversation history will be permanently erased.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        appState.clearChatHistory()
                        showClearHistoryDialog = false
                        statusFeedback = "Local message history cleared"
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
                        statusFeedback = "Offline message queue purged"
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

fun generateQrBitmap(content: String, size: Int = 220): BufferedImage {
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until size) {
        for (y in 0 until size) {
            image.setRGB(x, y, if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return image
}

fun Modifier.scale(scale: Float): Modifier = this
