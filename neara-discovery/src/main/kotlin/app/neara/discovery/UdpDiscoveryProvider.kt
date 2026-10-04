package app.neara.discovery

import app.neara.core.interfaces.DiscoveryProvider
import app.neara.core.model.*
import app.neara.protocol.FrameCodec
import app.neara.protocol.ProtocolConstants
import app.neara.protocol.ProtocolFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.*

@Serializable
data class DiscoveredGroup(
    val networkId: String,
    val name: String,
    val type: NetworkType = NetworkType.PUBLIC,
    val hostPeerId: String,
    val hostDisplayName: String,
    val hostIpAddress: String? = null,
    val hostPort: Int = 45781,
    val memberCount: Int = 1,
    val requiresPin: Boolean = false,
    val requireApproval: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

@Serializable
data class DiscoveryBeacon(
    val peerId: String,
    val displayName: String,
    val avatarId: String,
    val publicKeyHex: String,
    val capabilities: Set<Capability>,
    val transportTypes: Set<TransportType>,
    val port: Int,
    val networkIds: Set<String> = emptySet(),
    val groups: List<DiscoveredGroup> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class UdpDiscoveryProvider(
    private val localPeer: Peer,
    private val multicastGroupAddress: String = "239.255.60.60",
    private val discoveryPort: Int = 45780,
    var activeGroupsProvider: (() -> List<DiscoveredGroup>)? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : DiscoveryProvider {

    override val transportType: TransportType = TransportType.LAN_WIFI

    private val json = Json { ignoreUnknownKeys = true }
    private val _events = MutableSharedFlow<DiscoveryEvent>(extraBufferCapacity = 50)
    private val groupMap = java.util.concurrent.ConcurrentHashMap<String, DiscoveredGroup>()
    private val _discoveredGroups = MutableStateFlow<List<DiscoveredGroup>>(emptyList())
    val discoveredGroups: StateFlow<List<DiscoveredGroup>> = _discoveredGroups.asStateFlow()

    private var isRunning = false
    private var isVisibleState = true

    private var multicastSocket: MulticastSocket? = null
    private var broadcastJob: Job? = null
    private var receiveJob: Job? = null

    override fun setVisible(visible: Boolean) {
        isVisibleState = visible
    }

    override fun startDiscovery(): Flow<DiscoveryEvent> {
        if (isRunning) return _events.asSharedFlow()
        isRunning = true

        try {
            val group = InetAddress.getByName(multicastGroupAddress)
            val socket = MulticastSocket(discoveryPort).apply {
                reuseAddress = true
                timeToLive = 4
                try {
                    joinGroup(InetSocketAddress(group, discoveryPort), null)
                } catch (e: Exception) {
                    // Fallback on systems where multicast group join is restricted
                }
            }
            multicastSocket = socket

            // 1. Start listening loop
            receiveJob = scope.launch {
                val buffer = ByteArray(4096)
                while (isActive && isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val senderIp = packet.address.hostAddress
                        val frame = try {
                            FrameCodec.decodeFromBytes(packet.data.copyOf(packet.length))
                        } catch (e: Exception) {
                            null
                        }

                        if (frame != null && frame.type == ProtocolConstants.TYPE_DISCOVERY_BEACON) {
                            val beaconJson = String(frame.payload, Charsets.UTF_8)
                            val beacon = json.decodeFromString<DiscoveryBeacon>(beaconJson)

                            // Ignore self-announcements (peer ID match or loopback on same port)
                            val isSelf = beacon.peerId == localPeer.peerId ||
                                    (senderIp == localPeer.ipAddress && beacon.port == localPeer.port)
                            if (!isSelf) {
                                // Record any groups announced by this peer
                                val peerGroupIds = beacon.groups.map { it.networkId }.toSet()
                                var groupChanged = false
                                // Prune groups this peer no longer announces
                                val toRemove = groupMap.values.filter { it.hostPeerId == beacon.peerId && !peerGroupIds.contains(it.networkId) }
                                for (stale in toRemove) {
                                    groupMap.remove(stale.networkId)
                                    groupChanged = true
                                }
                                for (g in beacon.groups) {
                                    val updated = g.copy(
                                        hostIpAddress = g.hostIpAddress ?: senderIp,
                                        lastSeen = System.currentTimeMillis()
                                    )
                                    val prev = groupMap[g.networkId]
                                    if (prev != updated) {
                                        groupMap[g.networkId] = updated
                                        groupChanged = true
                                    }
                                }
                                if (groupChanged) {
                                    _discoveredGroups.value = groupMap.values.toList()
                                }

                                val discovered = Peer(
                                    peerId = beacon.peerId,
                                    displayName = beacon.displayName,
                                    avatarId = beacon.avatarId,
                                    publicKeyHex = beacon.publicKeyHex,
                                    capabilities = beacon.capabilities,
                                    transportTypes = beacon.transportTypes,
                                    lastSeen = System.currentTimeMillis(),
                                    connectionState = ConnectionState.DISCOVERED,
                                    networkIds = beacon.networkIds,
                                    proximityEstimate = ProximityEstimate.NEAR,
                                    ipAddress = senderIp,
                                    port = beacon.port
                                )
                                _events.emit(DiscoveryEvent.PeerFound(discovered))
                            }
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }

            // 2. Start periodic presence beacon loop (every 4 seconds)
            broadcastJob = scope.launch {
                while (isActive && isRunning) {
                    if (isVisibleState) {
                        broadcastPresence(localPeer)
                    }
                    // Clean up expired discovered groups (> 12s)
                    val now = System.currentTimeMillis()
                    val expired = groupMap.values.filter { now - it.lastSeen > 12_000L }
                    if (expired.isNotEmpty()) {
                        for (stale in expired) {
                            groupMap.remove(stale.networkId)
                        }
                        _discoveredGroups.value = groupMap.values.toList()
                    }
                    delay(4_000L)
                }
            }

        } catch (e: Exception) {
            // Log / handle socket initialization
        }

        return _events.asSharedFlow()
    }

    override fun broadcastPresence(peer: Peer) {
        if (!isVisibleState) return

        scope.launch(Dispatchers.IO) {
            try {
                val currentGroups = activeGroupsProvider?.invoke() ?: emptyList()
                val beacon = DiscoveryBeacon(
                    peerId = peer.peerId,
                    displayName = peer.displayName,
                    avatarId = peer.avatarId,
                    publicKeyHex = peer.publicKeyHex,
                    capabilities = peer.capabilities,
                    transportTypes = peer.transportTypes,
                    port = peer.port,
                    networkIds = peer.networkIds,
                    groups = currentGroups
                )
                val beaconBytes = json.encodeToString(beacon).toByteArray(Charsets.UTF_8)
                val frame = ProtocolFrame(
                    type = ProtocolConstants.TYPE_DISCOVERY_BEACON,
                    payload = beaconBytes
                )
                val packetBytes = FrameCodec.encode(frame)

                val group = InetAddress.getByName(multicastGroupAddress)
                val multicastPacket = DatagramPacket(packetBytes, packetBytes.size, group, discoveryPort)
                multicastSocket?.send(multicastPacket)

                // Also send to local broadcast address 255.255.255.255 for networks with multicast filtering
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val broadcastPacket = DatagramPacket(packetBytes, packetBytes.size, broadcastAddr, discoveryPort)
                multicastSocket?.send(broadcastPacket)
            } catch (e: Exception) {
                // Ignore transient network errors during presence broadcast
            }
        }
    }

    override fun stopDiscovery() {
        isRunning = false
        receiveJob?.cancel()
        broadcastJob?.cancel()
        try {
            multicastSocket?.close()
        } catch (e: Exception) {}
        multicastSocket = null
    }
}

class DiscoveryService(
    val peerManager: PeerStateManager,
    val discoveryProvider: DiscoveryProvider,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    fun start() {
        discoveryProvider.startDiscovery().onEach { event ->
            when (event) {
                is DiscoveryEvent.PeerFound -> peerManager.onPeerDiscovered(event.peer)
                is DiscoveryEvent.PeerUpdated -> peerManager.onPeerDiscovered(event.peer)
                is DiscoveryEvent.PeerLost -> peerManager.removePeer(event.peerId)
                is DiscoveryEvent.PeerConnected -> peerManager.updatePeerState(event.peerId, ConnectionState.CONNECTED)
                is DiscoveryEvent.PeerDisconnected -> peerManager.updatePeerState(event.peerId, ConnectionState.DISCONNECTED)
            }
        }.launchIn(scope)
    }

    val discoveredGroups: StateFlow<List<DiscoveredGroup>>
        get() = (discoveryProvider as? UdpDiscoveryProvider)?.discoveredGroups ?: MutableStateFlow(emptyList())

    fun stop() {
        discoveryProvider.stopDiscovery()
        peerManager.stop()
    }
}
