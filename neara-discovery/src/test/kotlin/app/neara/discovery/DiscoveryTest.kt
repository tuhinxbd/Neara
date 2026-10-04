package app.neara.discovery

import app.neara.core.model.*
import app.neara.protocol.FrameCodec
import app.neara.protocol.ProtocolConstants
import app.neara.protocol.ProtocolFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test PeerRepository upsert and state updates`() {
        val repo = PeerRepository()
        val peer = Peer(
            peerId = "peer-01",
            displayName = "Alice",
            publicKeyHex = "abcd",
            port = 45781
        )

        val (inserted, isNew) = repo.upsert(peer)
        assertTrue(isNew)
        assertEquals("Alice", inserted.displayName)

        // Update state
        val updated = repo.updateState("peer-01", ConnectionState.CONNECTED)
        assertNotNull(updated)
        assertEquals(ConnectionState.CONNECTED, updated?.connectionState)

        // Verify retrieval
        assertEquals(1, repo.getAll().size)
        assertEquals("Alice", repo.get("peer-01")?.displayName)
    }

    @Test
    fun `test DiscoveryBeacon framing and parsing`() {
        val beacon = DiscoveryBeacon(
            peerId = "peer-test-beacon",
            displayName = "Device Bob",
            avatarId = "avatar_bob",
            publicKeyHex = "12345678",
            capabilities = setOf(Capability.MESSAGING, Capability.VOICE_PTT),
            transportTypes = setOf(TransportType.LAN_WIFI),
            port = 45781
        )

        val serialized = json.encodeToString(beacon).toByteArray(Charsets.UTF_8)
        val frame = ProtocolFrame(type = ProtocolConstants.TYPE_DISCOVERY_BEACON, payload = serialized)

        val encodedBytes = FrameCodec.encode(frame)
        val decodedFrame = FrameCodec.decodeFromBytes(encodedBytes)

        assertEquals(ProtocolConstants.TYPE_DISCOVERY_BEACON, decodedFrame.type)

        val decodedBeacon = json.decodeFromString<DiscoveryBeacon>(String(decodedFrame.payload, Charsets.UTF_8))
        assertEquals(beacon.peerId, decodedBeacon.peerId)
        assertEquals(beacon.displayName, decodedBeacon.displayName)
        assertEquals(beacon.port, decodedBeacon.port)
    }

    @Test
    fun `test PeerStateManager emits events on peer discovered and removed`() = runTest {
        val repo = PeerRepository()
        val manager = PeerStateManager(repo, timeoutMillis = 10_000L, scope = this)

        val peer = Peer(
            peerId = "peer-charlie",
            displayName = "Charlie",
            publicKeyHex = "ffeedd",
            port = 45781
        )

        manager.registerPeer(peer)

        val peers = manager.peers.value
        assertEquals(1, peers.size)
        assertEquals("Charlie", peers[0].displayName)

        manager.updatePeerState("peer-charlie", ConnectionState.CONNECTED)
        assertEquals(ConnectionState.CONNECTED, manager.getPeer("peer-charlie")?.connectionState)

        manager.stop()
    }
}
