# Riff Identidade Visual: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** trocar o design system do `:core:ui` pela identidade "Pôster de turnê" (tema escuro único, New Rocker e Oswald, componentes de pôster) e criar as peças de desenho da pista do gameplay (geometria, gema, botão 3D, chama, rastro, placar, multiplicador e julgamento), mais o campo `sfxVolume` em `Settings`.

**Architecture:** a paleta vira um objeto `RiffColors` e um único `ColorScheme` escuro; o `RiffTheme` deixa de aceitar tema claro e Dynamic Color. Os componentes mantêm a API e mudam a aparência; três componentes novos (`PosterTitle`, `PosterButton`, `StageBackground`). As peças da pista ficam em `com.rodrigomachado.riff.core.ui.stage` como funções de `DrawScope` e lógica pura testável (`HighwayGeometry`, `flameFrame`), para o plano do gameplay só montá-las.

**Tech Stack:** Jetpack Compose (BOM 2026.09.00), Material 3, fontes em `res/font` (OFL), Robolectric 4.17 + Roborazzi 1.76.0 para screenshots, JUnit 4 nos testes Android e JUnit 5 nos módulos JVM.

**Spec:** `docs/superpowers/specs/2026-10-07-identidade-visual-design.md` (identidade) e `docs/superpowers/specs/2026-10-05-riff-design.md` (projeto).

## Global Constraints

- Tema único escuro, sem Dynamic Color, mesmo no Android 12+.
- Paleta (spec seção 2): preto de palco `#120D0B`, preto-tinta `#1B1210`, papel `#E8D9B8`, papel apagado `#A89D86`, vermelho `#B3121B`, carimbo de sucesso `#2E6B34`, carimbo de atenção `#7A4F00`, carimbo de erro `#8E1016`. Trilhas inalteradas: `#2ECC71` `#E74C3C` `#F1C40F` `#3498DB` `#E67E22`.
- Vermelho sobre preto nunca carrega texto (contraste 2,8).
- Toda combinação de texto da paleta tem contraste ≥ 4,5 (WCAG AA).
- Fontes: New Rocker (títulos, letreiro, botões principais, placar, multiplicador, julgamento) e Oswald 400/600 (o resto), embutidas em `core/ui/src/main/res/font`, licenças OFL em `core/ui/licenses/`.
- Inclinações entre 1° e 2° só em destaques (botão principal, cartão em evidência); multiplicador a −4°. Listas ficam retas.
- Alvos de toque de no mínimo 48dp; fontes em sp (escaláveis).
- Nada copiado do Guitar Hero: sem logo, fonte, arte ou som do jogo.
- `Settings.sfxVolume` vai de 0 a 1, padrão 0,6.
- Testes Robolectric e screenshots: SDK 35 (já fixado em `core/ui/src/test/resources/robolectric.properties`), qualificador `pt-rBR-w360dp-h640dp-xhdpi`.
- No WSL, o Gradle roda pelo `./scripts/gw <tasks>`. Antes de cada commit: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt`.
- Todo commit termina com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Fonte do sistema em 200%:** `PosterButton` e `PosterTitle` crescem sem cortar texto, e o botão continua com pelo menos 48dp de altura. Teste na Task 4.
2. **TalkBack no letreiro:** o `PosterTitle` desenha o texto três vezes (duas sombras), mas o leitor de tela deve ler uma vez só, como título. Teste na Task 4.
3. **Título de música muito longo no cartão inclinado:** corta em duas linhas com reticências, sem vazar do cartão. Screenshot na Task 4.
4. **Botão desabilitado:** aparece esmaecido e não dispara o clique. Teste na Task 4.
5. **Progresso da chama fora de 0..1 ou NaN** (oscilação do relógio): não desenha nada e não quebra. Teste na Task 5.

---

### Task 1: Volume dos efeitos em `Settings`

**Files:**
- Modify: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/Settings.kt`
- Test: `core/model/src/test/kotlin/com/rodrigomachado/riff/core/model/SettingsTest.kt`

**Interfaces:**
- Produces: `Settings(audioOffsetMs: Int, noteSpeed: Float, leftHanded: Boolean, defaultDifficulty: Difficulty, sfxVolume: Float)`, com `require(sfxVolume in 0f..1f)` e `Settings.DEFAULT.sfxVolume == 0.6f`.

- [ ] **Step 1: Teste que falha**

`SettingsTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SettingsTest {
    @Test
    fun defaultSfxVolumeIsSixtyPercent() {
        assertEquals(0.6f, Settings.DEFAULT.sfxVolume)
    }

    @Test
    fun acceptsSfxVolumeFromZeroToOne() {
        assertDoesNotThrow { Settings.DEFAULT.copy(sfxVolume = 0f) }
        assertDoesNotThrow { Settings.DEFAULT.copy(sfxVolume = 1f) }
    }

    @Test
    fun rejectsSfxVolumeOutsideZeroToOne() {
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(sfxVolume = -0.1f) }
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(sfxVolume = 1.1f) }
        assertThrows<IllegalArgumentException> { Settings.DEFAULT.copy(sfxVolume = Float.NaN) }
    }
}
```

Run: `./scripts/gw :core:model:test`
Expected: falha de compilação (`Unresolved reference 'sfxVolume'`).

- [ ] **Step 2: Implementar**

`Settings.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

/**
 * Preferências do jogador. [audioOffsetMs] negativo compensa atraso de áudio (fone Bluetooth).
 * [sfxVolume] é o volume dos sons de acerto e erro, de 0 (desligado) a 1.
 */
data class Settings(
    val audioOffsetMs: Int,
    val noteSpeed: Float,
    val leftHanded: Boolean,
    val defaultDifficulty: Difficulty,
    val sfxVolume: Float,
) {
    init {
        require(sfxVolume in 0f..1f) { "sfxVolume fora de 0..1: $sfxVolume" }
    }

    companion object {
        val DEFAULT = Settings(
            audioOffsetMs = 0,
            noteSpeed = 1f,
            leftHanded = false,
            defaultDifficulty = Difficulty.MEDIUM,
            sfxVolume = 0.6f,
        )
    }
}
```
(`NaN in 0f..1f` é falso, então o `require` também rejeita NaN.)

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:model:test`
Expected: `BUILD SUCCESSFUL`, 6 testes em `:core:model`.

```bash
git add -A
git commit -m "feat(core-model): volume dos efeitos sonoros em Settings" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Paleta fixa, contraste e tema sem Dynamic Color

