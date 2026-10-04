package app.neara.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionState {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    BLOCKED
}

@Serializable
enum class ProximityEstimate {
    IMMEDIATE, // < 1m
    NEAR,      // 1m - 5m
    FAR,       // > 5m
    UNKNOWN
}

@Serializable
enum class TransportType {
    LAN_WIFI,
    WIFI_DIRECT,
    HOTSPOT,
    BLE
}

@Serializable
enum class Capability {
    MESSAGING,
    FILE_TRANSFER,
    VOICE_PTT,
    VOICE_CALL,
    MESH_RELAY,
    GROUP_ADMIN
}

@Serializable
data class Peer(
    val peerId: String,
    val displayName: String,
    val avatarId: String = "avatar_default",
    val publicKeyHex: String,
    val capabilities: Set<Capability> = setOf(
        Capability.MESSAGING,
        Capability.FILE_TRANSFER,
        Capability.VOICE_PTT
    ),
    val transportTypes: Set<TransportType> = setOf(TransportType.LAN_WIFI),
    val lastSeen: Long = System.currentTimeMillis(),
    val connectionState: ConnectionState = ConnectionState.DISCOVERED,
    val networkIds: Set<String> = emptySet(),
    val proximityEstimate: ProximityEstimate = ProximityEstimate.UNKNOWN,
    val ipAddress: String? = null,
    val port: Int = 45781
)
