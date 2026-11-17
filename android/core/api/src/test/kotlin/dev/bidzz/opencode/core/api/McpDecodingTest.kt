package dev.bidzz.opencode.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The engine nests the status (`"status": {"status": ...}`) and wraps the list with a location. */
class McpDecodingTest {

    @Test
    fun `decodes the nested status, the optional title and the error beside a failure`() {
        val raw = """
            {"location":{"directory":"/p"},"data":[
              {"name":"files","status":{"status":"connected"},"title":"File server"},
              {"name":"search","status":{"status":"failed","error":"spawn ENOENT"}},
              {"name":"off","status":{"status":"disabled"},"integrationID":"x"}
            ]}
        """.trimIndent()

        val servers = ApiJson.decodeFromString<McpListResponse>(raw).data

        assertEquals(listOf("files", "search", "off"), servers.map { it.name })
        assertEquals("connected", servers[0].status.status)
        assertEquals("File server", servers[0].title)
        assertEquals("spawn ENOENT", servers[1].status.error)
        assertNull(servers[2].title)
    }
}
