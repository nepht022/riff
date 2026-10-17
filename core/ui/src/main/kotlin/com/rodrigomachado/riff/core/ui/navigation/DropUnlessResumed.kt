package com.rodrigomachado.riff.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Ignora a ação quando a tela não está em primeiro plano (por exemplo, saindo numa animação).
 * Evita que um toque duplo em "Jogar de novo" ou "Voltar" navegue duas vezes.
 */
@Composable
fun <A, B> dropUnlessResumed(block: (A, B) -> Unit): (A, B) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { a, b -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(a, b) }
}

/** Igual ao de dois parâmetros, para ações sem parâmetro. */
@Composable
fun dropUnlessResumed(block: () -> Unit): () -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block() }
}

/** Igual ao de dois parâmetros, para ações com um parâmetro. */
@Composable
fun <A> dropUnlessResumed(block: (A) -> Unit): (A) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { a -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(a) }
}

/** Igual ao de dois parâmetros, para ações com três parâmetros. */
@Composable
fun <A, B, C> dropUnlessResumed(block: (A, B, C) -> Unit): (A, B, C) -> Unit {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return { a, b, c -> if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(a, b, c) }
}
