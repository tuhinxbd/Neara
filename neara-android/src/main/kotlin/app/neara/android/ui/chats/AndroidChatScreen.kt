@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.neara.android.ui




import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import androidx.compose.ui.viewinterop.AndroidView
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker

@Composable
fun AndroidChatScreen(appState: AndroidAppState) {
    val activePeer by appState.activeConversationPeer.collectAsState()
    val activeNetwork by appState.activeConversationNetwork.collectAsState()
    val messages by appState.currentMessages.collectAsState()
    val offlinePending by appState.offlinePendingCount.collectAsState()
    val conversations by appState.conversationList.collectAsState()
    val peers by appState.discoveredPeers.collectAsState()
    var textInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSendingImage by remember { mutableStateOf(false) }
    var pendingBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pendingBase64List by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingCaptionText by remember { mutableStateOf("") }
    var showSendImageDialog by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }

    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var recordingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun cancelVoiceRecording() {
        recordingJob?.cancel()
        recordingJob = null
        voiceRecorder.cancelRecording()
        isRecordingVoice = false
        recordingSeconds = 0
    }

    fun startVoiceRecording() {
        cancelVoiceRecording()
        val file = voiceRecorder.startRecording()
        if (file != null) {
            isRecordingVoice = true
            recordingSeconds = 0
            recordingJob = coroutineScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(1000)
                    recordingSeconds++
                }
            }
        } else {
            Toast.makeText(context, "Could not start voice recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAndSendVoiceRecording() {
        recordingJob?.cancel()
        recordingJob = null
        val duration = recordingSeconds
        val base64 = voiceRecorder.stopAndGetBase64()
        isRecordingVoice = false
        recordingSeconds = 0
        if (base64 != null && duration >= 1) {
            appState.sendVoiceMessage(base64, duration)
        } else if (duration < 1) {
            Toast.makeText(context, "Hold or record for at least 1 second", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required to record voice messages", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecordingVoice) {
                cancelVoiceRecording()
            }
            VoicePlayerManager.stop()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    isSendingImage = true
                    val base64List = mutableListOf<String>()
                    val bitmaps = mutableListOf<Bitmap>()
                    for (uri in uris) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val originalBitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()

                        if (originalBitmap != null) {
                            val maxDim = 1200
                            val width = originalBitmap.width
                            val height = originalBitmap.height
                            val scale = if (width > maxDim || height > maxDim) {
                                min(maxDim.toFloat() / width, maxDim.toFloat() / height)
                            } else 1.0f

                            val scaledBitmap = if (scale < 1.0f) {
                                Bitmap.createScaledBitmap(
                                    originalBitmap,
                                    (width * scale).toInt(),
                                    (height * scale).toInt(),
                                    true
                                )
                            } else {
                                originalBitmap
                            }

                            val baos = ByteArrayOutputStream()
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                            val base64Str = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                            base64List.add(base64Str)
                            bitmaps.add(scaledBitmap)
                        }
                    }

                    if (base64List.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            pendingBitmaps = bitmaps
                            pendingBase64List = base64List
                            pendingCaptionText = textInput
                            textInput = ""
                            showSendImageDialog = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isSendingImage = false
                }
            }
        }
    }

    if (activePeer == null && activeNetwork == null) {
        // --- MESSENGER-STYLE CHATS INDEX ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
        ) {
            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search messages or people...", color = TextMuted, fontSize = 13.sp) },
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
            }

            // Online / Nearby Active Friends Horizontal Row (Messenger style)
            val activePeers = remember(peers) {
                peers.filter { it.connectionState != ConnectionState.DISCONNECTED }
                    .distinctBy { it.ipAddress ?: it.peerId }
            }

            if (activePeers.isNotEmpty()) {
                Text(
                    "ACTIVE NEARBY",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(activePeers) { peer ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { appState.selectConversation(peer) }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald.copy(alpha = 0.12f))
                                        .border(2.dp, AccentEmerald, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        peer.displayName.take(1).uppercase(),
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clip(CircleShape)
                                        .background(AccentEmerald)
                                        .border(2.dp, BgDark, CircleShape)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                peer.displayName.take(8),
                                color = TextPrimary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = BorderSubtle,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // Messenger Conversation Index List
            val filteredConversations = remember(conversations, searchQuery) {
                if (searchQuery.isBlank()) conversations
                else conversations.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.lastMessageText.contains(searchQuery, ignoreCase = true)
                }
            }

            if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(BgCard)
                                .border(1.dp, BorderSubtle, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("No messages yet", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap an active device above to send an encrypted message, or create a group in Networks.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredConversations) { item ->
                        MessengerConversationRow(
                            item = item,
                            appState = appState,
                            onClick = {
                                if (item.isGroup && item.network != null) {
                                    appState.selectNetworkConversation(item.network)
                                } else if (item.peer != null) {
                                    appState.selectConversation(item.peer)
                                }
                            },
                            onDelete = {
                                appState.deleteConversation(item)
                            }
                        )
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(BgDark).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (offlinePending > 0) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentWarning.copy(alpha = 0.12f))
                        .border(1.dp, AccentWarning.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.HourglassTop, contentDescription = null, tint = AccentWarning, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("$offlinePending offline queued message(s)", color = AccentWarning, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(8.dp))

            val visibleMessages = remember(messages) {
                messages.filter { (it.type != app.neara.core.model.MessageType.ACTION && !it.payload.startsWith("ACTION:")) || it.payload.startsWith("CALL_LOG:") }
            }

            // Messages
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(visibleMessages) { msg ->
                    val isSelf = msg.senderId == appState.localPeerId
                    val senderName = appState.getSenderDisplayName(msg.senderId)
                    AndroidMessageBubble(msg = msg, isSelf = isSelf, senderName = senderName, appState = appState)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Input Bar
            if (isRecordingVoice) {
                // Messenger-style voice recording bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(BgCard)
                        .border(1.dp, AccentDanger.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Discard / Delete button
                    IconButton(
                        onClick = { cancelVoiceRecording() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Cancel recording",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Pulsing red recording dot
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alphaAnim by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = alphaAnim))
                    )

                    Spacer(Modifier.width(8.dp))

                    // Timer format (e.g. 0:05)
                    val mins = recordingSeconds / 60
                    val secs = recordingSeconds % 60
                    Text(
                        String.format("%d:%02d", mins, secs),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.width(12.dp))

                    // Animated sound waveform bars
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val barCount = 14
                        for (i in 0 until barCount) {
                            val barHeight by infiniteTransition.animateFloat(
                                initialValue = 6f + (i * 3 % 10),
                                targetValue = 22f - (i * 2 % 8),
                                animationSpec = infiniteRepeatable(
                                    animation = tween(400 + (i * 45), easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "bar_$i"
                            )
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(AccentEmerald)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Send voice message button
                    IconButton(
                        onClick = { stopAndSendVoiceRecording() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send voice message",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(BgCard)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(26.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ── LEFT ICONS: Image · Mic · Location ──────────────────
                    // Image picker
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isSendingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AccentEmerald,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "Attach image",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Mic — tap to start/stop recording
                    IconButton(
                        onClick = {
                            if (isRecordingVoice) {
                                stopAndSendVoiceRecording()
                            } else {
                                val hasMicPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMicPermission) {
                                    startVoiceRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecordingVoice) "Stop recording" else "Record voice",
                            tint = if (isRecordingVoice) AccentDanger else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Location — open map picker (Messenger style)
                    IconButton(
                        onClick = { showLocationPicker = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Share location",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Thin divider line between left icons and text field
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(BorderSubtle)
                    )

                    Spacer(Modifier.width(2.dp))

                    // ── RIGHT: TextField + Send ──────────────────────────────
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Encrypted message...", color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textInput.isNotBlank()) {
                                appState.sendTextMessage(textInput.trim())
                                textInput = ""
                            }
                        }),
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

                    // Send button — only show when text is typed
                    if (textInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                appState.sendTextMessage(textInput.trim())
                                textInput = ""
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentEmerald)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

        }
    }

    // Messenger-style Send Photo Preview Fullscreen Overlay with Caption input
    if (showSendImageDialog && pendingBitmaps.isNotEmpty()) {
        var previewIndex by remember { mutableStateOf(0) }
        val safePreviewIdx = previewIndex.coerceIn(0, pendingBitmaps.size - 1)

        BackHandler {
            showSendImageDialog = false
            pendingBitmaps = emptyList()
            pendingBase64List = emptyList()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            showSendImageDialog = false
                            pendingBitmaps = emptyList()
                            pendingBase64List = emptyList()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }

                    Text(
                        if (pendingBitmaps.size > 1) "${safePreviewIdx + 1} of ${pendingBitmaps.size}" else "Send Photo",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Spacer(Modifier.size(48.dp))
                }

                // Main Image Preview Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = pendingBitmaps[safePreviewIdx].asImageBitmap(),
                        contentDescription = "Selected photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Discreet subtle left/right arrows if multiple photos
                    if (pendingBitmaps.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    previewIndex = if (safePreviewIdx > 0) safePreviewIdx - 1 else pendingBitmaps.size - 1
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            IconButton(
                                onClick = {
                                    previewIndex = (safePreviewIdx + 1) % pendingBitmaps.size
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Bottom Thumbnail strip if multiple photos
                if (pendingBitmaps.size > 1) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pendingBitmaps.indices.toList()) { idx ->
                            val isSelected = idx == safePreviewIdx
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) AccentEmerald else Color.White.copy(alpha = 0.35f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { previewIndex = idx }
                            ) {
                                Image(
                                    bitmap = pendingBitmaps[idx].asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Bottom Messenger-style Caption + Send Bar (Always visible above navigation bar & keyboard)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = pendingCaptionText,
                        onValueChange = { pendingCaptionText = it },
                        placeholder = { Text("Add a caption...", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 100.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    IconButton(
                        onClick = {
                            val cap = pendingCaptionText.trim()
                            appState.sendImagesMessage(pendingBase64List, caption = cap)
                            showSendImageDialog = false
                            pendingBitmaps = emptyList()
                            pendingBase64List = emptyList()
                            pendingCaptionText = ""
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send photo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // ── Messenger-style Location Picker Fullscreen Overlay ─────────────────
    if (showLocationPicker) {
        LocationPickerDialog(
            onDismiss = { showLocationPicker = false },
            onSendLocation = { lat, lon ->
                val latStr = String.format("%.6f", lat)
                val lonStr = String.format("%.6f", lon)
                appState.sendTextMessage("📍 Location: $latStr, $lonStr\nhttps://maps.google.com/?q=$latStr,$lonStr")
                showLocationPicker = false
            }
        )
    }
}

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
@Composable
private fun LocationPickerDialog(
    onDismiss: () -> Unit,
    onSendLocation: (lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current

    // Get current GPS location as starting point
    var pickedLat by remember { mutableStateOf(23.8103) }
    var pickedLon by remember { mutableStateOf(90.4125) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var hasGotGps by remember { mutableStateOf(false) }

    // Try to get real location on first compose
    LaunchedEffect(Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        var best: android.location.Location? = null
        for (p in listOf(
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.PASSIVE_PROVIDER
        )) {
            try {
                val l = lm?.getLastKnownLocation(p)
                if (l != null && (best == null || l.time > best.time)) best = l
            } catch (_: Exception) {}
        }
        best?.let {
            pickedLat = it.latitude
            pickedLon = it.longitude
            hasGotGps = true
            mapViewRef?.controller?.animateTo(GeoPoint(it.latitude, it.longitude), 17.0, 500L)
        }
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
        ) {
            // OSMDroid Map
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    Configuration.getInstance().userAgentValue = "Neara/1.0 (Android; Location-Picker)"

                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                        // Disallow parent scrolling
                        setOnTouchListener { v, event ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }

                        controller.setZoom(17.0)
                        controller.setCenter(GeoPoint(pickedLat, pickedLon))

                        // Dark theme filter
                        val inverseMatrix = ColorMatrix(
                            floatArrayOf(
                                -0.85f, 0f, 0f, 0f, 240f,
                                0f, -0.85f, 0f, 0f, 245f,
                                0f, 0f, -0.85f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                        overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))

                        mapViewRef = this
                    }
                },
                update = { map ->
                    // Update picked location from map center
                    val center = map.mapCenter
                    pickedLat = center.latitude
                    pickedLon = center.longitude
                },
                modifier = Modifier.fillMaxSize()
            )

            // Center pin (always stays in exact center of the map)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(44.dp)
                    )
                    // Small shadow dot under pin
                    Box(
                        modifier = Modifier
                            .size(8.dp, 4.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                }
            }

            // Top bar: Close + Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDark.copy(alpha = 0.95f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Share Location",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        "Drag map to adjust pin",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Center on GPS button
                IconButton(
                    onClick = {
                        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                        var best: android.location.Location? = null
                        for (p in listOf(
                            android.location.LocationManager.GPS_PROVIDER,
                            android.location.LocationManager.NETWORK_PROVIDER
                        )) {
                            try {
                                val l = lm?.getLastKnownLocation(p)
                                if (l != null && (best == null || l.time > best.time)) best = l
                            } catch (_: Exception) {}
                        }
                        best?.let {
                            pickedLat = it.latitude
                            pickedLon = it.longitude
                            mapViewRef?.controller?.animateTo(GeoPoint(it.latitude, it.longitude), 17.5, 600L)
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Center GPS",
                        tint = AccentEmerald
                    )
                }
            }

            // Bottom card: coordinates + send button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1A1F2E))
                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Selected Location",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "${String.format("%.6f", pickedLat)}, ${String.format("%.6f", pickedLon)}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentEmerald)
                            .clickable { onSendLocation(pickedLat, pickedLon) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Send Location",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
