package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Cores de status fora do esquema Material. Ficam fixas mesmo com Dynamic Color, para manter o significado. */
@Immutable
data class ExtendedColors(val success: Color, val onSuccess: Color, val warning: Color, val onWarning: Color)

internal val LightExtendedColors = ExtendedColors(
    success = Color(0xFFB7F1B0),
    onSuccess = Color(0xFF002204),
    warning = Color(0xFFFFDDB3),
    onWarning = Color(0xFF291800),
)

internal val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF1E5223),
    onSuccess = Color(0xFFB7F1B0),
    warning = Color(0xFF633F00),
    onWarning = Color(0xFFFFDDB3),
)

internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
