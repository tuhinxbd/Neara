package app.neara.core.model

import kotlinx.serialization.Serializable

sealed interface DiscoveryEvent {
    data class PeerFound(val peer: Peer) : DiscoveryEvent
    data class PeerUpdated(val peer: Peer) : DiscoveryEvent
    data class PeerLost(val peerId: String) : DiscoveryEvent
    data class PeerConnected(val peerId: String) : DiscoveryEvent
    data class PeerDisconnected(val peerId: String) : DiscoveryEvent
}

@Serializable
enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

@Serializable
data class FileMetadata(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val sha256Checksum: String,
    val totalChunks: Int,
    val chunkSize: Int = 64 * 1024 // 64 KB chunks
)

@Serializable
data class FileTransferProgress(
    val fileId: String,
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val status: TransferStatus
) {
    val percentage: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes) * 100f else 0f
}
