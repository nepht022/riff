package com.rodrigomachado.riff.feature.room

import androidx.compose.runtime.Composable

/** O Voltar do sistema (celular); no PC, nada: o Esc vem pelo `menuKeys` da tela. */
@Composable
internal expect fun RoomBackHandler(onBack: () -> Unit)

/** O log de erros da sala (só o que não tem outro tratamento); nunca lança. */
internal expect fun roomLog(message: String, error: Throwable)
