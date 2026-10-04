package app.neara.crypto

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NearaCryptoEngineTest {

    @Test
    fun `test identity generation creates valid Ed25519 and X25519 keys`() {
        val engine = NearaCryptoEngine("Alice")
        val identity = engine.localIdentity

        assertTrue(identity.peerId.startsWith("peer-"))
        assertEquals("Alice", identity.displayName)
        assertTrue(identity.signingPublicKey.isNotEmpty())
        assertTrue(identity.signingPrivateKey.isNotEmpty())
        assertTrue(identity.agreementPublicKey.isNotEmpty())
        assertTrue(identity.agreementPrivateKey.isNotEmpty())
    }

    @Test
    fun `test end-to-end authenticated encryption and decryption between two peers`() {
        val alice = NearaCryptoEngine("Alice")
        val bob = NearaCryptoEngine("Bob")

        val originalPlaintext = "Off-grid local mesh message: rendezvous at sector 4!".toByteArray(Charsets.UTF_8)

        // Alice encrypts for Bob
        val encryptedPayload = alice.encryptPayload(bob.localIdentity.agreementPublicKey, originalPlaintext)

        assertNotNull(encryptedPayload.ciphertext)
        assertEquals(12, encryptedPayload.iv.size)
        assertFalse(encryptedPayload.ciphertext.contentEquals(originalPlaintext))

        // Bob decrypts from Alice
        val decryptedPlaintext = bob.decryptPayload(alice.localIdentity.agreementPublicKey, encryptedPayload)

        assertArrayEquals(originalPlaintext, decryptedPlaintext)
        assertEquals(String(originalPlaintext, Charsets.UTF_8), String(decryptedPlaintext, Charsets.UTF_8))
    }

    @Test
    fun `test ciphertext tampering fails decryption`() {
        val alice = NearaCryptoEngine("Alice")
        val bob = NearaCryptoEngine("Bob")

        val originalPlaintext = "Secret private note".toByteArray(Charsets.UTF_8)
        val encryptedPayload = alice.encryptPayload(bob.localIdentity.agreementPublicKey, originalPlaintext)

        // Tamper with ciphertext byte
        val tamperedCiphertext = encryptedPayload.ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0xFF).toByte()

        val tamperedPayload = encryptedPayload.copy(ciphertext = tamperedCiphertext)

        assertThrows(Exception::class.java) {
            bob.decryptPayload(alice.localIdentity.agreementPublicKey, tamperedPayload)
        }
    }

    @Test
    fun `test digital signature creation and verification`() {
        val alice = NearaCryptoEngine("Alice")
        val message = "Authorize joining private network: University Hackathon".toByteArray(Charsets.UTF_8)

        val signature = alice.sign(message)
        assertTrue(signature.isNotEmpty())

        val isValid = alice.verify(alice.localIdentity.signingPublicKey, message, signature)
        assertTrue(isValid)

        // Verify with tampered message fails
        val tamperedMessage = "Authorize joining private network: University Malicious".toByteArray(Charsets.UTF_8)
        val isTamperedValid = alice.verify(alice.localIdentity.signingPublicKey, tamperedMessage, signature)
        assertFalse(isTamperedValid)
    }

    @Test
    fun `test HKDF produces deterministic output with same inputs`() {
        val ikm = "shared-secret-input-key-material".toByteArray(Charsets.UTF_8)
        val salt = "salt-value-12345".toByteArray(Charsets.UTF_8)
        val info = "neara-context".toByteArray(Charsets.UTF_8)

        val key1 = CryptoUtils.hkdf(ikm, salt, info, 32)
        val key2 = CryptoUtils.hkdf(ikm, salt, info, 32)

        assertEquals(32, key1.size)
        assertArrayEquals(key1, key2)
    }
}
