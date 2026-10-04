package app.neara.service

import app.neara.core.interfaces.MeshPacket
import app.neara.core.model.ChatMessage
import app.neara.core.model.ConnectionState
import app.neara.core.model.NetworkType
import app.neara.core.model.Peer
import app.neara.crypto.CryptoUtils
import app.neara.crypto.NearaCryptoEngine
import app.neara.discovery.PeerRepository
import app.neara.discovery.PeerStateManager
import app.neara.storage.DatabaseManager
import app.neara.storage.MessageRepository
import app.neara.storage.OfflineQueueManager
import app.neara.transport.TcpTransportProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServiceTest {

    private lateinit var dbManager: DatabaseManager
    private lateinit var messageRepo: MessageRepository
    private lateinit var offlineQueue: OfflineQueueManager
    private lateinit var cryptoEngine: NearaCryptoEngine
    private lateinit var peerRepo: PeerRepository
    private lateinit var peerManager: PeerStateManager
    private lateinit var transportProvider: TcpTransportProvider

    @BeforeEach
    fun setUp() {
        dbManager = DatabaseManager(":memory:")
        messageRepo = MessageRepository(dbManager)
        offlineQueue = OfflineQueueManager(dbManager, messageRepo)
        cryptoEngine = NearaCryptoEngine("Alice")
        peerRepo = PeerRepository()
        peerManager = PeerStateManager(peerRepo)
        transportProvider = TcpTransportProvider(cryptoEngine.localIdentity.peerId)
    }

    @AfterEach
    fun tearDown() {
        dbManager.close()
        peerManager.stop()
    }

    @Test
    fun `test NetworkManager create network, PIN validation, and QR URI`() {
        val networkManager = NetworkManager(cryptoEngine.localIdentity.peerId, cryptoEngine)

        val net = networkManager.createNetwork(
            name = "University Hackathon",
            type = NetworkType.PRIVATE,
            pin = "4321",
            requireApproval = false
        )

        assertEquals("University Hackathon", net.name)
        assertTrue(net.securityPolicy.requiresPin)
        assertEquals(1, net.members.size)

        // QR invitation URI
        val qrUri = networkManager.generateQrInvitationUri(net.networkId)
        assertTrue(qrUri.startsWith("neara://join?netId=${net.networkId}"))
        assertTrue(qrUri.contains("University"))

        // Join with incorrect PIN fails
        val badJoinReq = JoinNetworkRequest(
            networkId = net.networkId,
            applicantPeerId = "peer-bob",
            applicantDisplayName = "Bob",
            applicantPublicKeyHex = "abcd",
            pinAttemptHash = "invalid-hash",
            signatureHex = "sig"
        )
        val badResp = networkManager.processJoinRequest(badJoinReq)
        assertFalse(badResp.isApproved)

        // Join with correct PIN succeeds
        val correctPinHash = CryptoUtils.sha256Hex(("4321" + net.networkId).toByteArray(Charsets.UTF_8))
        val goodJoinReq = badJoinReq.copy(pinAttemptHash = correctPinHash)
        val goodResp = networkManager.processJoinRequest(goodJoinReq)
        assertTrue(goodResp.isApproved)

        val updatedNet = networkManager.getNetwork(net.networkId)
        assertEquals(2, updatedNet?.members?.size)
    }

    @Test
    fun `test ChatService queues message offline when peer is not connected`() = runBlocking {
        val chatService = ChatService(
            localPeerId = cryptoEngine.localIdentity.peerId,
            transportProvider = transportProvider,
            peerManager = peerManager,
            messageRepository = messageRepo,
            offlineQueueManager = offlineQueue,
            encryptionManager = cryptoEngine
        )

        val msg = ChatMessage(
            messageId = "msg-chat-1",
            conversationId = "conv-offline",
            senderId = cryptoEngine.localIdentity.peerId,
            recipientId = "peer-offline-bob",
            payload = "Delivering when online"
        )

        val result = chatService.sendMessage(msg)
        assertFalse(result.isDelivered)
        assertEquals(1, offlineQueue.getPendingCount())
    }

    @Test
    fun `test MeshRouter loop prevention and duplicate packet suppression`() = runBlocking {
        val meshRouter = MeshRouterImpl(
            localPeerId = cryptoEngine.localIdentity.peerId,
            transportProvider = transportProvider,
            peerManager = peerManager
        )

        val packet = MeshPacket(
            packetId = "mesh-pkt-1",
            sourcePeerId = "peer-source",
            destinationPeerId = cryptoEngine.localIdentity.peerId,
            intermediateHops = listOf("peer-hop-1"),
            ttl = 3,
            payload = "Payload content".toByteArray(Charsets.UTF_8)
        )

        // First packet routes to local destination
        val routed = meshRouter.routePacket(packet)
        assertTrue(routed)

        // Duplicate packet with same packetId is dropped
        val duplicate = meshRouter.routePacket(packet)
        assertFalse(duplicate)

        // Packet with TTL 0 is dropped
        val expiredPacket = packet.copy(packetId = "mesh-pkt-expired", ttl = 0)
        assertFalse(meshRouter.routePacket(expiredPacket))

        // Packet containing localPeerId in intermediate hops (loop) is dropped
        val loopPacket = packet.copy(
            packetId = "mesh-pkt-loop",
            intermediateHops = listOf("peer-hop-1", cryptoEngine.localIdentity.peerId)
        )
        assertFalse(meshRouter.routePacket(loopPacket))
    }
}
