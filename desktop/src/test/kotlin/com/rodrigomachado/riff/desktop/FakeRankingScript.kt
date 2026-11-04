package com.rodrigomachado.riff.desktop

import com.rodrigomachado.riff.core.data.ranking.RankingTransport
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Um endereço de teste para o ranking: nunca o script de verdade (o transporte dos testes nem sai do PC). */
internal const val TEST_RANKING_URL = "https://ranking.riff.test/exec"

/** O ranking sem o script: todo pedido falha como sem internet (os testes que não são do ranking). */
internal val OfflineRankingScript = RankingTransport { _, _ -> throw IOException("sem o script do ranking no teste") }

/**
 * O script do ranking falso (o protocolo do topo de scripts/ranking_apps_script.gs): anota cada pedido (o JSON) e
 * responde como o script. [songs] e [lines] são o que `musicas` e `placar` devolvem (com `tabelas: true`); o código
 * de vínculo certo é [LINK_CODE], do jogador [LINKED_NAME].
 */
internal class FakeRankingScript : RankingTransport {
    private val received = mutableListOf<JSONObject>()

    @Volatile var songs: JSONArray = JSONArray()

    @Volatile var lines: JSONArray = JSONArray()

    /** Os pedidos, na ordem em que chegaram (de qualquer thread). */
    val requests: List<JSONObject> get() = synchronized(received) { received.toList() }

    /** Cada pontuação enviada (de `enviar` e de `enviarVarios`). */
    val submissions: List<JSONObject>
        get() = requests.flatMap { request ->
            when (request.getString("acao")) {
                "enviar" -> listOf(request)
                "enviarVarios" -> request.getJSONArray("envios").objects()
                else -> emptyList()
            }
        }

    override suspend fun post(url: String, json: String): Pair<Int, String> {
        check(url == TEST_RANKING_URL) { "pedido para outro endereço: $url" }
        val request = JSONObject(json)
        synchronized(received) { received += request }
        val answer = when (request.getString("acao")) {
            "reservar" -> ok().put("nome", request.getString("nome"))
            "enviar" -> placed()
            "enviarVarios" -> placedEach(request.getJSONArray("envios"))
            "musicas" -> ok().put("musicas", songs)
            "placar" -> ok().put("linhas", lines)
            "codigo" -> ok().put("codigo", LINK_CODE).put("validoPor", CODE_SECONDS)
            "vincular" -> link(request.getString("codigo"))
            else -> refused("pedido")
        }
        return 200 to answer.toString()
    }

    private fun ok() = JSONObject().put("ok", true)

    private fun refused(reason: String) = JSONObject().put("ok", false).put("motivo", reason)

    /** Cada pontuação fica em 1º de 1 na tabela dela. */
    private fun placed() = ok().put("posicao", 1).put("total", 1)

    private fun placedEach(submissions: JSONArray): JSONObject =
        ok().put("resultados", JSONArray(submissions.objects().map { placed() }))

    private fun link(code: String): JSONObject =
        if (code == LINK_CODE) ok().put("nome", LINKED_NAME) else refused("codigo_invalido")

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    companion object {
        const val LINK_CODE = "ABC234"
        const val LINKED_NAME = "Ana do Celular"
        private const val CODE_SECONDS = 600

        /** Uma linha do placar, como o script devolve com `tabelas: true`. */
        fun line(name: String, points: Int, table: String, you: Boolean = false): JSONObject = JSONObject()
            .put("nome", name).put("voce", you).put("dificuldade", "EXPERT").put("pontos", points)
            .put("precisao", 0.9).put("combo", 10).put("perfeitos", 5).put("semErrar", JSONObject.NULL)
            .put("notas", 10).put("tabela", table)
    }
}
