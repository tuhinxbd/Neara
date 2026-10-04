package app.neara.protocol

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream

object ProtocolConstants {
    val MAGIC_BYTES = byteArrayOf(0x4E, 0x45, 0x41, 0x52) // "NEAR"
    const val CURRENT_VERSION: Byte = 0x01

    const val TYPE_DISCOVERY_BEACON: Byte = 0x01
    const val TYPE_DISCOVERY_QUERY: Byte = 0x02
    const val TYPE_KEY_EXCHANGE_INIT: Byte = 0x03
    const val TYPE_KEY_EXCHANGE_RESP: Byte = 0x04
    const val TYPE_CHAT_MESSAGE: Byte = 0x10
    const val TYPE_MESSAGE_ACK: Byte = 0x11
    const val TYPE_FILE_OFFER: Byte = 0x20
    const val TYPE_FILE_ACCEPT: Byte = 0x21
    const val TYPE_FILE_CHUNK: Byte = 0x22
    const val TYPE_VOICE_PACKET: Byte = 0x30
    const val TYPE_NETWORK_JOIN_REQ: Byte = 0x40
    const val TYPE_NETWORK_JOIN_RESP: Byte = 0x41
    const val TYPE_MESH_ROUTED_PACKET: Byte = 0x50

    const val MAX_PAYLOAD_SIZE = 10 * 1024 * 1024 // 10 MB maximum single frame
}

data class ProtocolFrame(
    val version: Byte = ProtocolConstants.CURRENT_VERSION,
    val type: Byte,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProtocolFrame
        if (version != other.version) return false
        if (type != other.type) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.toInt()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

object FrameCodec {

    fun encode(frame: ProtocolFrame): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.write(ProtocolConstants.MAGIC_BYTES)
        dos.writeByte(frame.version.toInt())
        dos.writeByte(frame.type.toInt())
        dos.writeInt(frame.payload.size)
        dos.write(frame.payload)
        dos.flush()

        return baos.toByteArray()
    }

    fun decodeFromStream(inputStream: InputStream): ProtocolFrame? {
        val dis = DataInputStream(inputStream)

        // Read magic bytes
        val magic = ByteArray(4)
        try {
            dis.readFully(magic)
        } catch (e: Exception) {
            return null // End of stream or connection closed
        }

        if (!magic.contentEquals(ProtocolConstants.MAGIC_BYTES)) {
            throw IllegalArgumentException("Invalid magic bytes in protocol frame")
        }

        val version = dis.readByte()
        val type = dis.readByte()
        val payloadLength = dis.readInt()

        if (payloadLength < 0 || payloadLength > ProtocolConstants.MAX_PAYLOAD_SIZE) {
            throw IllegalArgumentException("Invalid payload length: $payloadLength")
        }

        val payload = ByteArray(payloadLength)
        dis.readFully(payload)

        return ProtocolFrame(
            version = version,
            type = type,
            payload = payload
        )
    }

    fun decodeFromBytes(bytes: ByteArray): ProtocolFrame {
        val inputStream = bytes.inputStream()
        val frame = decodeFromStream(inputStream)
            ?: throw IllegalArgumentException("Unexpected EOF while decoding frame")
        return frame
    }
}
