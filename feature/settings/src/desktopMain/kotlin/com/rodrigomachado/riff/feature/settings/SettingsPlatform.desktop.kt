package com.rodrigomachado.riff.feature.settings

import androidx.compose.runtime.Composable
import com.rodrigomachado.riff.core.model.AppLanguage
import java.awt.event.KeyEvent
import java.util.Locale

@Composable
actual fun currentAppLanguage(): AppLanguage = AppLanguage.fromTag(Locale.getDefault().toLanguageTag())

private const val UNIT = 1024.0
private val SIZE_UNITS = listOf("KB", "MB", "GB")

/** Uma casa decimal em KB, MB ou GB (abaixo de 1 KB, em bytes), no idioma do processo. */
@Composable
actual fun formatFileSize(bytes: Long): String {
    if (bytes < UNIT) return "$bytes B"
    var value = bytes / UNIT
    var unit = 0
    while (value >= UNIT && unit < SIZE_UNITS.lastIndex) {
        value /= UNIT
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, SIZE_UNITS[unit])
}

@Composable
actual fun rememberLanguageRefresh(): () -> Unit = {}

actual fun keyName(keyCode: Long): String = KeyEvent.getKeyText((keyCode and AWT_CODE_BITS).toInt())

private const val AWT_CODE_BITS = 0xFFFFFFFFL

internal actual val separateOnlyChargingShown: Boolean = false
