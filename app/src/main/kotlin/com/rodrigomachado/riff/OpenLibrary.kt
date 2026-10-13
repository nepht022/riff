package com.rodrigomachado.riff

import android.content.Intent

private const val OPEN_LIBRARY = "riff.abrir_biblioteca"

/** A notificação da separação abre a Biblioteca. */
fun opensLibrary(intent: Intent?): Boolean = intent?.getBooleanExtra(OPEN_LIBRARY, false) == true

/** Como [opensLibrary], mas usa o pedido uma vez só: recriar a tela (girar o celular) não abre de novo. */
fun consumeOpenLibrary(intent: Intent?): Boolean = opensLibrary(intent).also { intent?.removeExtra(OPEN_LIBRARY) }
