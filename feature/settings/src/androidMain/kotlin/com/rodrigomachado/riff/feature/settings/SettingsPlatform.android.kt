package com.rodrigomachado.riff.feature.settings

import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.rodrigomachado.riff.core.model.AppLanguage

@Composable
actual fun currentAppLanguage(): AppLanguage =
    AppLanguage.fromTag(LocalConfiguration.current.locales[0].toLanguageTag())

@Composable
actual fun formatFileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

@Composable
actual fun rememberLanguageRefresh(): () -> Unit {
    val activity = LocalActivity.current
    // Antes do Android 13 o sistema não refaz a tela sozinho: refaz aqui, já no idioma novo.
    return { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) activity?.recreate() }
}

actual fun keyName(keyCode: Long): String = keyCode.toString()
