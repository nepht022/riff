package com.rodrigomachado.riff.core.chart.sng

/**
 * URIs de um `.sng` tratado como pasta: `<arquivo>#sng` (a pasta) e `<arquivo>#sng:<nome>` (um arquivo de
 * dentro). O URI do arquivo vem antes, então a regra de prefixo das subpastas continua valendo.
 */
object SngRef {
    private const val MARK = "#sng"

    fun packageUri(fileUri: String) = fileUri + MARK

    fun entryUri(fileUri: String, name: String) = "$fileUri$MARK:$name"

    fun packageFileOf(uri: String): String? = if (uri.endsWith(MARK)) uri.removeSuffix(MARK) else null

    fun entryOf(uri: String): Pair<String, String>? {
        val at = uri.lastIndexOf("$MARK:")
        return if (at < 0) null else uri.substring(0, at) to uri.substring(at + MARK.length + 1)
    }

    fun isPackageName(name: String) = name.endsWith(".sng", ignoreCase = true)
}
