package com.rodrigomachado.riff.core.data

import androidx.sqlite.SQLiteException

/** O SQLite embutido do PC não tem uma exceção própria para regras: a mensagem traz "constraint failed". */
internal actual fun SQLiteException.isConstraintViolation(): Boolean =
    message?.contains("constraint failed", ignoreCase = true) == true