**Files:**
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/Color.kt` (substitui o conteúdo)
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/RiffTheme.kt` (substitui o conteúdo)
- Delete: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/ExtendedColors.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/StatusBadge.kt` (cores dos tons)
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/Contrast.kt`
- Modify: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/ErrorStateTest.kt` (`RiffTheme` sem parâmetros)
- Modify: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/ComponentScreenshotTest.kt` (sem tema claro)
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/theme/ContrastTest.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/theme/RiffThemeTest.kt`

**Interfaces:**
- Produces:
  - `object RiffColors { StageBlack, Ink, Paper, PaperDim, TourRed, StampSuccess, StampWarning, StampError }` (todos `Color`)
  - `fun contrastRatio(foreground: Color, background: Color): Float` (WCAG, de 1 a 21)
  - `@Composable fun RiffTheme(content: @Composable () -> Unit)` (sem `darkTheme` nem `dynamicColor`)
- Remove: `ExtendedColors`, `LightColors`, `DarkColors` e o objeto `RiffTheme.extendedColors`.

- [ ] **Step 1: Testes que falham**

`theme/ContrastTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun whiteOnBlackIsTwentyOne() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
    }

    @Test
    fun sameColorIsOne() {
        assertEquals(1f, contrastRatio(RiffColors.Paper, RiffColors.Paper), 0.001f)
    }

    @Test
    fun everyTextPairOfThePaletteMeetsAa() {
        val textPairs = mapOf(
            "papel sobre preto" to (RiffColors.Paper to RiffColors.StageBlack),
            "tinta sobre papel" to (RiffColors.Ink to RiffColors.Paper),
            "papel apagado sobre preto" to (RiffColors.PaperDim to RiffColors.StageBlack),
            "papel sobre vermelho" to (RiffColors.Paper to RiffColors.TourRed),
            "papel sobre carimbo de sucesso" to (RiffColors.Paper to RiffColors.StampSuccess),
            "papel sobre carimbo de atenção" to (RiffColors.Paper to RiffColors.StampWarning),
            "papel sobre carimbo de erro" to (RiffColors.Paper to RiffColors.StampError),
            "tinta sobre papel apagado" to (RiffColors.Ink to RiffColors.PaperDim),
        )
        textPairs.forEach { (name, pair) ->
            val ratio = contrastRatio(pair.first, pair.second)
            assertTrue("$name: $ratio < 4,5", ratio >= 4.5f)
        }
    }

    @Test
    fun redOnBlackFailsAndMustNeverCarryText() {
        assertTrue(contrastRatio(RiffColors.TourRed, RiffColors.StageBlack) < 3f)
    }
}
```

`theme/RiffThemeTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RiffThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun usesFixedPaletteEvenWhereDynamicColorExists() {
        // O Robolectric roda no SDK 35, que tem Dynamic Color (Android 12+).
        var background = Color.Unspecified
        var primary = Color.Unspecified
        var onBackground = Color.Unspecified
        composeRule.setContent {
            RiffTheme {
                background = MaterialTheme.colorScheme.background
                primary = MaterialTheme.colorScheme.primary
                onBackground = MaterialTheme.colorScheme.onBackground
            }
        }
        assertEquals(RiffColors.StageBlack, background)
        assertEquals(RiffColors.TourRed, primary)
        assertEquals(RiffColors.Paper, onBackground)
    }
}
```

Run: `./scripts/gw :core:ui:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'RiffColors'`, `'contrastRatio'`).

- [ ] **Step 2: Paleta, contraste e tema**

`theme/Color.kt`:
```kotlin
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
```

`theme/Contrast.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** Razão de contraste WCAG 2.x entre duas cores opacas, de 1 (igual) a 21 (branco e preto). */
fun contrastRatio(foreground: Color, background: Color): Float {
    val a = foreground.luminance()
    val b = background.luminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}
```

`theme/RiffTheme.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Tema único e escuro, sem Dynamic Color: a identidade não muda com o papel de parede. */
@Composable
fun RiffTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RiffColorScheme, content = content)
}
```

Apague `theme/ExtendedColors.kt`.

- [ ] **Step 3: Ajustar quem usava o tema antigo**

`component/StatusBadge.kt`: troque as linhas que leem `RiffTheme.extendedColors` e `MaterialTheme.colorScheme` pelo mapeamento abaixo (o visual de carimbo completo vem na Task 4):
```kotlin
    val (container, content) = when (tone) {
        BadgeTone.SUCCESS -> RiffColors.StampSuccess to RiffColors.Paper
        BadgeTone.WARNING -> RiffColors.StampWarning to RiffColors.Paper
        BadgeTone.NEUTRAL -> RiffColors.PaperDim to RiffColors.Ink
        BadgeTone.ERROR -> RiffColors.StampError to RiffColors.Paper
    }
```
Remova os `val extended = ...` e `val scheme = ...` e os imports de `RiffTheme`; importe `com.rodrigomachado.riff.core.ui.theme.RiffColors`.

`ErrorStateTest.kt`: troque `RiffTheme(dynamicColor = false) {` por `RiffTheme {`.

`ComponentScreenshotTest.kt`: remova os testes `trackCardLight` e `statusBadgesLight`, renomeie `trackCardDark` para `trackCard` e `statusBadgesDark` para `statusBadges`, troque o `snapshot` para não receber `darkTheme`:
```kotlin
    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme {
                Surface {
                    Box(Modifier.padding(Spacing.md)) { content() }
                }
            }
        }
    }
