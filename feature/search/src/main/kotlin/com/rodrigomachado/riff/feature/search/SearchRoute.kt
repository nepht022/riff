package com.rodrigomachado.riff.feature.search

import kotlinx.serialization.Serializable

/** Pesquisa no Encore, aberta pela Biblioteca. */
@Serializable
data class SearchRoute(val query: String? = null)
