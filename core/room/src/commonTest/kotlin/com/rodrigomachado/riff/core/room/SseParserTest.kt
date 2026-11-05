package com.rodrigomachado.riff.core.room

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest : JsonTest() {
    private fun SseParser.feedAll(lines: List<String>): List<SseEvent> = lines.mapNotNull { feed(it) }

    @Test
    fun putEventWithDataIsParsed() {
        val events = SseParser().feedAll(sse("put", """{"path":"/","data":{"dono":"u1"}}"""))

        val event = events.single()
        assertEquals("put", event.type)
        assertEquals("/", event.path)
        assertEquals("u1", (event.data as JSONObject).getString("dono"))
    }

    @Test
    fun keepAliveIsIgnored() {
        val parser = SseParser()

        assertEquals(emptyList<SseEvent>(), parser.feedAll(sse("keep-alive", "null") + listOf(": comentário", "")))
        // depois do keep-alive, o próximo evento sai inteiro
        assertEquals("patch", parser.feedAll(sse("patch", """{"path":"/a","data":{"b":1}}""")).single().type)
    }

    @Test
    fun multiLineDataIsJoined() {
        val lines = listOf("event: put", """data: {"path":"/x",""", """data: "data":7}""", "")

        val event = SseParser().feedAll(lines).single()

        assertEquals("/x", event.path)
        assertEquals(7, event.data)
    }

    @Test
    fun cancelAndAuthRevokedAreSurfaced() {
        val parser = SseParser()

        val cancel = parser.feedAll(sse("cancel", "\"Permission denied\"")).single()
        val revoked = parser.feedAll(sse("auth_revoked", "\"credential is no longer valid\"")).single()

        assertEquals("cancel", cancel.type)
        assertNull(cancel.path)
        assertEquals("auth_revoked", revoked.type)
        assertEquals("\"credential is no longer valid\"", revoked.data)
    }
}
