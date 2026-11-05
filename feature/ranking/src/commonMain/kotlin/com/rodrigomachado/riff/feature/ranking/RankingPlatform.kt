package com.rodrigomachado.riff.feature.ranking

import androidx.compose.runtime.Composable
import java.util.Locale

/** O idioma dos números do placar (no celular, o da configuração da tela, como antes). */
@Composable
internal expect fun currentLocale(): Locale
