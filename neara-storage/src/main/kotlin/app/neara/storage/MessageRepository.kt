package app.neara.storage

import app.neara.core.model.ChatMessage
import app.neara.core.model.MessageStatus
import app.neara.core.model.MessageType
import java.sql.Connection

class MessageRepository(private val dbManager: DatabaseManager) : IMessageRepository {

    private val conn: Connection
        get() = dbManager.connection

    override fun saveMessage(message: ChatMessage) {
        if (message.type == MessageType.ACTION || message.payload.startsWith("ACTION:")) return
        val sql = """
            INSERT OR REPLACE INTO messages (
                message_id, conversation_id, sender_id, recipient_id,
                timestamp, sequence_number, type, payload, signature, status, reply_to_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, message.messageId)
            stmt.setString(2, message.conversationId)
            stmt.setString(3, message.senderId)
            stmt.setString(4, message.recipientId)
            stmt.setLong(5, message.timestamp)
            stmt.setLong(6, message.sequenceNumber)
            stmt.setString(7, message.type.name)
            stmt.setString(8, message.payload)
            stmt.setString(9, message.signature)
            stmt.setString(10, message.status.name)
            stmt.setString(11, message.replyToMessageId)
            stmt.executeUpdate()
        }
    }

    override fun getMessage(messageId: String): ChatMessage? {
        val sql = "SELECT * FROM messages WHERE message_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, messageId)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return ChatMessage(
                    messageId = rs.getString("message_id"),
                    conversationId = rs.getString("conversation_id"),
                    senderId = rs.getString("sender_id"),
                    recipientId = rs.getString("recipient_id"),
                    timestamp = rs.getLong("timestamp"),
                    sequenceNumber = rs.getLong("sequence_number"),
                    type = MessageType.valueOf(rs.getString("type")),
                    payload = rs.getString("payload"),
                    signature = rs.getString("signature"),
                    status = MessageStatus.valueOf(rs.getString("status")),
                    replyToMessageId = rs.getString("reply_to_id")
                )
            }
        }
        return null
    }

    override fun getMessagesForConversation(conversationId: String, limit: Int): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        val sql = "SELECT * FROM messages WHERE conversation_id = ? AND type != 'ACTION' AND payload NOT LIKE 'ACTION:%' ORDER BY timestamp ASC, sequence_number ASC LIMIT ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, conversationId)
            stmt.setInt(2, limit)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                list.add(
                    ChatMessage(
                        messageId = rs.getString("message_id"),
                        conversationId = rs.getString("conversation_id"),
                        senderId = rs.getString("sender_id"),
                        recipientId = rs.getString("recipient_id"),
                        timestamp = rs.getLong("timestamp"),
                        sequenceNumber = rs.getLong("sequence_number"),
                        type = MessageType.valueOf(rs.getString("type")),
                        payload = rs.getString("payload"),
                        signature = rs.getString("signature"),
                        status = MessageStatus.valueOf(rs.getString("status")),
                        replyToMessageId = rs.getString("reply_to_id")
                    )
                )
            }
        }
        return list
    }

    override fun getAllMessages(): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        val sql = "SELECT * FROM messages WHERE type != 'ACTION' AND payload NOT LIKE 'ACTION:%' ORDER BY timestamp ASC, sequence_number ASC"
        conn.prepareStatement(sql).use { stmt ->
            val rs = stmt.executeQuery()
            while (rs.next()) {
                list.add(
                    ChatMessage(
                        messageId = rs.getString("message_id"),
                        conversationId = rs.getString("conversation_id"),
                        senderId = rs.getString("sender_id"),
                        recipientId = rs.getString("recipient_id"),
                        timestamp = rs.getLong("timestamp"),
                        sequenceNumber = rs.getLong("sequence_number"),
                        type = MessageType.valueOf(rs.getString("type")),
                        payload = rs.getString("payload"),
                        signature = rs.getString("signature"),
                        status = MessageStatus.valueOf(rs.getString("status")),
                        replyToMessageId = rs.getString("reply_to_id")
                    )
                )
            }
        }
        return list
    }

    override fun updateMessageStatus(messageId: String, status: MessageStatus) {
        val sql = "UPDATE messages SET status = ? WHERE message_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, status.name)
            stmt.setString(2, messageId)
            stmt.executeUpdate()
        }
    }

    override fun markConversationAsRead(conversationId: String, localPeerId: String) {
        val sql = "UPDATE messages SET status = ? WHERE conversation_id = ? AND sender_id != ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, MessageStatus.READ.name)
            stmt.setString(2, conversationId)
            stmt.setString(3, localPeerId)
            stmt.executeUpdate()
        }
    }

    override fun deleteConversation(conversationId: String) {
        val sql = "DELETE FROM messages WHERE conversation_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, conversationId)
            stmt.executeUpdate()
        }
    }

    override fun deleteMessage(messageId: String) {
        val sql = "DELETE FROM messages WHERE message_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, messageId)
            stmt.executeUpdate()
        }
    }

    override fun deleteActionMessages() {
        try {
            val sql = "DELETE FROM messages WHERE type = 'ACTION' OR payload LIKE 'ACTION:%'"
            conn.prepareStatement(sql).use { it.executeUpdate() }
        } catch (e: Exception) {}
    }

    override fun clearAll() {
        val sql = "DELETE FROM messages"
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(sql)
        }
    }
}

class OfflineQueueManager(
    private val dbManager: DatabaseManager,
    private val messageRepository: IMessageRepository
) : IOfflineQueueManager {
    private val conn: Connection
        get() = dbManager.connection

    override fun enqueue(message: ChatMessage, targetPeerId: String) {
        if (message.type == MessageType.ACTION || message.payload.startsWith("ACTION:")) return
        messageRepository.saveMessage(message.copy(status = MessageStatus.PENDING))

        val sql = """
            INSERT OR IGNORE INTO offline_queue (message_id, target_peer_id, retry_count, created_at)
            VALUES (?, ?, 0, ?)
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, message.messageId)
            stmt.setString(2, targetPeerId)
            stmt.setLong(3, System.currentTimeMillis())
            stmt.executeUpdate()
        }
    }

    override fun getPendingForPeer(targetPeerId: String): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        val sql = """
            SELECT m.* FROM messages m
            INNER JOIN offline_queue q ON m.message_id = q.message_id
            WHERE q.target_peer_id = ?
            ORDER BY m.timestamp ASC
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, targetPeerId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                list.add(
                    ChatMessage(
                        messageId = rs.getString("message_id"),
                        conversationId = rs.getString("conversation_id"),
                        senderId = rs.getString("sender_id"),
                        recipientId = rs.getString("recipient_id"),
                        timestamp = rs.getLong("timestamp"),
                        sequenceNumber = rs.getLong("sequence_number"),
                        type = MessageType.valueOf(rs.getString("type")),
                        payload = rs.getString("payload"),
                        signature = rs.getString("signature"),
                        status = MessageStatus.valueOf(rs.getString("status")),
                        replyToMessageId = rs.getString("reply_to_id")
                    )
                )
            }
        }
        return list
    }

    override fun remove(messageId: String) {
        val sql = "DELETE FROM offline_queue WHERE message_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, messageId)
            stmt.executeUpdate()
        }
    }

    override fun incrementRetry(messageId: String) {
        val sql = "UPDATE offline_queue SET retry_count = retry_count + 1 WHERE message_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, messageId)
            stmt.executeUpdate()
        }
    }

    override fun getPendingCount(): Int {
        val sql = "SELECT COUNT(*) FROM offline_queue"
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery(sql)
            if (rs.next()) {
                return rs.getInt(1)
            }
        }
        return 0
    }

    override fun clear() {
        val sql = "DELETE FROM offline_queue"
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(sql)
        }
    }
}
