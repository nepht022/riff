package com.rodrigomachado.riff.feature.report

/**
 * JSON de objetos com textos, escrito à mão: no celular saía do `org.json` do Android, que guarda a ordem das chaves
 * e escapa `/`; o `org.json` original (o do PC) não guarda a ordem. Aqui as regras são as do Android, nos dois.
 */
internal class JsonWriter {
    private val out = StringBuilder()
    private var first = true

    fun obj(body: JsonWriter.() -> Unit): String {
        out.append('{')
        first = true
        body()
        out.append('}')
        return out.toString()
    }

    fun field(key: String, value: String) {
        key(key)
        string(value)
    }

    fun objField(key: String, body: JsonWriter.() -> Unit) {
        key(key)
        out.append('{')
        first = true
        body()
        out.append('}')
        first = false
    }

    private fun key(key: String) {
        if (!first) out.append(',')
        first = false
        string(key)
        out.append(':')
    }

    private fun string(value: String) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"', '\\', '/' -> out.append('\\').append(c)
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\u000c' -> out.append("\\f")
                else -> if (c.code <= LAST_CONTROL) unicode(c) else out.append(c)
            }
        }
        out.append('"')
    }

    /** Caractere de controle como `\u0001` (hexadecimal minúsculo, como o Android). */
    private fun unicode(c: Char) {
        out.append("\\u").append(c.code.toString(HEX).padStart(UNICODE_DIGITS, '0'))
    }

    private companion object {
        const val LAST_CONTROL = 0x1F
        const val HEX = 16
        const val UNICODE_DIGITS = 4
    }
}
