package com.rodrigomachado.riff.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
            RiffDesktopApp(graph)
        }
    }
}