```
e ajuste as chamadas para `snapshot("TrackCard") { ... }`, `snapshot("StatusBadge") { ... }`, `snapshot("EmptyState") { ... }` e `snapshot("ErrorState") { ... }`.

Apague as referências antigas: `rm core/ui/src/test/screenshots/*.png`.

- [ ] **Step 4: Testes passam, screenshots regravados e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect`, depois `./scripts/gw :core:ui:recordRoborazziDebug` e `./scripts/gw detekt :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. `ContrastTest` (4), `RiffThemeTest` (1), `ErrorStateTest` (1) e 4 screenshots passando; 4 PNGs em `core/ui/src/test/screenshots/`.

```bash
git add -A
git commit -m "feat(core-ui): paleta Pôster de turnê, contraste verificado e tema sem Dynamic Color" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Fontes, tipografia e formas

**Files:**
- Create: `core/ui/src/main/res/font/new_rocker_regular.ttf`, `core/ui/src/main/res/font/oswald_variable.ttf`
- Create: `core/ui/licenses/NewRocker-OFL.txt`, `core/ui/licenses/Oswald-OFL.txt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/Type.kt` (substitui o conteúdo)
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/Shape.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/RiffTheme.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/theme/TypographyTest.kt`

**Interfaces:**
- Consumes: `RiffTheme` (Task 2).
- Produces: `val NewRocker: FontFamily`, `val Oswald: FontFamily`, `val ScoreTextStyle: TextStyle` (New Rocker 32sp), `val JudgmentTextStyle: TextStyle` (New Rocker 40sp); `RiffTheme` passa a aplicar `RiffTypography` e `RiffShapes`.
- Tipografia: `display*`, `headline*`, `titleLarge` e `labelLarge` em New Rocker; `titleMedium`, `titleSmall`, `labelMedium` e `labelSmall` em Oswald 600; `body*` em Oswald 400. Formas: `extraSmall`, `small` e `medium` com cantos de 2dp; `large` e `extraLarge` com 4dp.

- [ ] **Step 1: Baixar as fontes e as licenças**

```bash
mkdir -p core/ui/src/main/res/font core/ui/licenses
curl -sSfLo core/ui/src/main/res/font/new_rocker_regular.ttf https://github.com/google/fonts/raw/main/ofl/newrocker/NewRocker-Regular.ttf
curl -sSfLo core/ui/src/main/res/font/oswald_variable.ttf "https://github.com/google/fonts/raw/main/ofl/oswald/Oswald%5Bwght%5D.ttf"
curl -sSfLo core/ui/licenses/NewRocker-OFL.txt https://github.com/google/fonts/raw/main/ofl/newrocker/OFL.txt
curl -sSfLo core/ui/licenses/Oswald-OFL.txt https://github.com/google/fonts/raw/main/ofl/oswald/OFL.txt
file core/ui/src/main/res/font/*.ttf
```
Expected: os dois arquivos identificados como `TrueType Font data`.

- [ ] **Step 2: Teste que falha**

`theme/TypographyTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TypographyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun captured(): Pair<Typography, Shapes> {
        var typography: Typography? = null
        var shapes: Shapes? = null
        composeRule.setContent {
            RiffTheme {
                typography = MaterialTheme.typography
                shapes = MaterialTheme.shapes
            }
        }
        return typography!! to shapes!!
    }

    @Test
    fun titlesAndMainButtonsUseNewRocker() {
        val (typography, _) = captured()
        assertEquals(NewRocker, typography.displayMedium.fontFamily)
        assertEquals(NewRocker, typography.headlineSmall.fontFamily)
        assertEquals(NewRocker, typography.titleLarge.fontFamily)
        assertEquals(NewRocker, typography.labelLarge.fontFamily)
    }

    @Test
    fun everythingElseUsesOswald() {
        val (typography, _) = captured()
        assertEquals(Oswald, typography.bodyMedium.fontFamily)
        assertEquals(Oswald, typography.titleMedium.fontFamily)
        assertEquals(FontWeight.SemiBold, typography.titleMedium.fontWeight)
        assertEquals(Oswald, typography.labelMedium.fontFamily)
    }

    @Test
    fun shapesAreAlmostSquare() {
        val (_, shapes) = captured()
        assertEquals(RoundedCornerShape(2.dp), shapes.small)
        assertEquals(RoundedCornerShape(4.dp), shapes.large)
    }
}
```

Run: `./scripts/gw :core:ui:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'NewRocker'`, `'Oswald'`).

- [ ] **Step 3: Implementar**

`theme/Type.kt`:
```kotlin
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
    Font(R.font.oswald_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.oswald_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
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
```

`theme/Shape.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Cantos quase retos, como papel recortado. */
internal val RiffShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)
```

`theme/RiffTheme.kt`: troque a chamada por
```kotlin
    MaterialTheme(colorScheme = RiffColorScheme, typography = RiffTypography, shapes = RiffShapes, content = content)
```

- [ ] **Step 4: Testes passam, screenshots regravados e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect`, depois `./scripts/gw :core:ui:recordRoborazziDebug` e `./scripts/gw detekt :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, `TypographyTest` (3) passando. Abra `core/ui/src/test/screenshots/TrackCard.png` com a ferramenta Read e confira que o texto está em Oswald (condensada), não na fonte padrão. Se a Oswald variável não renderizar no Robolectric, registre no ledger e confira no emulador na Task 6.

Se o compilador exigir opt-in para `variationSettings`, adicione `@OptIn(ExperimentalTextApi::class)` na declaração de `Oswald`.

```bash
git add -A
git commit -m "feat(core-ui): tipografia New Rocker e Oswald e formas de papel recortado" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Componentes de pôster

**Files:**
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/StageBackground.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/PosterTitle.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/PosterButton.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/TrackCard.kt` (substitui o conteúdo)
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/StatusBadge.kt` (substitui o conteúdo)
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/StatusViews.kt` (substitui o conteúdo)
- Modify: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/ComponentScreenshotTest.kt` (substitui o conteúdo)
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/component/PosterComponentsTest.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/LargeFontScreenshotTest.kt`

**Interfaces:**
- Consumes: `RiffColors`, `RiffTheme`, `NewRocker`, `Oswald` (Tasks 2 e 3); `Spacing` (existente).
- Produces:
  - `@Composable fun StageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)`
  - `@Composable fun PosterTitle(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.displayMedium)`
  - `@Composable fun PosterButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`
  - `@Composable fun TrackCard(title: String, artist: String, modifier: Modifier = Modifier, tilted: Boolean = false, onClick: (() -> Unit)? = null, badge: (@Composable () -> Unit)? = null)`
  - `StatusBadge`, `LoadingState`, `EmptyState`, `ErrorState` e `StatusViewsTestTags.RETRY` com a mesma assinatura de antes.

- [ ] **Step 1: Testes que falham**

`component/PosterComponentsTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PosterComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun posterButtonCallsOnClickAndIsAtLeast48dpTall() {
        var clicks = 0
        composeRule.setContent { RiffTheme { PosterButton("Jogar", onClick = { clicks++ }) } }

        composeRule.onNodeWithText("Jogar").performClick()

        assertEquals(1, clicks)
        composeRule.onNodeWithText("Jogar").assertHeightIsAtLeast(24.dp) // o texto em si
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun disabledPosterButtonDoesNotClick() {
        var clicks = 0
        composeRule.setContent { RiffTheme { PosterButton("Jogar", onClick = { clicks++ }, enabled = false) } }

        composeRule.onNodeWithText("Jogar").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun posterTitleIsReadOnceAsHeading() {
        composeRule.setContent { RiffTheme { PosterTitle("RIFF") } }

        composeRule.onAllNodesWithText("RIFF").assertCountEquals(1)
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
    }

    @Test
    fun clickableTrackCardCallsOnClick() {
        var clicks = 0
        composeRule.setContent { RiffTheme { TrackCard("Música", "Banda", onClick = { clicks++ }) } }

        composeRule.onNodeWithText("Música").performClick()

        assertEquals(1, clicks)
    }
}
```

`LargeFontScreenshotTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fonte do sistema em 200%: letreiro e botão crescem sem cortar texto. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi", fontScale = 2.0f)
class LargeFontScreenshotTest {
    @Test
    fun posterTitleAndButtonAtDoubleFontScale() {
        captureRoboImage("src/test/screenshots/LargeFont_title_button.png") {
            RiffTheme {
                StageBackground {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        PosterTitle("RIFF")
                        PosterButton("Importar pasta", onClick = {})
                    }
                }
            }
        }
    }
}
```

`ComponentScreenshotTest.kt` (substitui o conteúdo):
```kotlin
package com.rodrigomachado.riff.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class ComponentScreenshotTest {

    @Test
    fun posterTitle() = snapshot("PosterTitle") { PosterTitle("RIFF") }

    @Test
    fun posterButton() = snapshot("PosterButton") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PosterButton("Jogar", onClick = {})
            PosterButton("Jogar", onClick = {}, enabled = false)
        }
    }

    @Test
    fun trackCards() = snapshot("TrackCard") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TrackCard(
                title = "Through the Fire and Flames",
                artist = "DragonForce",
                tilted = true,
                onClick = {},
                badge = { StatusBadge("Mapa encontrado", BadgeTone.SUCCESS) },
            )
            TrackCard(
                title = "Um título de música absurdamente longo que precisa ser cortado em duas linhas no cartão",
                artist = "Uma banda com nome igualmente comprido demais para caber",
                tilted = true,
                badge = { StatusBadge("Confirmar mapa", BadgeTone.WARNING) },
            )
            TrackCard(title = "Garota de Ipanema", artist = "Tom Jobim", badge = { StatusBadge("Sem mapa", BadgeTone.NEUTRAL) })
        }
    }

    @Test
    fun statusBadges() = snapshot("StatusBadge") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BadgeTone.entries.forEach { StatusBadge(it.name, it) }
        }
    }

    @Test
    fun emptyState() = snapshot("EmptyState") {
        EmptyState(
            title = "Nenhum mapa importado",
            message = "Importe uma pasta com mapas do Clone Hero para começar.",
            actionLabel = "Importar pasta",
            onAction = {},
        )
    }

    @Test
    fun errorState() = snapshot("ErrorState") {
        ErrorState(message = "Não foi possível conectar ao Spotify.", onRetry = {})
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme {
                StageBackground {
                    Column(Modifier.padding(Spacing.md)) { content() }
                }
            }
        }
    }
}
```

Run: `./scripts/gw :core:ui:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'PosterButton'`, `'PosterTitle'`, `'StageBackground'`, parâmetro `tilted`).

- [ ] **Step 2: Componentes novos**

`component/StageBackground.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors

