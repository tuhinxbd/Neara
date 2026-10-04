package app.neara.storage

import app.neara.core.model.ChatMessage
import app.neara.core.model.MessageStatus
import app.neara.core.model.MessageType
import java.util.concurrent.ConcurrentHashMap

interface IMessageRepository {
    fun saveMessage(message: ChatMessage)
    fun getMessage(messageId: String): ChatMessage?
    fun getMessagesForConversation(conversationId: String, limit: Int = 100): List<ChatMessage>
    fun getAllMessages(): List<ChatMessage>
    fun updateMessageStatus(messageId: String, status: MessageStatus)
    fun markConversationAsRead(conversationId: String, localPeerId: String)
    fun deleteConversation(conversationId: String)
    fun deleteMessage(messageId: String)
    fun deleteActionMessages()
    fun clearAll()
}

interface IOfflineQueueManager {
    fun enqueue(message: ChatMessage, targetPeerId: String)
    fun getPendingForPeer(targetPeerId: String): List<ChatMessage>
    fun remove(messageId: String)
    fun incrementRetry(messageId: String)
    fun getPendingCount(): Int
    fun clear()
}

class InMemoryMessageRepository : IMessageRepository {
    private val messagesMap = ConcurrentHashMap<String, ChatMessage>()

    override fun saveMessage(message: ChatMessage) {
        if (message.type == MessageType.ACTION || message.payload.startsWith("ACTION:")) return
        messagesMap[message.messageId] = message
    }

    override fun getMessage(messageId: String): ChatMessage? {
        return messagesMap[messageId]
    }

    override fun getMessagesForConversation(conversationId: String, limit: Int): List<ChatMessage> {
        return messagesMap.values
            .filter { it.conversationId == conversationId && it.type != MessageType.ACTION && !it.payload.startsWith("ACTION:") }
            .sortedWith(compareBy({ it.timestamp }, { it.sequenceNumber }))
            .take(limit)
    }

    override fun getAllMessages(): List<ChatMessage> {
        return messagesMap.values
            .filter { it.type != MessageType.ACTION && !it.payload.startsWith("ACTION:") }
            .toList()
    }

    override fun deleteActionMessages() {
        val keysToRemove = messagesMap.filter { it.value.type == MessageType.ACTION || it.value.payload.startsWith("ACTION:") }.keys
        keysToRemove.forEach { messagesMap.remove(it) }
    }

    override fun updateMessageStatus(messageId: String, status: MessageStatus) {
        val msg = messagesMap[messageId] ?: return
        messagesMap[messageId] = msg.copy(status = status)
    }

    override fun markConversationAsRead(conversationId: String, localPeerId: String) {
        messagesMap.values
            .filter { it.conversationId == conversationId && it.senderId != localPeerId && it.status != MessageStatus.READ }
            .forEach { msg ->
                messagesMap[msg.messageId] = msg.copy(status = MessageStatus.READ)
            }
    }

    override fun deleteConversation(conversationId: String) {
        val toRemove = messagesMap.values.filter { it.conversationId == conversationId }.map { it.messageId }
        toRemove.forEach { messagesMap.remove(it) }
    }

    override fun deleteMessage(messageId: String) {
        messagesMap.remove(messageId)
    }

    override fun clearAll() {
        messagesMap.clear()
    }
}

class InMemoryOfflineQueueManager(
    private val messageRepository: IMessageRepository
) : IOfflineQueueManager {
    private val queue = ConcurrentHashMap<String, String>() // messageId -> targetPeerId
    private val retries = ConcurrentHashMap<String, Int>()

    init {
        try {
            for (msg in messageRepository.getAllMessages()) {
                val rId = msg.recipientId
                if (msg.status == MessageStatus.PENDING && rId != null) {
                    queue[msg.messageId] = rId
                }
            }
        } catch (e: Exception) {}
    }

    override fun enqueue(message: ChatMessage, targetPeerId: String) {
        if (message.type == MessageType.ACTION || message.payload.startsWith("ACTION:")) return
        messageRepository.saveMessage(message.copy(status = MessageStatus.PENDING))
        queue[message.messageId] = targetPeerId
        retries[message.messageId] = 0
    }

    override fun getPendingForPeer(targetPeerId: String): List<ChatMessage> {
        val messageIds = queue.entries.filter { it.value == targetPeerId }.map { it.key }
        return messageIds.mapNotNull { messageRepository.getMessage(it) }
            .sortedBy { it.timestamp }
    }

    override fun remove(messageId: String) {
        queue.remove(messageId)
        retries.remove(messageId)
    }

    override fun incrementRetry(messageId: String) {
        retries.compute(messageId) { _, current -> (current ?: 0) + 1 }
    }

    override fun getPendingCount(): Int {
        return queue.size
    }

    override fun clear() {
        queue.clear()
        retries.clear()
    }
}
