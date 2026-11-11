package com.rodrigomachado.riff.core.room

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class JsonTreeTest : JsonTest() {
    private fun room() = JSONObject("""{"dono":"u1","jogadores":{"u1":{"nome":"Ana"},"u2":{"nome":"Bia"}}}""")

    @Test
    fun putAtRootReplaces() {
        val tree = JsonTree.put(room(), "/", JSONObject("""{"dono":"u9"}"""))

        assertEquals("""{"dono":"u9"}""", tree.toString())
    }

    @Test
    fun putAtChildPathSetsDeep() {
        val before = room()

        val tree = JsonTree.put(before, "/jogadores/u3", JSONObject("""{"nome":"Caio"}""")) as JSONObject

        assertEquals("Caio", tree.getJSONObject("jogadores").getJSONObject("u3").getString("nome"))
        assertEquals("Ana", tree.getJSONObject("jogadores").getJSONObject("u1").getString("nome"))
        // a árvore emitida antes não muda
        assertFalse(before.getJSONObject("jogadores").has("u3"))
        // um caminho que ainda não existe é criado
        val fromNothing = JsonTree.put(null, "/a/b", 1) as JSONObject
        assertEquals(1, fromNothing.getJSONObject("a").getInt("b"))
    }

    @Test
    fun putNullDeletes() {
        val tree = JsonTree.put(room(), "/jogadores/u2", null) as JSONObject
        assertFalse(tree.getJSONObject("jogadores").has("u2"))

        // sem filhos, o pai some (no Firebase não há objeto vazio); sem nada, a árvore é null
        val noPlayers = JsonTree.put(tree, "/jogadores/u1", null) as JSONObject
        assertFalse(noPlayers.has("jogadores"))
        assertNull(JsonTree.put(noPlayers, "/dono", null))
        assertNull(JsonTree.put(room(), "/", null))
    }

    @Test
    fun patchMergesChildren() {
        val patch = JSONObject("""{"dono":"u2","jogadores/u3":{"nome":"Caio"},"jogadores/u1":null}""")

        val tree = JsonTree.patch(room(), "/", patch) as JSONObject

        assertEquals("u2", tree.getString("dono"))
        val players = tree.getJSONObject("jogadores")
        assertEquals(listOf("u2", "u3"), players.keys().asSequence().sorted().toList())
        val deep = JsonTree.patch(tree, "/jogadores/u2", JSONObject("""{"vistoEm":5}""")) as JSONObject
        assertEquals("Bia", deep.getJSONObject("jogadores").getJSONObject("u2").getString("nome"))
        assertEquals(5, deep.getJSONObject("jogadores").getJSONObject("u2").getInt("vistoEm"))
    }

    @Test
    fun putUnderAListKeepsItsItems() {
        // o Firebase manda {"1":…,"2":…} como lista; um evento depois num filho dela não pode apagar os outros
        val tree = JSONObject("""{"placar":[null,{"ana":{"pontos":1}},{"bia":{"pontos":2}}]}""")

        val after = JsonTree.put(tree, "/placar/2/caio", JSONObject("""{"pontos":3}""")) as JSONObject
        val patched = JsonTree.patch(after, "/placar", JSONObject("""{"3":{"ana":{"pontos":4}}}""")) as JSONObject

        val board = patched.getJSONObject("placar")
        assertEquals(setOf("1", "2", "3"), board.keys().asSequence().toSet())
        assertEquals(1, board.getJSONObject("1").getJSONObject("ana").getInt("pontos"))
        assertEquals(setOf("bia", "caio"), board.getJSONObject("2").keys().asSequence().toSet())
    }
}
