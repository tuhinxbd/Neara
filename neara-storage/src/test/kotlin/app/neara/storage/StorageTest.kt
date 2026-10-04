package app.neara.storage

import app.neara.core.model.ChatMessage
import app.neara.core.model.MessageStatus
import app.neara.core.model.MessageType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StorageTest {

    private lateinit var dbManager: DatabaseManager
    private lateinit var messageRepo: MessageRepository
    private lateinit var queueManager: OfflineQueueManager

    @BeforeEach
    fun setUp() {
        dbManager = DatabaseManager(":memory:")
        messageRepo = MessageRepository(dbManager)
        queueManager = OfflineQueueManager(dbManager, messageRepo)
    }

    @AfterEach
    fun tearDown() {
        dbManager.close()
    }

    @Test
    fun `test save and retrieve message with status update`() {
        val msg = ChatMessage(
            messageId = "msg-99",
            conversationId = "conv-1",
            senderId = "peer-alice",
            recipientId = "peer-bob",
            timestamp = 1000L,
            sequenceNumber = 1L,
            type = MessageType.TEXT,
            payload = "Offline message payload",
            signature = "sig-123",
            status = MessageStatus.PENDING
        )

        messageRepo.saveMessage(msg)

        val retrieved = messageRepo.getMessage("msg-99")
        assertNotNull(retrieved)
        assertEquals("msg-99", retrieved?.messageId)
        assertEquals(MessageStatus.PENDING, retrieved?.status)
        assertEquals("Offline message payload", retrieved?.payload)

        // Update status to DELIVERED
        messageRepo.updateMessageStatus("msg-99", MessageStatus.DELIVERED)
        val updated = messageRepo.getMessage("msg-99")
        assertEquals(MessageStatus.DELIVERED, updated?.status)
    }

    @Test
    fun `test offline queue persists messages and drains on reconnection`() {
        val msg1 = ChatMessage(
            messageId = "msg-off-1",
            conversationId = "conv-bob",
            senderId = "peer-alice",
            recipientId = "peer-bob",
            timestamp = 1000L,
            payload = "First offline message"
        )
        val msg2 = ChatMessage(
            messageId = "msg-off-2",
            conversationId = "conv-bob",
            senderId = "peer-alice",
            recipientId = "peer-bob",
            timestamp = 2000L,
            payload = "Second offline message"
        )

        queueManager.enqueue(msg1, "peer-bob")
        queueManager.enqueue(msg2, "peer-bob")

        assertEquals(2, queueManager.getPendingCount())

        val pendingBob = queueManager.getPendingForPeer("peer-bob")
        assertEquals(2, pendingBob.size)
        assertEquals("msg-off-1", pendingBob[0].messageId)
        assertEquals("msg-off-2", pendingBob[1].messageId)

        // Simulate delivery of first message
        queueManager.remove("msg-off-1")
        assertEquals(1, queueManager.getPendingCount())

        // Simulate delivery of second message
        queueManager.remove("msg-off-2")
        assertEquals(0, queueManager.getPendingCount())
    }
}
