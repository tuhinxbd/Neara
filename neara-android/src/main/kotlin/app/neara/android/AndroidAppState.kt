package app.neara.android

import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
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
import app.neara.service.JoinNetworkRequest
import app.neara.storage.IMessageRepository
import app.neara.storage.InMemoryOfflineQueueManager
import app.neara.storage.JsonFileMessageRepository
import app.neara.transport.TcpTransportProvider
import app.neara.transport.TcpPeerConnection
import app.neara.protocol.ProtocolConstants
import app.neara.protocol.ProtocolFrame
import android.widget.Toast
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

enum class AndroidTab {
    CHATS,
    NEARBY,
    NETWORKS,
    FILES,
    PROFILE,
    SETTINGS
}

data class ConversationItem(
    val conversationId: String,
    val isGroup: Boolean,
    val peer: Peer? = null,
    val network: Network? = null,
    val title: String,
    val lastMessageText: String,
    val lastMessageTime: Long,
    val lastStatus: MessageStatus = MessageStatus.SENT,
    val isLastSelf: Boolean = false,
    val isOnline: Boolean = false,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false
)

class AndroidAppState(
    private val context: Context,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private var multicastLock: WifiManager.MulticastLock? = null

    // 1. Identity & Crypto (Persisted to maintain stable peerId across app restarts)
    private fun loadOrCreateIdentity(): DeviceIdentity {
        val prefs = context.getSharedPreferences("neara_identity_prefs", Context.MODE_PRIVATE)

        // Derive a stable device-level peer ID from ANDROID_ID — survives app data clears
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" } // exclude emulator default
        val stablePeerId = if (androidId != null) "peer-${androidId.take(16)}" else null

        val savedPeerId = prefs.getString("peer_id", null)
        val savedDisplayName = prefs.getString("display_name", null)
        val savedSigningPub = prefs.getString("signing_pub", null)
        val savedSigningPriv = prefs.getString("signing_priv", null)
        val savedAgreementPub = prefs.getString("agreement_pub", null)
        val savedAgreementPriv = prefs.getString("agreement_priv", null)

        // Restore from prefs if keys are saved — update peerId to stable one if it changed
        if (savedSigningPub != null && savedSigningPriv != null &&
            savedAgreementPub != null && savedAgreementPriv != null) {
            try {
                val resolvedPeerId = stablePeerId ?: savedPeerId ?: "peer-unknown"
                val identity = DeviceIdentity(
                    peerId = resolvedPeerId,
                    displayName = savedDisplayName ?: "Android-${android.os.Build.MODEL.take(8)}",
                    signingPublicKey = CryptoUtils.fromHex(savedSigningPub),
                    signingPrivateKey = CryptoUtils.fromHex(savedSigningPriv),
                    agreementPublicKey = CryptoUtils.fromHex(savedAgreementPub),
                    agreementPrivateKey = CryptoUtils.fromHex(savedAgreementPriv)
                )
                // Persist the stable peerId back (in case it was just upgraded)
                if (resolvedPeerId != savedPeerId) {
                    prefs.edit().putString("peer_id", resolvedPeerId).apply()
                }
                return identity
            } catch (e: Exception) {}
        }

        // Generate a fresh identity but bind it to the stable device peerId
        val defaultName = "Android-${android.os.Build.MODEL.take(8)}"
        val newIdentity = NearaCryptoEngine.generateIdentity(defaultName, fixedPeerId = stablePeerId)
        prefs.edit()
            .putString("peer_id", newIdentity.peerId)
            .putString("display_name", newIdentity.displayName)
            .putString("signing_pub", CryptoUtils.toHex(newIdentity.signingPublicKey))
            .putString("signing_priv", CryptoUtils.toHex(newIdentity.signingPrivateKey))
            .putString("agreement_pub", CryptoUtils.toHex(newIdentity.agreementPublicKey))
            .putString("agreement_priv", CryptoUtils.toHex(newIdentity.agreementPrivateKey))
            .apply()
        return newIdentity
    }

    private val deviceIdentity = loadOrCreateIdentity()
    val cryptoEngine = NearaCryptoEngine(displayName = deviceIdentity.displayName, savedIdentity = deviceIdentity)
    val localPeerId: String get() = cryptoEngine.localIdentity.peerId

    val localIpAddress: String = run {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            val nonLoopback = interfaces.flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            nonLoopback?.hostAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }
    val localPort = 45781

    val localPeer = Peer(
        peerId = localPeerId,
        displayName = cryptoEngine.localIdentity.displayName,
        avatarId = "avatar_android",
        publicKeyHex = CryptoUtils.toHex(cryptoEngine.localIdentity.agreementPublicKey),
        port = localPort,
        ipAddress = localIpAddress
    )

    // 2. Storage (File-backed persistence so messages & groups survive app closing/restarting)
    val messageRepository: IMessageRepository = JsonFileMessageRepository(File(context.filesDir, "messages.json"))
    val offlineQueueManager = InMemoryOfflineQueueManager(messageRepository)

    // 3. Network Manager (File-backed persistence so groups survive app closing/restarting)
    val networkManager = NetworkManager(localPeerId, cryptoEngine, File(context.filesDir, "networks.json"))

    // 4. Transport & Discovery
    val peerRepository = PeerRepository()
    val peerStateManager = PeerStateManager(peerRepository)
    val transportProvider = TcpTransportProvider(localPeerId)
    val discoveryProvider = UdpDiscoveryProvider(
        localPeer = localPeer,
        activeGroupsProvider = {
            networkManager.networksFlow.value.map { net ->
                app.neara.discovery.DiscoveredGroup(
                    networkId = net.networkId,
                    name = net.name,
                    type = net.type,
                    hostPeerId = localPeerId,
                    hostDisplayName = cryptoEngine.localIdentity.displayName,
                    hostIpAddress = localIpAddress,
                    hostPort = localPort,
                    memberCount = net.members.size,
                    requiresPin = net.securityPolicy.requiresPin,
                    requireApproval = net.securityPolicy.requireApproval
                )
            }
        }
    )
    val discoveryService = DiscoveryService(peerStateManager, discoveryProvider)
    val nearbyGroups: StateFlow<List<app.neara.discovery.DiscoveredGroup>> = discoveryService.discoveredGroups

    // 5. Services
    val chatService = ChatService(
        localPeerId = localPeerId,
        transportProvider = transportProvider,
        peerManager = peerStateManager,
        messageRepository = messageRepository,
        offlineQueueManager = offlineQueueManager,
        encryptionManager = cryptoEngine
    )
    val meshRouter = MeshRouterImpl(localPeerId, transportProvider, peerStateManager)
    val fileTransferService = FileTransferService(
        localPeerId = localPeerId,
        transportProvider = transportProvider,
        downloadDirectory = File(context.filesDir, "NearaDownloads")
    )
    val voiceService = VoiceService(localPeerId)
    val callManager = app.neara.android.call.AndroidCallManager(
        context = context,
        localPeer = localPeer,
        chatService = chatService,
        peerProvider = { peerId -> peerStateManager.getPeer(peerId) },
        ipResolver = { peerId -> transportProvider.getConnection(peerId)?.remoteIp },
        onCallEnded = { peer, payload, direction, convId ->
            val targetConvId = convId
                ?: activeConversationId.value
                ?: activeConversationNetwork.value?.networkId
                ?: "conv-${listOf(localPeerId, peer.peerId).sorted().joinToString("-")}"
            val isGroup = targetConvId.startsWith("net-") || activeConversationNetwork.value?.networkId == targetConvId
            val msg = ChatMessage(
                messageId = "call-${UUID.randomUUID().toString().take(8)}",
                conversationId = targetConvId,
                senderId = if (direction == app.neara.android.call.CallDirection.OUTGOING) localPeerId else peer.peerId,
                recipientId = if (direction == app.neara.android.call.CallDirection.OUTGOING) (if (isGroup) null else peer.peerId) else localPeerId,
                timestamp = System.currentTimeMillis(),
                type = MessageType.SYSTEM,
                payload = payload,
                status = MessageStatus.READ
            )
            scope.launch {
                messageRepository.saveMessage(msg)
                withContext(Dispatchers.Main) {
                    refreshActiveMessages()
                    refreshConversations()
                }
            }
        }
    )

    // UI States
    val currentTab = MutableStateFlow(AndroidTab.CHATS)
    val isVisible = MutableStateFlow(true)
    val meshRelayEnabled = MutableStateFlow(true)
    val autoAcceptFiles = MutableStateFlow(false)
    val activeConversationPeer = MutableStateFlow<Peer?>(null)
    val activeConversationNetwork = MutableStateFlow<Network?>(null)
    val activeConversationId = MutableStateFlow<String?>(null)
    val currentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val conversationList = MutableStateFlow<List<ConversationItem>>(emptyList())
    val isPttActive = MutableStateFlow(false)
    val offlinePendingCount = MutableStateFlow(0)
    val showGroupInfo = MutableStateFlow(false)

    val discoveredPeers: StateFlow<List<Peer>> = peerStateManager.peers
    val activeNetworks: StateFlow<List<Network>> = networkManager.networksFlow

    private val chatPrefs by lazy { context.getSharedPreferences("neara_chat_prefs", Context.MODE_PRIVATE) }
    private val pinnedConversations = mutableSetOf<String>()
    private val mutedConversations = mutableSetOf<String>() // keeps conversationIds
    private val muteUntilMap = mutableMapOf<String, Long>() // conversationId -> muteUntil timestamp (Long.MAX_VALUE = forever)
    private val manualUnreadConversations = mutableSetOf<String>()
    // Tracks groups the user has explicitly deleted from the chat list (so they don't reappear)
    private val dismissedNetworks = mutableSetOf<String>()

    init {
        pinnedConversations.addAll(chatPrefs.getStringSet("pinned_conversations", emptySet()) ?: emptySet())
        mutedConversations.addAll(chatPrefs.getStringSet("muted_conversations", emptySet()) ?: emptySet())
        // Load mute expiry times
        mutedConversations.forEach { convId ->
            val until = chatPrefs.getLong("mute_until_$convId", Long.MAX_VALUE)
            muteUntilMap[convId] = until
        }
        // Clean expired mutes
        val now = System.currentTimeMillis()
        val expired = muteUntilMap.filter { it.value != Long.MAX_VALUE && it.value <= now }.keys
        mutedConversations.removeAll(expired)
        expired.forEach { muteUntilMap.remove(it) }
        manualUnreadConversations.addAll(chatPrefs.getStringSet("manual_unread_conversations", emptySet()) ?: emptySet())
        dismissedNetworks.addAll(chatPrefs.getStringSet("dismissed_networks", emptySet()) ?: emptySet())
        messageRepository.deleteActionMessages()
        acquireMulticastLock()
        startNetworking()
        refreshConversations()
        updateOfflineCount()
    }

    private fun saveChatPrefs() {
        val editor = chatPrefs.edit()
            .putStringSet("pinned_conversations", HashSet(pinnedConversations))
            .putStringSet("muted_conversations", HashSet(mutedConversations))
            .putStringSet("manual_unread_conversations", HashSet(manualUnreadConversations))
            .putStringSet("dismissed_networks", HashSet(dismissedNetworks))
        // Save mute expiry times
        muteUntilMap.forEach { (convId, until) ->
            editor.putLong("mute_until_$convId", until)
        }
        editor.apply()
    }

    fun isNetworkDismissed(networkId: String): Boolean = networkId in dismissedNetworks

    fun unmarkDismissedNetwork(networkId: String) {
        dismissedNetworks.remove(networkId)
        saveChatPrefs()
        refreshConversations()
    }

    fun createNetwork(name: String, type: NetworkType, pin: String? = null): Network {
        val net = networkManager.createNetwork(name, type, pin)
        dismissedNetworks.remove(net.networkId)
        saveChatPrefs()
        refreshConversations()
        return net
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("neara_multicast_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {}
    }

    private fun startNetworking() {
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
                    if (msg.type == MessageType.SYSTEM && msg.payload.startsWith("CALL_SIG:")) {
                        callManager.handleCallSignal(msg.senderId, msg.payload)
                        return@collect
                    }
                    refreshActiveMessages()
                    updateOfflineCount()
                }
            }

            // Observe delivery receipts
            launch {
                chatService.deliveryReceipts.collect {
                    refreshActiveMessages()
                    updateOfflineCount()
                }
            }

            chatService.networkManager = networkManager

            // Observe networks changes to keep active conversation network and member counts reactive in real-time
            launch {
                networkManager.networksFlow.collect { nets ->
                    val currentNetId = activeConversationNetwork.value?.networkId
                    if (currentNetId != null) {
                        val updated = nets.find { it.networkId == currentNetId }
                        if (updated != null) {
                            activeConversationNetwork.value = updated
                        }
                    }
                    refreshConversations()
                }
            }
        }
    }

    fun setTab(tab: AndroidTab) {
        currentTab.value = tab
    }

    fun toggleVisibility(visible: Boolean = !isVisible.value) {
        isVisible.value = visible
        discoveryProvider.setVisible(visible)
    }

    fun selectConversation(peer: Peer) {
        activeConversationNetwork.value = null
        activeConversationPeer.value = peer
        val convId = "conv-${listOf(localPeerId, peer.peerId).sorted().joinToString("-")}"
        activeConversationId.value = convId
        currentTab.value = AndroidTab.CHATS
        manualUnreadConversations.remove(convId)
        saveChatPrefs()
        messageRepository.markConversationAsRead(convId, localPeerId)
        refreshActiveMessages()
    }

    fun selectNetworkConversation(network: Network) {
        activeConversationPeer.value = null
        activeConversationNetwork.value = network
        activeConversationId.value = network.networkId
        currentTab.value = AndroidTab.CHATS
        manualUnreadConversations.remove(network.networkId)
        saveChatPrefs()
        messageRepository.markConversationAsRead(network.networkId, localPeerId)
        refreshActiveMessages()
    }

    fun openGroupInfo(network: Network) {
        selectNetworkConversation(network)
        showGroupInfo.value = true
    }

    fun closeGroupInfo() {
        showGroupInfo.value = false
    }

    fun markConversationAsUnread(conversationId: String) {
        manualUnreadConversations.add(conversationId)
        saveChatPrefs()
        refreshConversations()
    }

    fun markConversationAsRead(conversationId: String) {
        manualUnreadConversations.remove(conversationId)
        saveChatPrefs()
        messageRepository.markConversationAsRead(conversationId, localPeerId)
        refreshConversations()
    }

    fun toggleMuteConversation(conversationId: String) {
        if (mutedConversations.contains(conversationId)) {
            mutedConversations.remove(conversationId)
            muteUntilMap.remove(conversationId)
        } else {
            mutedConversations.add(conversationId)
            muteUntilMap[conversationId] = Long.MAX_VALUE
        }
        saveChatPrefs()
        refreshConversations()
    }

    /** Mute for a specific duration. Pass Long.MAX_VALUE for "Until I turn it back on" */
    fun muteConversation(conversationId: String, durationMs: Long) {
        mutedConversations.add(conversationId)
        muteUntilMap[conversationId] = if (durationMs == Long.MAX_VALUE) Long.MAX_VALUE
                                       else System.currentTimeMillis() + durationMs
        saveChatPrefs()
        refreshConversations()
    }

    fun unmuteConversation(conversationId: String) {
        mutedConversations.remove(conversationId)
        muteUntilMap.remove(conversationId)
        saveChatPrefs()
        refreshConversations()
    }

    fun isMuteActive(conversationId: String): Boolean {
        if (!mutedConversations.contains(conversationId)) return false
        val until = muteUntilMap[conversationId] ?: Long.MAX_VALUE
        if (until == Long.MAX_VALUE) return true
        return System.currentTimeMillis() < until
    }

    fun togglePinConversation(conversationId: String) {
        if (pinnedConversations.contains(conversationId)) {
            pinnedConversations.remove(conversationId)
        } else {
            pinnedConversations.add(conversationId)
        }
        saveChatPrefs()
        refreshConversations()
    }

    fun joinDiscoveredGroup(group: app.neara.discovery.DiscoveredGroup) {
        scope.launch(Dispatchers.IO) {
            val hostIp = group.hostIpAddress ?: peerStateManager.getPeer(group.hostPeerId)?.ipAddress
            val targetPort = group.hostPort

            var joinedNet: Network? = null

            if (hostIp != null) {
                try {
                    val conn = transportProvider.connect(hostIp, targetPort, group.hostPeerId) as? TcpPeerConnection
                    if (conn != null) {
                        chatService.handleIncomingConnection(conn)

                        val req = JoinNetworkRequest(
                            networkId = group.networkId,
                            applicantPeerId = localPeerId,
                            applicantDisplayName = cryptoEngine.localIdentity.displayName,
                            applicantPublicKeyHex = CryptoUtils.toHex(cryptoEngine.localIdentity.agreementPublicKey)
                        )
                        val frame = ProtocolFrame(
                            type = ProtocolConstants.TYPE_NETWORK_JOIN_REQ,
                            payload = Json.encodeToString(req).toByteArray(Charsets.UTF_8)
                        )
                        conn.sendFrame(frame)

                        val resp = withTimeoutOrNull(3500) {
                            chatService.incomingJoinResponses.first { it.networkId == group.networkId }
                        }

                        if (resp != null) {
                            if (resp.isApproved && resp.network != null) {
                                // Save received chat history so the new member sees previous messages
                                for (histMsg in resp.recentMessages) {
                                    messageRepository.saveMessage(histMsg)
                                }
                                joinedNet = resp.network
                            } else if (!resp.isApproved) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, resp.reason ?: "Approval required by group creator", Toast.LENGTH_LONG).show()
                                }
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (joinedNet == null) {
                if (group.requireApproval) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Approval required by group creator", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                joinedNet = Network(
                    networkId = group.networkId,
                    name = group.name,
                    type = group.type,
                    ownerId = group.hostPeerId,
                    createdAt = System.currentTimeMillis(),
                    publicKeyHex = "",
                    securityPolicy = SecurityPolicy(requireApproval = group.requireApproval, requiresPin = group.requiresPin),
                    members = listOf(
                        NetworkMember(group.hostPeerId, group.hostDisplayName, MemberRole.OWNER, System.currentTimeMillis()),
                        NetworkMember(localPeerId, cryptoEngine.localIdentity.displayName, MemberRole.MEMBER, System.currentTimeMillis())
                    )
                )
            }

            val finalNet = joinedNet
            withContext(Dispatchers.Main) {
                dismissedNetworks.remove(finalNet.networkId)
                saveChatPrefs()
                networkManager.addDiscoveredNetwork(finalNet)
                selectNetworkConversation(finalNet)
                refreshConversations()
                Toast.makeText(context, "Joined ${finalNet.name}!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getSenderDisplayName(senderId: String): String {
        if (senderId == localPeerId) return "You"
        activeConversationPeer.value?.let { if (it.peerId == senderId) return it.displayName }
        discoveredPeers.value.find { it.peerId == senderId }?.let { return it.displayName }
        activeConversationNetwork.value?.members?.find { it.peerId == senderId }?.let { return it.displayName }
        for (net in networkManager.networksFlow.value) {
            val member = net.members.find { it.peerId == senderId }
            if (member != null) return member.displayName
        }
        return "User-${senderId.take(6)}"
    }

    fun refreshActiveMessages() {
        val convId = activeConversationId.value
        if (convId != null) {
            currentMessages.value = messageRepository.getMessagesForConversation(convId)
                .filter { (it.type != MessageType.ACTION && !it.payload.startsWith("ACTION:")) || it.payload.startsWith("CALL_LOG:") }
        }
        refreshConversations()
        updateOfflineCount()
    }

    fun refreshConversations() {
        val allMsgs = messageRepository.getAllMessages()
            .filter { (it.type != MessageType.ACTION && !it.payload.startsWith("ACTION:")) || it.payload.startsWith("CALL_LOG:") }
        val grouped = allMsgs.groupBy { it.conversationId }
        val onlinePeers = peerStateManager.peers.value.associateBy { it.peerId }
        val localNetworks = networkManager.networksFlow.value.associateBy { it.networkId }
        val discoveredG = nearbyGroups.value.associateBy { it.networkId }

        val items = mutableListOf<ConversationItem>()

        for ((convId, msgs) in grouped) {
            // Skip conversations the user has explicitly deleted
            if (convId in dismissedNetworks) continue
            val lastMsg = msgs.maxByOrNull { it.timestamp } ?: continue
            val rawUnread = msgs.count { it.senderId != localPeerId && it.status != MessageStatus.READ }
            val isManualUnread = convId in manualUnreadConversations
            val unreadCount = if (isManualUnread) maxOf(rawUnread, 1) else rawUnread
            val isMuted = convId in mutedConversations
            val isPinned = convId in pinnedConversations
            val isGroup = convId.startsWith("net-")
            if (isGroup) {
                val net = localNetworks[convId]
                val discGroup = discoveredG[convId]
                val title = net?.name ?: discGroup?.name ?: "Group Channel"
                val displayMsg = when {
                    lastMsg.type == MessageType.IMAGE -> "📷 Photo"
                    lastMsg.type == MessageType.VOICE -> "🎤 Voice message"
                    lastMsg.payload.startsWith("CALL_LOG:") -> {
                        val isVideo = lastMsg.payload.contains("type=VIDEO")
                        val isMissed = lastMsg.payload.contains("status=MISSED") || lastMsg.payload.contains("status=CANCELLED")
                        when {
                            isVideo && isMissed -> "📹 Missed video call"
                            isVideo -> "📹 Video call"
                            isMissed -> "📞 Missed audio call"
                            else -> "📞 Audio call"
                        }
                    }
                    else -> lastMsg.payload
                }
                items.add(
                    ConversationItem(
                        conversationId = convId,
                        isGroup = true,
                        network = net,
                        title = title,
                        lastMessageText = displayMsg,
                        lastMessageTime = lastMsg.timestamp,
                        lastStatus = lastMsg.status,
                        isLastSelf = lastMsg.senderId == localPeerId,
                        isOnline = true,
                        unreadCount = unreadCount,
                        isMuted = isMuted,
                        isPinned = isPinned
                    )
                )
            } else {
                val otherPeerId = if (lastMsg.senderId == localPeerId) lastMsg.recipientId ?: "" else lastMsg.senderId
                val peer = onlinePeers[otherPeerId]
                val title = peer?.displayName ?: "User-${otherPeerId.take(6)}"
                val displayMsg = when {
                    lastMsg.type == MessageType.IMAGE -> "📷 Photo"
                    lastMsg.type == MessageType.VOICE -> "🎤 Voice message"
                    lastMsg.payload.startsWith("CALL_LOG:") -> {
                        val isVideo = lastMsg.payload.contains("type=VIDEO")
                        val isMissed = lastMsg.payload.contains("status=MISSED") || lastMsg.payload.contains("status=CANCELLED")
                        when {
                            isVideo && isMissed -> "📹 Missed video call"
                            isVideo -> "📹 Video call"
                            isMissed -> "📞 Missed audio call"
                            else -> "📞 Audio call"
                        }
                    }
                    else -> lastMsg.payload
                }
                items.add(
                    ConversationItem(
                        conversationId = convId,
                        isGroup = false,
                        peer = peer ?: Peer(otherPeerId, title, "avatar", "", port = 45781),
                        title = title,
                        lastMessageText = displayMsg,
                        lastMessageTime = lastMsg.timestamp,
                        lastStatus = lastMsg.status,
                        isLastSelf = lastMsg.senderId == localPeerId,
                        isOnline = peer != null && peer.connectionState != ConnectionState.DISCONNECTED,
                        unreadCount = unreadCount,
                        isMuted = isMuted,
                        isPinned = isPinned
                    )
                )
            }
        }

        // Also show local hosted/joined networks if not yet messaged — skip dismissed ones
        for ((netId, net) in localNetworks) {
            if (grouped[netId] == null && netId !in dismissedNetworks) {
                val isManualUnread = netId in manualUnreadConversations
                val isMuted = netId in mutedConversations
                val isPinned = netId in pinnedConversations
                items.add(
                    ConversationItem(
                        conversationId = netId,
                        isGroup = true,
                        network = net,
                        title = net.name,
                        lastMessageText = "Group channel created • Tap to chat",
                        lastMessageTime = net.createdAt,
                        lastStatus = MessageStatus.SENT,
                        isLastSelf = false,
                        isOnline = true,
                        unreadCount = if (isManualUnread) 1 else 0,
                        isMuted = isMuted,
                        isPinned = isPinned
                    )
                )
            }
        }

        conversationList.value = items.sortedWith(
            compareByDescending<ConversationItem> { it.isPinned }
                .thenByDescending { it.lastMessageTime }
        )
    }

    fun deleteConversation(item: ConversationItem) {
        messageRepository.deleteConversation(item.conversationId)
        pinnedConversations.remove(item.conversationId)
        mutedConversations.remove(item.conversationId)
        manualUnreadConversations.remove(item.conversationId)
        // If it's a group, delete the network if we are owner, or leave & remove from local storage
        if (item.isGroup) {
            val net = networkManager.getNetwork(item.conversationId)
            if (net != null && net.ownerId == localPeerId) {
                networkManager.deleteNetwork(item.conversationId)
            } else {
                networkManager.leaveNetwork(item.conversationId, localPeerId)
                networkManager.deleteNetwork(item.conversationId)
            }
            dismissedNetworks.add(item.conversationId)
        }
        saveChatPrefs()
        if (activeConversationId.value == item.conversationId || activeConversationNetwork.value?.networkId == item.conversationId) {
            activeConversationId.value = null
            activeConversationPeer.value = null
            activeConversationNetwork.value = null
            currentMessages.value = emptyList()
        }
        refreshConversations()
    }

    fun removeGroupMember(networkId: String, memberPeerId: String): Boolean {
        val success = networkManager.removeMember(networkId, memberPeerId, localPeerId)
        if (activeConversationNetwork.value?.networkId == networkId) {
            activeConversationNetwork.value = networkManager.getNetwork(networkId)
        }
        return success
    }

    fun leaveGroup(networkId: String) {
        networkManager.leaveNetwork(networkId, localPeerId)
        networkManager.deleteNetwork(networkId)
        messageRepository.deleteConversation(networkId)
        pinnedConversations.remove(networkId)
        mutedConversations.remove(networkId)
        manualUnreadConversations.remove(networkId)
        dismissedNetworks.add(networkId)  // prevent reappearing in chat list
        saveChatPrefs()
        if (activeConversationId.value == networkId || activeConversationNetwork.value?.networkId == networkId) {
            activeConversationId.value = null
            activeConversationPeer.value = null
            activeConversationNetwork.value = null
            currentMessages.value = emptyList()
        }
        refreshConversations()
    }

    fun deleteGroup(networkId: String) {
        networkManager.deleteNetwork(networkId)
        messageRepository.deleteConversation(networkId)
        pinnedConversations.remove(networkId)
        mutedConversations.remove(networkId)
        manualUnreadConversations.remove(networkId)
        dismissedNetworks.add(networkId)  // prevent reappearing in chat list
        saveChatPrefs()
        if (activeConversationId.value == networkId || activeConversationNetwork.value?.networkId == networkId) {
            activeConversationId.value = null
            activeConversationPeer.value = null
            activeConversationNetwork.value = null
            currentMessages.value = emptyList()
        }
        refreshConversations()
    }

    fun updateGroupName(networkId: String, newName: String): Boolean {
        val success = networkManager.updateNetworkName(networkId, newName)
        if (activeConversationNetwork.value?.networkId == networkId) {
            activeConversationNetwork.value = networkManager.getNetwork(networkId)
        }
        refreshConversations()
        return success
    }

    fun addMemberToGroup(networkId: String, peer: Peer): Boolean {
        val success = networkManager.addMember(networkId, peer.peerId, peer.displayName, MemberRole.MEMBER)
        if (activeConversationNetwork.value?.networkId == networkId) {
            activeConversationNetwork.value = networkManager.getNetwork(networkId)
        }
        refreshConversations()
        return success
    }

    fun approveJoinRequest(networkId: String, applicantPeerId: String): Boolean {
        val success = networkManager.approveJoinRequest(networkId, applicantPeerId, localPeerId)
        if (activeConversationNetwork.value?.networkId == networkId) {
            activeConversationNetwork.value = networkManager.getNetwork(networkId)
        }
        refreshConversations()
        return success
    }

    fun rejectJoinRequest(networkId: String, applicantPeerId: String): Boolean {
        val success = networkManager.rejectJoinRequest(networkId, applicantPeerId, localPeerId)
        if (activeConversationNetwork.value?.networkId == networkId) {
            activeConversationNetwork.value = networkManager.getNetwork(networkId)
        }
        refreshConversations()
        return success
    }

    fun setGroupApprovalRequired(networkId: String, required: Boolean): Boolean {
        val success = networkManager.updateSecurityPolicy(networkId, requireApproval = required)
        if (activeConversationNetwork.value?.networkId == networkId) {
            activeConversationNetwork.value = networkManager.getNetwork(networkId)
        }
        refreshConversations()
        return success
    }

    fun updateOfflineCount() {
        offlinePendingCount.value = offlineQueueManager.getPendingCount()
    }

    fun sendTextMessage(text: String) {
        val convId = activeConversationId.value ?: return
        if (text.isBlank()) return

        val targetPeer = activeConversationPeer.value

        val msg = ChatMessage(
            messageId = "msg-${UUID.randomUUID().toString().take(8)}",
            conversationId = convId,
            senderId = localPeerId,
            recipientId = targetPeer?.peerId,
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

    fun sendImageMessage(base64Image: String, caption: String = "") {
        sendImagesMessage(listOf(base64Image), caption)
    }

    fun sendImagesMessage(base64Images: List<String>, caption: String = "") {
        val validImages = base64Images.filter { it.isNotBlank() }
        if (validImages.isEmpty()) return

        val convId = activeConversationId.value ?: return
        val targetPeer = activeConversationPeer.value

        val imagesPart = if (validImages.size == 1) validImages.first() else validImages.joinToString("|||IMG|||")
        val payload = if (caption.isNotBlank()) "CAPTION:$caption|||CAPTION_SEP|||$imagesPart" else imagesPart

        val msg = ChatMessage(
            messageId = "msg-${UUID.randomUUID().toString().take(8)}",
            conversationId = convId,
            senderId = localPeerId,
            recipientId = targetPeer?.peerId,
            timestamp = System.currentTimeMillis(),
            sequenceNumber = System.currentTimeMillis(),
            type = MessageType.IMAGE,
            payload = payload,
            status = MessageStatus.PENDING
        )

        scope.launch {
            chatService.sendMessage(msg)
            refreshActiveMessages()
            updateOfflineCount()
        }
    }

    fun sendVoiceMessage(base64Audio: String, durationSecs: Int) {
        if (base64Audio.isBlank()) return
        val convId = activeConversationId.value ?: return
        val targetPeer = activeConversationPeer.value

        val payload = "DURATION:$durationSecs|||VOICE_SEP|||$base64Audio"

        val msg = ChatMessage(
            messageId = "msg-${UUID.randomUUID().toString().take(8)}",
            conversationId = convId,
            senderId = localPeerId,
            recipientId = targetPeer?.peerId,
            timestamp = System.currentTimeMillis(),
            sequenceNumber = System.currentTimeMillis(),
            type = MessageType.VOICE,
            payload = payload,
            status = MessageStatus.PENDING
        )

        scope.launch {
            chatService.sendMessage(msg)
            refreshActiveMessages()
            updateOfflineCount()
        }
    }

    fun editMessage(messageId: String, newText: String) {
        if (newText.isBlank()) return
        val existing = messageRepository.getMessage(messageId) ?: return
        val updated = existing.copy(payload = newText)
        messageRepository.saveMessage(updated)

        val convId = activeConversationId.value
        val targetPeer = activeConversationPeer.value

        val actionMsg = ChatMessage(
            messageId = "action-${UUID.randomUUID().toString().take(8)}",
            conversationId = convId ?: existing.conversationId,
            senderId = localPeerId,
            recipientId = targetPeer?.peerId,
            timestamp = System.currentTimeMillis(),
            sequenceNumber = System.currentTimeMillis(),
            type = MessageType.ACTION,
            payload = "ACTION:EDIT:$messageId:$newText",
            status = MessageStatus.PENDING
        )

        scope.launch {
            chatService.sendMessage(actionMsg)
        }

        refreshActiveMessages()
        refreshConversations()
    }

    fun deleteMessage(messageId: String) {
        // Delete locally (Delete for me)
        messageRepository.deleteMessage(messageId)
        offlineQueueManager.remove(messageId)
        refreshActiveMessages()
        refreshConversations()
    }

    fun deleteMessageForEveryone(messageId: String) {
        val existing = messageRepository.getMessage(messageId)
        val convId = activeConversationId.value ?: existing?.conversationId ?: ""
        val targetPeer = activeConversationPeer.value

        val actionMsg = ChatMessage(
            messageId = "action-${UUID.randomUUID().toString().take(8)}",
            conversationId = convId,
            senderId = localPeerId,
            recipientId = targetPeer?.peerId,
            timestamp = System.currentTimeMillis(),
            sequenceNumber = System.currentTimeMillis(),
            type = MessageType.ACTION,
            payload = "ACTION:UNSEND:$messageId",
            status = MessageStatus.PENDING
        )

        scope.launch {
            chatService.sendMessage(actionMsg)
        }

        deleteMessage(messageId)
    }

    fun toggleMessageReaction(messageId: String, emoji: String) {
        val existing = messageRepository.getMessage(messageId) ?: return
        val currentReactions = existing.reactions.mapValues { it.value.toMutableList() }.toMutableMap()

        // Check if user already reacted with this exact emoji
        val sendersForEmoji = currentReactions[emoji] ?: mutableListOf()
        val alreadyReactedSame = sendersForEmoji.contains(localPeerId)

        // In Messenger style: remove any prior reaction by this user on this message
        var removedPrevEmoji: String? = null
        for ((em, senders) in currentReactions) {
            if (senders.remove(localPeerId)) {
                if (em != emoji) {
                    removedPrevEmoji = em
                }
            }
        }

        val isAdded: Boolean
        if (alreadyReactedSame) {
            // User clicked the same emoji again -> untoggle (remove)
            isAdded = false
        } else {
            // Add the new reaction
            val list = currentReactions[emoji] ?: mutableListOf()
            if (!list.contains(localPeerId)) list.add(localPeerId)
            currentReactions[emoji] = list
            isAdded = true
        }

        // Clean up any empty reaction entries
        val cleanedReactions = currentReactions.filter { it.value.isNotEmpty() }

        val updated = existing.copy(reactions = cleanedReactions)
        messageRepository.saveMessage(updated)

        val convId = activeConversationId.value ?: existing.conversationId
        val targetPeer = activeConversationPeer.value

        // If replacing previous emoji, send remove action first
        if (removedPrevEmoji != null) {
            val removeMsg = ChatMessage(
                messageId = "action-${UUID.randomUUID().toString().take(8)}",
                conversationId = convId,
                senderId = localPeerId,
                recipientId = targetPeer?.peerId,
                timestamp = System.currentTimeMillis(),
                sequenceNumber = System.currentTimeMillis(),
                type = MessageType.ACTION,
                payload = "ACTION:REACT_REMOVE:$messageId:$removedPrevEmoji",
                status = MessageStatus.PENDING
            )
            scope.launch {
                chatService.sendMessage(removeMsg)
            }
        }

        val actionMsg = ChatMessage(
            messageId = "action-${UUID.randomUUID().toString().take(8)}",
            conversationId = convId,
            senderId = localPeerId,
            recipientId = targetPeer?.peerId,
            timestamp = System.currentTimeMillis(),
            sequenceNumber = System.currentTimeMillis(),
            type = MessageType.ACTION,
            payload = if (isAdded) "ACTION:REACT_ADD:$messageId:$emoji" else "ACTION:REACT_REMOVE:$messageId:$emoji",
            status = MessageStatus.PENDING
        )

        scope.launch {
            chatService.sendMessage(actionMsg)
        }

        refreshActiveMessages()
    }

    fun toggleMeshRelay(enabled: Boolean) {
        meshRelayEnabled.value = enabled
    }

    fun toggleAutoAcceptFiles(enabled: Boolean) {
        autoAcceptFiles.value = enabled
    }

    fun clearChatHistory() {
        messageRepository.clearAll()
        currentMessages.value = emptyList()
        refreshConversations()
    }

    fun purgeOfflineQueue() {
        offlineQueueManager.clear()
        updateOfflineCount()
    }

    fun startVoiceCall(peer: Peer, convId: String? = activeConversationId.value) {
        val effectiveConvId = convId ?: "conv-${listOf(localPeerId, peer.peerId).sorted().joinToString("-")}"
        callManager.startCall(peer, isVideo = false, convId = effectiveConvId)
    }

    fun startVideoCall(peer: Peer, convId: String? = activeConversationId.value) {
        val effectiveConvId = convId ?: "conv-${listOf(localPeerId, peer.peerId).sorted().joinToString("-")}"
        callManager.startCall(peer, isVideo = true, convId = effectiveConvId)
    }

    fun startGroupCall(network: Network, isVideo: Boolean) {
        val otherMembers = network.members.filter { it.peerId != localPeerId }
        val targetPeer = if (otherMembers.isNotEmpty()) {
            getPeerForMember(otherMembers[0].peerId, otherMembers[0].displayName)
        } else {
            val recentMsg = currentMessages.value.findLast { it.senderId != localPeerId && it.senderId.isNotEmpty() }
            if (recentMsg != null) {
                getPeerForMember(recentMsg.senderId, getSenderDisplayName(recentMsg.senderId))
            } else {
                Peer(
                    peerId = network.networkId,
                    displayName = network.name,
                    avatarId = "avatar_group",
                    publicKeyHex = "",
                    port = 45781
                )
            }
        }
        callManager.startCall(targetPeer, isVideo = isVideo, convId = network.networkId)
    }

    fun getPeerForMember(memberPeerId: String, displayName: String): Peer {
        val existing = peerStateManager.getPeer(memberPeerId)
            ?: discoveredPeers.value.find { it.peerId == memberPeerId }
        if (existing != null) return existing

        val socketIp = transportProvider.getConnection(memberPeerId)?.remoteIp
        return Peer(
            peerId = memberPeerId,
            displayName = displayName,
            avatarId = "avatar_member",
            publicKeyHex = "",
            port = 45781,
            ipAddress = socketIp
        )
    }

    fun close() {
        discoveryService.stop()
        callManager.close()
        try {
            multicastLock?.release()
        } catch (e: Exception) {}
        scope.launch(Dispatchers.IO) {
            transportProvider.stopServer()
            voiceService.close()
        }
    }
}
