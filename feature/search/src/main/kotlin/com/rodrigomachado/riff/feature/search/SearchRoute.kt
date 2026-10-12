package com.rodrigomachado.riff.feature.search

import kotlinx.serialization.Serializable

/**
 * Pesquisa no Encore. Vinda do Tocando agora, traz a faixa do Spotify: a busca já começa com "artista título", os
 * resultados mais parecidos vêm primeiro e o mapa baixado fica casado com ela.
 */
@Serializable
data class SearchRoute(
    val query: String? = null,
    val matchTrackUri: String? = null,
    val matchTrackTitle: String? = null,
    val matchTrackArtist: String? = null,
    val matchTrackDurationMs: Long? = null,
)
