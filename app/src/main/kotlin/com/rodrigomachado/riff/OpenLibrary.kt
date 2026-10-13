package com.rodrigomachado.riff

import android.content.Intent

/** A notificação da separação abre a Biblioteca. */
fun opensLibrary(intent: Intent?): Boolean = intent?.getBooleanExtra("riff.abrir_biblioteca", false) == true
