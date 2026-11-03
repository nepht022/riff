package com.rodrigomachado.riff.feature.search

import androidx.compose.runtime.Composable

/** Aviso da pesquisa (Encore fora do ar, resposta mudada, download que falhou): no celular, o logcat. */
internal expect fun searchLog(message: String, error: Throwable)

/**
 * Escolher a pasta da biblioteca de novo para ganhar a permissão de gravar: no celular, o seletor do sistema aberto
 * em [startAt]; no PC, a janela de escolher pasta. [onPicked] recebe o endereço da pasta, ou null se cancelada.
 */
@Composable
internal expect fun rememberFolderPicker(startAt: String?, onPicked: (String?) -> Unit): () -> Unit
