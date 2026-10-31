package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Tema único e escuro, sem Dynamic Color: a identidade não muda com o papel de parede. */
@Composable
fun RiffTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RiffColorScheme, typography = riffTypography(), shapes = RiffShapes, content = content)
}
