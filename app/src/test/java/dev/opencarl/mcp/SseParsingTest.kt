package dev.opencarl.mcp

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParsingTest {

    @Test
    fun `parses a message event with json data`() {
        val source = Buffer().writeUtf8("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1}\n\n")

        val event = source.readSseEvent()

        assertEquals("message", event?.first)
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1}", event?.second)
    }

    @Test
    fun `joins multi line data and skips comment lines`() {
        val source = Buffer().writeUtf8(": keepalive\ndata: line one\ndata: line two\n\n")

        val event = source.readSseEvent()

        assertNull(event?.first)
        assertEquals("line one\nline two", event?.second)
    }

    @Test
    fun `returns null at end of stream`() {
        val source = Buffer().writeUtf8("")

        assertNull(source.readSseEvent())
    }

    @Test
    fun `resolves relative endpoint against the stream origin`() {
        assertEquals(
            "https://example.com/mcp",
            resolveUrl("https://example.com/sse?token=1", "/mcp"),
        )
        assertEquals(
            "https://example.com/messages",
            resolveUrl("https://example.com/sse", "messages"),
        )
    }
}
