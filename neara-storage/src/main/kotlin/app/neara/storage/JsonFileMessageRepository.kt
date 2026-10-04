package app.neara.storage

import app.neara.core.model.ChatMessage
import app.neara.core.model.MessageStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class JsonFileMessageRepository(
    private val storageFile: File
) : IMessageRepository {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }
    private val messagesMap = ConcurrentHashMap<String, ChatMessage>()
    private val fileLock = Any()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        try {
            if (storageFile.exists()) {
                val text = storageFile.readText(Charsets.UTF_8)
                if (text.isNotBlank()) {
                    val list = json.decodeFromString<List<ChatMessage>>(text)
                    var dirty = false
                    for (msg in list) {
                        if (msg.type != app.neara.core.model.MessageType.ACTION && !msg.payload.startsWith("ACTION:")) {
                            messagesMap[msg.messageId] = msg
                        } else {
                            dirty = true
                        }
                    }
                    if (dirty) {
                        persistToDisk()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistToDisk() {
        synchronized(fileLock) {
            try {
                storageFile.parentFile?.mkdirs()
                val list = messagesMap.values.filter { it.type != app.neara.core.model.MessageType.ACTION && !it.payload.startsWith("ACTION:") }
                val tempFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
                tempFile.writeText(json.encodeToString(list), Charsets.UTF_8)
                if (tempFile.exists()) {
                    if (storageFile.exists()) storageFile.delete()
                    tempFile.renameTo(storageFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun saveMessage(message: ChatMessage) {
        if (message.type == app.neara.core.model.MessageType.ACTION || message.payload.startsWith("ACTION:")) return
        messagesMap[message.messageId] = message
        persistToDisk()
    }

    override fun getMessage(messageId: String): ChatMessage? {
        val msg = messagesMap[messageId]
        if (msg != null && (msg.type == app.neara.core.model.MessageType.ACTION || msg.payload.startsWith("ACTION:"))) {
            return null
        }
        return msg
    }

    override fun getMessagesForConversation(conversationId: String, limit: Int): List<ChatMessage> {
        return messagesMap.values
            .filter { it.conversationId == conversationId && it.type != app.neara.core.model.MessageType.ACTION && !it.payload.startsWith("ACTION:") }
            .sortedWith(compareBy({ it.timestamp }, { it.sequenceNumber }))
            .take(limit)
    }

    override fun getAllMessages(): List<ChatMessage> {
        return messagesMap.values
            .filter { it.type != app.neara.core.model.MessageType.ACTION && !it.payload.startsWith("ACTION:") }
            .toList()
    }

    override fun updateMessageStatus(messageId: String, status: MessageStatus) {
        val msg = messagesMap[messageId] ?: return
        messagesMap[messageId] = msg.copy(status = status)
        persistToDisk()
    }

    override fun markConversationAsRead(conversationId: String, localPeerId: String) {
        var changed = false
        messagesMap.values
            .filter { it.conversationId == conversationId && it.senderId != localPeerId && it.status != MessageStatus.READ }
            .forEach { msg ->
                messagesMap[msg.messageId] = msg.copy(status = MessageStatus.READ)
                changed = true
            }
        if (changed) persistToDisk()
    }

    override fun deleteConversation(conversationId: String) {
        val toRemove = messagesMap.values.filter { it.conversationId == conversationId }.map { it.messageId }
        if (toRemove.isNotEmpty()) {
            toRemove.forEach { messagesMap.remove(it) }
            persistToDisk()
        }
    }

    override fun deleteMessage(messageId: String) {
        if (messagesMap.remove(messageId) != null) {
            persistToDisk()
        }
    }

    override fun deleteActionMessages() {
        val keysToRemove = messagesMap.filter { it.value.type == app.neara.core.model.MessageType.ACTION || it.value.payload.startsWith("ACTION:") }.keys
        if (keysToRemove.isNotEmpty()) {
            keysToRemove.forEach { messagesMap.remove(it) }
            persistToDisk()
        }
    }

    override fun clearAll() {
        messagesMap.clear()
        persistToDisk()
    }
}
