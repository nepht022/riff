package com.rodrigomachado.riff.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rodrigomachado.riff.desktop.pad.pressMenuKey
import java.awt.Dimension

private const val MIN_WIDTH = 800
private const val MIN_HEIGHT = 500

fun main() {
    val graph = DesktopGraph(DesktopPaths.dataDir)
    graph.languages.apply()
    application {
        Window(
            onCloseRequest = {
                graph.close()
                exitApplication()
            },
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
}
