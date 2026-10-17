package com.rodrigomachado.riff.feature.report

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

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
    @Inject constructor() : this(BuildConfig.REPORT_URL, UrlConnectionJsonPost)

    override val isConfigured: Boolean get() = url.startsWith("https://")

    override suspend fun send(report: BugReport) {
        if (!isConfigured) throw ReportFailedException("envio de relatos não configurado")
        val (code, body) = post(report)
        check(code, body)
    }

    /** O motivo da recusa vai para o logcat (resposta do script, nunca o texto do relato). */
    private fun check(code: Int, body: String) {
        val answered = code in HTTP_OK && OK_ANSWER.containsMatchIn(body)
        if (!answered) {
            val reason = "resposta inesperada: HTTP $code: ${body.take(ANSWER_SAMPLE).replace('\n', ' ')}"
            Log.w(TAG, "relato não enviado: $reason")
            throw ReportFailedException(reason)
        }
    }

    private suspend fun post(report: BugReport): Pair<Int, String> = try {
        transport.post(url, report.toJson())
    } catch (e: IOException) {
        Log.w(TAG, "relato não enviado: sem conexão (${e.javaClass.simpleName})")
        throw ReportFailedException("sem conexão", e)
    }

    private companion object {
        val HTTP_OK = 200..299
        const val ANSWER_SAMPLE = 160
        const val TAG = "RiffReport"
        val OK_ANSWER = Regex(""""ok"\s*:\s*true""")
    }
}

/** HTTP do próprio Android. O Apps Script responde com um redirecionamento, que o Android segue sozinho. */
object UrlConnectionJsonPost : JsonPost {
    // O Apps Script parado demora a acordar: prazo curto daria "falhou" com o email já a caminho.
    private const val TIMEOUT_MS = 30_000

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
