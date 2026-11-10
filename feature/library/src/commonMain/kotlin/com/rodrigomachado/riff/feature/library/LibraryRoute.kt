package com.rodrigomachado.riff.feature.library

import kotlinx.serialization.Serializable

/** Biblioteca: os mapas da pasta e os baixados do Encore. */
@Serializable
data object LibraryRoute

/**
 * A Biblioteca no modo de escolha da sala [roomCode]: o toque na música abre o diálogo de dificuldade de sempre e a
 * escolha volta para a sala (que grava a música e a dificuldade do dono), sem jogar.
 */
@Serializable
data class LibraryPickRoute(val roomCode: String)
