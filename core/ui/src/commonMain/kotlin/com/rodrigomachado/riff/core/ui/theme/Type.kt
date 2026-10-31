package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.ui.resources.Res
import com.rodrigomachado.riff.core.ui.resources.new_rocker_regular
import com.rodrigomachado.riff.core.ui.resources.oswald_variable
import org.jetbrains.compose.resources.Font

// As fontes vêm dos recursos do Compose Multiplatform (os mesmos arquivos no celular e no PC); carregá-las é
// @Composable, por isso as famílias e a tipografia são montadas na composição.

/** Fonte de pôster de rock (OFL): títulos, letreiro, botões principais, placar e julgamento. */
@Composable
fun newRocker(): FontFamily = FontFamily(Font(Res.font.new_rocker_regular))

/** Fonte condensada (OFL, variável): textos corridos, listas, badges e configurações. */
@Composable
fun oswald(): FontFamily = FontFamily(
    Font(
        Res.font.oswald_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        Res.font.oswald_variable,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/** A tipografia do tema: New Rocker nos títulos e botões principais, Oswald no resto. */
@Composable
fun riffTypography(): Typography {
    val newRocker = newRocker()
    val oswald = oswald()
    return remember(newRocker, oswald) { riffTypography(newRocker, oswald) }
}

private fun riffTypography(rocker: FontFamily, condensed: FontFamily): Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = rocker),
        displayMedium = displayMedium.copy(fontFamily = rocker),
        displaySmall = displaySmall.copy(fontFamily = rocker),
        headlineLarge = headlineLarge.copy(fontFamily = rocker),
        headlineMedium = headlineMedium.copy(fontFamily = rocker),
        headlineSmall = headlineSmall.copy(fontFamily = rocker),
        titleLarge = titleLarge.copy(fontFamily = rocker),
        titleMedium = titleMedium.copy(fontFamily = condensed, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = condensed, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = condensed),
        bodyMedium = bodyMedium.copy(fontFamily = condensed),
        bodySmall = bodySmall.copy(fontFamily = condensed),
        labelLarge = labelLarge.copy(fontFamily = rocker),
        labelMedium = labelMedium.copy(fontFamily = condensed, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = condensed, fontWeight = FontWeight.SemiBold),
    )
}

/** Placar do gameplay. */
@Composable
fun scoreTextStyle(): TextStyle = TextStyle(fontFamily = newRocker(), fontSize = 32.sp)

/** "PERFEITO!" e "BOM!" no gameplay. */
@Composable
fun judgmentTextStyle(): TextStyle = TextStyle(fontFamily = newRocker(), fontSize = 40.sp)
