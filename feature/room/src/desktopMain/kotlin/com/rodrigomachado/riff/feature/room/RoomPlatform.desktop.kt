package com.rodrigomachado.riff.feature.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import java.awt.HeadlessException
import java.awt.datatransfer.StringSelection

@Composable
internal actual fun RoomBackHandler(onBack: () -> Unit) = Unit

internal actual fun roomLog(message: String, error: Throwable) {
    System.err.println("RiffRoom: $message: $error")
}

/** Copia só o código para a área de transferência (colar no chat do jogo, no WhatsApp Web…). */
@OptIn(ExperimentalComposeUiApi::class) // o ClipEntry do PC com um Transferable do AWT
@Composable
internal actual fun rememberCodeShare(): CodeShare {
    val clipboard = LocalClipboard.current
    return remember(clipboard) {
        CodeShare { code, _ ->
            try {
                clipboard.setClipEntry(ClipEntry(StringSelection(code)))
                true
            } catch (e: IllegalStateException) {
                // A área de transferência ocupada por outro programa.
                roomLog("não deu para copiar o código", e)
                false
            } catch (e: HeadlessException) {
                roomLog("sem área de transferência", e)
                false
            }
        }
    }
}

internal actual val codeShareCopies: Boolean = true
