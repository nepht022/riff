package com.rodrigomachado.riff.feature.room

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun RoomBackHandler(onBack: () -> Unit) = BackHandler(onBack = onBack)
