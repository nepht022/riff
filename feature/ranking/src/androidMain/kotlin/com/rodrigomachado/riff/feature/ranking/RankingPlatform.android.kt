package com.rodrigomachado.riff.feature.ranking

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Composable
internal actual fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
