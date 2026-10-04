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
fun AndroidMessageBubble(
    msg: ChatMessage,
    isSelf: Boolean,
    senderName: String,
    appState: AndroidAppState
) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(msg.timestamp))

    var showActionSheet by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteOptionsSheet by remember { mutableStateOf(false) }
    var showMoreEmojis by remember { mutableStateOf(false) }
    var showFullImageDialog by remember { mutableStateOf(false) }
    var editText by remember(msg.payload) { mutableStateOf(msg.payload) }

    val (captionText, rawImagesPayload) = remember(msg.payload, msg.type) {
        if (msg.type == MessageType.IMAGE && msg.payload.contains("|||CAPTION_SEP|||")) {
            val split = msg.payload.split("|||CAPTION_SEP|||", limit = 2)
            val cap = split[0].removePrefix("CAPTION:")
            cap to split[1]
        } else {
            "" to msg.payload
        }
    }

    val imageBitmaps = remember(rawImagesPayload, msg.type) {
        if (msg.type == MessageType.IMAGE) {
            val parts = if (rawImagesPayload.contains("|||IMG|||")) {
                rawImagesPayload.split("|||IMG|||")
            } else {
                listOf(rawImagesPayload)
            }
            parts.mapNotNull { b64 ->
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    null
                }
            }
        } else emptyList()
    }
    var currentStackIndex by remember { mutableStateOf(0) }
    val hasReactions = remember(msg.reactions) { msg.reactions.entries.any { it.value.isNotEmpty() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (hasReactions) 6.dp else 0.dp),
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

        Box(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            val activeReactions = msg.reactions.entries.filter { it.value.isNotEmpty() }
            val hasReactions = activeReactions.isNotEmpty()
            val hasCaption = captionText.isNotBlank()

            if (msg.type == MessageType.IMAGE && hasCaption) {
                // Messenger / WhatsApp unified bubble for Image with Caption
                Column(
                    modifier = Modifier
                        .padding(bottom = if (hasReactions) 12.dp else 0.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isSelf) 16.dp else 4.dp,
                                bottomEnd = if (isSelf) 4.dp else 16.dp
                            )
                        )
                        .combinedClickable(
                            onClick = {
                                if (imageBitmaps.isNotEmpty()) showFullImageDialog = true
                            },
                            onLongClick = { showActionSheet = true }
                        )
                        .background(if (isSelf) AccentEmerald else BgCard)
                        .border(1.dp, if (isSelf) AccentEmerald else BorderSubtle, RoundedCornerShape(16.dp))
                ) {
                    if (!isSelf) {
                        Text(
                            senderName,
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 4.dp)
                        )
                    }

                    if (imageBitmaps.isNotEmpty()) {
                        val safeIndex = currentStackIndex.coerceIn(0, imageBitmaps.size - 1)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            Image(
                                bitmap = imageBitmaps[safeIndex].asImageBitmap(),
                                contentDescription = "Attached photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 15.dp,
                                            topEnd = 15.dp,
                                            bottomStart = 4.dp,
                                            bottomEnd = 4.dp
                                        )
                                    )
                            )

                            if (imageBitmaps.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .clickable {
                                            currentStackIndex = (safeIndex + 1) % imageBitmaps.size
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "${safeIndex + 1}/${imageBitmaps.size}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Caption Text
                    Text(
                        captionText,
                        color = if (isSelf) Color.White else TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )

                    // Time and Delivery Status
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(timeStr, color = if (isSelf) Color.White.copy(alpha = 0.8f) else TextMuted, fontSize = 10.sp)
                        if (isSelf) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                when (msg.status) {
                                    MessageStatus.PENDING -> "⏱"
                                    MessageStatus.SENDING -> "⏳"
                                    MessageStatus.SENT -> "✓"
                                    MessageStatus.DELIVERED, MessageStatus.READ -> "✓✓"
                                    MessageStatus.FAILED -> "⚠"
                                },
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            } else if (msg.type == MessageType.IMAGE) {
                // Messenger-style borderless photo / stacked album (NO card wrapper, NO green bubble background)
                Column(
                    modifier = Modifier.padding(bottom = if (hasReactions) 12.dp else 0.dp)
                ) {
                    if (!isSelf) {
                        Text(
                            senderName,
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }

                    if (imageBitmaps.isNotEmpty()) {
                        val safeIndex = currentStackIndex.coerceIn(0, imageBitmaps.size - 1)
                        if (imageBitmaps.size > 1) {
                            // Stacked cards deck matching user's Messenger reference
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, end = 12.dp, bottom = 4.dp)
                                    .combinedClickable(
                                        onClick = { showFullImageDialog = true },
                                        onLongClick = { showActionSheet = true }
                                    )
                            ) {
                                // Background Card 3 (if 3 or more photos)
                                if (imageBitmaps.size >= 3) {
                                    val idx3 = (safeIndex + 2) % imageBitmaps.size
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(230.dp)
                                            .offset(x = 10.dp, y = (-8).dp)
                                            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp))
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(BgCard)
                                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                    ) {
                                        Image(
                                            bitmap = imageBitmaps[idx3].asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.28f))
                                        )
                                    }
                                }

                                // Background Card 2 (if 2 or more photos)
                                val idx2 = (safeIndex + 1) % imageBitmaps.size
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(230.dp)
                                        .offset(x = 5.dp, y = (-4).dp)
                                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BgCard)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                ) {
                                    Image(
                                        bitmap = imageBitmaps[idx2].asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.14f))
                                    )
                                }

                                // Front Card 1
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(230.dp)
                                        .shadow(elevation = 5.dp, shape = RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BgCard)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                ) {
                                    Image(
                                        bitmap = imageBitmaps[safeIndex].asImageBitmap(),
                                        contentDescription = "Attached photo ${safeIndex + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Counter Pill Badge (Top-Right)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(10.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .clickable {
                                                currentStackIndex = (safeIndex + 1) % imageBitmaps.size
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.PhotoLibrary,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "${safeIndex + 1}/${imageBitmaps.size}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Discreet Time + Status pill inside bottom-right of image
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(timeStr, color = Color.White, fontSize = 10.sp)
                                            if (isSelf) {
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    when (msg.status) {
                                                        MessageStatus.PENDING -> "⏱"
                                                        MessageStatus.SENDING -> "⏳"
                                                        MessageStatus.SENT -> "✓"
                                                        MessageStatus.DELIVERED, MessageStatus.READ -> "✓✓"
                                                        MessageStatus.FAILED -> "⚠"
                                                    },
                                                    color = Color.White,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Single Image: borderless rounded image with subtle border to prevent blending with background
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BgCard)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = { showFullImageDialog = true },
                                        onLongClick = { showActionSheet = true }
                                    )
                            ) {
                                Image(
                                    bitmap = imageBitmaps[0].asImageBitmap(),
                                    contentDescription = "Attached photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 140.dp, max = 260.dp)
                                )

                                // Discreet Time + Status pill inside bottom-right of image
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(timeStr, color = Color.White, fontSize = 10.sp)
                                        if (isSelf) {
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                when (msg.status) {
                                                    MessageStatus.PENDING -> "⏱"
                                                    MessageStatus.SENDING -> "⏳"
                                                    MessageStatus.SENT -> "✓"
                                                    MessageStatus.DELIVERED, MessageStatus.READ -> "✓✓"
                                                    MessageStatus.FAILED -> "⚠"
                                                },
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Broken image placeholder
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(BgCard)
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Photo unavailable", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else if (msg.type == MessageType.VOICE) {
                // Messenger-style Capsule Voice Message Bubble with Symmetrical Diamond Waveform
                val (voiceDurationSecs, base64Audio) = remember(msg.payload) {
                    if (msg.payload.contains("|||VOICE_SEP|||")) {
                        val split = msg.payload.split("|||VOICE_SEP|||", limit = 2)
                        val dur = split[0].removePrefix("DURATION:").toIntOrNull() ?: 0
                        dur to split[1]
                    } else {
                        0 to msg.payload
                    }
                }

                val coroutineScope = rememberCoroutineScope()
                val isPlayingThis = VoicePlayerManager.activeMessageId == msg.messageId && VoicePlayerManager.isPlaying
                val currentProgress = if (VoicePlayerManager.activeMessageId == msg.messageId) VoicePlayerManager.progress else 0f
                val currentPosSec = if (VoicePlayerManager.activeMessageId == msg.messageId) VoicePlayerManager.currentPositionSec else 0

                Column(
                    horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
                    modifier = Modifier.padding(bottom = if (hasReactions) 12.dp else 2.dp)
                ) {
                    if (!isSelf) {
                        Text(
                            senderName,
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp, bottom = 3.dp)
                        )
                    }

                    // Pill-shaped Capsule Container (exact style from user's image)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .combinedClickable(
                                onClick = {
                                    VoicePlayerManager.togglePlay(context, msg.messageId, base64Audio, coroutineScope)
                                },
                                onLongClick = { showActionSheet = true }
                            )
                            .background(if (isSelf) AccentEmerald else BgCard)
                            .border(1.dp, if (isSelf) AccentEmerald else BorderSubtle, RoundedCornerShape(32.dp))
                            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Circular Vibrant Play / Pause Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelf) Color.White.copy(alpha = 0.28f) else Color(0xFF0084FF))
                                .clickable {
                                    VoicePlayerManager.togglePlay(context, msg.messageId, base64Audio, coroutineScope)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingThis) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(22.dp)
                                    .offset(x = if (isPlayingThis) 0.dp else 1.dp)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        // Symmetrical Diamond / Spindle Waveform (3dp dots at ends -> 28dp peak at center)
                        Row(
                            modifier = Modifier.height(30.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val count = 27
                            val mid = (count - 1) / 2f
                            val spindleHeights = remember {
                                List(count) { i ->
                                    val dist = Math.abs(i - mid) / mid // 0.0 at center, 1.0 at ends
                                    val factor = 1f - dist
                                    (factor * 25f + 3f).dp
                                }
                            }

                            spindleHeights.forEachIndexed { idx, h ->
                                val barRatio = idx.toFloat() / count.toFloat()
                                val isPlayed = barRatio <= currentProgress
                                val barColor = if (isPlayed) Color.White else Color.White.copy(alpha = 0.42f)

                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height(h)
                                        .clip(CircleShape)
                                        .background(barColor)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Duration Text (e.g. 0:03)
                        val displaySecs = if (isPlayingThis) currentPosSec else voiceDurationSecs
                        val mins = displaySecs / 60
                        val secs = displaySecs % 60
                        Text(
                            String.format("%d:%02d", mins, secs),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Time and delivery status below the pill
                    Row(
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp, start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(timeStr, color = TextMuted, fontSize = 10.sp)
                        if (isSelf) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                when (msg.status) {
                                    MessageStatus.PENDING -> "⏱"
                                    MessageStatus.SENDING -> "⏳"
                                    MessageStatus.SENT -> "✓"
                                    MessageStatus.DELIVERED, MessageStatus.READ -> "✓✓"
                                    MessageStatus.FAILED -> "⚠"
                                },
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            } else if (msg.payload.startsWith("CALL_LOG:")) {
                // Exact Messenger-Style Call Log Bubble Card (from user screenshots)
                val parts = remember(msg.payload) {
                    msg.payload.removePrefix("CALL_LOG:").split("|").associate {
                        val idx = it.indexOf("=")
                        if (idx != -1) it.substring(0, idx) to it.substring(idx + 1) else it to ""
                    }
                }
                val callType = parts["type"] ?: "AUDIO"
                val durationSecs = parts["duration"]?.toIntOrNull() ?: 0
                val callStatus = parts["status"] ?: "COMPLETED"

                val isOutgoing = isSelf
                val isMissed = callStatus == "MISSED"
                val isCancelled = callStatus == "CANCELLED"
                val isVideo = callType == "VIDEO"
                val cardTitle = when {
                    isVideo && isMissed -> "Missed video call"
                    isVideo -> "Video call"
                    isMissed -> "Missed audio call"
                    else -> "Audio call"
                }
                val cardSubtitle = when {
                    isMissed -> timeStr
                    isCancelled && isOutgoing -> "No answer • $timeStr"
                    isCancelled -> timeStr
                    durationSecs > 0 -> {
                        val m = durationSecs / 60
                        val s = durationSecs % 60
                        if (m > 0 && s > 0) "$m min $s sec"
                        else if (m > 0) "$m min"
                        else "$s sec"
                    }
                    else -> timeStr
                }
                val buttonText = if (isMissed) "Call back" else "Call again"

                Column(
                    horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start,
                    modifier = Modifier.padding(bottom = if (hasReactions) 12.dp else 4.dp)
                ) {
                    if (!isSelf) {
                        Text(
                            senderName,
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 10.dp, bottom = 3.dp)
                        )
                    }

                    // Messenger Dark Charcoal Rounded Call Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF2E3236),
                        modifier = Modifier
                            .width(220.dp)
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            // Top Row: Circular Icon + Title + Duration/Time
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF50555B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isVideo -> Icons.Default.Videocam
                                            isMissed -> Icons.Default.CallMissed
                                            else -> Icons.Default.Call
                                        },
                                        contentDescription = cardTitle,
                                        tint = if (isMissed) AccentDanger else Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = cardTitle,
                                        color = Color.White,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        text = cardSubtitle,
                                        color = Color(0xFFB0B3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Bottom Full-Width Pill Action Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF45494E))
                                    .clickable {
                                        val groupNet = appState.activeConversationNetwork.value
                                        if (groupNet != null) {
                                            appState.startGroupCall(groupNet, isVideo = isVideo)
                                        } else {
                                            val otherId = if (isSelf) msg.recipientId ?: "" else msg.senderId
                                            val targetPeer = appState.activeConversationPeer.value ?: appState.getPeerForMember(otherId, senderName)
                                            if (isVideo) {
                                                appState.startVideoCall(targetPeer, msg.conversationId)
                                            } else {
                                                appState.startVoiceCall(targetPeer, msg.conversationId)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = buttonText,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Subtle time below card
                    Row(
                        modifier = Modifier.padding(top = 2.dp, end = 6.dp, start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(timeStr, color = TextMuted, fontSize = 10.sp)
                    }
                }
            } else {
                // Standard Text Message Bubble
                Column(
                    modifier = Modifier
                        .padding(bottom = if (hasReactions) 12.dp else 0.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isSelf) 16.dp else 4.dp,
                                bottomEnd = if (isSelf) 4.dp else 16.dp
                            )
                        )
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { showActionSheet = true }
                        )
                        .background(if (isSelf) AccentEmerald else BgCard)
                        .border(1.dp, if (isSelf) AccentEmerald else BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
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

                    Text(msg.payload, color = if (isSelf) Color.White else TextPrimary, fontSize = 14.sp)

                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(timeStr, color = if (isSelf) Color.White.copy(alpha = 0.8f) else TextMuted, fontSize = 10.sp)
                        if (isSelf) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                when (msg.status) {
                                    MessageStatus.PENDING -> "⏱"
                                    MessageStatus.SENDING -> "⏳"
                                    MessageStatus.SENT -> "✓"
                                    MessageStatus.DELIVERED -> "✓✓"
                                    MessageStatus.READ -> "✓✓"
                                    MessageStatus.FAILED -> "⚠"
                                },
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Messenger-style overlapping reaction circular badge on bottom-right corner of the bubble
            if (hasReactions) {
                val totalCount = activeReactions.sumOf { it.value.size }
                val isSingleEmoji = activeReactions.size == 1 && totalCount == 1

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp)
                        .shadow(elevation = 2.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            0.8.dp,
                            Color(0xFFE2E8F0),
                            CircleShape
                        )
                        .clickable {
                            val myReaction = activeReactions.firstOrNull { it.value.contains(appState.localPeerId) }
                            if (myReaction != null) {
                                appState.toggleMessageReaction(msg.messageId, myReaction.key)
                            } else {
                                val firstEmoji = activeReactions.first().key
                                appState.toggleMessageReaction(msg.messageId, firstEmoji)
                            }
                        }
                        .then(
                            if (isSingleEmoji) {
                                Modifier.size(24.dp)
                            } else {
                                Modifier
                                    .height(24.dp)
                                    .padding(horizontal = 6.dp)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        activeReactions.take(3).forEach { (emoji, _) ->
                            Text(emoji, fontSize = 13.sp)
                        }
                        if (totalCount > 1) {
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "$totalCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        }
    }

    // Long press action bottom sheet
    if (showActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            containerColor = BgCard,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderSubtle)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                // Quick Reaction Emoji Row (Messenger style)
                val quickEmojis = listOf("❤️", "😆", "😮", "😢", "😡", "👍", "🔥")
                val extraEmojis = listOf("😂", "🎉", "👏", "💯", "🥰", "🥳", "🤔", "🥺", "💔", "🤝", "👌", "✨", "👀", "🙏")

                Text(
                    "REACT WITH EMOJI",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgDark)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickEmojis.forEach { emoji ->
                        val isReacted = msg.reactions[emoji]?.contains(appState.localPeerId) == true
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isReacted) AccentEmerald.copy(alpha = 0.25f) else Color.Transparent)
                                .border(
                                    if (isReacted) 1.5.dp else 0.dp,
                                    if (isReacted) AccentEmerald else Color.Transparent,
                                    CircleShape
                                )
                                .clickable {
                                    showActionSheet = false
                                    appState.toggleMessageReaction(msg.messageId, emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 19.sp)
                        }
                    }

                    // Plus (+) button to reveal more emojis
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (showMoreEmojis) AccentEmerald.copy(alpha = 0.2f) else BgCard)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .clickable { showMoreEmojis = !showMoreEmojis },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (showMoreEmojis) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "More emojis",
                            tint = if (showMoreEmojis) AccentEmerald else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Expanded Emojis Grid
                if (showMoreEmojis) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgDark)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        extraEmojis.take(6).forEach { emoji ->
                            val isReacted = msg.reactions[emoji]?.contains(appState.localPeerId) == true
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isReacted) AccentEmerald.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable {
                                        showActionSheet = false
                                        appState.toggleMessageReaction(msg.messageId, emoji)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 19.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgDark)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        extraEmojis.drop(6).forEach { emoji ->
                            val isReacted = msg.reactions[emoji]?.contains(appState.localPeerId) == true
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isReacted) AccentEmerald.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable {
                                        showActionSheet = false
                                        appState.toggleMessageReaction(msg.messageId, emoji)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 19.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                Spacer(Modifier.height(6.dp))

                // Action 1: Edit Message (if isSelf & TEXT)
                if (isSelf && msg.type == MessageType.TEXT) {
                    MessengerActionItem(
                        icon = Icons.Default.Edit,
                        title = "Edit message",
                        subtitle = "Modify this sent message text for everyone",
                        iconTint = AccentEmerald,
                        onClick = {
                            showActionSheet = false
                            editText = msg.payload
                            showEditDialog = true
                        }
                    )
                }

                // Action 2: Copy message text (if TEXT)
                if (msg.type == MessageType.TEXT) {
                    MessengerActionItem(
                        icon = Icons.Default.ContentCopy,
                        title = "Copy text",
                        subtitle = "Copy message payload to clipboard",
                        iconTint = AccentCyan,
                        onClick = {
                            showActionSheet = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Neara Message", msg.payload))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Action 3: Remove / Unsend message
                MessengerActionItem(
                    icon = Icons.Default.DeleteOutline,
                    title = if (isSelf) "Unsend / Delete message" else "Delete for me",
                    subtitle = if (isSelf) "Unsend for everyone or delete for yourself" else "Remove message from your chat history",
                    iconTint = AccentDanger,
                    textColor = AccentDanger,
                    onClick = {
                        showActionSheet = false
                        showDeleteOptionsSheet = true
                    }
                )

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Messenger-style Delete / Unsend Options Sheet
    if (showDeleteOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDeleteOptionsSheet = false },
            containerColor = BgCard,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderSubtle)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    if (isSelf) "Who do you want to unsend this message for?" else "Delete message?",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isSelf) {
                    // Option 1: Unsend for everyone
                    MessengerActionItem(
                        icon = Icons.Default.DeleteForever,
                        title = "Unsend for everyone",
                        subtitle = "This message will be removed for everyone in this chat",
                        iconTint = AccentDanger,
                        textColor = AccentDanger,
                        onClick = {
                            showDeleteOptionsSheet = false
                            appState.deleteMessageForEveryone(msg.messageId)
                        }
                    )

                    Spacer(Modifier.height(4.dp))
                }

                // Option 2: Unsend for you / Delete for me
                MessengerActionItem(
                    icon = Icons.Default.DeleteOutline,
                    title = if (isSelf) "Unsend for you" else "Delete for me",
                    subtitle = "This message will only be removed from your device. Others will still see it.",
                    iconTint = AccentWarning,
                    textColor = TextPrimary,
                    onClick = {
                        showDeleteOptionsSheet = false
                        appState.deleteMessage(msg.messageId)
                    }
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))

                // Cancel button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgDark)
                        .clickable { showDeleteOptionsSheet = false }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // Edit message dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = BgCard,
            title = {
                Text("Edit Message", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column {
                    Text("Update your message content:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentEmerald,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editText.isNotBlank()) {
                            appState.editMessage(msg.messageId, editText.trim())
                        }
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // Full screen image preview dialog
    if (showFullImageDialog && imageBitmaps.isNotEmpty()) {
        val safeIndex = currentStackIndex.coerceIn(0, imageBitmaps.size - 1) 
        val currentBitmap = imageBitmaps[safeIndex]

        Dialog(
            onDismissRequest = { showFullImageDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "Full preview ${safeIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )

                // Top action bar (Clean close button and photo counter if multiple)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (imageBitmaps.size > 1) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${safeIndex + 1} / ${imageBitmaps.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    IconButton(
                        onClick = { showFullImageDialog = false },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Left/Right navigation controls for multiple photos
                if (imageBitmaps.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                currentStackIndex = if (safeIndex > 0) safeIndex - 1 else imageBitmaps.size - 1
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous photo", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        IconButton(
                            onClick = {
                                currentStackIndex = (safeIndex + 1) % imageBitmaps.size
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next photo", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Bottom dot indicator
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        imageBitmaps.indices.forEach { idx ->
                            Box(
                                modifier = Modifier
                                    .size(if (idx == safeIndex) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (idx == safeIndex) AccentEmerald else Color.White.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }
    }
}
