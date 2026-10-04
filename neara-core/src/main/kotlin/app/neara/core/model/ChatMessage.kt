package app.neara.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    PENDING,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

@Serializable
enum class MessageType {
    TEXT,
    EMOJI,
    IMAGE,
    FILE,
    VOICE,
    SYSTEM,
    ACTION
}

@Serializable
data class ChatMessage(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String? = null, // null indicates group / broadcast message
    val timestamp: Long = System.currentTimeMillis(),
    val sequenceNumber: Long = 0L,
    val type: MessageType = MessageType.TEXT,
    val payload: String,
    val signature: String = "",
    val status: MessageStatus = MessageStatus.PENDING,
    val replyToMessageId: String? = null,
    val reactions: Map<String, List<String>> = emptyMap() // emoji -> list of senderIds
)
