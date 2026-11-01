package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent

/**
 * Joga-se tocando na pista (celular, em pé ou deitado, com a pista na tela toda). No PC, não: joga-se com a palheta,
 * o mouse na pista não toca, e a janela deitada mostra a pista central com o placar nas laterais.
 */
expect val playsByTouch: Boolean

/**
 * O relógio dos eventos de toque (o mesmo do `uptimeMillis` de cada toque): a diferença diz quanto o toque esperou
 * até chegar à partida. No celular, `SystemClock.uptimeMillis`; no PC (sem toque), o relógio monotônico da JVM.
 */
expect fun touchUptimeMs(): Long

/** "Remover animações" do sistema: no celular, a escala de animação zerada; no PC, nunca. */
@Composable
expect fun systemAnimationsReduced(): Boolean

/** Voltar do sistema enquanto [enabled]: no celular, o gesto/botão Voltar; no PC não há (a pausa é pela tecla). */
@Composable
expect fun GameBackHandler(enabled: Boolean, onBack: () -> Unit)

/** A tela não apaga durante a partida: no celular, a tela acesa; no PC, nada. */
@Composable
expect fun KeepScreenOn()

/** Quanto a tecla [event] esperou até chegar à partida (como o toque): o julgamento usa o instante da tecla. */
expect fun keyLateByMs(event: KeyEvent): Long

/**
 * A janela perdeu o foco no meio da partida: [onLost]. No PC, o Alt+Tab (as teclas seguradas não vão subir aqui).
 * No celular, nada: sair do app já pausa pelo ON_PAUSE, e a cortina de notificações tira o foco sem sair do app.
 */
@Composable
expect fun OnWindowFocusLost(onLost: () -> Unit)
