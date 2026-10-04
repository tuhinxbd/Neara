package app.neara.storage

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class DatabaseManager(private val dbPath: String = ":memory:") {

    val connection: Connection by lazy {
        val url = if (dbPath == ":memory:") {
            "jdbc:sqlite::memory:"
        } else {
            val file = File(dbPath)
            file.parentFile?.mkdirs()
            "jdbc:sqlite:$dbPath"
        }
        val conn = DriverManager.getConnection(url)
        initSchema(conn)
        conn
    }

    private fun initSchema(conn: Connection) {
        conn.createStatement().use { stmt ->
            // Messages table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS messages (
                    message_id TEXT PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    sender_id TEXT NOT NULL,
                    recipient_id TEXT,
                    timestamp INTEGER NOT NULL,
                    sequence_number INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    signature TEXT NOT NULL,
                    status TEXT NOT NULL,
                    reply_to_id TEXT
                );
            """.trimIndent())

            // Offline queue table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS offline_queue (
                    queue_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    message_id TEXT NOT NULL UNIQUE,
                    target_peer_id TEXT NOT NULL,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(message_id) REFERENCES messages(message_id) ON DELETE CASCADE
                );
            """.trimIndent())

            // Peers table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS peers (
                    peer_id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    avatar_id TEXT NOT NULL,
                    public_key_hex TEXT NOT NULL,
                    last_seen INTEGER NOT NULL,
                    connection_state TEXT NOT NULL,
                    ip_address TEXT,
                    port INTEGER NOT NULL
                );
            """.trimIndent())

            // Networks table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS networks (
                    network_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    owner_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    public_key_hex TEXT NOT NULL,
                    policy_json TEXT NOT NULL
                );
            """.trimIndent())
        }
    }

    fun close() {
        try {
            if (!connection.isClosed) {
                connection.close()
            }
        } catch (e: Exception) {}
    }
}
