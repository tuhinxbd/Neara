package app.neara.service

import app.neara.core.interfaces.AudioPacket
import app.neara.core.interfaces.FileTransport
import app.neara.core.interfaces.VoiceTransport
import app.neara.core.model.FileMetadata
import app.neara.core.model.FileTransferProgress
import app.neara.core.model.TransferStatus
import app.neara.crypto.CryptoUtils
import app.neara.protocol.ProtocolConstants
import app.neara.protocol.ProtocolFrame
import app.neara.transport.TcpTransportProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class WireFileOffer(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val sha256Checksum: String,
    val totalChunks: Int,
    val chunkSize: Int
)

@Serializable
data class WireFileChunk(
    val fileId: String,
    val chunkIndex: Int,
    val chunkDataHex: String,
    val chunkChecksum: String
)

class FileTransferService(
    private val localPeerId: String,
    private val transportProvider: TcpTransportProvider,
    private val downloadDirectory: File = File(System.getProperty("user.home"), "Downloads/Neara")
) : FileTransport {

    private val json = Json { ignoreUnknownKeys = true }
    private val _incomingFiles = MutableSharedFlow<Pair<FileMetadata, File>>(extraBufferCapacity = 10)

    init {
        try {
            downloadDirectory.mkdirs()
        } catch (e: Exception) {}
    }

    override suspend fun sendFile(
        peerId: String,
        file: File,
        metadata: FileMetadata
    ): Flow<FileTransferProgress> = flow {
        val connection = transportProvider.getConnection(peerId)
            ?: throw IllegalStateException("No active connection to peer: $peerId")

        // 1. Send file offer frame
        val offer = WireFileOffer(
            fileId = metadata.fileId,
            fileName = metadata.fileName,
            fileSize = metadata.fileSize,
            mimeType = metadata.mimeType,
            sha256Checksum = metadata.sha256Checksum,
            totalChunks = metadata.totalChunks,
            chunkSize = metadata.chunkSize
        )
        val offerFrame = ProtocolFrame(
            type = ProtocolConstants.TYPE_FILE_OFFER,
            payload = json.encodeToString(offer).toByteArray(Charsets.UTF_8)
        )
        connection.sendFrame(offerFrame)

        emit(FileTransferProgress(metadata.fileId, metadata.fileName, 0L, metadata.fileSize, 0L, TransferStatus.IN_PROGRESS))

        // 2. Stream chunks
        val buffer = ByteArray(metadata.chunkSize)
        var bytesSent = 0L
        val startTime = System.currentTimeMillis()

        FileInputStream(file).use { fis ->
            var chunkIndex = 0
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                val chunkData = buffer.copyOf(bytesRead)
                val chunkChecksum = CryptoUtils.sha256Hex(chunkData)

                val chunk = WireFileChunk(
                    fileId = metadata.fileId,
                    chunkIndex = chunkIndex++,
                    chunkDataHex = CryptoUtils.toHex(chunkData),
                    chunkChecksum = chunkChecksum
                )

                val chunkFrame = ProtocolFrame(
                    type = ProtocolConstants.TYPE_FILE_CHUNK,
                    payload = json.encodeToString(chunk).toByteArray(Charsets.UTF_8)
                )
                connection.sendFrame(chunkFrame)

                bytesSent += bytesRead
                val elapsedSec = maxOf(1L, (System.currentTimeMillis() - startTime) / 1000L)
                val speed = bytesSent / elapsedSec

                emit(FileTransferProgress(metadata.fileId, metadata.fileName, bytesSent, metadata.fileSize, speed, TransferStatus.IN_PROGRESS))
            }
        }

        emit(FileTransferProgress(metadata.fileId, metadata.fileName, metadata.fileSize, metadata.fileSize, 0L, TransferStatus.COMPLETED))
    }.flowOn(Dispatchers.IO)

    override fun observeIncomingFiles(): Flow<Pair<FileMetadata, File>> = _incomingFiles.asSharedFlow()
}

interface AudioCaptureProvider {
    fun startCapture(onAudioData: (ByteArray) -> Unit)
    fun stopCapture()
}

class VoiceService(
    private val localPeerId: String,
    private val voiceUdpPort: Int = 45782,
    private val captureProvider: AudioCaptureProvider? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : VoiceTransport {

    private val isPttActive = AtomicBoolean(false)
    private val packetSeq = AtomicLong(0L)
    private val _incomingAudio = MutableSharedFlow<AudioPacket>(extraBufferCapacity = 100)

    private var udpSocket: DatagramSocket? = null

    init {
        startUdpAudioReceiver()
    }

    private fun startUdpAudioReceiver() {
        scope.launch {
            try {
                val socket = DatagramSocket(voiceUdpPort)
                udpSocket = socket
                val buffer = ByteArray(2048)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val audioData = packet.data.copyOf(packet.length)
                    val audioPacket = AudioPacket(
                        senderId = "peer-remote",
                        sequenceNumber = System.currentTimeMillis(),
                        timestamp = System.currentTimeMillis(),
                        audioData = audioData
                    )
                    _incomingAudio.emit(audioPacket)
                }
            } catch (e: Exception) {}
        }
    }

    override suspend fun startPushToTalk(targetId: String, isGroup: Boolean) = withContext(Dispatchers.IO) {
        if (!isPttActive.compareAndSet(false, true)) return@withContext

        captureProvider?.startCapture { audioFrame ->
            if (isPttActive.get()) {
                val packet = AudioPacket(localPeerId, packetSeq.incrementAndGet(), System.currentTimeMillis(), audioFrame)
                _incomingAudio.tryEmit(packet)
            }
        }
    }

    override suspend fun stopPushToTalk() {
        withContext(Dispatchers.IO) {
            isPttActive.set(false)
            captureProvider?.stopCapture()
        }
    }

    override fun observeIncomingAudio(): Flow<AudioPacket> = _incomingAudio.asSharedFlow()

    fun close() {
        isPttActive.set(false)
        captureProvider?.stopCapture()
        udpSocket?.close()
    }
}
