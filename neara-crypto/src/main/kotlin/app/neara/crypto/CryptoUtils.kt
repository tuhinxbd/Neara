package app.neara.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private val secureRandom = SecureRandom()

    fun randomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun sha256(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data)
    }

    fun sha256Hex(data: ByteArray): String {
        return toHex(sha256(data))
    }

    fun toHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val octet = b.toInt() and 0xFF
            result.append(hexChars[octet ushr 4])
            result.append(hexChars[octet and 0x0F])
        }
        return result.toString()
    }

    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim().lowercase()
        require(clean.length % 2 == 0) { "Hex string must have an even length" }
        val bytes = ByteArray(clean.length / 2)
        for (i in bytes.indices) {
            val high = Character.digit(clean[i * 2], 16)
            val low = Character.digit(clean[i * 2 + 1], 16)
            require(high != -1 && low != -1) { "Invalid hex character" }
            bytes[i] = ((high shl 4) or low).toByte()
        }
        return bytes
    }

    /**
     * HKDF Extract and Expand implementation (RFC 5869)
     */
    fun hkdf(ikm: ByteArray, salt: ByteArray = ByteArray(32), info: ByteArray = ByteArray(0), length: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm) // Extract

        // Expand
        val numBlocks = (length + 31) / 32
        var t = ByteArray(0)
        val okm = ByteArray(length)
        var offset = 0

        for (i in 1..numBlocks) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()

            val toCopy = minOf(32, length - offset)
            System.arraycopy(t, 0, okm, offset, toCopy)
            offset += toCopy
        }

        return okm
    }
}
