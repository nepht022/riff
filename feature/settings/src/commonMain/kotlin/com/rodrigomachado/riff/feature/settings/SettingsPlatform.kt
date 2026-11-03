package com.rodrigomachado.riff.feature.settings

import androidx.compose.runtime.Composable
import com.rodrigomachado.riff.core.model.AppLanguage

/**
 * O idioma em que a tela está: no celular, o da configuração (acompanha também uma troca feita nas configurações do
 * celular); no PC, o do processo.
 */
@Composable
expect fun currentAppLanguage(): AppLanguage

/** O tamanho de um arquivo, curto ("21 MB"): no celular, como o sistema escreve; no PC, com uma casa decimal. */
@Composable
expect fun formatFileSize(bytes: Long): String

/**
 * O que fazer depois de trocar o idioma para a tela aparecer já no idioma novo: no celular, antes do Android 13,
 * refazer a atividade (depois dele o sistema refaz sozinho); no PC, nada (o app refaz as telas).
 */
@Composable
expect fun rememberLanguageRefresh(): () -> Unit

/**
 * O nome de uma tecla gravada nos controles (o `Key.keyCode` do Compose): no PC, o nome que o Java dá ao código
 * AWT dos 32 bits de baixo ("A", "Up", "Enter"); no celular (sem a aba), o próprio número.
 */
expect fun keyName(keyCode: Long): String
