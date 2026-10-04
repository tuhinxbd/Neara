package app.neara.core.interfaces

import app.neara.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream

interface DiscoveryProvider {
    val transportType: TransportType
    fun startDiscovery(): Flow<DiscoveryEvent>
    fun stopDiscovery()
    fun broadcastPresence(peer: Peer)
    fun setVisible(visible: Boolean)
}

interface PeerConnection {
    val peerId: String
    val isConnected: Boolean
    suspend fun sendBytes(data: ByteArray): Boolean
    fun receiveBytes(): Flow<ByteArray>
    suspend fun close()
}

interface TransportProvider {
    val transportType: TransportType
    suspend fun startServer(port: Int): Flow<PeerConnection>
    suspend fun connect(ipAddress: String, port: Int, targetPeerId: String): PeerConnection?
    suspend fun stopServer()
}

interface PeerManager {
    val peers: StateFlow<List<Peer>>
    val activeConnections: StateFlow<Map<String, ConnectionState>>
    fun getPeer(peerId: String): Peer?
    fun updatePeerState(peerId: String, state: ConnectionState)
    fun registerPeer(peer: Peer)
    fun removePeer(peerId: String)
}

data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val senderPublicKeyHash: ByteArray,
    val authTag: ByteArray = ByteArray(0)
)

data class DeviceIdentity(
    val peerId: String,
    val displayName: String,
    val signingPublicKey: ByteArray,
    val signingPrivateKey: ByteArray,
    val agreementPublicKey: ByteArray,
    val agreementPrivateKey: ByteArray
)

interface EncryptionManager {
    val localIdentity: DeviceIdentity
    fun encryptPayload(recipientPublicKey: ByteArray, plaintext: ByteArray): EncryptedPayload
    fun decryptPayload(senderPublicKey: ByteArray, encrypted: EncryptedPayload): ByteArray
    fun sign(data: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean
}

data class MessageDeliveryResult(
    val messageId: String,
    val isDelivered: Boolean,
    val error: String? = null
)

interface MessageTransport {
    suspend fun sendMessage(message: ChatMessage): MessageDeliveryResult
    fun observeIncomingMessages(): Flow<ChatMessage>
}

interface FileTransport {
    suspend fun sendFile(peerId: String, file: File, metadata: FileMetadata): Flow<FileTransferProgress>
    fun observeIncomingFiles(): Flow<Pair<FileMetadata, File>>
}

data class AudioPacket(
    val senderId: String,
    val sequenceNumber: Long,
    val timestamp: Long,
    val audioData: ByteArray
)

interface VoiceTransport {
    suspend fun startPushToTalk(targetId: String, isGroup: Boolean)
    suspend fun stopPushToTalk()
    fun observeIncomingAudio(): Flow<AudioPacket>
}

data class MeshPacket(
    val packetId: String,
    val sourcePeerId: String,
    val destinationPeerId: String,
    val intermediateHops: List<String> = emptyList(),
    val ttl: Int = 5,
    val payload: ByteArray
)

interface MeshRouter {
    suspend fun routePacket(packet: MeshPacket): Boolean
    fun observeRoutedPackets(): Flow<MeshPacket>
    fun updateRoutingTable(destinationPeerId: String, nextHopPeerId: String, cost: Int)
}
