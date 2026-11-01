package com.rodrigomachado.riff.desktop

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick

/**
 * Clica em [node] na thread da janela, como no app. A navegação (lifecycle) só aceita mudanças na thread principal,
 * que no PC é a da janela (o `Dispatchers.Main` do Swing); o clique comum do teste sai da thread do próprio teste.
 */
fun ComposeContentTestRule.clickOnWindowThread(node: SemanticsNodeInteraction) = runOnUiThread { node.performClick() }
