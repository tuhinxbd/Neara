package app.neara.transport

import app.neara.core.interfaces.PeerConnection
import app.neara.core.interfaces.TransportProvider
import app.neara.core.model.TransportType
import app.neara.protocol.FrameCodec
import app.neara.protocol.ProtocolFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class TcpPeerConnection(
    override val peerId: String,
    val socket: Socket,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : PeerConnection {

    val remoteIp: String? get() = socket.inetAddress?.hostAddress

    private val inputStream = BufferedInputStream(socket.getInputStream())
    private val outputStream = BufferedOutputStream(socket.getOutputStream())

    private val _incomingFrames = MutableSharedFlow<ProtocolFrame>(replay = 10, extraBufferCapacity = 64)
    private val _incomingBytes = MutableSharedFlow<ByteArray>(replay = 10, extraBufferCapacity = 64)

    private val isRunning = true
    private var readJob: Job? = null

    init {
        startReading()
    }

    override val isConnected: Boolean
        get() = !socket.isClosed && socket.isConnected

    private fun startReading() {
        readJob = scope.launch {
            try {
                while (isActive && !socket.isClosed) {
                    val frame = FrameCodec.decodeFromStream(inputStream)
                    if (frame != null) {
                        _incomingFrames.emit(frame)
                        _incomingBytes.emit(frame.payload)
                    } else {
                        break
                    }
                }
            } catch (e: Exception) {
                // Socket closed or read error
            } finally {
                close()
            }
        }
    }

    suspend fun sendFrame(frame: ProtocolFrame): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected) return@withContext false
            val encoded = FrameCodec.encode(frame)
            synchronized(outputStream) {
                outputStream.write(encoded)
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    override suspend fun sendBytes(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val frame = ProtocolFrame(type = 0x10, payload = data)
        sendFrame(frame)
    }

    override fun receiveBytes(): Flow<ByteArray> = _incomingBytes.asSharedFlow()

    fun receiveFrames(): Flow<ProtocolFrame> = _incomingFrames.asSharedFlow()

    override suspend fun close() = withContext(Dispatchers.IO) {
        try {
            readJob?.cancel()
            socket.close()
        } catch (e: Exception) {}
    }
}

class TcpTransportProvider(
    private val localPeerId: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : TransportProvider {

    override val transportType: TransportType = TransportType.LAN_WIFI

    private var serverSocket: ServerSocket? = null
    private val _incomingConnections = MutableSharedFlow<PeerConnection>(replay = 10, extraBufferCapacity = 32)
    private val activeConnections = ConcurrentHashMap<String, TcpPeerConnection>()
    private var isServerRunning = false

    override suspend fun startServer(port: Int): Flow<PeerConnection> = withContext(Dispatchers.IO) {
        if (isServerRunning) return@withContext _incomingConnections.asSharedFlow()

        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(port))
        serverSocket = server
        isServerRunning = true

        scope.launch {
            while (isActive && isServerRunning) {
                try {
                    val clientSocket = server.accept()
                    // Set TCP NoDelay for low latency local communication
                    clientSocket.tcpNoDelay = true

                    val connection = TcpPeerConnection(
                        peerId = "pending-${clientSocket.inetAddress.hostAddress}",
                        socket = clientSocket
                    )
                    _incomingConnections.emit(connection)
                } catch (e: Exception) {
                    if (!isServerRunning) break
                }
            }
        }

        _incomingConnections.asSharedFlow()
    }

    override suspend fun connect(ipAddress: String, port: Int, targetPeerId: String): PeerConnection? = withContext(Dispatchers.IO) {
        try {
            val existing = activeConnections[targetPeerId]
            if (existing != null && existing.isConnected) {
                return@withContext existing
            }

            val socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(ipAddress, port), 3000)

            val connection = TcpPeerConnection(targetPeerId, socket)
            activeConnections[targetPeerId] = connection
            connection
        } catch (e: Exception) {
            null
        }
    }

    fun registerConnection(peerId: String, connection: TcpPeerConnection) {
        activeConnections[peerId] = connection
    }

    fun getConnection(peerId: String): TcpPeerConnection? {
        val conn = activeConnections[peerId]
        return if (conn != null && conn.isConnected) conn else null
    }

    override suspend fun stopServer() = withContext(Dispatchers.IO) {
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null

        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
    }
}
