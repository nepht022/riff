package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.Composable

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
