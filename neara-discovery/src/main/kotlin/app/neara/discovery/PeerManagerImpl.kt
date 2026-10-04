package app.neara.discovery

import app.neara.core.interfaces.PeerManager
import app.neara.core.model.ConnectionState
import app.neara.core.model.DiscoveryEvent
import app.neara.core.model.Peer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class PeerRepository {
    private val _peersMap = ConcurrentHashMap<String, Peer>()
    private val _peersFlow = MutableStateFlow<List<Peer>>(emptyList())
    val peersFlow: StateFlow<List<Peer>> = _peersFlow.asStateFlow()

    fun upsert(peer: Peer): Pair<Peer, Boolean> {
        // Remove any old/stale peer that shares the exact same IP address and port, but different peerId
        if (peer.ipAddress != null) {
            val sameHostPeers = _peersMap.values.filter {
                it.ipAddress == peer.ipAddress && it.port == peer.port && it.peerId != peer.peerId
            }
            for (stale in sameHostPeers) {
                _peersMap.remove(stale.peerId)
            }
        }

        val isNew = !_peersMap.containsKey(peer.peerId)
        _peersMap[peer.peerId] = peer
        _peersFlow.value = _peersMap.values.toList()
        return Pair(peer, isNew)
    }

    fun get(peerId: String): Peer? = _peersMap[peerId]

    fun remove(peerId: String): Peer? {
        val removed = _peersMap.remove(peerId)
        if (removed != null) {
            _peersFlow.value = _peersMap.values.toList()
        }
        return removed
    }

    fun updateState(peerId: String, newState: ConnectionState): Peer? {
        val current = _peersMap[peerId] ?: return null
        val updated = current.copy(connectionState = newState, lastSeen = System.currentTimeMillis())
        _peersMap[peerId] = updated
        _peersFlow.value = _peersMap.values.toList()
        return updated
    }

    fun getAll(): List<Peer> = _peersMap.values.toList()
}

class PeerStateManager(
    private val repository: PeerRepository,
    private val timeoutMillis: Long = 15_000L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : PeerManager {

    private val _events = MutableSharedFlow<DiscoveryEvent>(replay = 10)
    val events: SharedFlow<DiscoveryEvent> = _events.asSharedFlow()

    private val _activeConnections = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    override val activeConnections: StateFlow<Map<String, ConnectionState>> = _activeConnections.asStateFlow()

    override val peers: StateFlow<List<Peer>> = repository.peersFlow

    private var sweepJob: Job? = null

    init {
        startHeartbeatMonitor()
    }

    fun startHeartbeatMonitor() {
        sweepJob?.cancel()
        sweepJob = scope.launch {
            while (isActive) {
                delay(3_000L)
                val now = System.currentTimeMillis()
                val peers = repository.getAll()
                for (peer in peers) {
                    if ((now - peer.lastSeen) > timeoutMillis) {
                        repository.remove(peer.peerId)
                        val currentConnections = _activeConnections.value.toMutableMap()
                        currentConnections.remove(peer.peerId)
                        _activeConnections.value = currentConnections
                        _events.emit(DiscoveryEvent.PeerLost(peer.peerId))
                    }
                }
            }
        }
    }

    fun onPeerDiscovered(discoveredPeer: Peer) {
        val (peer, isNew) = repository.upsert(discoveredPeer)
        scope.launch {
            if (isNew) {
                _events.emit(DiscoveryEvent.PeerFound(peer))
            } else {
                _events.emit(DiscoveryEvent.PeerUpdated(peer))
            }
        }
    }

    override fun getPeer(peerId: String): Peer? = repository.get(peerId)

    override fun updatePeerState(peerId: String, state: ConnectionState) {
        val updated = repository.updateState(peerId, state) ?: return
        val currentConnections = _activeConnections.value.toMutableMap()
        currentConnections[peerId] = state
        _activeConnections.value = currentConnections

        scope.launch {
            when (state) {
                ConnectionState.CONNECTED -> _events.emit(DiscoveryEvent.PeerConnected(peerId))
                ConnectionState.DISCONNECTED -> _events.emit(DiscoveryEvent.PeerDisconnected(peerId))
                else -> _events.emit(DiscoveryEvent.PeerUpdated(updated))
            }
        }
    }

    override fun registerPeer(peer: Peer) {
        onPeerDiscovered(peer)
    }

    override fun removePeer(peerId: String) {
        repository.remove(peerId)
        val currentConnections = _activeConnections.value.toMutableMap()
        currentConnections.remove(peerId)
        _activeConnections.value = currentConnections
        scope.launch {
            _events.emit(DiscoveryEvent.PeerLost(peerId))
        }
    }

    fun stop() {
        sweepJob?.cancel()
    }
}
