package app.neara.service

import app.neara.core.interfaces.EncryptionManager
import app.neara.core.model.*
import app.neara.crypto.CryptoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class JoinNetworkRequest(
    val networkId: String,
    val applicantPeerId: String,
    val applicantDisplayName: String,
    val applicantPublicKeyHex: String = "",
    val pinAttemptHash: String? = null,
    val signatureHex: String = ""
)

@Serializable
data class JoinNetworkResponse(
    val networkId: String,
    val isApproved: Boolean,
    val reason: String? = null,
    val encryptedNetworkKeyHex: String? = null,
    val network: Network? = null,
    val recentMessages: List<app.neara.core.model.ChatMessage> = emptyList()
)

class NetworkManager(
    private val localPeerId: String,
    private val encryptionManager: EncryptionManager,
    private val storageFile: File? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _networks = ConcurrentHashMap<String, Network>()
    private val _networksFlow = MutableStateFlow<List<Network>>(emptyList())
    val networksFlow: StateFlow<List<Network>> = _networksFlow.asStateFlow()
    private val fileLock = Any()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        try {
            if (file.exists()) {
                val text = file.readText(Charsets.UTF_8)
                if (text.isNotBlank()) {
                    val list = json.decodeFromString<List<Network>>(text)
                    for (net in list) {
                        _networks[net.networkId] = net
                    }
                    _networksFlow.value = _networks.values.toList()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistToDisk() {
        val file = storageFile ?: return
        synchronized(fileLock) {
            try {
                file.parentFile?.mkdirs()
                val list = _networks.values.toList()
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                tempFile.writeText(json.encodeToString(list), Charsets.UTF_8)
                if (tempFile.exists()) {
                    if (file.exists()) file.delete()
                    tempFile.renameTo(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createNetwork(
        name: String,
        type: NetworkType,
        pin: String? = null,
        requireApproval: Boolean = (type == NetworkType.PRIVATE)
    ): Network {
        val networkId = "net-${UUID.randomUUID().toString().take(8)}"
        val pinHash = if (!pin.isNullOrBlank()) {
            CryptoUtils.sha256Hex((pin + networkId).toByteArray(Charsets.UTF_8))
        } else null

        val ownerMember = NetworkMember(
            peerId = localPeerId,
            displayName = encryptionManager.localIdentity.displayName,
            role = MemberRole.OWNER,
            joinedAt = System.currentTimeMillis()
        )

        val policy = SecurityPolicy(
            requireApproval = requireApproval,
            requiresPin = pinHash != null,
            pinHash = pinHash,
            allowRelay = true
        )

        val network = Network(
            networkId = networkId,
            name = name,
            type = type,
            ownerId = localPeerId,
            createdAt = System.currentTimeMillis(),
            publicKeyHex = CryptoUtils.toHex(encryptionManager.localIdentity.signingPublicKey),
            securityPolicy = policy,
            members = listOf(ownerMember)
        )

        _networks[networkId] = network
        _networksFlow.value = _networks.values.toList()
        persistToDisk()
        return network
    }

    fun addDiscoveredNetwork(network: Network) {
        _networks[network.networkId] = network
        _networksFlow.value = _networks.values.toList()
        persistToDisk()
    }

    fun generateQrInvitationUri(networkId: String, listeningPort: Int = 45781): String {
        val network = _networks[networkId] ?: throw IllegalArgumentException("Network not found: $networkId")
        val encName = URLEncoder.encode(network.name, StandardCharsets.UTF_8.name())
        return "neara://join?netId=$networkId&name=$encName&pubKey=${network.publicKeyHex}&port=$listeningPort&type=${network.type.name}"
    }

    fun processJoinRequest(request: JoinNetworkRequest): JoinNetworkResponse {
        val network = _networks[request.networkId]
            ?: return JoinNetworkResponse(request.networkId, false, "Network not found on host")

        // 1. PIN verification if required
        if (network.securityPolicy.requiresPin) {
            val expectedHash = network.securityPolicy.pinHash
            if (expectedHash == null || request.pinAttemptHash != expectedHash) {
                return JoinNetworkResponse(request.networkId, false, "Invalid PIN / Password")
            }
        }

        // 2. Check if admin approval is required
        if (network.securityPolicy.requireApproval) {
            val pendingMember = NetworkMember(
                peerId = request.applicantPeerId,
                displayName = request.applicantDisplayName,
                role = MemberRole.MEMBER,
                joinedAt = System.currentTimeMillis()
            )
            val updatedPending = network.pendingMembers.filterNot { it.peerId == request.applicantPeerId } + pendingMember
            val updatedNetwork = network.copy(pendingMembers = updatedPending)
            _networks[network.networkId] = updatedNetwork
            _networksFlow.value = _networks.values.toList()
            persistToDisk()
            return JoinNetworkResponse(
                networkId = network.networkId,
                isApproved = false,
                reason = "Request submitted. Awaiting admin approval."
            )
        }

        // 3. Add applicant directly to member list
        val newMember = NetworkMember(
            peerId = request.applicantPeerId,
            displayName = request.applicantDisplayName,
            role = MemberRole.MEMBER,
            joinedAt = System.currentTimeMillis()
        )

        val updatedMembers = network.members.filterNot { it.peerId == request.applicantPeerId } + newMember
        val updatedPending = network.pendingMembers.filterNot { it.peerId == request.applicantPeerId }
        val updatedNetwork = network.copy(members = updatedMembers, pendingMembers = updatedPending)
        _networks[network.networkId] = updatedNetwork
        _networksFlow.value = _networks.values.toList()
        persistToDisk()

        return JoinNetworkResponse(
            networkId = network.networkId,
            isApproved = true,
            network = updatedNetwork
        )
    }

    fun addMember(networkId: String, peerId: String, displayName: String, role: MemberRole = MemberRole.MEMBER): Boolean {
        val network = _networks[networkId] ?: return false
        val newMember = NetworkMember(peerId, displayName, role, System.currentTimeMillis())
        val updatedMembers = network.members.filterNot { it.peerId == peerId } + newMember
        val updatedPending = network.pendingMembers.filterNot { it.peerId == peerId }
        val updated = network.copy(members = updatedMembers, pendingMembers = updatedPending)
        _networks[networkId] = updated
        _networksFlow.value = _networks.values.toList()
        persistToDisk()
        return true
    }

    fun approveJoinRequest(networkId: String, applicantPeerId: String, adminPeerId: String): Boolean {
        val network = _networks[networkId] ?: return false
        val admin = network.members.find { it.peerId == adminPeerId }
        if (admin == null || (admin.role != MemberRole.OWNER && admin.role != MemberRole.ADMIN)) {
            return false // Unauthorized
        }

        val applicant = network.pendingMembers.find { it.peerId == applicantPeerId } ?: return false
        val updatedPending = network.pendingMembers.filterNot { it.peerId == applicantPeerId }
        val updatedMembers = network.members.filterNot { it.peerId == applicantPeerId } + applicant.copy(joinedAt = System.currentTimeMillis())
        val updated = network.copy(members = updatedMembers, pendingMembers = updatedPending)
        _networks[networkId] = updated
        _networksFlow.value = _networks.values.toList()
        persistToDisk()
        return true
    }

    fun rejectJoinRequest(networkId: String, applicantPeerId: String, adminPeerId: String): Boolean {
        val network = _networks[networkId] ?: return false
        val admin = network.members.find { it.peerId == adminPeerId }
        if (admin == null || (admin.role != MemberRole.OWNER && admin.role != MemberRole.ADMIN)) {
            return false // Unauthorized
        }

        val updatedPending = network.pendingMembers.filterNot { it.peerId == applicantPeerId }
        val updated = network.copy(pendingMembers = updatedPending)
        _networks[networkId] = updated
        _networksFlow.value = _networks.values.toList()
        persistToDisk()
        return true
    }

    fun updateSecurityPolicy(networkId: String, requireApproval: Boolean): Boolean {
        val network = _networks[networkId] ?: return false
        val updatedPolicy = network.securityPolicy.copy(requireApproval = requireApproval)
        val updated = network.copy(securityPolicy = updatedPolicy)
        _networks[networkId] = updated
        _networksFlow.value = _networks.values.toList()
        persistToDisk()
        return true
    }

    fun removeMember(networkId: String, memberPeerId: String, adminPeerId: String): Boolean {
        val network = _networks[networkId] ?: return false
        val admin = network.members.find { it.peerId == adminPeerId }
        if (admin == null || (admin.role != MemberRole.OWNER && admin.role != MemberRole.ADMIN)) {
            return false // Unauthorized
        }

        val updated = network.copy(members = network.members.filterNot { it.peerId == memberPeerId })
        _networks[networkId] = updated
        _networksFlow.value = _networks.values.toList()
        persistToDisk()
        return true
    }

    fun leaveNetwork(networkId: String, peerId: String): Boolean {
        val network = _networks[networkId] ?: return false
        if (peerId == localPeerId || network.ownerId == peerId) {
            return deleteNetwork(networkId)
        }
        val updated = network.copy(members = network.members.filterNot { it.peerId == peerId })
        _networks[networkId] = updated
        _networksFlow.value = _networks.values.toList()
        persistToDisk()
        return true
    }

    fun deleteNetwork(networkId: String): Boolean {
        val removed = _networks.remove(networkId) != null
        if (removed) {
            _networksFlow.value = _networks.values.toList()
            persistToDisk()
        }
        return removed
    }

    fun updateNetworkName(networkId: String, newName: String): Boolean {
        val network = _networks[networkId] ?: return false
        val updated = network.copy(name = newName)
        _networks[networkId] = updated
        _networksFlow.value = _networks.values.toList()
        persistToDisk()
        return true
    }

    fun getNetwork(networkId: String): Network? = _networks[networkId]
}
