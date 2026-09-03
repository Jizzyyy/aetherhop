package com.kadhafi.aetherhop.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MeshBackupSchemaTest {

    @Test
    fun testDatabaseBackupSchemaSerialization() {
        val schema = DatabaseBackupSchema(
            version = 1,
            timestamp = 1000L,
            messages = listOf(
                MessageBackupSchema("m1", "p1", "s1", "Alice", "Hello", 1000L, true, "SENT")
            ),
            peers = listOf(
                PeerBackupSchema("p1", "Bob", "192.168.49.2", 1000L)
            )
        )

        assertNotNull(schema)
        assertEquals(1, schema.version)
        assertEquals(1, schema.messages.size)
        assertEquals(1, schema.peers.size)
    }
}
