package com.rodrigomachado.riff.core.data

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.SQLiteException

internal actual fun SQLiteException.isConstraintViolation(): Boolean = this is SQLiteConstraintException
