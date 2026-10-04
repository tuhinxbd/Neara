package app.neara.transport

import app.neara.protocol.ProtocolConstants
import app.neara.protocol.ProtocolFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TcpTransportTest {

    private var serverProvider: TcpTransportProvider? = null

    @AfterEach
    fun tearDown() {
        runBlocking {
            serverProvider?.stopServer()
        }
    }

    @Test
    fun `test real local TCP server and client connection with framed data exchange`() = runBlocking {
        val testPort = 45999
        val server = TcpTransportProvider("local-server")
        serverProvider = server

        val serverConnections = server.startServer(testPort)

        // Connect client
        val client = TcpTransportProvider("local-client")
        val clientConn = client.connect("127.0.0.1", testPort, "local-server") as? TcpPeerConnection
        assertNotNull(clientConn)
        assertTrue(clientConn!!.isConnected)

        val serverConn = withTimeout(5000) {
            serverConnections.first() as TcpPeerConnection
        }
        assertNotNull(serverConn)

        // Client sends frame to server
        val testPayload = "Off-grid TCP framed message".toByteArray(Charsets.UTF_8)
        val frameToSend = ProtocolFrame(type = ProtocolConstants.TYPE_CHAT_MESSAGE, payload = testPayload)

        val sent = clientConn.sendFrame(frameToSend)
        assertTrue(sent)

        // Server receives frame
        val receivedFrame = withTimeout(5000) {
            serverConn.receiveFrames().first()
        }
        assertEquals(ProtocolConstants.TYPE_CHAT_MESSAGE, receivedFrame.type)
        assertArrayEquals(testPayload, receivedFrame.payload)

        // Server sends response to client
        val ackPayload = "ACK-12345".toByteArray(Charsets.UTF_8)
        val ackFrame = ProtocolFrame(type = ProtocolConstants.TYPE_MESSAGE_ACK, payload = ackPayload)
        serverConn.sendFrame(ackFrame)

        val receivedAck = withTimeout(5000) {
            clientConn.receiveFrames().first()
        }
        assertEquals(ProtocolConstants.TYPE_MESSAGE_ACK, receivedAck.type)
        assertArrayEquals(ackPayload, receivedAck.payload)

        clientConn.close()
        serverConn.close()
        client.stopServer()
    }
}
