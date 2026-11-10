package com.rodrigomachado.riff.core.room

import org.json.JSONObject
import org.json.JSONTokener

/**
 * Um evento do streaming do Firebase. Em `put`/`patch`, [path] é o caminho dentro do que se observa e [data] o
 * valor (JSONObject, JSONArray, primitivo ou null); nos outros (`cancel`, `auth_revoked`), [path] é null e [data]
 * é o texto cru.
 */
data class SseEvent(val type: String, val path: String?, val data: Any?)

/** Lê o SSE linha a linha (sem os `\n`): a linha vazia fecha o evento. O `keep-alive` não vira evento. */
class SseParser {
    private var type: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** O evento que esta linha fechou, ou null. Lança JSONException se o `put`/`patch` vier ilegível. */
    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null // comentário
        val field = line.substringBefore(':')
        val value = line.substringAfter(':', "").removePrefix(" ")
        when (field) {
            "event" -> type = value

            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    private fun dispatch(): SseEvent? {
        val eventType = type
        val text = data.toString()
        type = null
        data.clear()
        hasData = false
        return when (eventType) {
            null, KEEP_ALIVE -> null

            PUT, PATCH -> {
                val json = JSONObject(text)
                SseEvent(eventType, json.getString("path"), jsonValue(json.opt("data")))
            }

            else -> SseEvent(eventType, null, text)
        }
    }

    companion object {
        const val PUT = "put"
        const val PATCH = "patch"
        const val KEEP_ALIVE = "keep-alive"
        const val CANCEL = "cancel"
        const val AUTH_REVOKED = "auth_revoked"
    }
}

/** O JSONObject.NULL vira null (e o resto fica como está). */
internal fun jsonValue(value: Any?): Any? = if (value == null || value == JSONObject.NULL) null else value

/** Um valor JSON qualquer do texto (objeto, lista, texto, número, booleano ou null). */
internal fun parseJson(text: String): Any? = if (text.isBlank()) null else jsonValue(JSONTokener(text).nextValue())
