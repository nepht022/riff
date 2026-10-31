package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** Paleta "Pôster de turnê". Vermelho sobre preto nunca carrega texto (contraste 2,8). */
object RiffColors {
    val StageBlack = Color(0xFF120D0B)
    val Ink = Color(0xFF1B1210)
    val Paper = Color(0xFFE8D9B8)
    val PaperDim = Color(0xFFA89D86)
    val TourRed = Color(0xFFB3121B)
    val StampSuccess = Color(0xFF2E6B34)
    val StampWarning = Color(0xFF7A4F00)
    val StampError = Color(0xFF8E1016)
}

internal val RiffColorScheme = darkColorScheme(
    primary = RiffColors.TourRed,
    onPrimary = RiffColors.Paper,
    primaryContainer = RiffColors.TourRed,
    onPrimaryContainer = RiffColors.Paper,
    secondary = RiffColors.Paper,
    onSecondary = RiffColors.Ink,
    secondaryContainer = RiffColors.Paper,
    onSecondaryContainer = RiffColors.Ink,
    background = RiffColors.StageBlack,
    onBackground = RiffColors.Paper,
    surface = RiffColors.StageBlack,
    onSurface = RiffColors.Paper,
    surfaceVariant = RiffColors.Ink,
    onSurfaceVariant = RiffColors.PaperDim,
    error = RiffColors.StampError,
    onError = RiffColors.Paper,
    errorContainer = RiffColors.StampError,
    onErrorContainer = RiffColors.Paper,
    outline = RiffColors.PaperDim,
)
