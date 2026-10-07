package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pontuação com algarismos de largura fixa, para o número não tremer ao mudar. */
val ScoreTextStyle = TextStyle(
    fontSize = 32.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)
