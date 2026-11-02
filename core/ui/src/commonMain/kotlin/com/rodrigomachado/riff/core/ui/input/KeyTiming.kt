package com.rodrigomachado.riff.core.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent

/**
 * Quanto esta tecla esperou até chegar à tela (como o toque): quem usa o instante da tecla (o julgamento da partida,
 * os toques da calibração) desconta esse atraso do relógio de agora.
 */
expect fun KeyEvent.lateByMs(): Long

/**
 * A janela perdeu o foco: [onLost]. No PC, o Alt+Tab (as teclas seguradas não vão subir aqui).
 * No celular, nada: sair do app já passa pelo ciclo de vida, e a cortina de notificações tira o foco sem sair do app.
 */
@Composable
expect fun OnWindowFocusLost(onLost: () -> Unit)
