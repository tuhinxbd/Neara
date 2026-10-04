package app.neara.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class NetworkType {
    PUBLIC,
    PRIVATE
}

@Serializable
enum class MemberRole {
    OWNER,
    ADMIN,
    MEMBER
}

@Serializable
data class NetworkMember(
    val peerId: String,
    val displayName: String,
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis()
)

@Serializable
data class SecurityPolicy(
    val requireApproval: Boolean = false,
    val requiresPin: Boolean = false,
    val pinHash: String? = null,
    val allowRelay: Boolean = true
)

@Serializable
data class Network(
    val networkId: String,
    val name: String,
    val type: NetworkType = NetworkType.PUBLIC,
    val ownerId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val publicKeyHex: String = "",
    val securityPolicy: SecurityPolicy = SecurityPolicy(),
    val members: List<NetworkMember> = emptyList(),
    val pendingMembers: List<NetworkMember> = emptyList()
)