private const val HATCH_ALPHA = 0.025f

/** Fundo de todas as telas: preto de palco com hachura diagonal sutil. */
@Composable
fun StageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(RiffColors.StageBlack).drawBehind { drawHatch() }, content = content)
}

private fun DrawScope.drawHatch() {
    val step = 6.dp.toPx()
    val stroke = 1.dp.toPx()
    val color = RiffColors.Paper.copy(alpha = HATCH_ALPHA)
    var x = -size.height
    while (x < size.width) {
        drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
        x += step
    }
}
```

`component/PosterTitle.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors

private val RedShadow = 3.dp
private val BlackShadow = 6.dp

/** Letreiro de pôster: texto em papel com sombras duras vermelha e preta. Lido uma vez só, como título. */
@Composable
fun PosterTitle(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.displayMedium) {
    Box(modifier.padding(end = BlackShadow, bottom = BlackShadow)) {
        Text(text, style = style, color = RiffColors.Ink, modifier = Modifier.offset(BlackShadow, BlackShadow).clearAndSetSemantics {})
        Text(text, style = style, color = RiffColors.TourRed, modifier = Modifier.offset(RedShadow, RedShadow).clearAndSetSemantics {})
        Text(text, style = style, color = RiffColors.Paper, modifier = Modifier.semantics { heading() })
    }
}
```

`component/PosterButton.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

private val ShadowOffset = 5.dp
private val PressDepth = 3.dp
private const val TILT_DEGREES = -1f
private const val DISABLED_ALPHA = 0.45f

/** Botão principal de pôster: vermelho, texto em papel, sombra dura que some ao pressionar. */
@Composable
fun PosterButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shift = if (pressed) PressDepth else 0.dp
    Box(
        modifier
            .rotate(TILT_DEGREES)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(end = ShadowOffset, bottom = ShadowOffset),
    ) {
        if (!pressed) {
            Box(Modifier.matchParentSize().offset(ShadowOffset, ShadowOffset).background(RiffColors.Ink))
        }
        Box(
            Modifier
                .offset(shift, shift)
                .background(RiffColors.TourRed)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper)
        }
    }
}
```

- [ ] **Step 3: Reestilizar os componentes existentes**

`component/TrackCard.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

private val CardShadow = 4.dp
private const val TILT_DEGREES = -1.5f

/** Cartão de música em papel envelhecido com sombra dura. Use [tilted] só no cartão em evidência, não em listas. */
@Composable
fun TrackCard(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    tilted: Boolean = false,
    onClick: (() -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .then(if (tilted) Modifier.rotate(TILT_DEGREES) else Modifier)
            .padding(end = CardShadow, bottom = CardShadow),
    ) {
        Box(Modifier.matchParentSize().offset(CardShadow, CardShadow).background(RiffColors.Ink))
        Row(
            Modifier
                .fillMaxWidth()
                .background(RiffColors.Paper, MaterialTheme.shapes.small)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 48.dp)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = RiffColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RiffColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badge != null) {
                Spacer(Modifier.width(Spacing.sm))
                badge()
            }
        }
    }
}
```

`component/StatusBadge.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

enum class BadgeTone { SUCCESS, WARNING, NEUTRAL, ERROR }

/** Carimbo chapado, cantos retos, texto maiúsculo espaçado. */
@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val (container, content) = when (tone) {
        BadgeTone.SUCCESS -> RiffColors.StampSuccess to RiffColors.Paper
        BadgeTone.WARNING -> RiffColors.StampWarning to RiffColors.Paper
        BadgeTone.NEUTRAL -> RiffColors.PaperDim to RiffColors.Ink
        BadgeTone.ERROR -> RiffColors.StampError to RiffColors.Paper
    }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
        color = content,
        modifier = modifier.background(container).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
```

`component/StatusViews.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.rodrigomachado.riff.core.ui.R
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

