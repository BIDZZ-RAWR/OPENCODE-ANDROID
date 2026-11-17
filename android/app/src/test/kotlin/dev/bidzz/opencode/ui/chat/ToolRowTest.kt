package dev.bidzz.opencode.ui.chat

import dev.bidzz.opencode.core.api.dto.ToolCall
import dev.bidzz.opencode.core.api.dto.inputCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolRowTest {

    private fun call(name: String, inputJson: String? = null) = ToolCall(
        id = "t1",
        name = name,
        status = "completed",
        input = inputJson?.let { Json.parseToJsonElement(it) },
    )

    private fun streamingCall(name: String, partial: String) = ToolCall(
        id = "t2",
        name = name,
        status = "streaming",
        input = JsonPrimitive(partial),
    )

    @Test
    fun `edit and write tools surface their file basenames`() {
        val tools = listOf(
            call("read", """{"filePath":"src/app/Main.kt"}"""),
            call("edit", """{"filePath":"src/app/util/Math.kt"}"""),
            call("write", """{"filePath":"README.md"}"""),
            call("bash", """{"command":"ls -la"}"""),
        )
        assertEquals(listOf("Math.kt", "README.md"), touchedFiles(tools))
    }

    @Test
    fun `duplicates collapse and non-file tools stay out`() {
        val tools = listOf(
            call("edit", """{"filePath":"a.kt"}"""),
            call("edit", """{"filePath":"a.kt"}"""),
            call("grep", """{"pattern":"x"}"""),
        )
        assertEquals(listOf("a.kt"), touchedFiles(tools))
    }

    @Test
    fun `bash input extracts the command`() {
        assertEquals("ls -la", call("bash", """{"command":"ls -la"}""").inputCommand())
    }

    @Test
    fun `a streaming partial input has no command`() {
        assertNull(streamingCall("bash", """{"command": "ls""").inputCommand())
    }

    @Test
    fun `non-command tools have no command`() {
        assertNull(call("read", """{"filePath":"a.kt"}""").inputCommand())
    }
}
