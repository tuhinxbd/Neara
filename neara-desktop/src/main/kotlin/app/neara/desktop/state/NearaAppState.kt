package app.neara.desktop.state

import app.neara.core.interfaces.AudioPacket
import app.neara.core.interfaces.DeviceIdentity
import app.neara.core.model.*
import app.neara.crypto.CryptoUtils
import app.neara.crypto.NearaCryptoEngine
import app.neara.discovery.DiscoveryService
import app.neara.discovery.PeerRepository
import app.neara.discovery.PeerStateManager
import app.neara.discovery.UdpDiscoveryProvider
import app.neara.service.ChatService
import app.neara.service.FileTransferService
import app.neara.service.MeshRouterImpl
import app.neara.service.NetworkManager
import app.neara.service.VoiceService
import app.neara.storage.DatabaseManager
import app.neara.storage.MessageRepository
import app.neara.storage.OfflineQueueManager
import app.neara.transport.TcpTransportProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.UUID

enum class AppTab {
    NEARBY,
    CHATS,
    NETWORKS,
    FILES,
    PROFILE,
    SETTINGS
}

class NearaAppState(
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    // 1. Identity & Crypto (Persisted to maintain stable peerId across app restarts)
    private fun loadOrCreateIdentity(): DeviceIdentity {
        val identityFile = File(System.getProperty("user.home"), ".neara/identity.properties")
        try {
            if (identityFile.exists()) {
                val props = java.util.Properties()
                identityFile.inputStream().use { props.load(it) }
                val peerId = props.getProperty("peer_id")
                val displayName = props.getProperty("display_name")
                val signingPub = props.getProperty("signing_pub")
                val signingPriv = props.getProperty("signing_priv")
                val agreementPub = props.getProperty("agreement_pub")
                val agreementPriv = props.getProperty("agreement_priv")
                if (peerId != null && signingPub != null && signingPriv != null && agreementPub != null && agreementPriv != null) {
                    return DeviceIdentity(
                        peerId = peerId,
                        displayName = displayName ?: "Desktop-User",
                        signingPublicKey = CryptoUtils.fromHex(signingPub),
                        signingPrivateKey = CryptoUtils.fromHex(signingPriv),
                        agreementPublicKey = CryptoUtils.fromHex(agreementPub),
                        agreementPrivateKey = CryptoUtils.fromHex(agreementPriv)
                    )
                }
            }
        } catch (e: Exception) {}

        val newIdentity = NearaCryptoEngine.generateIdentity("Desktop-User")
        try {
            identityFile.parentFile?.mkdirs()
            val props = java.util.Properties()
            props.setProperty("peer_id", newIdentity.peerId)
            props.setProperty("display_name", newIdentity.displayName)
            props.setProperty("signing_pub", CryptoUtils.toHex(newIdentity.signingPublicKey))
            props.setProperty("signing_priv", CryptoUtils.toHex(newIdentity.signingPrivateKey))
            props.setProperty("agreement_pub", CryptoUtils.toHex(newIdentity.agreementPublicKey))
            props.setProperty("agreement_priv", CryptoUtils.toHex(newIdentity.agreementPrivateKey))
            identityFile.outputStream().use { props.store(it, "Neara Desktop Identity") }
        } catch (e: Exception) {}
        return newIdentity
    }

    private val deviceIdentity = loadOrCreateIdentity()
    val cryptoEngine = NearaCryptoEngine(displayName = deviceIdentity.displayName, savedIdentity = deviceIdentity)
    val localPeerId: String get() = cryptoEngine.localIdentity.peerId

    val localIpAddress: String = run {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            val nonLoopback = interfaces.flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
            nonLoopback?.hostAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }
    val localPort = 45781

    val localPeer = Peer(
        peerId = localPeerId,
        displayName = cryptoEngine.localIdentity.displayName,
        avatarId = "avatar_desktop",
        publicKeyHex = app.neara.crypto.CryptoUtils.toHex(cryptoEngine.localIdentity.agreementPublicKey),
        port = localPort,
        ipAddress = localIpAddress
    )

    // 2. Storage
    val dbManager = DatabaseManager(File(System.getProperty("user.home"), ".neara/neara.db").absolutePath)
    val messageRepository = MessageRepository(dbManager)
    val offlineQueueManager = OfflineQueueManager(dbManager, messageRepository)

    // 3. Transport & Discovery
    val peerRepository = PeerRepository()
    val peerStateManager = PeerStateManager(peerRepository)
    val transportProvider = TcpTransportProvider(localPeerId)
    val discoveryProvider = UdpDiscoveryProvider(localPeer = localPeer)
    val discoveryService = DiscoveryService(peerStateManager, discoveryProvider)

    // 4. Services
    val chatService = ChatService(
        localPeerId = localPeerId,
        transportProvider = transportProvider,
        peerManager = peerStateManager,
        messageRepository = messageRepository,
        offlineQueueManager = offlineQueueManager,
        encryptionManager = cryptoEngine
    )
    val networkManager = NetworkManager(localPeerId, cryptoEngine, File(System.getProperty("user.home"), ".neara/networks.json"))
    val meshRouter = MeshRouterImpl(localPeerId, transportProvider, peerStateManager)
    val fileTransferService = FileTransferService(localPeerId, transportProvider)
    val voiceService = VoiceService(localPeerId, captureProvider = app.neara.desktop.audio.DesktopAudioCaptureProvider())

    // UI States
    val currentTab = MutableStateFlow(AppTab.NEARBY)
    val isVisible = MutableStateFlow(true)
    val meshRelayEnabled = MutableStateFlow(true)
    val activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationPeer = MutableStateFlow<Peer?>(null)
    val currentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val activeTransfers = MutableStateFlow<List<FileTransferProgress>>(emptyList())
    val isPttActive = MutableStateFlow(false)
    val offlinePendingCount = MutableStateFlow(0)

    val discoveredPeers: StateFlow<List<Peer>> = peerStateManager.peers
    val activeNetworks: StateFlow<List<Network>> = networkManager.networksFlow

    init {
        start()
    }

    private fun start() {
        scope.launch(Dispatchers.IO) {
            // Start TCP server
            val incoming = transportProvider.startServer(localPort)
            launch {
                incoming.collect { conn ->
                    if (conn is app.neara.transport.TcpPeerConnection) {
                        chatService.handleIncomingConnection(conn)
                    }
                }
            }

            // Start UDP discovery
            discoveryService.start()

            // Observe incoming messages
            launch {
                chatService.observeIncomingMessages().collect { msg ->
                    refreshActiveMessages()
                    updateOfflineCount()
                }
            }

            // Observe delivery receipts
            launch {
                chatService.deliveryReceipts.collect { ack ->
                    refreshActiveMessages()
                    updateOfflineCount()
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        currentTab.value = tab
    }

    fun toggleVisibility(visible: Boolean = !isVisible.value) {
        isVisible.value = visible
        discoveryProvider.setVisible(visible)
    }

    fun selectConversation(peer: Peer) {
        activeConversationPeer.value = peer
        val convId = "conv-${listOf(localPeerId, peer.peerId).sorted().joinToString("-")}"
        activeConversationId.value = convId
        currentTab.value = AppTab.CHATS
        refreshActiveMessages()
    }

    fun getSenderDisplayName(senderId: String): String {
        if (senderId == localPeerId) return "You"
        activeConversationPeer.value?.let { if (it.peerId == senderId) return it.displayName }
        discoveredPeers.value.find { it.peerId == senderId }?.let { return it.displayName }
        for (net in networkManager.networksFlow.value) {
            val member = net.members.find { it.peerId == senderId }
            if (member != null) return member.displayName
        }
        return "User-${senderId.take(6)}"
    }

    fun refreshActiveMessages() {
        val convId = activeConversationId.value ?: return
        currentMessages.value = messageRepository.getMessagesForConversation(convId)
        updateOfflineCount()
    }

    fun updateOfflineCount() {
        offlinePendingCount.value = offlineQueueManager.getPendingCount()
    }

    fun sendTextMessage(text: String) {
        val convId = activeConversationId.value ?: return
        val targetPeer = activeConversationPeer.value ?: return
        if (text.isBlank()) return

        val msg = ChatMessage(
            messageId = "msg-${UUID.randomUUID().toString().take(8)}",
            conversationId = convId,
            senderId = localPeerId,
            recipientId = targetPeer.peerId,
            timestamp = System.currentTimeMillis(),
            sequenceNumber = System.currentTimeMillis(),
            type = MessageType.TEXT,
            payload = text,
            status = MessageStatus.PENDING
        )

        scope.launch {
            chatService.sendMessage(msg)
            refreshActiveMessages()
            updateOfflineCount()
        }
    }

    fun startPtt() {
        isPttActive.value = true
        scope.launch {
            voiceService.startPushToTalk(activeConversationPeer.value?.peerId ?: "broadcast", false)
        }
    }

    fun stopPtt() {
        isPttActive.value = false
        scope.launch {
            voiceService.stopPushToTalk()
        }
    }

    fun toggleMeshRelay(enabled: Boolean) {
        meshRelayEnabled.value = enabled
    }

    fun clearChatHistory() {
        messageRepository.clearAll()
        currentMessages.value = emptyList()
    }

    fun purgeOfflineQueue() {
        offlineQueueManager.clear()
        updateOfflineCount()
    }

    fun close() {
        discoveryService.stop()
        scope.launch(Dispatchers.IO) {
            transportProvider.stopServer()
            voiceService.close()
            dbManager.close()
        }
    }
}
