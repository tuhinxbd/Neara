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
fun MessengerActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = TextPrimary,
    textColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MuteNotificationsDialog(
    conversationId: String,
    isMuted: Boolean,
    appState: AndroidAppState,
    onDismiss: () -> Unit
) {
    if (isMuted) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = BgCard,
            shape = RoundedCornerShape(18.dp),
            title = { Text("Unmute Notifications", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = { Text("Resume notifications for this conversation?", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { appState.unmuteConversation(conversationId); onDismiss() }) {
                    Text("Unmute", color = AccentEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
            }
        )
    } else {
        val durations = listOf(
            "15 minutes" to (15L * 60 * 1000),
            "1 hour" to (60L * 60 * 1000),
            "8 hours" to (8L * 60 * 60 * 1000),
            "24 hours" to (24L * 60 * 60 * 1000),
            "Until I turn it back on" to Long.MAX_VALUE
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = BgCard,
            shape = RoundedCornerShape(18.dp),
            title = { Text("Mute Notifications", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column {
                    Text("Mute notifications for:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    durations.forEachIndexed { index, (label, durationMs) ->
                        val isForever = durationMs == Long.MAX_VALUE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    appState.muteConversation(conversationId, durationMs)
                                    onDismiss()
                                }
                                .background(if (isForever) AccentWarning.copy(alpha = 0.06f) else Color.Transparent)
                                .padding(horizontal = 6.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                label,
                                color = if (isForever) AccentWarning else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isForever) FontWeight.Medium else FontWeight.Normal
                            )
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                        if (index < durations.lastIndex) HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
        )
    }
}

@Composable
fun MessengerMuteBanner(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF26262B),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Purple circular badge with white NotificationsOff icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFA855F7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Muted",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Middle Text: Mute & Message and call notifications from this chat are off.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 8.dp)
                ) {
                    Text(
                        "Mute",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        "Message and call notifications from this chat are off.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }

                // Right actions: "Turn on" pill button & "✕" close button
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF3F3F46))
                            .clickable { onTurnOn() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Turn on",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3F3F46))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss mute banner",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
        }
    }
}