object StatusViewsTestTags {
    const val RETRY = "status_views_retry"
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    CenteredColumn(modifier) {
        CircularProgressIndicator(color = RiffColors.Paper)
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = RiffColors.PaperDim, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CenteredColumn(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = RiffColors.Paper, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = RiffColors.PaperDim, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            PosterButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CenteredColumn(modifier) {
        Text(message, style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper, textAlign = TextAlign.Center)
        PosterButton(
            stringResource(R.string.core_ui_retry),
            onClick = onRetry,
            modifier = Modifier.testTag(StatusViewsTestTags.RETRY),
        )
    }
}

@Composable
private fun CenteredColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
```

- [ ] **Step 4: Testes, screenshots e verificação visual**

```bash
rm core/ui/src/test/screenshots/*.png
```
Run: `./scripts/gw detekt -PdetektAutoCorrect`, depois `./scripts/gw :core:ui:recordRoborazziDebug` e `./scripts/gw detekt :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`; `PosterComponentsTest` (4), `ErrorStateTest`, testes de tema e 7 screenshots (`PosterTitle`, `PosterButton`, `TrackCard`, `StatusBadge`, `EmptyState`, `ErrorState`, `LargeFont_title_button`).

Abra cada PNG com a ferramenta Read e confira:
- `TrackCard.png`: o título longo corta em duas linhas com reticências e não vaza do cartão; o artista longo corta em uma linha.
- `LargeFont_title_button.png`: "RIFF" e "Importar pasta" aparecem inteiros, sem corte.
- `PosterButton.png`: o desabilitado está claramente esmaecido.
Se algum falhar, corrija o componente e regrave.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(core-ui): componentes de pôster (letreiro, botão, fundo de palco) e reestilo de cartão, carimbo e estados" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Peças da pista do gameplay

**Files:**
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/HighwayGeometry.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/Flame.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StageDrawing.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/Hud.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/HighwayGeometryTest.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/FlameTest.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/HudTest.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/StageScreenshotTest.kt`

**Interfaces:**
- Consumes: `RiffColors`, `LaneColors`, `ScoreTextStyle`, `JudgmentTextStyle`, `NewRocker`, `PosterTitle`, `RiffTheme`, `StageBackground`.
- Produces (pacote `com.rodrigomachado.riff.core.ui.stage`):
  - `class HighwayGeometry(size: Size, laneCount: Int = 5, topWidthFraction: Float = 0.4f)` com `fun widthAt(depth: Float): Float`, `fun leftAt(depth: Float): Float`, `fun yAt(depth: Float): Float`, `fun laneCenter(lane: Int, depth: Float): Offset` e `fun scaleAt(depth: Float): Float`. `depth` vai de 0 (fundo da pista, longe) a 1 (base da tela).
  - `const val FLAME_DURATION_MS = 250`, `data class FlameFrame(val alpha: Float, val scaleY: Float)` e `fun flameFrame(progress: Float): FlameFrame`.
  - `fun DrawScope.drawHighway(geometry: HighwayGeometry, fretDepths: List<Float>)`
  - `fun DrawScope.drawGem(center: Offset, radius: Float, color: Color, hopo: Boolean = false)`
  - `fun DrawScope.drawFretButton(center: Offset, radius: Float, color: Color, pressed: Boolean)`
  - `fun DrawScope.drawSustainTrail(start: Offset, end: Offset, width: Float, color: Color, active: Boolean)`
  - `fun DrawScope.drawFlame(base: Offset, width: Float, height: Float, progress: Float)`
  - `@Composable fun ScoreText(score: Long, modifier: Modifier = Modifier)` (agrupamento de milhar do idioma do aparelho)
  - `@Composable fun MultiplierStamp(multiplier: Int, modifier: Modifier = Modifier)`
  - `@Composable fun JudgmentText(text: String, modifier: Modifier = Modifier)`
- **Alocação por frame:** as funções de desenho criam `Brush` e `Path` a cada chamada. Isso basta para screenshots e para o catálogo. A regra da especificação de "nenhuma alocação por frame" na chama vale para a pista em movimento, então o plano do gameplay mede o desempenho e, se preciso, passa a guardar esses objetos em cache. Esta task não muda isso.

- [ ] **Step 1: Testes que falham (lógica pura e HUD)**

`stage/HighwayGeometryTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighwayGeometryTest {
    private val geometry = HighwayGeometry(Size(1000f, 2000f), laneCount = 5, topWidthFraction = 0.4f)

    @Test
    fun middleLaneIsAlwaysCentered() {
        listOf(0f, 0.5f, 1f).forEach { depth -> assertEquals(500f, geometry.laneCenter(2, depth).x, 0.01f) }
    }

    @Test
    fun highwayNarrowsTowardTheTop() {
        assertEquals(geometry.widthAt(1f) * 0.4f, geometry.widthAt(0f), 0.01f)
        assertTrue(geometry.widthAt(0.5f) < geometry.widthAt(1f))
        assertEquals(0.4f, geometry.scaleAt(0f), 0.0001f)
        assertEquals(1f, geometry.scaleAt(1f), 0.0001f)
    }

    @Test
    fun lanesSplitTheWidthEvenly() {
        val left = geometry.leftAt(1f)
        val laneWidth = geometry.widthAt(1f) / 5
        assertEquals(left + laneWidth / 2, geometry.laneCenter(0, 1f).x, 0.01f)
        assertEquals(left + laneWidth * 4.5f, geometry.laneCenter(4, 1f).x, 0.01f)
    }

    @Test
    fun depthMapsLinearlyToHeight() {
        assertEquals(0f, geometry.yAt(0f), 0.01f)
        assertEquals(1500f, geometry.yAt(0.75f), 0.01f)
        assertEquals(1500f, geometry.laneCenter(1, 0.75f).y, 0.01f)
    }
}
```

`stage/FlameTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import org.junit.Assert.assertEquals
import org.junit.Test

class FlameTest {
    @Test
    fun startsInvisibleAndEndsInvisible() {
        assertEquals(0f, flameFrame(0f).alpha, 0.0001f)
        assertEquals(0f, flameFrame(1f).alpha, 0.0001f)
    }

    @Test
    fun peaksEarly() {
        val peak = flameFrame(0.15f)
        assertEquals(1f, peak.alpha, 0.0001f)
        assertEquals(1.15f, peak.scaleY, 0.0001f)
    }

    @Test
    fun interpolatesBetweenKeyframes() {
        val middle = flameFrame(0.325f) // metade entre 0,15 (1; 1,15) e 0,5 (0,9; 0,95)
        assertEquals(0.95f, middle.alpha, 0.0001f)
        assertEquals(1.05f, middle.scaleY, 0.0001f)
    }

    @Test
    fun outOfRangeOrNaNProgressDrawsNothing() {
        listOf(-0.1f, 1.5f, Float.NaN).forEach { progress ->
            assertEquals(FlameFrame(alpha = 0f, scaleY = 0f), flameFrame(progress))
        }
    }
}
```

`stage/HudTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HudTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "pt-rBR")
    fun scoreUsesBrazilianThousandsSeparator() {
        composeRule.setContent { RiffTheme { ScoreText(48_300) } }
        composeRule.onNodeWithText("48.300").assertExists()
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun scoreUsesEnglishThousandsSeparator() {
        composeRule.setContent { RiffTheme { ScoreText(48_300) } }
        composeRule.onNodeWithText("48,300").assertExists()
    }

    @Test
    fun multiplierShowsAsXN() {
        composeRule.setContent { RiffTheme { MultiplierStamp(3) } }
        composeRule.onNodeWithText("x3").assertExists()
    }
}
```

Run: `./scripts/gw :core:ui:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'HighwayGeometry'`, `'flameFrame'`, `'ScoreText'`).

- [ ] **Step 2: Geometria e chama**

`stage/HighwayGeometry.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.util.lerp

private const val BOTTOM_WIDTH_FRACTION = 0.94f

/**
 * Geometria da pista em trapézio: estreita no fundo (depth 0) e larga na base da tela (depth 1).
 * As trilhas dividem a largura por igual em qualquer profundidade.
 */
class HighwayGeometry(
    val size: Size,
    val laneCount: Int = 5,
    private val topWidthFraction: Float = 0.4f,
) {
    private val bottomWidth = size.width * BOTTOM_WIDTH_FRACTION
    private val topWidth = bottomWidth * topWidthFraction

    fun widthAt(depth: Float): Float = lerp(topWidth, bottomWidth, depth)

    fun leftAt(depth: Float): Float = (size.width - widthAt(depth)) / 2

    fun yAt(depth: Float): Float = depth * size.height

    fun laneCenter(lane: Int, depth: Float): Offset =
        Offset(leftAt(depth) + widthAt(depth) * (lane + 0.5f) / laneCount, yAt(depth))

    /** Fator de escala de gemas e botões na profundidade [depth] (1 na base da tela). */
    fun scaleAt(depth: Float): Float = widthAt(depth) / bottomWidth
}
```

`stage/Flame.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.util.lerp

/** Duração da chama de acerto. */
const val FLAME_DURATION_MS = 250

/** Quadro da chama: opacidade e altura relativa. */
data class FlameFrame(val alpha: Float, val scaleY: Float)

private val NOTHING = FlameFrame(alpha = 0f, scaleY = 0f)

private val KEYFRAMES = listOf(
    0f to FlameFrame(alpha = 0f, scaleY = 0.2f),
    0.15f to FlameFrame(alpha = 1f, scaleY = 1.15f),
    0.5f to FlameFrame(alpha = 0.9f, scaleY = 0.95f),
    1f to FlameFrame(alpha = 0f, scaleY = 0.6f),
)

/** Quadro da chama em [progress] (0 a 1 ao longo de [FLAME_DURATION_MS]). Fora disso, ou NaN, não desenha nada. */
fun flameFrame(progress: Float): FlameFrame {
    if (progress !in 0f..1f) return NOTHING
    val index = KEYFRAMES.indexOfLast { it.first <= progress }.coerceAtMost(KEYFRAMES.lastIndex - 1)
    val (start, from) = KEYFRAMES[index]
    val (end, to) = KEYFRAMES[index + 1]
    val t = (progress - start) / (end - start)
    return FlameFrame(alpha = lerp(from.alpha, to.alpha, t), scaleY = lerp(from.scaleY, to.scaleY, t))
}
```

- [ ] **Step 3: Desenho da pista e HUD**

`stage/StageDrawing.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.RiffColors

private val ChromeLight = Color(0xFFF4F4F4)
private val ChromeMid = Color(0xFF7B7B7B)
private val ChromeDark = Color(0xFF5A5A5A)
private val GemBase = Color(0xFF151515)
private val HighwayFar = Color(0xFF000000)
private val HighwayNear = Color(0xFF3B1C16)
private val FlameRed = Color(0xFFFF3D00)
private val FlameOrange = Color(0xFFFF9D00)
private val FlameYellow = Color(0xFFFFD44D)
private val FlameCore = Color(0xFFFFFBE6)
private const val LINE_ALPHA = 0.18f

private fun chrome(center: Offset) =
    Brush.sweepGradient(listOf(ChromeLight, ChromeMid, ChromeLight, ChromeDark, ChromeLight), center)

/** Braço da pista: trapézio escuro com moldura de papel e vermelho, linhas de trilha e de traste. */
fun DrawScope.drawHighway(geometry: HighwayGeometry, fretDepths: List<Float>) {
    val outline = Path().apply {
        moveTo(geometry.leftAt(0f), 0f)
        lineTo(geometry.leftAt(0f) + geometry.widthAt(0f), 0f)
        lineTo(geometry.leftAt(1f) + geometry.widthAt(1f), size.height)
        lineTo(geometry.leftAt(1f), size.height)
        close()
    }
    drawPath(outline, Brush.verticalGradient(listOf(HighwayFar, HighwayNear)))
    val line = RiffColors.Paper.copy(alpha = LINE_ALPHA)
    for (lane in 1 until geometry.laneCount) {
        val top = Offset(geometry.leftAt(0f) + geometry.widthAt(0f) * lane / geometry.laneCount, 0f)
        val bottom = Offset(geometry.leftAt(1f) + geometry.widthAt(1f) * lane / geometry.laneCount, size.height)
        drawLine(line, top, bottom, 1.dp.toPx())
    }
    fretDepths.filter { it in 0f..1f }.forEach { depth ->
        val y = geometry.yAt(depth)
        drawLine(line, Offset(geometry.leftAt(depth), y), Offset(geometry.leftAt(depth) + geometry.widthAt(depth), y), 2.dp.toPx())
    }
    drawPath(outline, RiffColors.TourRed, style = Stroke(width = 7.dp.toPx()))
    drawPath(outline, RiffColors.Paper, style = Stroke(width = 3.dp.toPx()))
}

/** Gema 3D: sombra, aro cromado, base preta e domo colorido com reflexo. HOPO tem o centro mais claro. */
fun DrawScope.drawGem(center: Offset, radius: Float, color: Color, hopo: Boolean = false) {
    drawCircle(Color.Black, radius, center + Offset(0f, radius * 0.14f))
    drawCircle(chrome(center), radius, center)
    drawCircle(GemBase, radius * 0.8f, center)
    val dome = radius * 0.62f
    drawCircle(
        Brush.radialGradient(
            0f to Color.White,
            0.35f to color,
            1f to lerp(color, Color.Black, 0.45f),
            center = center + Offset(-dome * 0.25f, -dome * 0.35f),
            radius = dome * 1.4f,
        ),
        dome,
        center,
    )
    if (hopo) drawCircle(Color.White.copy(alpha = 0.85f), dome * 0.45f, center)
    drawCircle(Color.White.copy(alpha = 0.7f), dome * 0.18f, center + Offset(-dome * 0.35f, -dome * 0.4f))
}

/** Botão da linha de acerto. Pressionado, desce e acende na cor da trilha. */
fun DrawScope.drawFretButton(center: Offset, radius: Float, color: Color, pressed: Boolean) {
    val drop = radius * 0.14f
    val c = if (pressed) center + Offset(0f, drop * 0.7f) else center
    if (pressed) {
        drawCircle(color.copy(alpha = 0.35f), radius * 1.3f, c)
    } else {
        drawCircle(Color.Black, radius, center + Offset(0f, drop))
    }
    drawCircle(chrome(c), radius, c)
    drawCircle(color, radius * 0.8f, c)
    drawCircle(GemBase, radius * 0.55f, c)
    if (pressed) {
        drawCircle(Brush.radialGradient(0f to Color.White, 0.5f to color, 1f to color, center = c, radius = radius * 0.5f), radius * 0.5f, c)
    }
}

/** Rastro da nota longa. Brilha enquanto está sendo segurada. */
fun DrawScope.drawSustainTrail(start: Offset, end: Offset, width: Float, color: Color, active: Boolean) {
    if (active) drawLine(color.copy(alpha = 0.3f), start, end, width * 2.2f, StrokeCap.Round)
    drawLine(color.copy(alpha = if (active) 1f else 0.6f), start, end, width, StrokeCap.Round)
}

/** Chama do acerto saindo de [base] (centro do botão), no quadro dado por [progress] (0 a 1). */
fun DrawScope.drawFlame(base: Offset, width: Float, height: Float, progress: Float) {
    val frame = flameFrame(progress)
    if (frame.alpha <= 0f) return
    val h = height * frame.scaleY
    fun layer(w: Float, layerHeight: Float, dx: Float, vararg stops: Pair<Float, Color>) {
        drawOval(
            brush = Brush.verticalGradient(*stops, startY = base.y - layerHeight, endY = base.y),
            topLeft = Offset(base.x - w / 2 + dx, base.y - layerHeight),
            size = Size(w, layerHeight),
            alpha = frame.alpha,
        )
    }
    layer(width, h, 0f, 0f to FlameRed.copy(alpha = 0f), 0.3f to FlameRed, 0.7f to FlameOrange, 1f to FlameYellow)
    layer(width * 0.55f, h * 0.7f, 0f, 0f to FlameYellow.copy(alpha = 0f), 0.4f to FlameYellow, 1f to FlameCore)
    layer(width * 0.3f, h * 0.4f, -width * 0.15f, 0f to FlameYellow.copy(alpha = 0f), 1f to FlameYellow)
    listOf(-0.45f, 0.1f, 0.4f).forEach { spread ->
        val spark = Offset(base.x + spread * width * (0.5f + progress), base.y - h * (0.7f + 0.6f * progress))
        drawCircle(FlameYellow, width * 0.05f, spark, alpha = frame.alpha)
    }
}
```

`stage/Hud.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.theme.JudgmentTextStyle
import com.rodrigomachado.riff.core.ui.theme.NewRocker
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.ScoreTextStyle
import com.rodrigomachado.riff.core.ui.theme.Spacing
import java.text.NumberFormat

private val JudgmentGlow = Color(0xFFFF6A00)
private val JudgmentColor = Color(0xFFFFF3D6)
private const val STAMP_TILT_DEGREES = -4f

/** Placar com separador de milhar do idioma do aparelho, no estilo letreiro. */
@Composable
fun ScoreText(score: Long, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(score, locale) { NumberFormat.getIntegerInstance(locale).format(score) }
    PosterTitle(formatted, modifier, style = ScoreTextStyle)
}

/** Multiplicador como carimbo vermelho inclinado. */
@Composable
fun MultiplierStamp(multiplier: Int, modifier: Modifier = Modifier) {
    Text(
        text = "x$multiplier",
        color = RiffColors.Paper,
        style = ScoreTextStyle.copy(fontFamily = NewRocker, fontSize = 22.sp),
        modifier = modifier
            .rotate(STAMP_TILT_DEGREES)
            .background(RiffColors.TourRed)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}

/** Texto de julgamento ("PERFEITO!", "BOM!") com brilho laranja. A animação de salto fica no gameplay. */
@Composable
fun JudgmentText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = JudgmentColor,
        style = JudgmentTextStyle.copy(shadow = Shadow(color = JudgmentGlow, blurRadius = 24f)),
        modifier = modifier,
    )
}
```

- [ ] **Step 4: Screenshots da pista**

`stage/StageScreenshotTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class StageScreenshotTest {
    private val strikeDepth = 0.86f

    @Test
    fun stageWithGemsButtonsFlameAndHud() {
        captureRoboImage("src/test/screenshots/Stage.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val geometry = HighwayGeometry(size)
                        drawHighway(geometry, fretDepths = listOf(0.12f, 0.34f, 0.56f, 0.78f))
                        val baseRadius = size.width / 5 * 0.36f
                        val trailStart = geometry.laneCenter(3, 0.55f)
                        val trailEnd = geometry.laneCenter(3, 0.25f)
                        drawSustainTrail(trailStart, trailEnd, baseRadius * 0.35f, LaneColors.forLane(3), active = false)
                        listOf(0 to 0.2f, 2 to 0.35f, 1 to 0.5f, 3 to 0.55f, 4 to 0.7f).forEach { (lane, depth) ->
                            val r = baseRadius * geometry.scaleAt(depth)
                            drawGem(geometry.laneCenter(lane, depth), r, LaneColors.forLane(lane), hopo = lane == 1)
                        }
                        for (lane in 0 until 5) {
                            drawFretButton(geometry.laneCenter(lane, strikeDepth), baseRadius, LaneColors.forLane(lane), pressed = lane == 2)
                        }
                        val hit = geometry.laneCenter(2, strikeDepth)
                        drawFlame(hit, baseRadius * 1.6f, baseRadius * 4f, progress = 0.2f)
                    }
                    Row(
                        Modifier.fillMaxSize().padding(Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        ScoreText(48_300)
                        MultiplierStamp(3)
                    }
                    JudgmentText("PERFEITO!", Modifier.align(Alignment.Center))
                }
            }
        }
    }

    @Test
    fun gemAndButtonCloseUp() {
        captureRoboImage("src/test/screenshots/Stage_closeup.png") {
            RiffTheme {
                StageBackground {
                    Canvas(Modifier.size(360.dp, 140.dp)) {
                        val r = 34.dp.toPx()
                        val y = size.height / 2
                        val red = LaneColors.forLane(1)
                        drawGem(Offset(size.width * 0.14f, y), r, red)
                        drawGem(Offset(size.width * 0.38f, y), r, red, hopo = true)
                        drawFretButton(Offset(size.width * 0.62f, y), r, red, pressed = false)
                        drawFretButton(Offset(size.width * 0.86f, y), r, red, pressed = true)
                    }
                }
            }
        }
    }

    @Test
    fun flameKeyframes() {
        captureRoboImage("src/test/screenshots/Stage_flame_frames.png") {
            RiffTheme {
                StageBackground {
                    Column {
                        Canvas(Modifier.size(360.dp, 200.dp)) {
                            val progresses = listOf(0.05f, 0.15f, 0.4f, 0.8f)
                            progresses.forEachIndexed { i, progress ->
                                val base = Offset(size.width * (i + 0.5f) / progresses.size, size.height * 0.9f)
                                drawFlame(base, 50.dp.toPx(), 140.dp.toPx(), progress)
                            }
                        }
                        Box(Modifier.padding(Spacing.md))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Testes, screenshots, verificação visual e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect`, depois `./scripts/gw :core:ui:recordRoborazziDebug` e `./scripts/gw detekt :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`; `HighwayGeometryTest` (4), `FlameTest` (4), `HudTest` (3) e 3 screenshots novos.

Abra os três PNGs com a ferramenta Read e compare com o mockup aprovado (pista B2): pista escura em trapézio com moldura papel e vermelho; gemas com aro cromado e domo com reflexo; a gema HOPO com centro claro; botão do meio afundado e aceso, com a chama saindo dele; placar "48.300" e carimbo "x3" no topo; "PERFEITO!" com brilho laranja. Em `Stage_flame_frames.png`, a chama cresce, atinge o pico no segundo quadro e diminui. Ajuste proporções e cores se algo destoar do mockup e regrave.

```bash
git add -A
git commit -m "feat(core-ui): peças da pista (geometria, gema, botão 3D, chama, rastro e HUD)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Catálogo no app, README e verificação final

**Files:**
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/CatalogScreen.kt` (substitui o conteúdo)
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`
- Modify: `README.md`

**Interfaces:**
- Consumes: todos os componentes e peças das Tasks 2 a 5.

- [ ] **Step 1: Strings novas**

Em `app/src/main/res/values/strings.xml`, dentro de `<resources>`, adicione:
```xml
    <string name="catalog_now_playing">Tocando agora no Spotify</string>
    <string name="catalog_play">Jogar</string>
```
Em `app/src/main/res/values-en/strings.xml`:
```xml
    <string name="catalog_now_playing">Now playing on Spotify</string>
    <string name="catalog_play">Play</string>
```

- [ ] **Step 2: Catálogo**

`CatalogScreen.kt`:
```kotlin
package com.rodrigomachado.riff

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.stage.HighwayGeometry
import com.rodrigomachado.riff.core.ui.stage.drawFlame
import com.rodrigomachado.riff.core.ui.stage.drawFretButton
import com.rodrigomachado.riff.core.ui.stage.drawGem
import com.rodrigomachado.riff.core.ui.stage.drawHighway
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Vitrine temporária da identidade visual, até as features existirem. */
@Composable
fun CatalogScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { PosterTitle("RIFF") }
        item {
            Text(stringResource(R.string.catalog_now_playing), style = MaterialTheme.typography.labelMedium, color = RiffColors.PaperDim)
        }
        item {
            TrackCard("Through the Fire and Flames", "DragonForce", tilted = true, onClick = {}, badge = {
                StatusBadge(stringResource(R.string.catalog_badge_matched), BadgeTone.SUCCESS)
            })
        }
        item { PosterButton(stringResource(R.string.catalog_play), onClick = {}) }
        item {
            TrackCard("Bohemian Rhapsody", "Queen", onClick = {}, badge = {
                StatusBadge(stringResource(R.string.catalog_badge_confirm), BadgeTone.WARNING)
            })
        }
        item {
            TrackCard("Garota de Ipanema", "Tom Jobim", badge = {
                StatusBadge(stringResource(R.string.catalog_badge_no_chart), BadgeTone.NEUTRAL)
            })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LaneColors.all.forEach { Box(Modifier.size(40.dp).background(it, CircleShape)) }
            }
        }
        item { StagePreview() }
        item {
            Box(Modifier.height(280.dp)) {
                EmptyState(
                    title = stringResource(R.string.catalog_empty_title),
                    message = stringResource(R.string.catalog_empty_message),
                    actionLabel = stringResource(R.string.catalog_empty_action),
                    onAction = {},
                )
            }
        }
        item {
            Box(Modifier.height(220.dp)) {
                ErrorState(message = stringResource(R.string.catalog_error_message), onRetry = {})
            }
        }
    }
}

/** Pista estática para conferir as peças no aparelho de verdade. */
@Composable
private fun StagePreview() {
    Canvas(Modifier.fillMaxWidth().height(320.dp)) {
        val geometry = HighwayGeometry(size)
        drawHighway(geometry, fretDepths = listOf(0.2f, 0.45f, 0.7f))
        val radius = size.width / 5 * 0.36f
        drawGem(geometry.laneCenter(0, 0.3f), radius * geometry.scaleAt(0.3f), LaneColors.forLane(0))
        drawGem(geometry.laneCenter(3, 0.5f), radius * geometry.scaleAt(0.5f), LaneColors.forLane(3), hopo = true)
        for (lane in 0 until 5) {
            drawFretButton(geometry.laneCenter(lane, 0.84f), radius, LaneColors.forLane(lane), pressed = lane == 1)
        }
        drawFlame(geometry.laneCenter(1, 0.84f), radius * 1.6f, radius * 4f, progress = 0.2f)
    }
}
```

`MainActivity.kt`: troque o `setContent` por
```kotlin
        setContent {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    CatalogScreen(Modifier.safeDrawingPadding())
                }
            }
        }
```
Ajuste os imports: remova `androidx.compose.foundation.layout.padding` e `androidx.compose.material3.Scaffold`; adicione `androidx.compose.foundation.layout.safeDrawingPadding` e `com.rodrigomachado.riff.core.ui.component.StageBackground`.

- [ ] **Step 3: README**

Em `README.md`, na tabela de módulos, troque a linha do `:core:ui` por:
```markdown
| `:core:ui` | Identidade "Pôster de turnê": tema escuro único, fontes New Rocker e Oswald (licenças OFL em `core/ui/licenses/`), componentes e peças de desenho da pista, com screenshots em `src/test/screenshots` |
```
E acrescente no fim da seção "Status":
```markdown

Identidade visual "Pôster de turnê" aplicada ao design system (`docs/superpowers/specs/2026-10-07-identidade-visual-design.md`).
```

- [ ] **Step 4: Verificação completa**

Run: `./scripts/gw detekt -PdetektAutoCorrect`, depois `./scripts/gw detekt lintDebug test koverVerify :core:ui:verifyRoborazziDebug assembleDebug`
Expected: `BUILD SUCCESSFUL`, lint sem erros.

- [ ] **Step 5: Conferir no emulador**

```bash
ADB="/mnt/c/Users/Rodrigo Machado/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"/mnt/c/Users/Rodrigo Machado/AppData/Local/Android/Sdk/emulator/emulator.exe" -avd Pixel_4_2 -no-snapshot-save -no-audio > /dev/null 2>&1 &
# espere sys.boot_completed = 1 e o estado "device"
"$ADB" install -r 'app\build\outputs\apk\debug\app-debug.apk'
"$ADB" shell am start -n com.rodrigomachado.riff/.MainActivity
"$ADB" exec-out screencap -p > <scratchpad>/riff-identidade.png
```
Abra o PNG com a ferramenta Read: fundo preto com hachura, letreiro "RIFF" com sombras, cartão de papel inclinado, botão vermelho "Play" (o emulador está em inglês) e a pista com gemas, botões e chama, com as fontes New Rocker e Oswald de verdade. Role a lista (`"$ADB" shell input swipe 500 1600 500 400`) e capture de novo para ver a pista e os estados. Confira também o `logcat` sem `AndroidRuntime: FATAL`.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(app): catálogo com a identidade Pôster de turnê e pista estática" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
