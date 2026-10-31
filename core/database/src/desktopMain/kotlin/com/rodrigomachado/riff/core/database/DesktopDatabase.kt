package com.rodrigomachado.riff.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/** O banco do Riff no PC, no arquivo [file] (a pasta de dados do app), com o SQLite embutido. */
fun riffDatabase(file: File): RiffDatabase = Room.databaseBuilder<RiffDatabase>(name = file.absolutePath)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
