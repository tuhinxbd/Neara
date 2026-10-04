package app.neara.service

import app.neara.core.interfaces.EncryptionManager
import app.neara.core.interfaces.MessageDeliveryResult
import app.neara.core.interfaces.MessageTransport
import app.neara.core.interfaces.PeerManager
import app.neara.core.model.*
import app.neara.protocol.FrameCodec
import app.neara.protocol.ProtocolConstants
import app.neara.protocol.ProtocolFrame
import app.neara.storage.IMessageRepository
import app.neara.storage.IOfflineQueueManager
import app.neara.transport.TcpPeerConnection
import app.neara.transport.TcpTransportProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WireChatMessage(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String?,
    val timestamp: Long,
    val sequenceNumber: Long,
    val type: MessageType,
    val ivHex: String,
    val encryptedPayloadHex: String,
    val signatureHex: String,
    val replyToMessageId: String? = null
)

@Serializable
data class WireMessageAck(
    val messageId: String,
    val conversationId: String,
    val acknowledgedByPeerId: String,
    val status: MessageStatus,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatService(
    private val localPeerId: String,
    private val transportProvider: TcpTransportProvider,
    private val peerManager: PeerManager,
    private val messageRepository: IMessageRepository,
    private val offlineQueueManager: IOfflineQueueManager,
    private val encryptionManager: EncryptionManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : MessageTransport {

    private val json = Json { ignoreUnknownKeys = true }
    private val _incomingMessages = MutableSharedFlow<ChatMessage>(replay = 20, extraBufferCapacity = 50)
    private val _deliveryReceipts = MutableSharedFlow<WireMessageAck>(replay = 20, extraBufferCapacity = 50)
    val deliveryReceipts: SharedFlow<WireMessageAck> = _deliveryReceipts.asSharedFlow()

    private val _incomingJoinResponses = MutableSharedFlow<JoinNetworkResponse>(replay = 5, extraBufferCapacity = 20)
    val incomingJoinResponses: SharedFlow<JoinNetworkResponse> = _incomingJoinResponses.asSharedFlow()

    var networkManager: NetworkManager? = null

    private val processedMessageIds = ConcurrentHashMap.newKeySet<String>()
    private val listeningConnections = ConcurrentHashMap.newKeySet<TcpPeerConnection>()

    init {
        // Monitor peer states to automatically drain offline queue upon discovery or reconnection
        scope.launch {
            peerManager.peers.collect { peerList ->
                for (peer in peerList) {
                    if (peer.ipAddress != null && offlineQueueManager.getPendingForPeer(peer.peerId).isNotEmpty()) {
                        drainOfflineQueue(peer.peerId)
                    }
                }
            }
        }
    }

    fun handleIncomingConnection(connection: TcpPeerConnection) {
        if (!listeningConnections.add(connection)) return
        scope.launch {
            try {
                connection.receiveFrames().collect { frame ->
                    when (frame.type) {
                        ProtocolConstants.TYPE_CHAT_MESSAGE -> processIncomingMessageFrame(frame, connection)
                        ProtocolConstants.TYPE_MESSAGE_ACK -> processIncomingAckFrame(frame)
                        ProtocolConstants.TYPE_NETWORK_JOIN_REQ -> processIncomingJoinReqFrame(frame, connection)
                        ProtocolConstants.TYPE_NETWORK_JOIN_RESP -> processIncomingJoinRespFrame(frame)
                    }
                }
            } finally {
                listeningConnections.remove(connection)
            }
        }
    }

    private suspend fun processIncomingMessageFrame(frame: ProtocolFrame, connection: TcpPeerConnection) {
        try {
            val wireMsg = json.decodeFromString<WireChatMessage>(String(frame.payload, Charsets.UTF_8))

            // Map incoming socket to sender's peer ID so replies reuse the established connection
            transportProvider.registerConnection(wireMsg.senderId, connection)
            peerManager.updatePeerState(wireMsg.senderId, ConnectionState.CONNECTED)

            // Duplicate prevention
            if (!processedMessageIds.add(wireMsg.messageId)) {
                // Already processed, just send ACK back
                sendAck(connection, wireMsg.messageId, wireMsg.conversationId, MessageStatus.DELIVERED)
                return
            }

            // Verify signature
            val senderPeer = peerManager.getPeer(wireMsg.senderId)
            val senderPubKey = if (senderPeer != null) hexToBytes(senderPeer.publicKeyHex) else null

            // Decrypt payload
            val plaintext = if (senderPubKey != null) {
                try {
                    val encryptedPayload = app.neara.core.interfaces.EncryptedPayload(
                        iv = hexToBytes(wireMsg.ivHex),
                        ciphertext = hexToBytes(wireMsg.encryptedPayloadHex),
                        senderPublicKeyHash = ByteArray(0)
                    )
                    String(encryptionManager.decryptPayload(senderPubKey, encryptedPayload), Charsets.UTF_8)
                } catch (e: Exception) {
                    // Fallback if encryption mismatch
                    String(hexToBytes(wireMsg.encryptedPayloadHex), Charsets.UTF_8)
                }
            } else {
                String(hexToBytes(wireMsg.encryptedPayloadHex), Charsets.UTF_8)
            }

            val chatMsg = ChatMessage(
                messageId = wireMsg.messageId,
                conversationId = wireMsg.conversationId,
                senderId = wireMsg.senderId,
                recipientId = wireMsg.recipientId,
                timestamp = wireMsg.timestamp,
                sequenceNumber = wireMsg.sequenceNumber,
                type = wireMsg.type,
                payload = plaintext,
                signature = wireMsg.signatureHex,
                status = MessageStatus.DELIVERED,
                replyToMessageId = wireMsg.replyToMessageId
            )
            val isActionMsg = chatMsg.type == MessageType.ACTION || plaintext.startsWith("ACTION:")
            if (isActionMsg) {
                if (plaintext.startsWith("ACTION:UNSEND:")) {
                    val targetId = plaintext.removePrefix("ACTION:UNSEND:").trim()
                    messageRepository.deleteMessage(targetId)
                } else if (plaintext.startsWith("ACTION:EDIT:")) {
                    val parts = plaintext.removePrefix("ACTION:EDIT:").split(":", limit = 2)
                    if (parts.size == 2) {
                        val targetId = parts[0]
                        val newContent = parts[1]
                        val existing = messageRepository.getMessage(targetId)
                        if (existing != null) {
                            messageRepository.saveMessage(existing.copy(payload = newContent))
                        }
                    }
                } else if (plaintext.startsWith("ACTION:REACT_ADD:")) {
                    val parts = plaintext.removePrefix("ACTION:REACT_ADD:").split(":", limit = 2)
                    if (parts.size == 2) {
                        val targetId = parts[0]
                        val emoji = parts[1]
                        val existing = messageRepository.getMessage(targetId)
                        if (existing != null) {
                            val map = existing.reactions.mapValues { it.value.toMutableList() }.toMutableMap()
                            // Remove sender from any other emoji on this message (Messenger style: 1 reaction per user)
                            for ((em, senders) in map) {
                                if (em != emoji) senders.remove(chatMsg.senderId)
                            }
                            val list = map[emoji] ?: mutableListOf()
                            if (!list.contains(chatMsg.senderId)) list.add(chatMsg.senderId)
                            map[emoji] = list
                            val cleaned = map.filter { it.value.isNotEmpty() }
                            messageRepository.saveMessage(existing.copy(reactions = cleaned))
                        }
                    }
                } else if (plaintext.startsWith("ACTION:REACT_REMOVE:")) {
                    val parts = plaintext.removePrefix("ACTION:REACT_REMOVE:").split(":", limit = 2)
                    if (parts.size == 2) {
                        val targetId = parts[0]
                        val emoji = parts[1]
                        val existing = messageRepository.getMessage(targetId)
                        if (existing != null) {
                            val map = existing.reactions.mapValues { it.value.toMutableList() }.toMutableMap()
                            val list = map[emoji] ?: mutableListOf()
                            list.remove(chatMsg.senderId)
                            if (list.isEmpty()) map.remove(emoji) else map[emoji] = list
                            val cleaned = map.filter { it.value.isNotEmpty() }
                            messageRepository.saveMessage(existing.copy(reactions = cleaned))
                        }
                    }
                }
                _incomingMessages.emit(chatMsg)
                sendAck(connection, chatMsg.messageId, chatMsg.conversationId, MessageStatus.DELIVERED)
                return
            }

            val isCallSig = chatMsg.type == MessageType.SYSTEM && plaintext.startsWith("CALL_SIG:")
            if (isCallSig) {
                _incomingMessages.emit(chatMsg)
                sendAck(connection, chatMsg.messageId, chatMsg.conversationId, MessageStatus.DELIVERED)
                return
            }

            messageRepository.saveMessage(chatMsg)
            _incomingMessages.emit(chatMsg)

            // Send DELIVERED ACK back to sender
            sendAck(connection, chatMsg.messageId, chatMsg.conversationId, MessageStatus.DELIVERED)

        } catch (e: Exception) {
            // Log/handle malformed frame
        }
    }

    private suspend fun processIncomingAckFrame(frame: ProtocolFrame) {
        try {
            val ack = json.decodeFromString<WireMessageAck>(String(frame.payload, Charsets.UTF_8))
            messageRepository.updateMessageStatus(ack.messageId, ack.status)
            offlineQueueManager.remove(ack.messageId)
            _deliveryReceipts.emit(ack)
        } catch (e: Exception) {}
    }

    private suspend fun sendAck(connection: TcpPeerConnection, messageId: String, conversationId: String, status: MessageStatus) {
        val ack = WireMessageAck(
            messageId = messageId,
            conversationId = conversationId,
            acknowledgedByPeerId = localPeerId,
            status = status
        )
        val payload = json.encodeToString(ack).toByteArray(Charsets.UTF_8)
        val frame = ProtocolFrame(type = ProtocolConstants.TYPE_MESSAGE_ACK, payload = payload)
        connection.sendFrame(frame)
    }

    private suspend fun processIncomingJoinReqFrame(frame: ProtocolFrame, connection: TcpPeerConnection) {
        try {
            val req = json.decodeFromString<JoinNetworkRequest>(String(frame.payload, Charsets.UTF_8))
            transportProvider.registerConnection(req.applicantPeerId, connection)
            peerManager.updatePeerState(req.applicantPeerId, ConnectionState.CONNECTED)

            val netMgr = networkManager ?: return
            val resp = netMgr.processJoinRequest(req)

            // If approved, bundle recent chat history into the response
            val finalResp = if (resp.isApproved) {
                val history = messageRepository.getMessagesForConversation(req.networkId, limit = 100)
                resp.copy(recentMessages = history)
            } else {
                resp
            }

            val respPayload = json.encodeToString(finalResp).toByteArray(Charsets.UTF_8)
            val respFrame = ProtocolFrame(
                type = ProtocolConstants.TYPE_NETWORK_JOIN_RESP,
                payload = respPayload
            )
            connection.sendFrame(respFrame)

            if (finalResp.isApproved) {
                val sysMsg = ChatMessage(
                    messageId = "sys-${UUID.randomUUID().toString().take(8)}",
                    conversationId = req.networkId,
                    senderId = req.applicantPeerId,
                    recipientId = null,
                    timestamp = System.currentTimeMillis(),
                    sequenceNumber = System.currentTimeMillis(),
                    type = MessageType.SYSTEM,
                    payload = "${req.applicantDisplayName} joined the group",
                    status = MessageStatus.SENT
                )
                messageRepository.saveMessage(sysMsg)
                _incomingMessages.emit(sysMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun processIncomingJoinRespFrame(frame: ProtocolFrame) {
        try {
            val resp = json.decodeFromString<JoinNetworkResponse>(String(frame.payload, Charsets.UTF_8))
            _incomingJoinResponses.emit(resp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun sendMessage(message: ChatMessage): MessageDeliveryResult = withContext(Dispatchers.IO) {
        val isAction = message.type == MessageType.ACTION || message.payload.startsWith("ACTION:")
        // Save initial message state (skip for control/action messages)
        if (!isAction) {
            messageRepository.saveMessage(message.copy(status = MessageStatus.SENDING))
        }

        val recipientId = message.recipientId
        if (recipientId == null || message.conversationId.startsWith("net-")) {
            // Group message broadcast to all discovered peers
            if (!isAction) {
                messageRepository.saveMessage(message.copy(status = MessageStatus.SENT))
            }
            val currentPeers = peerManager.peers.value
            for (p in currentPeers) {
                val ip = p.ipAddress
                if (p.peerId != localPeerId && ip != null) {
                    try {
                        var conn = transportProvider.getConnection(p.peerId)
                        if (conn == null || !conn.isConnected) {
                            val newConn = transportProvider.connect(ip, p.port, p.peerId) as? TcpPeerConnection
                            if (newConn != null && newConn.isConnected) {
                                conn = newConn
                                handleIncomingConnection(newConn)
                                peerManager.updatePeerState(p.peerId, ConnectionState.CONNECTED)
                            }
                        }
                        if (conn != null && conn.isConnected) {
                            sendOverConnection(conn, message, p)
                        }
                    } catch (e: Exception) {}
                }
            }
            return@withContext MessageDeliveryResult(message.messageId, true)
        }

        val targetPeer = peerManager.getPeer(recipientId)
        var activeConn = transportProvider.getConnection(recipientId)

        // Attempt direct connection if not currently connected
        if (activeConn == null || !activeConn.isConnected) {
            val ip = targetPeer?.ipAddress
            if (ip != null) {
                try {
                    val newConn = transportProvider.connect(ip, targetPeer.port, recipientId) as? TcpPeerConnection
                    if (newConn != null && newConn.isConnected) {
                        activeConn = newConn
                        handleIncomingConnection(newConn)
                        peerManager.updatePeerState(recipientId, ConnectionState.CONNECTED)
                    }
                } catch (e: Exception) {}
            }
        }

        if (activeConn != null && activeConn.isConnected) {
            val sent = sendOverConnection(activeConn, message, targetPeer)
            if (sent) {
                if (!isAction) {
                    messageRepository.updateMessageStatus(message.messageId, MessageStatus.SENT)
                }
                drainOfflineQueue(recipientId)
                return@withContext MessageDeliveryResult(message.messageId, true)
            }
        }

        // Peer is offline or socket failed -> Enqueue for reliable offline delivery
        if (!isAction && recipientId != null) {
            offlineQueueManager.enqueue(message, recipientId)
            messageRepository.updateMessageStatus(message.messageId, MessageStatus.PENDING)
        }
        MessageDeliveryResult(message.messageId, false, if (isAction) "Recipient offline" else "Queued offline; will deliver upon peer reconnection")
    }

    private suspend fun sendOverConnection(connection: TcpPeerConnection, message: ChatMessage, targetPeer: Peer?): Boolean {
        return try {
            val recipientPubKey = if (targetPeer != null) hexToBytes(targetPeer.publicKeyHex) else null

            val (encryptedBytes, ivBytes) = if (recipientPubKey != null) {
                try {
                    val enc = encryptionManager.encryptPayload(recipientPubKey, message.payload.toByteArray(Charsets.UTF_8))
                    Pair(enc.ciphertext, enc.iv)
                } catch (e: Exception) {
                    Pair(message.payload.toByteArray(Charsets.UTF_8), ByteArray(12))
                }
            } else {
                Pair(message.payload.toByteArray(Charsets.UTF_8), ByteArray(12))
            }

            val signature = bytesToHex(encryptionManager.sign(encryptedBytes))

            val wireMsg = WireChatMessage(
                messageId = message.messageId,
                conversationId = message.conversationId,
                senderId = localPeerId,
                recipientId = message.recipientId,
                timestamp = message.timestamp,
                sequenceNumber = message.sequenceNumber,
                type = message.type,
                ivHex = bytesToHex(ivBytes),
                encryptedPayloadHex = bytesToHex(encryptedBytes),
                signatureHex = signature,
                replyToMessageId = message.replyToMessageId
            )

            val payload = json.encodeToString(wireMsg).toByteArray(Charsets.UTF_8)
            val frame = ProtocolFrame(type = ProtocolConstants.TYPE_CHAT_MESSAGE, payload = payload)
            connection.sendFrame(frame)
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun drainOfflineQueue(peerId: String) = withContext(Dispatchers.IO) {
        val pendingList = offlineQueueManager.getPendingForPeer(peerId)
        if (pendingList.isEmpty()) return@withContext

        val targetPeer = peerManager.getPeer(peerId) ?: return@withContext
        val conn = transportProvider.getConnection(peerId) ?: run {
            val ip = targetPeer.ipAddress
            if (ip != null) {
                val newConn = transportProvider.connect(ip, targetPeer.port, peerId) as? TcpPeerConnection
                if (newConn != null && newConn.isConnected) {
                    handleIncomingConnection(newConn)
                    peerManager.updatePeerState(peerId, ConnectionState.CONNECTED)
                }
                newConn
            } else null
        } ?: return@withContext

        for (msg in pendingList) {
            val success = sendOverConnection(conn, msg, targetPeer)
            if (success) {
                messageRepository.updateMessageStatus(msg.messageId, MessageStatus.SENT)
                offlineQueueManager.remove(msg.messageId)
            } else {
                offlineQueueManager.incrementRetry(msg.messageId)
                break
            }
        }
    }

    override fun observeIncomingMessages(): Flow<ChatMessage> = _incomingMessages.asSharedFlow()

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().lowercase()
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            result[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
}
