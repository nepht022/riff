package com.rodrigomachado.riff.feature.calibration

/**
 * O relógio dos instantes da calibração: os cliques no alto-falante e os toques do jogador são comparados nele.
 * No celular, `SystemClock.uptimeMillis` (o mesmo do `uptimeMillis` de cada toque); no PC, o relógio monotônico da JVM.
 */
expect fun calibrationClockMs(): Long

/**
 * Mede-se tocando na tela (celular). No PC, pelo teclado: qualquer tecla (menos o Esc) é um toque, no instante em
 * que foi apertada.
 */
expect val tapsByTouch: Boolean
