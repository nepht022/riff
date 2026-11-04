package com.rodrigomachado.riff.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rodrigomachado.riff.core.model.AppLanguage
import com.rodrigomachado.riff.desktop.pad.pressMenuKey
import java.awt.Dimension
import java.io.File
import javax.swing.JOptionPane
import kotlin.system.exitProcess

private const val MIN_WIDTH = 800
private const val MIN_HEIGHT = 500

fun main() {
    val graph = try {
        DesktopGraph(DesktopPaths.dataDir)
    } catch (_: RiffAlreadyOpenException) {
        // Outro Riff com os mesmos dados (ou um ainda fechando, ~2 s): avisa e sai sem abrir nada.
        alreadyOpen(DesktopPaths.dataDir)
        exitProcess(0)
    }
    graph.languages.apply()
    // A janela some na hora; depois, o graph fecha: a separação da vez é cancelada (volta à fila, sem pasta pela
    // metade) em até ~1,5 s, mesmo com o modelo no meio de um trecho, e o banco fecha.
    application(exitProcessOnExit = false) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Riff",
            state = rememberWindowState(width = 1280.dp, height = 800.dp),
        ) {
            window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
            // O controle começa a ser lido com a janela aberta (o AWT escolhe a escala da tela antes do GLFW).
            LaunchedEffect(Unit) { graph.gamepad.start() }
            // O controle nos menus: as teclas dele chegam à janela como as do teclado.
            val sendMenuKey = remember(window) { { code: Int -> window.pressMenuKey(code) } }
            RiffDesktopApp(graph, sendMenuKey)
        }
    }
    graph.close()
    exitProcess(0)
}

/** O aviso de que o Riff já está aberto, no idioma escolhido no jogo. */
private fun alreadyOpen(dataDir: File) {
    val english = DesktopAppLanguageStore(File(dataDir, LANGUAGE_FILE)).current == AppLanguage.ENGLISH
    val message = if (english) "Riff is already open." else "O Riff já está aberto."
    JOptionPane.showMessageDialog(null, message, "Riff", JOptionPane.INFORMATION_MESSAGE)
}
