package com.rodrigomachado.riff.desktop

import java.io.File

/** Onde o Riff guarda os dados no PC: `%APPDATA%\Riff` no Windows; fora dele, `~/.riff`. */
object DesktopPaths {
    val dataDir: File by lazy {
        val appData = System.getenv("APPDATA")
        (if (appData != null) File(appData, "Riff") else File(System.getProperty("user.home"), ".riff"))
            .apply { mkdirs() }
    }
}
