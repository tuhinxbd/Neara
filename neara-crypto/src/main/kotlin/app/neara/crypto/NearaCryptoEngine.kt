package app.neara.crypto

import app.neara.core.interfaces.DeviceIdentity
import app.neara.core.interfaces.EncryptedPayload
import app.neara.core.interfaces.EncryptionManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class NearaCryptoEngine(
    displayName: String = "User-${CryptoUtils.toHex(CryptoUtils.randomBytes(2))}",
    savedIdentity: DeviceIdentity? = null
) : EncryptionManager {

    companion object {
        val bcProvider: Provider = BouncyCastleProvider()

        private fun getKeyFactory(algorithm: String): KeyFactory {
            return try {
                KeyFactory.getInstance(algorithm, bcProvider)
            } catch (e: Throwable) {
                KeyFactory.getInstance(algorithm)
            }
        }

        private fun getKeyAgreement(algorithm: String): KeyAgreement {
            return try {
                KeyAgreement.getInstance(algorithm, bcProvider)
            } catch (e: Throwable) {
                KeyAgreement.getInstance(algorithm)
            }
        }

        private fun getSignature(algorithm: String): Signature {
            return try {
                Signature.getInstance(algorithm, bcProvider)
            } catch (e: Throwable) {
                Signature.getInstance(algorithm)
            }
        }

        private fun getCipher(transformation: String): Cipher {
            return try {
                Cipher.getInstance(transformation, bcProvider)
            } catch (e: Throwable) {
                Cipher.getInstance(transformation)
            }
        }

        fun generateIdentity(displayName: String, fixedPeerId: String? = null): DeviceIdentity {
            val kpgEd = try {
                KeyPairGenerator.getInstance("Ed25519", bcProvider)
            } catch (e: Throwable) {
                KeyPairGenerator.getInstance("Ed25519")
            }
            val kpEd = kpgEd.generateKeyPair()

            val kpgX = try {
                KeyPairGenerator.getInstance("X25519", bcProvider)
            } catch (e: Throwable) {
                KeyPairGenerator.getInstance("X25519")
            }
            val kpX = kpgX.generateKeyPair()

            // Use a fixed/stable peerId if provided (e.g. device hardware ID),
            // otherwise derive from public key hash as before.
            val peerId = fixedPeerId
                ?: "peer-${CryptoUtils.toHex(CryptoUtils.sha256(kpEd.public.encoded)).take(12)}"

            return DeviceIdentity(
                peerId = peerId,
                displayName = displayName,
                signingPublicKey = kpEd.public.encoded,
                signingPrivateKey = kpEd.private.encoded,
                agreementPublicKey = kpX.public.encoded,
                agreementPrivateKey = kpX.private.encoded
            )
        }
    }

    override val localIdentity: DeviceIdentity = savedIdentity ?: generateIdentity(displayName)

    private val edPrivateKey: PrivateKey
    private val edPublicKey: PublicKey
    private val xPrivateKey: PrivateKey
    private val xPublicKey: PublicKey

    init {
        val keyFactoryEd = getKeyFactory("Ed25519")
        edPrivateKey = keyFactoryEd.generatePrivate(PKCS8EncodedKeySpec(localIdentity.signingPrivateKey))
        edPublicKey = keyFactoryEd.generatePublic(X509EncodedKeySpec(localIdentity.signingPublicKey))

        val keyFactoryX = getKeyFactory("X25519")
        xPrivateKey = keyFactoryX.generatePrivate(PKCS8EncodedKeySpec(localIdentity.agreementPrivateKey))
        xPublicKey = keyFactoryX.generatePublic(X509EncodedKeySpec(localIdentity.agreementPublicKey))
    }

    override fun encryptPayload(recipientPublicKey: ByteArray, plaintext: ByteArray): EncryptedPayload {
        val keyFactory = getKeyFactory("X25519")
        val recipientPubKey = keyFactory.generatePublic(X509EncodedKeySpec(recipientPublicKey))

        val keyAgreement = getKeyAgreement("X25519")
        keyAgreement.init(xPrivateKey)
        keyAgreement.doPhase(recipientPubKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        val sessionKeyBytes = CryptoUtils.hkdf(
            ikm = sharedSecret,
            salt = CryptoUtils.sha256(localIdentity.agreementPublicKey),
            info = "neara-session-v1".toByteArray(Charsets.UTF_8),
            length = 32
        )

        val iv = CryptoUtils.randomBytes(12)
        val cipher = getCipher("ChaCha20-Poly1305")
        val secretKeySpec = SecretKeySpec(sessionKeyBytes, "ChaCha20")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec)

        val ciphertext = cipher.doFinal(plaintext)
        val senderPubKeyHash = CryptoUtils.sha256(localIdentity.agreementPublicKey).sliceArray(0 until 8)

        return EncryptedPayload(
            iv = iv,
            ciphertext = ciphertext,
            senderPublicKeyHash = senderPubKeyHash,
            authTag = ByteArray(0)
        )
    }

    override fun decryptPayload(senderPublicKey: ByteArray, encrypted: EncryptedPayload): ByteArray {
        val keyFactory = getKeyFactory("X25519")
        val senderPubKey = keyFactory.generatePublic(X509EncodedKeySpec(senderPublicKey))

        val keyAgreement = getKeyAgreement("X25519")
        keyAgreement.init(xPrivateKey)
        keyAgreement.doPhase(senderPubKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        val sessionKeyBytes = CryptoUtils.hkdf(
            ikm = sharedSecret,
            salt = CryptoUtils.sha256(senderPublicKey),
            info = "neara-session-v1".toByteArray(Charsets.UTF_8),
            length = 32
        )

        val cipher = getCipher("ChaCha20-Poly1305")
        val secretKeySpec = SecretKeySpec(sessionKeyBytes, "ChaCha20")
        val ivSpec = IvParameterSpec(encrypted.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)

        return cipher.doFinal(encrypted.ciphertext)
    }

    override fun sign(data: ByteArray): ByteArray {
        val signer = getSignature("Ed25519")
        signer.initSign(edPrivateKey)
        signer.update(data)
        return signer.sign()
    }

    override fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val keyFactory = getKeyFactory("Ed25519")
            val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKey))
            val verifier = getSignature("Ed25519")
            verifier.initVerify(pubKey)
            verifier.update(data)
            verifier.verify(signature)
        } catch (e: Exception) {
            false
        }
    }
}
