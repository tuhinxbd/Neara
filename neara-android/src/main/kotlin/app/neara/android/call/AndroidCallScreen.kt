package app.neara.android.call

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.neara.android.AndroidAppState
import app.neara.android.ui.*
import app.neara.core.model.ConnectionState
import app.neara.core.model.Network

@Composable
fun AndroidCallOverlay(
    callManager: AndroidCallManager,
    modifier: Modifier = Modifier
) {
    val session by callManager.activeCall.collectAsState()
    val activeSession = session ?: return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        if (activeSession.isVideo && activeSession.state == CallState.CONNECTED) {
            VideoCallContent(session = activeSession, callManager = callManager)
        } else {
            VoiceCallContent(session = activeSession, callManager = callManager)
        }
    }
}

@Composable
private fun VoiceCallContent(
    session: CallSession,
    callManager: AndroidCallManager
) {
    val isIncomingRinging = session.state == CallState.RINGING && session.direction == CallDirection.INCOMING
    val isConnected = session.state == CallState.CONNECTED

    // Infinite ripple animation for ringing
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (session.isVideo) AccentIndigo.copy(alpha = 0.2f) else AccentEmerald.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (session.isVideo) AccentIndigo else AccentEmerald),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (session.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = if (session.isVideo) AccentIndigo else AccentEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (session.isVideo) "NEARA VIDEO CALL" else "NEARA VOICE CALL",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Text(
                text = session.peer.displayName,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))

            val statusText = when (session.state) {
                CallState.DIALING -> "Calling..."
                CallState.RINGING -> if (session.direction == CallDirection.INCOMING) "Incoming call..." else "Ringing..."
                CallState.CONNECTED -> formatCallDuration(session.durationSecs)
                CallState.ENDED -> session.statusMessage.ifBlank { "Call ended" }
                else -> ""
            }
            Text(
                text = statusText,
                color = if (isConnected) AccentEmerald else TextSecondary,
                fontSize = if (isConnected) 18.sp else 15.sp,
                fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        // Center Avatar & Visualizer
        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isIncomingRinging || session.state == CallState.RINGING || session.state == CallState.DIALING) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(AccentEmerald.copy(alpha = pulseAlpha))
                )
            }

            // Audio-reactive border when connected
            val dynamicScale = if (isConnected) {
                1f + (session.remoteAudioLevel + session.localAudioLevel) * 0.15f
            } else 1f

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(dynamicScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                if (session.isVideo) AccentIndigo else AccentEmerald,
                                Color(0xFF1E293B)
                            )
                        )
                    )
                    .border(
                        3.dp,
                        if (isConnected) AccentEmerald else BorderSubtle,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = session.peer.displayName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Waveform Visualizer when connected
        if (isConnected) {
            CallAudioVisualizer(
                localLevel = session.localAudioLevel,
                remoteLevel = session.remoteAudioLevel,
                isMuted = session.isMicMuted
            )
        } else {
            Spacer(Modifier.height(48.dp))
        }

        // Bottom Actions
        if (isIncomingRinging) {
            // Incoming Call: Decline or Accept
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallActionButton(
                    icon = Icons.Default.CallEnd,
                    label = "Decline",
                    backgroundColor = AccentDanger,
                    iconTint = Color.White,
                    onClick = { callManager.declineCall() }
                )

                CallActionButton(
                    icon = if (session.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    label = "Accept",
                    backgroundColor = AccentEmerald,
                    iconTint = Color.White,
                    onClick = { callManager.acceptCall() }
                )
            }
        } else {
            // Outgoing / Connected: Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConnected) {
                    CallControlButton(
                        icon = if (session.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        label = "Speaker",
                        isActive = session.isSpeakerOn,
                        onClick = { callManager.toggleSpeaker() }
                    )

                    CallControlButton(
                        icon = if (session.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (session.isMicMuted) "Unmute" else "Mute",
                        isActive = !session.isMicMuted,
                        isAlert = session.isMicMuted,
                        onClick = { callManager.toggleMic() }
                    )
                }

                CallActionButton(
                    icon = Icons.Default.CallEnd,
                    label = "End",
                    backgroundColor = AccentDanger,
                    iconTint = Color.White,
                    onClick = { callManager.hangupCall() }
                )
            }
        }
    }
}

