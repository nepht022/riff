package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.Composable

/**
 * O relógio dos eventos de toque (o mesmo do `uptimeMillis` de cada toque): a diferença diz quanto o toque esperou
 * até chegar à partida. No celular, `SystemClock.uptimeMillis`; no PC, o relógio monotônico da JVM.
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
