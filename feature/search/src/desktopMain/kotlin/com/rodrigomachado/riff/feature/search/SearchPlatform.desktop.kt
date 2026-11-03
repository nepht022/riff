package com.rodrigomachado.riff.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import com.rodrigomachado.riff.core.data.folder.pcUri
import java.io.File
import javax.swing.JFileChooser

internal actual fun searchLog(message: String, error: Throwable) {
    System.err.println("RiffSearch: $message ($error)")
}

@Composable
internal actual fun rememberFolderPicker(startAt: String?, onPicked: (String?) -> Unit): () -> Unit {
    val start = rememberUpdatedState(startAt)
    val callback = rememberUpdatedState(onPicked)
    return {
        // Diálogo modal na thread da janela (a do AWT, a mesma do Compose para desktop), como o da Biblioteca.
        val chooser = JFileChooser(start.value?.let(::File)).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Pasta dos mapas"
        }
        val chosen = chooser.takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
        callback.value(chosen?.let(::pcUri))
    }
}
