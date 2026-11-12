package com.rodrigomachado.riff.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

/** A área de transferência de mentira: guarda o que o app copiou (a do sistema não é tocada pelos testes). */
internal class FakeClipboard : Clipboard {
    private var entry: ClipEntry? = null

    /** O texto copiado por último (null: nada). */
    @OptIn(ExperimentalComposeUiApi::class)
    val text: String?
        get() = (entry?.nativeClipEntry as? Transferable)?.getTransferData(DataFlavor.stringFlavor) as? String

    override suspend fun getClipEntry(): ClipEntry? = entry

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        entry = clipEntry
    }

    override val nativeClipboard: java.awt.datatransfer.Clipboard
        get() = error("a área de transferência de mentira não tem a do sistema")
}
