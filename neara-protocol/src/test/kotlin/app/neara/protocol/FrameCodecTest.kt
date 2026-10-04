package app.neara.protocol

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class FrameCodecTest {

    @Test
    fun `test encode and decode single frame`() {
        val payload = "Hello Neara Local P2P".toByteArray(Charsets.UTF_8)
        val frame = ProtocolFrame(
            version = ProtocolConstants.CURRENT_VERSION,
            type = ProtocolConstants.TYPE_CHAT_MESSAGE,
            payload = payload
        )

        val encoded = FrameCodec.encode(frame)
        assertTrue(encoded.size > payload.size)

        val decoded = FrameCodec.decodeFromBytes(encoded)
        assertEquals(ProtocolConstants.CURRENT_VERSION, decoded.version)
        assertEquals(ProtocolConstants.TYPE_CHAT_MESSAGE, decoded.type)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun `test stream with multiple consecutive frames`() {
        val frame1 = ProtocolFrame(type = ProtocolConstants.TYPE_DISCOVERY_BEACON, payload = byteArrayOf(1, 2, 3))
        val frame2 = ProtocolFrame(type = ProtocolConstants.TYPE_MESSAGE_ACK, payload = byteArrayOf(4, 5, 6, 7))

        val streamBytes = FrameCodec.encode(frame1) + FrameCodec.encode(frame2)
        val inputStream = ByteArrayInputStream(streamBytes)

        val decoded1 = FrameCodec.decodeFromStream(inputStream)
        assertNotNull(decoded1)
        assertEquals(ProtocolConstants.TYPE_DISCOVERY_BEACON, decoded1?.type)
        assertArrayEquals(byteArrayOf(1, 2, 3), decoded1?.payload)

        val decoded2 = FrameCodec.decodeFromStream(inputStream)
        assertNotNull(decoded2)
        assertEquals(ProtocolConstants.TYPE_MESSAGE_ACK, decoded2?.type)
        assertArrayEquals(byteArrayOf(4, 5, 6, 7), decoded2?.payload)

        val decoded3 = FrameCodec.decodeFromStream(inputStream)
        assertNull(decoded3) // Stream exhausted cleanly
    }

    @Test
    fun `test corrupted magic bytes throws exception`() {
        val badBytes = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x01, 0x55)
        assertThrows(IllegalArgumentException::class.java) {
            FrameCodec.decodeFromBytes(badBytes)
        }
    }
}
