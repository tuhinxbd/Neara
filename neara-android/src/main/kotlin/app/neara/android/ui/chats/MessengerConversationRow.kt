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
fun MessengerConversationRow(
    item: ConversationItem,
    appState: AndroidAppState,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(item.lastMessageTime))
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveGroupConfirm by remember { mutableStateOf(false) }
    var showDeleteGroupConfirm by remember { mutableStateOf(false) }
    var showMuteDialog by remember { mutableStateOf(false) }

    if (showMuteDialog) {
        MuteNotificationsDialog(
            conversationId = item.conversationId,
            isMuted = item.isMuted,
            appState = appState,
            onDismiss = { showMuteDialog = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (item.isPinned) BgSurface else BgCard)
            .border(
                1.dp,
                if (item.unreadCount > 0) AccentEmerald.copy(alpha = 0.45f)
                else if (item.isPinned) AccentEmerald.copy(alpha = 0.25f)
                else BorderSubtle,
                RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = { showOptionsSheet = true }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with online badge
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (item.isGroup) AccentIndigo.copy(alpha = 0.15f) else AccentEmerald.copy(alpha = 0.12f))
                    .border(1.5.dp, if (item.isGroup) AccentIndigo else AccentEmerald.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (item.isGroup) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = AccentIndigo, modifier = Modifier.size(22.dp))
                } else {
                    Text(
                        item.title.take(1).uppercase(),
                        color = AccentEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }

            if (item.isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald)
                        .border(2.dp, BgCard, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.title,
                        color = TextPrimary,
                        fontWeight = if (item.unreadCount > 0) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isPinned) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = AccentEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (item.isGroup) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.size(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (item.isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = if (item.isMuted) "Muted" else "Notifications on",
                                tint = TextMuted.copy(alpha = 0.55f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else if (item.isMuted) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier.size(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = "Muted",
                                tint = TextMuted.copy(alpha = 0.55f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Text(
                    timeStr,
                    color = if (item.unreadCount > 0) AccentEmerald else TextMuted,
                    fontWeight = if (item.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isLastSelf) {
                        val statusText = when (item.lastStatus) {
                            MessageStatus.PENDING -> "⏳ "
                            MessageStatus.SENDING -> "↗ "
                            MessageStatus.SENT -> "✓ "
                            MessageStatus.DELIVERED -> "✓✓ "
                            MessageStatus.READ -> "✓✓ "
                            MessageStatus.FAILED -> "⚠ "
                        }
                        Text(
                            statusText,
                            color = if (item.lastStatus == MessageStatus.DELIVERED || item.lastStatus == MessageStatus.READ) AccentEmerald else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        item.lastMessageText,
                        color = if (item.unreadCount > 0) TextPrimary else TextSecondary,
                        fontWeight = if (item.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Messenger Unread Counter Badge
                if (item.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // --- MESSENGER OPTIONS MODAL BOTTOM SHEET ---
    if (showOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BgCard,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = BorderSubtle)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .navigationBarsPadding()
            ) {
                // Header Preview (Messenger style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (item.isGroup) AccentIndigo.copy(alpha = 0.15f) else AccentEmerald.copy(alpha = 0.12f))
                            .border(1.5.dp, if (item.isGroup) AccentIndigo else AccentEmerald, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isGroup) {
                            Icon(Icons.Default.Hub, contentDescription = null, tint = AccentIndigo, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                item.title.take(1).uppercase(),
                                color = AccentEmerald,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.title,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            if (item.isPinned) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = AccentEmerald, modifier = Modifier.size(13.dp))
                            }
                            if (item.isMuted) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.NotificationsOff, contentDescription = "Muted", tint = AccentWarning, modifier = Modifier.size(13.dp))
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            item.lastMessageText,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(bottom = 6.dp))

                // Action 1: Mark as read / Mark as unread
                MessengerActionItem(
                    icon = if (item.unreadCount > 0) Icons.Default.MarkChatRead else Icons.Default.MarkChatUnread,
                    title = if (item.unreadCount > 0) "Mark as read" else "Mark as unread",
                    subtitle = if (item.unreadCount > 0) "Clear unread counter badge" else "Mark to respond or check later",
                    iconTint = AccentEmerald,
                    onClick = {
                        showOptionsSheet = false
                        if (item.unreadCount > 0) {
                            appState.markConversationAsRead(item.conversationId)
                        } else {
                            appState.markConversationAsUnread(item.conversationId)
                        }
                    }
                )

                // Action 2: Mute notifications / Unmute
                MessengerActionItem(
                    icon = if (item.isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    title = if (item.isMuted) "Unmute notifications" else "Mute notifications",
                    subtitle = if (item.isMuted) "Resume message notifications" else "Silence alerts for this chat",
                    iconTint = if (item.isMuted) AccentEmerald else TextSecondary,
                    onClick = {
                        showOptionsSheet = false
                        showMuteDialog = true
                    }
                )

                // Action 3: Pin to top / Unpin
                MessengerActionItem(
                    icon = Icons.Default.PushPin,
                    title = if (item.isPinned) "Unpin conversation" else "Pin to top",
                    subtitle = if (item.isPinned) "Remove from top priority" else "Keep this conversation pinned to top",
                    iconTint = if (item.isPinned) AccentEmerald else TextSecondary,
                    onClick = {
                        showOptionsSheet = false
                        appState.togglePinConversation(item.conversationId)
                    }
                )

                // Action 6: Open chat
                MessengerActionItem(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "Open chat",
                    subtitle = "View and send encrypted messages",
                    iconTint = AccentEmerald,
                    onClick = {
                        showOptionsSheet = false
                        onClick()
                    }
                )

                // Action 7: If group, Leave Group (separate option!)
                if (item.isGroup && item.network != null) {
                    val isOwner = item.network.ownerId == appState.localPeerId
                    if (!isOwner) {
                        MessengerActionItem(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            title = "Leave Group",
                            subtitle = "Exit this group and stop receiving messages",
                            iconTint = AccentWarning,
                            textColor = AccentWarning,
                            onClick = {
                                showOptionsSheet = false
                                showLeaveGroupConfirm = true
                            }
                        )
                    } else {
                        MessengerActionItem(
                            icon = Icons.Default.DeleteForever,
                            title = "Delete Group Channel",
                            subtitle = "Permanently delete this group channel for everyone",
                            iconTint = AccentDanger,
                            textColor = AccentDanger,
                            onClick = {
                                showOptionsSheet = false
                                showDeleteGroupConfirm = true
                            }
                        )
                    }
                }

                // Action 8: Remove Chat / Delete Conversation
                MessengerActionItem(
                    icon = Icons.Default.DeleteOutline,
                    title = if (item.isGroup) "Remove Chat from List" else "Delete Conversation",
                    subtitle = if (item.isGroup) "Clears chat history (keeps group membership)" else "Delete local message history",
                    iconTint = AccentDanger,
                    textColor = AccentDanger,
                    onClick = {
                        showOptionsSheet = false
                        showDeleteConfirm = true
                    }
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Dialog 1: Leave Group (Member)
    if (showLeaveGroupConfirm && item.network != null) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = AccentWarning, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Leave Group?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Are you sure you want to leave \"${item.title}\"? You will exit membership and stop receiving messages from this group.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveGroupConfirm = false
                        appState.leaveGroup(item.network.networkId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentWarning)
                ) {
                    Text("Leave Group", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }

    // Dialog 2: Delete Group Channel (Host only)
    if (showDeleteGroupConfirm && item.network != null) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = AccentDanger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Group Channel?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Are you sure you want to permanently delete \"${item.title}\"? This will erase the channel for all members. This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteGroupConfirm = false
                        appState.deleteGroup(item.network.networkId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDanger)
                ) {
                    Text("Delete Channel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgCard
        )
    }

    // Dialog 3: Remove Chat / Delete Conversation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AccentDanger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (item.isGroup) "Remove Chat from List" else "Delete Conversation",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    if (item.isGroup)
                        "Are you sure you want to remove \"${item.title}\" from your chats and networks? All local messages and channel records will be removed."
                    else
                        "Are you sure you want to delete your conversation with \"${item.title}\"? All local messages will be removed.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDanger)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
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
}
