package app.neara.service

import app.neara.core.interfaces.MeshPacket
import app.neara.core.interfaces.MeshRouter
import app.neara.core.interfaces.PeerManager
import app.neara.protocol.FrameCodec
import app.neara.protocol.ProtocolConstants
import app.neara.protocol.ProtocolFrame
import app.neara.transport.TcpTransportProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WireMeshPacket(
    val packetId: String,
    val sourcePeerId: String,
    val destinationPeerId: String,
    val visitedPeers: List<String>,
    val ttl: Int,
    val payloadBase64: String
)

data class RouteEntry(
    val destinationPeerId: String,
    val nextHopPeerId: String,
    val hopCost: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

class MeshRouterImpl(
    private val localPeerId: String,
    private val transportProvider: TcpTransportProvider,
    private val peerManager: PeerManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : MeshRouter {

    private val json = Json { ignoreUnknownKeys = true }
    private val routingTable = ConcurrentHashMap<String, RouteEntry>()
    private val seenPacketIds = ConcurrentHashMap.newKeySet<String>()

    private val _routedPackets = MutableSharedFlow<MeshPacket>(replay = 10, extraBufferCapacity = 50)

    override fun updateRoutingTable(destinationPeerId: String, nextHopPeerId: String, cost: Int) {
        routingTable[destinationPeerId] = RouteEntry(destinationPeerId, nextHopPeerId, cost)
    }

    fun getRoute(destinationPeerId: String): RouteEntry? = routingTable[destinationPeerId]

    override suspend fun routePacket(packet: MeshPacket): Boolean = withContext(Dispatchers.IO) {
        // 1. Check duplicate packet
        if (!seenPacketIds.add(packet.packetId)) {
            return@withContext false // Drop duplicate
        }

        // 2. Loop prevention & TTL check
        if (packet.ttl <= 0 || packet.intermediateHops.contains(localPeerId)) {
            return@withContext false // Drop loop or expired
        }

        // 3. Destination is local node!
        if (packet.destinationPeerId == localPeerId) {
            _routedPackets.emit(packet)
            return@withContext true
        }

        // 4. Forwarding to next hop
        val updatedHops = packet.intermediateHops + localPeerId
        val forwardedPacket = packet.copy(
            ttl = packet.ttl - 1,
            intermediateHops = updatedHops
        )

        // Find next hop: either direct or from routing table
        val nextHopPeerId = routingTable[packet.destinationPeerId]?.nextHopPeerId
            ?: packet.destinationPeerId

        val connection = transportProvider.getConnection(nextHopPeerId)
            ?: run {
                val peer = peerManager.getPeer(nextHopPeerId)
                val ip = peer?.ipAddress
                if (ip != null) {
                    transportProvider.connect(ip, peer.port, nextHopPeerId)
                } else null
            }

        if (connection != null && connection.isConnected) {
            val wireMesh = WireMeshPacket(
                packetId = forwardedPacket.packetId,
                sourcePeerId = forwardedPacket.sourcePeerId,
                destinationPeerId = forwardedPacket.destinationPeerId,
                visitedPeers = forwardedPacket.intermediateHops,
                ttl = forwardedPacket.ttl,
                payloadBase64 = java.util.Base64.getEncoder().encodeToString(forwardedPacket.payload)
            )

            val wireBytes = json.encodeToString(wireMesh).toByteArray(Charsets.UTF_8)
            val frame = ProtocolFrame(type = ProtocolConstants.TYPE_MESH_ROUTED_PACKET, payload = wireBytes)
            connection.sendBytes(FrameCodec.encode(frame))
            true
        } else {
            false
        }
    }

    override fun observeRoutedPackets(): Flow<MeshPacket> = _routedPackets.asSharedFlow()
}
