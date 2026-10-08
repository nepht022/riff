package com.rodrigomachado.riff.feature.gameplay

import kotlinx.serialization.Serializable

@Serializable
data class GameplayRoute(val chartId: String, val difficulty: String)
