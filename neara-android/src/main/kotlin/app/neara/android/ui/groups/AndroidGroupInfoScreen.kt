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
fun AddMembersDialog(
    group: Network,
    appState: AndroidAppState,
    onDismiss: () -> Unit
) {
    val discoveredPeers by appState.discoveredPeers.collectAsState()
    val networks by appState.activeNetworks.collectAsState()
    val currentGroup = networks.find { it.networkId == group.networkId } ?: group
    val existingMemberIds = remember(currentGroup.members) { currentGroup.members.map { it.peerId }.toSet() }
    val availablePeersToAdd = remember(discoveredPeers, existingMemberIds) {
        discoveredPeers.filter { !existingMemberIds.contains(it.peerId) }
            .distinctBy { it.ipAddress ?: it.peerId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Members", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                Text(
                    "Nearby active devices on local network:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                if (availablePeersToAdd.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Radar, contentDescription = null, tint = TextMuted, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No new nearby devices found",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Make sure other devices have Neara running on the same Wi-Fi or hotspot.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(availablePeersToAdd) { peer ->
                            var added by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgSurface)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(AccentEmerald.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            peer.displayName.take(1).uppercase(),
                                            color = AccentEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            peer.displayName,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            peer.ipAddress ?: peer.peerId.take(10),
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                if (added) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentEmerald.copy(alpha = 0.15f))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text("Added ✓", color = AccentEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            appState.addMemberToGroup(currentGroup.networkId, peer)
                                            added = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = BgCard
    )
}


@Composable
fun AndroidGroupInfoScreen(
    group: Network,
    appState: AndroidAppState,
    onBack: () -> Unit
) {
    val networks by appState.activeNetworks.collectAsState()
    val currentGroup = networks.find { it.networkId == group.networkId } ?: group

    val isOwner = currentGroup.ownerId == appState.localPeerId
    val currentRole = currentGroup.members.find { it.peerId == appState.localPeerId }?.role ?: if (isOwner) MemberRole.OWNER else MemberRole.MEMBER
    val canManage = isOwner || currentRole == MemberRole.ADMIN

    var showQrDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddMembersDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf(currentGroup.name) }
    var memberToRemove by remember { mutableStateOf<NetworkMember?>(null) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val discoveredPeers by appState.discoveredPeers.collectAsState()
    val existingMemberIds = remember(currentGroup.members) { currentGroup.members.map { it.peerId }.toSet() }
    val availablePeersToAdd = remember(discoveredPeers, existingMemberIds) {
        discoveredPeers.filter { !existingMemberIds.contains(it.peerId) }
            .distinctBy { it.ipAddress ?: it.peerId }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val createdDateStr = dateFormat.format(Date(currentGroup.createdAt))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Avatar & Group Name
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(AccentIndigo.copy(alpha = 0.15f))
                .border(2.dp, AccentIndigo, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Hub,
                contentDescription = null,
                tint = AccentIndigo,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                currentGroup.name,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (canManage) {
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        renameInput = currentGroup.name
                        showRenameDialog = true
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename Group",
                        tint = AccentIndigo,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            "${currentGroup.members.size} members • ${currentGroup.type.name} Channel",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(20.dp))

        // Messenger-style Action Buttons Bar (QR Invite, Add, Mute, Rename)
        val isGroupMuted = remember(currentGroup.networkId, currentGroup) {
            appState.isMuteActive(currentGroup.networkId)
        }
        var showGroupMuteDialog by remember { mutableStateOf(false) }

        if (showGroupMuteDialog) {
            MuteNotificationsDialog(
                conversationId = currentGroup.networkId,
                isMuted = isGroupMuted,
                appState = appState,
                onDismiss = { showGroupMuteDialog = false }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // QR Code Invite Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showQrDialog = true }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald.copy(alpha = 0.15f))
                        .border(1.dp, AccentEmerald.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("QR Invite", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            // Add Members Button (Messenger style)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAddMembersDialog = true }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald.copy(alpha = 0.15f))
                        .border(1.dp, AccentEmerald.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text("Add", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            // Mute / Unmute Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showGroupMuteDialog = true }
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isGroupMuted) Color(0xFFA855F7).copy(alpha = 0.18f) else BgCard)
                        .border(1.dp, if (isGroupMuted) Color(0xFFA855F7) else BorderSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isGroupMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        contentDescription = "Mute",
                        tint = if (isGroupMuted) Color(0xFFA855F7) else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(if (isGroupMuted) "Unmute" else "Mute", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            // Rename Button
            if (canManage) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            renameInput = currentGroup.name
                            showRenameDialog = true
                        }
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentIndigo.copy(alpha = 0.15f))
                            .border(1.dp, AccentIndigo.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = AccentIndigo, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Rename", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Join Permission & Admin Approval Switch (Messenger style)
        if (canManage) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text("MEMBER PERMISSIONS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentIndigo.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = AccentIndigo,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Require Admin Approval",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Admins must approve new members to join",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    NearaSwitch(
                        checked = currentGroup.securityPolicy.requireApproval,
                        onCheckedChange = { appState.setGroupApprovalRequired(currentGroup.networkId, it) },
                        activeColor = AccentIndigo
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Pending Join Requests Queue (If any pending)
        if (currentGroup.pendingMembers.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(1.dp, AccentIndigo.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PENDING JOIN REQUESTS (${currentGroup.pendingMembers.size})",
                        color = AccentIndigo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentWarning.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Action Required", color = AccentWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                currentGroup.pendingMembers.forEachIndexed { index, pending ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AccentIndigo.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    pending.displayName.take(1).uppercase(),
                                    color = AccentIndigo,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    pending.displayName,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    pending.peerId.take(12),
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (canManage) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { appState.rejectJoinRequest(currentGroup.networkId, pending.peerId) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Decline",
                                        tint = AccentDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Button(
                                    onClick = { appState.approveJoinRequest(currentGroup.networkId, pending.peerId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Approve", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (index < currentGroup.pendingMembers.lastIndex) {
                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Group Details Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text("CHANNEL INFO", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Channel ID", color = TextSecondary, fontSize = 12.sp)
                Text(currentGroup.networkId.take(16), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = BorderSubtle, thickness = 0.5.dp)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Security Type", color = TextSecondary, fontSize = 12.sp)
                Text(
                    if (currentGroup.securityPolicy.requiresPin) "PIN Protected" else "Public Wi-Fi Mesh",
                    color = AccentIndigo,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = BorderSubtle, thickness = 0.5.dp)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Created Date", color = TextSecondary, fontSize = 12.sp)
                Text(createdDateStr, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Group Members Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "MEMBERS (${currentGroup.members.size})",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                TextButton(
                    onClick = { showAddMembersDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Add", color = AccentEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            currentGroup.members.forEachIndexed { index, member ->
                val isMe = member.peerId == appState.localPeerId
                val memberName = if (isMe) "${member.displayName} (You)" else member.displayName

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isMe) AccentEmerald.copy(alpha = 0.15f) else AccentIndigo.copy(alpha = 0.15f))
                                .border(1.dp, if (isMe) AccentEmerald else AccentIndigo.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                member.displayName.take(1).uppercase(),
                                color = if (isMe) AccentEmerald else AccentIndigo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(Modifier.width(10.dp))

                        Column {
                            Text(
                                memberName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                member.peerId.take(12),
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Role Chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when (member.role) {
                                        MemberRole.OWNER -> AccentWarning.copy(alpha = 0.15f)
                                        MemberRole.ADMIN -> AccentIndigo.copy(alpha = 0.15f)
                                        MemberRole.MEMBER -> BgSurface
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                when (member.role) {
                                    MemberRole.OWNER -> "Host"
                                    MemberRole.ADMIN -> "Admin"
                                    MemberRole.MEMBER -> "Member"
                                },
                                color = when (member.role) {
                                    MemberRole.OWNER -> AccentWarning
                                    MemberRole.ADMIN -> AccentIndigo
                                    MemberRole.MEMBER -> TextSecondary
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Remove button for owner/admin (cannot remove yourself here)
                        if (canManage && !isMe) {
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = { memberToRemove = member },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.PersonRemove,
                                    contentDescription = "Remove Member",
                                    tint = AccentDanger,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                if (index < currentGroup.members.lastIndex) {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Danger Zone: Leave / Delete Group Channel
        if (isOwner) {
            Button(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = AccentDanger),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete Group Channel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        } else {
            OutlinedButton(
                onClick = { showLeaveConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentDanger),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentDanger),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = AccentDanger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Leave Group", color = AccentDanger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(30.dp))
    }

    // Dialog: Add Members (Messenger Style)
    if (showAddMembersDialog) {
        AddMembersDialog(
            group = currentGroup,
            appState = appState,
            onDismiss = { showAddMembersDialog = false }
        )
    }

    // Dialog: QR Code Invite
    if (showQrDialog) {
        val qrUri = remember(currentGroup.networkId) {
            appState.networkManager.generateQrInvitationUri(currentGroup.networkId)
        }
        val qrBitmap = remember(qrUri) { generateAndroidQrBitmap(qrUri) }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Text("Scan to Join \"${currentGroup.name}\"", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(10.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Point another device camera or scan in Networks tab to join instantly.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQrDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text("Done")
                }
            },
            containerColor = BgCard
        )
    }

    // Dialog: Rename Group
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Group Channel", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    placeholder = { Text("New group name...", color = TextMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BgSurface,
                        unfocusedContainerColor = BgSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            appState.updateGroupName(currentGroup.networkId, renameInput.trim())
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }

    // Dialog: Remove Member
    val memberTarget = memberToRemove
    if (memberTarget != null) {
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Remove Member", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to remove \"${memberTarget.displayName}\" from this channel?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        appState.removeGroupMember(currentGroup.networkId, memberTarget.peerId)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDanger)
                ) {
                    Text("Remove", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }

    // Dialog: Delete Group Channel
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Group Channel", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete \"${currentGroup.name}\"? All messages and member records will be permanently removed.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        appState.deleteGroup(currentGroup.networkId)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDanger)
                ) {
                    Text("Delete Channel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }

    // Dialog: Leave Group Channel
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Group Channel", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to leave \"${currentGroup.name}\"? You will no longer receive updates from this group.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        appState.leaveGroup(currentGroup.networkId)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDanger)
                ) {
                    Text("Leave", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }
}
