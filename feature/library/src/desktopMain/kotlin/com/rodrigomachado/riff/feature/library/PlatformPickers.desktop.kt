package com.rodrigomachado.riff.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import com.rodrigomachado.riff.core.data.folder.pcUri
import javax.swing.JFileChooser

@Composable
actual fun rememberFolderPicker(onPicked: (String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return {
        // Diálogo modal na thread da janela (a do AWT, a mesma do Compose para desktop).
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Pasta dos mapas"
        }
        val chosen = chooser.takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
        callback.value(chosen?.let(::pcUri))
    }
}

@Composable
actual fun rememberNotificationPermissionThen(action: () -> Unit): () -> Unit = action
