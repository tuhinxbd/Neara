package app.neara.android.call

import android.graphics.Bitmap
import app.neara.core.model.Peer

enum class CallState {
    IDLE,
    DIALING,    // Caller waiting for remote peer to ring
    RINGING,    // Receiver ringing or Caller hearing ringback tone
    CONNECTED,  // Active audio/video call
    ENDED       // Call finished/declined/missed
}

enum class CallDirection {
    OUTGOING,
    INCOMING
}

data class CallSession(
    val callId: String,
    val peer: Peer,
    val isVideo: Boolean,
    val direction: CallDirection,
    val state: CallState,
    val conversationId: String? = null,
    val durationSecs: Int = 0,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoMuted: Boolean = false,
    val isFrontCamera: Boolean = true,
    val localAudioLevel: Float = 0f,
    val remoteAudioLevel: Float = 0f,
    val localVideoFrame: Bitmap? = null,
    val remoteVideoFrame: Bitmap? = null,
    val statusMessage: String = ""
)
