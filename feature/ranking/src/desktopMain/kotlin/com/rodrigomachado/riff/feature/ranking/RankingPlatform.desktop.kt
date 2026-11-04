package com.rodrigomachado.riff.feature.ranking

import androidx.compose.runtime.Composable
import java.util.Locale

@Composable
internal actual fun currentLocale(): Locale =
    Locale.forLanguageTag(androidx.compose.ui.text.intl.Locale.current.toLanguageTag())