@Composable
private fun VideoCallContent(
    session: CallSession,
    callManager: AndroidCallManager
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Remote video background
        val remoteFrame = session.remoteVideoFrame
        if (remoteFrame != null) {
            Image(
                bitmap = remoteFrame.asImageBitmap(),
                contentDescription = "Remote Video",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback screen if remote camera is not transmitting yet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(AccentIndigo.copy(alpha = 0.3f))
                            .border(2.dp, AccentIndigo, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.peer.displayName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "${session.peer.displayName}'s camera is loading...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Gradient overlay at top and bottom for readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // 2. Top Header (Peer info, duration, switch camera)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 20.dp, end = 20.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = session.peer.displayName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatCallDuration(session.durationSecs),
                    color = AccentEmerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Flip camera button
            IconButton(
                onClick = { callManager.flipCamera() },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Switch Camera",
                    tint = Color.White
                )
            }
        }

        // 3. Floating Picture-in-Picture Local Camera Preview (Top Right)
        val localFrame = session.localVideoFrame
        Box(
            modifier = Modifier
                .padding(top = 100.dp, end = 16.dp)
                .size(width = 110.dp, height = 150.dp)
                .align(Alignment.TopEnd)
                .shadow(12.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B))
                .border(2.dp, AccentEmerald.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (localFrame != null && !session.isVideoMuted) {
                Image(
                    bitmap = localFrame.asImageBitmap(),
                    contentDescription = "Local Video",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "Camera Muted",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Off", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        // 4. Floating Bottom Controls Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 20.dp, end = 20.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xCC0F172A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speaker toggle
                IconButton(
                    onClick = { callManager.toggleSpeaker() },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (session.isSpeakerOn) AccentEmerald.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = if (session.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Speaker",
                        tint = if (session.isSpeakerOn) AccentEmerald else Color.White
                    )
                }

                // Camera toggle
                IconButton(
                    onClick = { callManager.toggleVideo() },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (session.isVideoMuted) AccentDanger.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = if (session.isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = "Toggle Video",
                        tint = if (session.isVideoMuted) AccentDanger else Color.White
                    )
                }

                // Mic toggle
                IconButton(
                    onClick = { callManager.toggleMic() },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (session.isMicMuted) AccentDanger.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = if (session.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Toggle Mic",
                        tint = if (session.isMicMuted) AccentDanger else Color.White
                    )
                }

                // End call
                IconButton(
                    onClick = { callManager.hangupCall() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(AccentDanger)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallAudioVisualizer(
    localLevel: Float,
    remoteLevel: Float,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 19
    val effectiveLevel = if (remoteLevel > 0.05f) remoteLevel else (if (!isMuted) localLevel else 0f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val mid = (barCount - 1) / 2f
            val dist = kotlin.math.abs(i - mid) / mid
            val shapeFactor = (1f - dist)

            // Dynamic height based on audio level
            val targetHeight = (4.dp + (36.dp * shapeFactor * (effectiveLevel * 1.5f).coerceIn(0.1f, 1f)))
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(70),
                label = "barHeight_$i"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .width(3.dp)
                    .height(animatedHeight)
                    .clip(CircleShape)
                    .background(
                        if (effectiveLevel > 0.1f) AccentEmerald else TextSecondary.copy(alpha = 0.35f)
                    )
            )
        }
    }
}

@Composable
private fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    isAlert: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isAlert -> AccentDanger.copy(alpha = 0.2f)
                        isActive -> AccentEmerald.copy(alpha = 0.2f)
                        else -> Color.White.copy(alpha = 0.1f)
                    }
                )
                .border(
                    1.dp,
                    when {
                        isAlert -> AccentDanger
                        isActive -> AccentEmerald
                        else -> Color.White.copy(alpha = 0.2f)
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    isAlert -> AccentDanger
                    isActive -> AccentEmerald
                    else -> Color.White
                },
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp
        )
    }
}

private fun formatCallDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

@Composable
fun GroupCallPickerModal(
    network: Network,
    isVideo: Boolean,
    appState: AndroidAppState,
    onDismiss: () -> Unit,
    onCallPeer: (app.neara.core.model.Peer, Boolean) -> Unit
) {
    val currentMsgs by appState.currentMessages.collectAsState()
    val members = remember(network, currentMsgs) {
        val memberMap = network.members.filter { it.peerId != appState.localPeerId }.associateBy { it.peerId }.toMutableMap()
        for (m in currentMsgs) {
            if (m.senderId != appState.localPeerId && m.senderId.isNotBlank() && !memberMap.containsKey(m.senderId)) {
                val name = appState.getSenderDisplayName(m.senderId)
                memberMap[m.senderId] = app.neara.core.model.NetworkMember(m.senderId, name, app.neara.core.model.MemberRole.MEMBER, m.timestamp)
            }
        }
        memberMap.values.toList()
    }
    val discoveredPeers by appState.discoveredPeers.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isVideo) AccentIndigo.copy(alpha = 0.15f) else AccentEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = if (isVideo) AccentIndigo else AccentEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isVideo) "Group Video Call" else "Group Voice Call",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = network.name,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                Text(
                    text = "Select a member to call directly or connect with the group:",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (members.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No other active members listed.",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    appState.startGroupCall(network, isVideo)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isVideo) AccentIndigo else AccentEmerald
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (isVideo) "Call Group (Video)" else "Call Group (Voice)")
                            }
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(members.size) { idx ->
                            val member = members[idx]
                            val isDiscovered = discoveredPeers.any { it.peerId == member.peerId }
                            val targetPeer = appState.getPeerForMember(member.peerId, member.displayName)
                            val isOnline = isDiscovered || targetPeer.connectionState == ConnectionState.CONNECTED

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BgDark,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onCallPeer(targetPeer, isVideo)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isOnline) AccentEmerald.copy(alpha = 0.15f) else AccentIndigo.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = member.displayName.take(1).uppercase(),
                                                color = if (isOnline) AccentEmerald else AccentIndigo,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = member.displayName,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isOnline) AccentEmerald else Color.Gray)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (isOnline) "Online on LAN" else "Offline",
                                                    color = if (isOnline) AccentEmerald else TextMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    // Action buttons
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(
                                            onClick = {
                                                onDismiss()
                                                onCallPeer(targetPeer, false)
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(AccentEmerald.copy(alpha = 0.15f))
                                        ) {
                                            Icon(
                                                Icons.Default.Call,
                                                contentDescription = "Voice Call",
                                                tint = AccentEmerald,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                onDismiss()
                                                onCallPeer(targetPeer, true)
                                            },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(AccentIndigo.copy(alpha = 0.15f))
                                        ) {
                                            Icon(
                                                Icons.Default.Videocam,
                                                contentDescription = "Video Call",
                                                tint = AccentIndigo,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (members.isNotEmpty()) {
                Button(
                    onClick = {
                        val firstAvailable = members.firstOrNull { m ->
                            discoveredPeers.any { it.peerId == m.peerId }
                        } ?: members.first()
                        val targetPeer = appState.getPeerForMember(firstAvailable.peerId, firstAvailable.displayName)
                        onDismiss()
                        onCallPeer(targetPeer, isVideo)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isVideo) AccentIndigo else AccentEmerald
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isVideo) "Call First Available (Video)" else "Call First Available (Voice)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = BgCard,
        shape = RoundedCornerShape(20.dp)
    )
}
