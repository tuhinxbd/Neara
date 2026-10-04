package app.neara.core

import app.neara.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CoreModelsTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `test Peer serialization and deserialization`() {
        val peer = Peer(
            peerId = "peer-alpha-123",
            displayName = "Alice",
            avatarId = "avatar_1",
            publicKeyHex = "abcd1234ef",
            capabilities = setOf(Capability.MESSAGING, Capability.FILE_TRANSFER, Capability.VOICE_PTT),
            transportTypes = setOf(TransportType.LAN_WIFI),
            connectionState = ConnectionState.CONNECTED,
            proximityEstimate = ProximityEstimate.NEAR,
            ipAddress = "192.168.1.100",
            port = 45781
        )

        val serialized = json.encodeToString(peer)
        assertTrue(serialized.contains("peer-alpha-123"))
        assertTrue(serialized.contains("Alice"))

        val deserialized = json.decodeFromString<Peer>(serialized)
        assertEquals(peer.peerId, deserialized.peerId)
        assertEquals(peer.displayName, deserialized.displayName)
        assertEquals(peer.capabilities, deserialized.capabilities)
        assertEquals(peer.ipAddress, deserialized.ipAddress)
    }

    @Test
    fun `test ChatMessage serialization and defaults`() {
        val message = ChatMessage(
            messageId = "msg-001",
            conversationId = "conv-101",
            senderId = "peer-alpha-123",
            recipientId = "peer-beta-456",
            payload = "Hello over local mesh!",
            status = MessageStatus.SENT,
            reactions = mapOf("👍" to listOf("peer-beta-456"))
        )

        val serialized = json.encodeToString(message)
        val deserialized = json.decodeFromString<ChatMessage>(serialized)

        assertEquals("msg-001", deserialized.messageId)
        assertEquals(MessageStatus.SENT, deserialized.status)
        assertEquals(listOf("peer-beta-456"), deserialized.reactions["👍"])
    }

    @Test
    fun `test Network serialization`() {
        val network = Network(
            networkId = "net-university-event",
            name = "Campus Hackathon Local",
            type = NetworkType.PRIVATE,
            ownerId = "peer-admin",
            securityPolicy = SecurityPolicy(requireApproval = true, requiresPin = true),
            members = listOf(
                NetworkMember("peer-admin", "Admin User", MemberRole.OWNER),
                NetworkMember("peer-guest", "Bob", MemberRole.MEMBER)
            )
        )

        val serialized = json.encodeToString(network)
        val deserialized = json.decodeFromString<Network>(serialized)

        assertEquals("net-university-event", deserialized.networkId)
        assertEquals(NetworkType.PRIVATE, deserialized.type)
        assertEquals(2, deserialized.members.size)
    }
}
