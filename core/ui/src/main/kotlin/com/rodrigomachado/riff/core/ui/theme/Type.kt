package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.ui.R

/** Fonte de pôster de rock (OFL): títulos, letreiro, botões principais, placar e julgamento. */
val NewRocker = FontFamily(Font(R.font.new_rocker_regular))

/** Fonte condensada (OFL, variável): textos corridos, listas, badges e configurações. */
val Oswald = FontFamily(
    Font(
        R.font.oswald_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.oswald_variable,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

internal val RiffTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = NewRocker),
        displayMedium = displayMedium.copy(fontFamily = NewRocker),
        displaySmall = displaySmall.copy(fontFamily = NewRocker),
        headlineLarge = headlineLarge.copy(fontFamily = NewRocker),
        headlineMedium = headlineMedium.copy(fontFamily = NewRocker),
        headlineSmall = headlineSmall.copy(fontFamily = NewRocker),
        titleLarge = titleLarge.copy(fontFamily = NewRocker),
        titleMedium = titleMedium.copy(fontFamily = Oswald, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = Oswald, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = Oswald),
        bodyMedium = bodyMedium.copy(fontFamily = Oswald),
        bodySmall = bodySmall.copy(fontFamily = Oswald),
        labelLarge = labelLarge.copy(fontFamily = NewRocker),
        labelMedium = labelMedium.copy(fontFamily = Oswald, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = Oswald, fontWeight = FontWeight.SemiBold),
    )
}

/** Placar do gameplay. */
val ScoreTextStyle = TextStyle(fontFamily = NewRocker, fontSize = 32.sp)

/** "PERFEITO!" e "BOM!" no gameplay. */
val JudgmentTextStyle = TextStyle(fontFamily = NewRocker, fontSize = 40.sp)
