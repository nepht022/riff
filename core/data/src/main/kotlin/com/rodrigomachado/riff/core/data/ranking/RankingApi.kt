package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
class RankingApi(private val url: String, private val transport: RankingTransport) {
    val isConfigured: Boolean get() = url.startsWith("https://")

    suspend fun reserve(name: String, key: String): ApiAnswer<String> =
        call(JSONObject().put("acao", "reservar").put("nome", name).put("chave", key)) { it.getString("nome") }

    suspend fun submit(key: String, submission: Submission): ApiAnswer<SubmitStatus.Placed?> = call(
        JSONObject().put("acao", "enviar").put("chave", key)
            .put("mapa", submission.chartId).put("titulo", submission.title).put("artista", submission.artist)
            .put("dificuldade", submission.difficulty.name).put("pontos", submission.points)
            .put("precisao", submission.accuracy.toDouble()).put("combo", submission.combo)
            .put("perfeitos", submission.perfect).put("semErrar", submission.cleanMs ?: JSONObject.NULL)
            .put("notas", submission.notes ?: JSONObject.NULL),
    ) { json ->
        if (json.isNull("posicao")) null else SubmitStatus.Placed(json.getInt("posicao"), json.getInt("total"))
    }

    suspend fun songs(key: String, query: String): ApiAnswer<List<RankedSong>> =
        call(JSONObject().put("acao", "musicas").put("chave", key).put("busca", query)) { json ->
            val list = json.getJSONArray("musicas")
            (0 until list.length()).map { list.getJSONObject(it) }.map {
                RankedSong(
                    it.getString("mapa"),
                    it.optString("titulo"),
                    it.optString("artista"),
                    it.getInt("jogadores"),
                    if (it.isNull("suaPosicao")) null else it.getInt("suaPosicao"),
                )
            }
        }

    /** Todas as linhas do mapa, de todas as dificuldades: o placar é montado no celular ([rankBoard]). */
    suspend fun board(key: String, chartId: String): ApiAnswer<List<BoardEntry>> =
        call(JSONObject().put("acao", "placar").put("chave", key).put("mapa", chartId)) { json ->
            val lines = json.getJSONArray("linhas")
            (0 until lines.length()).map { lines.getJSONObject(it) }.mapNotNull { line ->
                val difficulty = difficultyOf(line.getString("dificuldade")) ?: return@mapNotNull null
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
                )
            }
        }

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

/** HTTP do próprio Android. O Apps Script responde com um redirecionamento, que o Android segue sozinho. */
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
