package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.RankingTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** POST de JSON: devolve o código HTTP e o corpo. */
fun interface RankingTransport {
    suspend fun post(url: String, json: String): Pair<Int, String>
}

sealed interface ApiAnswer<out T> {
    data class Ok<T>(val value: T) : ApiAnswer<T>

    /** O script respondeu e recusou (nome em uso, limite...). */
    data class Refused(val reason: String) : ApiAnswer<Nothing>

    /** Sem internet, ou uma resposta que não é a do script (página de login do Google, cota estourada). */
    data object Offline : ApiAnswer<Nothing>
}

/** O protocolo do scripts/ranking_apps_script.gs. Sem endereço no build, tudo é [ApiAnswer.Offline]. */
@Suppress("TooManyFunctions") // uma função por ação do script, mais a leitura do JSON de cada uma
class RankingApi(private val url: String, private val transport: RankingTransport) {
    val isConfigured: Boolean get() = url.startsWith("https://")

    suspend fun reserve(name: String, key: String): ApiAnswer<String> =
        call(JSONObject().put("acao", "reservar").put("nome", name).put("chave", key)) { it.getString("nome") }

    suspend fun submit(key: String, submission: Submission): ApiAnswer<SubmitStatus.Placed?> =
        call(submission.toJson().put("acao", "enviar").put("chave", key)) { it.placed() }

    /**
     * Várias pontuações num pedido (o limite por hora conta uma vez): um resultado por envio, na mesma ordem. O
     * script de antes desta ação responde [ApiAnswer.Refused] "pedido".
     */
    suspend fun submitMany(
        key: String,
        submissions: List<Submission>,
    ): ApiAnswer<List<ApiAnswer<SubmitStatus.Placed?>>> = call(
        JSONObject().put("acao", "enviarVarios").put("chave", key)
            .put("envios", JSONArray(submissions.map { it.toJson() })),
    ) { json ->
        val results = json.getJSONArray("resultados")
        (0 until results.length()).map { results.getJSONObject(it) }.map {
            if (it.optBoolean("ok")) ApiAnswer.Ok(it.placed()) else ApiAnswer.Refused(it.optString("motivo"))
        }
    }

    /** As músicas com alguém em qualquer tabela, com quem está em cada uma ([RankedSong.inTable]). */
    suspend fun songs(key: String, query: String): ApiAnswer<List<RankedSong>> = call(
        JSONObject().put("acao", "musicas").put("chave", key).put("busca", query).put("tabelas", true),
    ) { json ->
        val list = json.getJSONArray("musicas")
        (0 until list.length()).map { list.getJSONObject(it) }.map {
            RankedSong(
                it.getString("mapa"),
                it.optString("titulo"),
                it.optString("artista"),
                it.getInt("jogadores"),
                it.position(),
                it.optJSONObject("tabelas")?.standings().orEmpty(),
            )
        }
    }

    /**
     * Todas as linhas do mapa, de todas as dificuldades e tabelas: o placar é montado no celular ([rankBoard]). Sem
     * a tabela (o script de antes da 4d), a linha é do celular; uma tabela desconhecida fica de fora.
     */
    suspend fun board(key: String, chartId: String): ApiAnswer<List<BoardEntry>> = call(
        JSONObject().put("acao", "placar").put("chave", key).put("mapa", chartId).put("tabelas", true),
    ) { json ->
        val lines = json.getJSONArray("linhas")
        (0 until lines.length()).map { lines.getJSONObject(it) }.mapNotNull { line ->
            val difficulty = difficultyOf(line.getString("dificuldade")) ?: return@mapNotNull null
            val table = RankingTable.fromWire(line.optString("tabela")) ?: return@mapNotNull null
            BoardEntry(
                line.getString("nome"),
                line.getBoolean("voce"),
                difficulty,
                line.number("pontos"),
                line.number("precisao"),
                line.number("combo"),
                line.number("perfeitos"),
                line.number("semErrar"),
                line.number("notas")?.toInt(),
                table,
            )
        }
    }

    /**
     * Um código para usar o nome deste aparelho em outro. O script de antes da 4d responde [ApiAnswer.Refused]
     * "pedido".
     */
    suspend fun linkCode(key: String): ApiAnswer<LinkCode> =
        call(JSONObject().put("acao", "codigo").put("chave", key)) {
            LinkCode(it.getString("codigo"), it.getInt("validoPor"))
        }

    /** Este aparelho passa a ser do jogador do [code]: devolve o nome. */
    suspend fun link(key: String, code: String): ApiAnswer<String> =
        call(JSONObject().put("acao", "vincular").put("chave", key).put("codigo", code)) { it.getString("nome") }

    private fun JSONObject.placed() =
        if (isNull("posicao")) null else SubmitStatus.Placed(getInt("posicao"), getInt("total"))

    private fun Submission.toJson() = JSONObject()
        .put("mapa", chartId).put("titulo", title).put("artista", artist)
        .put("dificuldade", difficulty.name).put("pontos", points)
        .put("precisao", accuracy.toDouble()).put("combo", combo)
        .put("perfeitos", perfect).put("semErrar", cleanMs ?: JSONObject.NULL)
        .put("notas", notes ?: JSONObject.NULL)
        .put("tabela", table.wire)

    private fun JSONObject.position(): Int? = if (isNull("suaPosicao")) null else getInt("suaPosicao")

    private fun JSONObject.standings(): Map<RankingTable, TableStanding> = keys().asSequence().mapNotNull { wire ->
        val table = RankingTable.fromWire(wire) ?: return@mapNotNull null
        val standing = getJSONObject(wire)
        table to TableStanding(standing.getInt("jogadores"), standing.position())
    }.toMap()

    private fun JSONObject.number(name: String): Double? = if (isNull(name)) null else getDouble(name)

    @Suppress("SwallowedException") // sem rede ou resposta estranha: o envio fica na fila e tenta de novo
    private suspend fun <T> call(request: JSONObject, read: (JSONObject) -> T): ApiAnswer<T> {
        if (!isConfigured) return ApiAnswer.Offline
        return try {
            val (code, body) = transport.post(url, request.toString())
            val json = JSONObject(body)
            when {
                code !in HTTP_OK -> ApiAnswer.Offline
                json.optBoolean("ok") -> ApiAnswer.Ok(read(json))
                else -> ApiAnswer.Refused(json.optString("motivo"))
            }
        } catch (e: IOException) {
            ApiAnswer.Offline
        } catch (e: JSONException) {
            ApiAnswer.Offline // página do Google no lugar da resposta do script
        }
    }

    private fun difficultyOf(name: String) = Difficulty.entries.firstOrNull { it.name == name }

    private companion object {
        val HTTP_OK = 200..299
    }
}

/** HTTP do Java, fora da thread da tela (celular e PC): o redirecionamento do Apps Script é seguido sozinho. */
object UrlConnectionRankingTransport : RankingTransport {
    // O Apps Script parado demora a acordar.
    private const val TIMEOUT_MS = 30_000
    private const val HTTP_ERROR = 400

    override suspend fun post(url: String, json: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(json.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code >= HTTP_ERROR) connection.errorStream else connection.inputStream
            code to (stream?.use { it.readBytes().decodeToString() } ?: "")
        } finally {
            connection.disconnect()
        }
    }
}
