package app.neara.android.call

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import app.neara.core.model.ChatMessage
import app.neara.core.model.MessageType
import app.neara.core.model.Peer
import app.neara.service.ChatService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AndroidCallManager(
    private val context: Context,
    private val localPeer: Peer,
    private val chatService: ChatService,
    private val peerProvider: (String) -> Peer?,
    private val ipResolver: ((String) -> String?)? = null,
    private val onCallEnded: ((Peer, String, CallDirection, String?) -> Unit)? = null
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private var audioStreamer: AudioStreamer? = null
    private var videoStreamer: VideoStreamer? = null
    private var ringtone: Ringtone? = null
    private var timerJob: Job? = null
    private var timeoutJob: Job? = null
    private var isVibrating = false

    private val myAudioPort = 45784
    private val myVideoPort = 45786
    private var remoteAudioPort = 45784
    private var remoteVideoPort = 45786

    fun startCall(peer: Peer, isVideo: Boolean, convId: String? = null) {
        if (_activeCall.value != null) return

        val callId = "call-${UUID.randomUUID().toString().take(8)}"
        val session = CallSession(
            callId = callId,
            peer = peer,
            isVideo = isVideo,
            direction = CallDirection.OUTGOING,
            state = CallState.DIALING,
            conversationId = convId ?: peer.peerId,
            isSpeakerOn = isVideo, // Video call defaults to speaker
            statusMessage = "Calling ${peer.displayName}..."
        )
        _activeCall.value = session

        ToneHelper.playRingback()

        // Send OFFER signal with convId so call logs match group or 1-on-1 chat
        val offerPayload = "CALL_SIG:OFFER|callId:$callId|video:$isVideo|convId:${session.conversationId}|audioPort:$myAudioPort|videoPort:$myVideoPort"
        sendSignal(peer.peerId, offerPayload)

        // 35s timeout
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(35000)
            if (_activeCall.value?.callId == callId && _activeCall.value?.state != CallState.CONNECTED) {
                endCallSession("No answer")
            }
        }
    }

    fun handleCallSignal(senderPeerId: String, payload: String) {
        val parts = payload.removePrefix("CALL_SIG:").split("|").associate {
            val idx = it.indexOf(":")
            if (idx != -1) it.substring(0, idx) to it.substring(idx + 1) else it to ""
        }
        val type = payload.removePrefix("CALL_SIG:").substringBefore("|")

        when (type) {
            "OFFER" -> {
                val callId = parts["callId"] ?: return
                val isVideo = parts["video"]?.toBoolean() ?: false
                val incomingConvId = parts["convId"] ?: senderPeerId
                remoteAudioPort = parts["audioPort"]?.toIntOrNull() ?: 45784
                remoteVideoPort = parts["videoPort"]?.toIntOrNull() ?: 45786

                val current = _activeCall.value
                if (current != null && current.state != CallState.ENDED) {
                    sendSignal(senderPeerId, "CALL_SIG:BUSY|callId:$callId")
                    return
                }

                val senderPeer = peerProvider(senderPeerId) ?: Peer(
                    peerId = senderPeerId,
                    displayName = "Peer-${senderPeerId.take(6)}",
                    publicKeyHex = "",
                    port = 45781
                )

                _activeCall.value = CallSession(
                    callId = callId,
                    peer = senderPeer,
                    isVideo = isVideo,
                    direction = CallDirection.INCOMING,
                    state = CallState.RINGING,
                    conversationId = incomingConvId,
                    isSpeakerOn = isVideo,
                    statusMessage = if (isVideo) "Incoming video call..." else "Incoming voice call..."
                )

                startIncomingAlert()
            }

            "ANSWER" -> {
                val callId = parts["callId"] ?: return
                val current = _activeCall.value ?: return
                if (current.callId != callId) return

                val accepted = parts["accept"]?.toBoolean() ?: false
                if (accepted) {
                    timeoutJob?.cancel()
                    ToneHelper.stopTone()
                    remoteAudioPort = parts["audioPort"]?.toIntOrNull() ?: 45784
                    remoteVideoPort = parts["videoPort"]?.toIntOrNull() ?: 45786

                    connectCall(current)
                } else {
                    val reason = parts["reason"] ?: "Declined"
                    endCallSession(reason)
                }
            }

            "HANGUP" -> {
                val callId = parts["callId"]
                val current = _activeCall.value
                if (current != null && (callId == null || current.callId == callId)) {
                    endCallSession("Call ended")
                }
            }

            "BUSY" -> {
                endCallSession("User is busy")
            }
        }
    }

    fun acceptCall() {
        val current = _activeCall.value ?: return
        if (current.state != CallState.RINGING || current.direction != CallDirection.INCOMING) return

        stopIncomingAlert()

        // Send ANSWER accept signal
        val answerPayload = "CALL_SIG:ANSWER|callId:${current.callId}|accept:true|audioPort:$myAudioPort|videoPort:$myVideoPort"
        sendSignal(current.peer.peerId, answerPayload)

        connectCall(current)
    }

    fun declineCall() {
        val current = _activeCall.value ?: return
        stopIncomingAlert()
        sendSignal(current.peer.peerId, "CALL_SIG:ANSWER|callId:${current.callId}|accept:false|reason:Declined")
        val callLogPayload = "CALL_LOG:type=${if (current.isVideo) "VIDEO" else "AUDIO"}|duration=0|status=MISSED"
        onCallEnded?.invoke(current.peer, callLogPayload, CallDirection.INCOMING, current.conversationId)
        cleanUp()
        _activeCall.value = null
    }

    fun hangupCall() {
        val current = _activeCall.value ?: return
        sendSignal(current.peer.peerId, "CALL_SIG:HANGUP|callId:${current.callId}")
        endCallSession("Call ended")
    }

    private fun connectCall(session: CallSession) {
        val targetIp = session.peer.ipAddress
            ?: peerProvider(session.peer.peerId)?.ipAddress
            ?: ipResolver?.invoke(session.peer.peerId)
            ?: "127.0.0.1"

        // 1. Start audio
        audioStreamer?.stop()
        val audio = AudioStreamer(
            context = context,
            localPort = myAudioPort,
            remoteIp = targetIp,
            remotePort = remoteAudioPort,
            onLocalAudioLevel = { lvl ->
                _activeCall.value = _activeCall.value?.copy(localAudioLevel = lvl)
            },
            onRemoteAudioLevel = { lvl ->
                _activeCall.value = _activeCall.value?.copy(remoteAudioLevel = lvl)
            }
        )
        audioStreamer = audio
        audio.start(speakerOn = session.isSpeakerOn)

        // 2. Start video if video call
        if (session.isVideo) {
            videoStreamer?.stop()
            val video = VideoStreamer(
                localPort = myVideoPort,
                remoteIp = targetIp,
                remotePort = remoteVideoPort,
                onLocalFrame = { frame ->
                    _activeCall.value = _activeCall.value?.copy(localVideoFrame = frame)
                },
                onRemoteFrame = { frame ->
                    _activeCall.value = _activeCall.value?.copy(remoteVideoFrame = frame)
                }
            )
            videoStreamer = video
            video.start(frontCamera = true)
        }

        _activeCall.value = session.copy(
            state = CallState.CONNECTED,
            statusMessage = "Connected"
        )

        // Duration timer
        timerJob?.cancel()
        timerJob = scope.launch {
            var secs = 0
            while (isActive) {
                delay(1000)
                secs++
                _activeCall.value = _activeCall.value?.copy(durationSecs = secs)
            }
        }
    }

    fun toggleMic() {
        val current = _activeCall.value ?: return
        val newMuted = !current.isMicMuted
        audioStreamer?.isMicMuted?.set(newMuted)
        _activeCall.value = current.copy(isMicMuted = newMuted)
    }

    fun toggleSpeaker() {
        val current = _activeCall.value ?: return
        val newSpeaker = !current.isSpeakerOn
        audioStreamer?.setSpeakerphone(newSpeaker)
        _activeCall.value = current.copy(isSpeakerOn = newSpeaker)
    }

    fun toggleVideo() {
        val current = _activeCall.value ?: return
        if (!current.isVideo) return
        val newMuted = !current.isVideoMuted
        videoStreamer?.isVideoMuted?.set(newMuted)
        _activeCall.value = current.copy(isVideoMuted = newMuted)
    }

    fun flipCamera() {
        val current = _activeCall.value ?: return
        if (!current.isVideo) return
        videoStreamer?.flipCamera()
        _activeCall.value = current.copy(isFrontCamera = videoStreamer?.isFrontCamera ?: true)
    }

    private fun endCallSession(reason: String) {
        val current = _activeCall.value
        if (current != null) {
            val status = when {
                current.state == CallState.CONNECTED -> "COMPLETED"
                current.direction == CallDirection.INCOMING -> "MISSED"
                else -> "CANCELLED"
            }
            val callLogPayload = "CALL_LOG:type=${if (current.isVideo) "VIDEO" else "AUDIO"}|duration=${current.durationSecs}|status=$status"
            onCallEnded?.invoke(current.peer, callLogPayload, current.direction, current.conversationId)
        }

        ToneHelper.stopTone()
        stopIncomingAlert()
        timeoutJob?.cancel()
        timerJob?.cancel()

        ToneHelper.playEndBeep()

        _activeCall.value = _activeCall.value?.copy(
            state = CallState.ENDED,
            statusMessage = reason
        )

        scope.launch {
            delay(1500)
            cleanUp()
            _activeCall.value = null
        }
    }

    private fun cleanUp() {
        ToneHelper.stopTone()
        stopIncomingAlert()
        timeoutJob?.cancel()
        timerJob?.cancel()

        audioStreamer?.stop()
        audioStreamer = null

        videoStreamer?.stop()
        videoStreamer = null
    }

    private fun sendSignal(targetPeerId: String, payload: String) {
        scope.launch(Dispatchers.IO) {
            val msg = ChatMessage(
                messageId = "sig-${UUID.randomUUID().toString().take(8)}",
                conversationId = targetPeerId,
                senderId = localPeer.peerId,
                recipientId = targetPeerId,
                timestamp = System.currentTimeMillis(),
                type = MessageType.SYSTEM,
                payload = payload
            )
            chatService.sendMessage(msg)
        }
    }

    private fun startIncomingAlert() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {}

        startVibration()
    }

    private fun stopIncomingAlert() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {}

        stopVibration()
    }

    private fun startVibration() {
        isVibrating = true
        scope.launch {
            val pattern = longArrayOf(0, 800, 1000)
            while (isVibrating && isActive) {
                try {
                    val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    v?.vibrate(effect)
                } catch (e: Exception) {}
                delay(1800)
            }
        }
    }

    private fun stopVibration() {
        isVibrating = false
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            v?.cancel()
        } catch (e: Exception) {}
    }

    fun close() {
        cleanUp()
        scope.cancel()
    }
}
