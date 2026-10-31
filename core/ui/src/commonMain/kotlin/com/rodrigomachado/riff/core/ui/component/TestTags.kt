package com.rodrigomachado.riff.core.ui.component

import androidx.compose.ui.Modifier

/**
 * Expõe as etiquetas de teste (`testTag`) desta parte da tela aos percursos de desempenho do Android (UiAutomator);
 * no PC não há o que expor.
 */
expect fun Modifier.exposeTestTags(): Modifier
