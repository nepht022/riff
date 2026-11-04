package com.rodrigomachado.riff.feature.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ReportFailedException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Manda o relato para o desenvolvedor. */
interface ReportSender {
    /** Sem endereço no build (local.properties), não há para onde mandar. */
    val isConfigured: Boolean

    /** @throws ReportFailedException se o relato não chegou (sem internet, erro do script). */
    suspend fun send(report: BugReport)
}

/** POST de JSON: devolve o código HTTP e o corpo da resposta. */
fun interface JsonPost {
    suspend fun post(url: String, json: String): Pair<Int, String>
}

/**
 * Envia para um Google Apps Script publicado como app da Web, que repassa por email ao desenvolvedor. O endereço
 * fica no build; nenhuma senha vai no app. O script responde `{"ok":true}`: qualquer outra resposta (por exemplo,
 * a página de login do Google, se o script não estiver aberto a qualquer pessoa) é falha.
 */
class AppsScriptReportSender(private val url: String, private val transport: JsonPost) : ReportSender {
    override val isConfigured: Boolean get() = url.startsWith("https://")

    override suspend fun send(report: BugReport) {
        if (!isConfigured) throw ReportFailedException("envio de relatos não configurado")
        val (code, body) = post(report)
        check(code, body)
    }

    /** O motivo da recusa vai para o log (no celular, o logcat) (resposta do script, nunca o texto do relato). */
    private fun check(code: Int, body: String) {
        val answered = code in HTTP_OK && OK_ANSWER.containsMatchIn(body)
        if (!answered) {
            val reason = "resposta inesperada: HTTP $code: ${body.take(ANSWER_SAMPLE).replace('\n', ' ')}"
            reportLog("relato não enviado: $reason")
            throw ReportFailedException(reason)
        }
    }

    private suspend fun post(report: BugReport): Pair<Int, String> = try {
        transport.post(url, report.toJson())
    } catch (e: IOException) {
        reportLog("relato não enviado: sem conexão (${e.javaClass.simpleName})")
        throw ReportFailedException("sem conexão", e)
    }

    private companion object {
        val HTTP_OK = 200..299
        const val ANSWER_SAMPLE = 160
        val OK_ANSWER = Regex(""""ok"\s*:\s*true""")
    }
}

/** HTTP do Java. O Apps Script responde com um redirecionamento, que o Android segue sozinho. */
object UrlConnectionJsonPost : JsonPost {
    // O Apps Script parado demora a acordar: prazo curto daria "falhou" com o email já a caminho.
    private const val TIMEOUT_MS = 30_000

    // URL(String) é obsoleto só no Java do PC; o mesmo do celular nos dois (o endereço vem do build).
    @Suppress("DEPRECATION")
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

    private const val HTTP_ERROR = 400
}
