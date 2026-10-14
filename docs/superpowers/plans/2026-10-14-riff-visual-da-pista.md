# Visual da pista e imagem do mapa — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** o jogador escolhe, nas Configurações, o fundo da pista, o desenho estampado nela, o design das notas e o dos botões (temas de rock, tudo desenhado em código). A partida pode mostrar a imagem do mapa atrás da pista.

**Architecture:**
- **Modelo e preferências:** quatro enums em `:core:model`, com o padrão igual ao visual de hoje. Os campos novos vão em `Settings` e são gravados pelo nome nas preferências.
- **Pista (`:core:ui/stage`):**
  - Fundo e estampa viram texturas (`Bitmap`) geradas uma vez por escolha (`HighwayTexture`).
  - Elas são desenhadas com `BitmapShader` mais uma matriz de perspectiva (`setPolyToPoly`) que acompanha a rolagem dos trastes.
  - Com Palco e Sem desenho, roda o código de hoje sem mudança.
  - Notas e botões escolhem a forma pelo estilo, com `Path`s em tamanho 1 prontos.
- **Imagem do mapa:** a imagem (`background.*`, senão `album.*`) é lida por `ChartArtLoader` (`:core:data`). O `GameplayViewModel` a decodifica reduzida, e a tela a desenha escurecida atrás da pista.
- **Configurações:** quatro linhas que abrem uma lista com miniaturas desenhadas pelas mesmas funções da pista.

**Tech Stack:** Kotlin, Compose (Canvas, `drawIntoCanvas`), `android.graphics` (Bitmap, BitmapShader, Matrix, Path, BlurMaskFilter), DataStore, Robolectric + Roborazzi (`GraphicsMode.NATIVE`).

**Spec:** `docs/superpowers/specs/2026-10-14-visual-da-pista-design.md`

## Global Constraints

- O visual de hoje é o padrão de fábrica: `HighwayBackground.STAGE`, `HighwayArt.NONE`, `NoteStyle.DISCS`, `FretStyle.DISCS`. Com ele, os screenshots atuais (`Stage.png`, `Stage_closeup.png`, `Gameplay_stage*.png`) não podem mudar.
- Nada do Guitar Hero: tudo desenhado em código, sem imagens novas no APK.
- A partida continua a 120 fps no S25 com qualquer combinação. Nada é alocado por quadro: matriz, `Paint`, `Path` e texturas são preparados fora do laço.
- As cores das trilhas (`LaneColors`) não mudam em nenhum design; nota perdida esmaece (`MISSED_GEM_ALPHA`); HOPO tem o centro branco.
- Imagem do mapa: `background.*` preferida, depois `album.*` (`jpg`, `jpeg`, `png`, sem diferenciar maiúsculas); no máximo 8 MB lidos; decodificada reduzida; véu preto de 65 % por cima; ligada de fábrica (`showChartImage = true`).
- "Perguntar sempre se separa": ligado de fábrica, chave nova `ask_separation_always_v2` (a antiga é ignorada).
- Textos:
  - Configurações: "Fundo da pista", "Design da pista", "Design das notas", "Design dos botões", "Imagem do mapa" / "Mostra a capa ou a imagem de fundo do mapa atrás da pista.", "Perguntar sempre se separa";
  - fundos: "Palco", "Amplificador", "Couro e rebites", "Braço de guitarra", "Chamas", "Neon", "Chapa de metal";
  - designs da pista: "Sem desenho", "Caveira alada", "Raios", "Estrelas", "Guitarras", "Palhetas", "Tatuagem";
  - notas: "Discos", "Palhetas", "Caveiras", "Estrelas", "Raios", "Chamas";
  - botões: "Discos", "Pedais de efeito", "Knobs de amplificador", "Alto-falantes", "Palhetas".
- Fluxo: TDD, detekt (LongMethod 60, TooManyFunctions 11, CyclomaticComplex 14), suíte completa `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --console=plain`.

## Review Focus

1. Com o visual padrão, a pista tem que sair pixel a pixel igual à de hoje — os screenshots existentes não mudam (Tasks 4 e 5).
2. Textura rolando sem emenda visível: a borda de cima do ladrilho continua a de baixo (ruído e estampas repetem na profundidade) — teste no Task 3.
3. Preferência gravada com um nome de opção que não existe mais volta ao padrão em vez de quebrar a leitura — teste no Task 1.
4. Imagem do mapa enorme ou corrompida: decodificada reduzida, ou ignorada sem erro — teste no Task 6.
5. Modo canhoto e notas perdidas/HOPO com cada design de nota: cor, esmaecimento e centro branco continuam — teste no Task 5.

---

### Task 1: Modelo e preferências

**Files:**
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/TrackLook.kt`
- Modify: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/Settings.kt`
- Modify: `core/datastore/src/main/kotlin/com/rodrigomachado/riff/core/datastore/RiffPreferences.kt`
- Test: `core/model/src/test/kotlin/com/rodrigomachado/riff/core/model/SettingsTest.kt`, `core/datastore/src/test/kotlin/com/rodrigomachado/riff/core/datastore/RiffPreferencesTest.kt`

**Interfaces:**
- Produces:
  - `enum class HighwayBackground { STAGE, AMPLIFIER, LEATHER, FRETBOARD, FLAMES, NEON, DIAMOND_PLATE }`;
  - `enum class HighwayArt { NONE, WINGED_SKULL, BOLTS, STARS, GUITARS, PICKS, TATTOO }`;
  - `enum class NoteStyle { DISCS, PICKS, SKULLS, STARS, BOLTS, FLAMES }`;
  - `enum class FretStyle { DISCS, PEDALS, KNOBS, SPEAKERS, PICKS }`;
  - `data class TrackLook(background, art, notes, frets)` com `TrackLook.DEFAULT`;
  - `Settings` ganha `highwayBackground`, `highwayArt`, `noteStyle`, `fretStyle`, `showChartImage = true` e a propriedade `val trackLook: TrackLook`; `askSeparationAlways` passa a valer `true` por padrão.

- [ ] **Step 1: Escrever os testes que falham**

`SettingsTest.kt`, acrescentar:
```kotlin
    @Test
    fun theLookOfTodayAndTheMapImageAreTheDefaults() {
        val settings = Settings.DEFAULT

        assertEquals(TrackLook.DEFAULT, settings.trackLook)
        assertEquals(
            TrackLook(HighwayBackground.STAGE, HighwayArt.NONE, NoteStyle.DISCS, FretStyle.DISCS),
            TrackLook.DEFAULT,
        )
        assertTrue(settings.showChartImage)
        assertTrue(settings.askSeparationAlways)
    }
```
`RiffPreferencesTest.kt`, acrescentar:
```kotlin
    @Test
    fun theTrackLookIsSavedByName() = runTest {
        val preferences = RiffPreferences(InMemoryPreferencesStore())

        preferences.updateSettings {
            it.copy(
                highwayBackground = HighwayBackground.NEON,
                highwayArt = HighwayArt.WINGED_SKULL,
                noteStyle = NoteStyle.SKULLS,
                fretStyle = FretStyle.SPEAKERS,
                showChartImage = false,
            )
        }

        val settings = preferences.settings.first()
        assertEquals(TrackLook(HighwayBackground.NEON, HighwayArt.WINGED_SKULL, NoteStyle.SKULLS, FretStyle.SPEAKERS), settings.trackLook)
        assertEquals(false, settings.showChartImage)
    }

    @Test
    fun anOptionThatNoLongerExistsFallsBackToTheDefault() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit { it[stringPreferencesKey("highway_background")] = "DISCO_BALL" }

        assertEquals(HighwayBackground.STAGE, RiffPreferences(store).settings.first().highwayBackground)
    }

    @Test
    fun askingAlwaysIsOnEvenIfTheOldPreferenceWasSavedOff() = runTest {
        val store = InMemoryPreferencesStore()
        store.edit { it[booleanPreferencesKey("ask_separation_always")] = false }

        assertEquals(true, RiffPreferences(store).settings.first().askSeparationAlways)
    }
```
O teste existente `askingAlwaysToSeparateStartsOffAndCanBeTurnedOn` vira `askingAlwaysToSeparateStartsOnAndCanBeTurnedOff`: espera `true` no começo e `false` depois de `updateSettings { it.copy(askSeparationAlways = false) }`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:model:test :core:datastore:testDebugUnitTest --console=plain`
Expected: FAIL na compilação ("Unresolved reference: TrackLook").

- [ ] **Step 3: Implementar**

`TrackLook.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

/** Superfície da pista (temas de rock desenhados em código). STAGE é a pista de sempre. */
enum class HighwayBackground { STAGE, AMPLIFIER, LEATHER, FRETBOARD, FLAMES, NEON, DIAMOND_PLATE }

/** Desenho claro estampado na pista, por baixo das notas. */
enum class HighwayArt { NONE, WINGED_SKULL, BOLTS, STARS, GUITARS, PICKS, TATTOO }

/** Forma das notas que descem (as cores das trilhas não mudam). */
enum class NoteStyle { DISCS, PICKS, SKULLS, STARS, BOLTS, FLAMES }

/** Forma dos cinco botões da linha de acerto. */
enum class FretStyle { DISCS, PEDALS, KNOBS, SPEAKERS, PICKS }

/** O visual escolhido da pista; [DEFAULT] é o de sempre. */
data class TrackLook(
    val background: HighwayBackground,
    val art: HighwayArt,
    val notes: NoteStyle,
    val frets: FretStyle,
) {
    companion object {
        val DEFAULT = TrackLook(HighwayBackground.STAGE, HighwayArt.NONE, NoteStyle.DISCS, FretStyle.DISCS)
    }
}
```
`Settings.kt`: os campos novos depois de `askSeparationAlways` (que passa a `= true`):
```kotlin
    val highwayBackground: HighwayBackground = HighwayBackground.STAGE,
    val highwayArt: HighwayArt = HighwayArt.NONE,
    val noteStyle: NoteStyle = NoteStyle.DISCS,
    val fretStyle: FretStyle = FretStyle.DISCS,
    /** Mostrar a capa ou a imagem de fundo do mapa atrás da pista. */
    val showChartImage: Boolean = true,
```
e, no corpo da classe:
```kotlin
    val trackLook: TrackLook get() = TrackLook(highwayBackground, highwayArt, noteStyle, fretStyle)
```
`RiffPreferences.kt`:
- em `updateSettings`, gravar:
```kotlin
            prefs[HIGHWAY_BACKGROUND] = updated.highwayBackground.name
            prefs[HIGHWAY_ART] = updated.highwayArt.name
            prefs[NOTE_STYLE] = updated.noteStyle.name
            prefs[FRET_STYLE] = updated.fretStyle.name
            prefs[SHOW_CHART_IMAGE] = updated.showChartImage
            prefs.remove(LEGACY_ASK_SEPARATION_ALWAYS)
```
- em `toSettings()`, ler:
```kotlin
            highwayBackground = enumOr(this[HIGHWAY_BACKGROUND], default.highwayBackground),
            highwayArt = enumOr(this[HIGHWAY_ART], default.highwayArt),
            noteStyle = enumOr(this[NOTE_STYLE], default.noteStyle),
            fretStyle = enumOr(this[FRET_STYLE], default.fretStyle),
            showChartImage = this[SHOW_CHART_IMAGE] ?: default.showChartImage,
```
- a chave de perguntar vira `booleanPreferencesKey("ask_separation_always_v2")`, com `LEGACY_ASK_SEPARATION_ALWAYS = booleanPreferencesKey("ask_separation_always")` só para ser removida;
- as chaves novas `stringPreferencesKey("highway_background")`, `"highway_art"`, `"note_style"`, `"fret_style"` e `booleanPreferencesKey("show_chart_image")`;
- o auxiliar privado:
```kotlin
/** Nome gravado de uma opção; um nome que não existe mais (versão antiga ou futura) volta ao padrão. */
private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:model:test :core:datastore:testDebugUnitTest testDebugUnitTest detekt --console=plain`
Expected: PASS. Testes de outros módulos que esperavam `askSeparationAlways = false` por padrão, se houver, passam a pedir `false` explicitamente; anotar o Ruling.

- [ ] **Step 5: Commit** — `feat: preferências do visual da pista, imagem do mapa e perguntar sempre ligado`

---

### Task 2: Imagem do mapa (`ChartArtLoader`)

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/game/ChartArtLoader.kt`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/DataModule.kt` (`@Binds` no `FolderBindsModule`)
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/game/ChartArtLoaderTest.kt`

**Interfaces:**
- Produces: `interface ChartArtLoader { suspend fun load(chartId: String): ByteArray? }`; `class FolderChartArtLoader @Inject constructor(source: FolderSource, charts: ChartRepository, @IoDispatcher io)`; `object ChartArtFinder { fun find(files: List<FolderEntry>): FolderEntry? }`.

- [ ] **Step 1: Escrever o teste que falha**
```kotlin
package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.importer.FakeChartRepository
import com.rodrigomachado.riff.core.data.importer.FakeFolderSource
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartArtLoaderTest {
    private val source = FakeFolderSource()
    private val charts = FakeChartRepository()

    private suspend fun chart(folder: String) = charts.saveScanned(
        Chart("id", "M", "B", null, null, null, folder, ChartFormat.CHART, setOf(Difficulty.EXPERT)),
        scanId = 1,
    )

    private fun TestScope.loader() = FolderChartArtLoader(source, charts, StandardTestDispatcher(testScheduler))

    @Test
    fun theBackgroundWinsOverTheAlbumCover() = runTest {
        source.file("raiz/M/album.jpg", "capa")
        source.file("raiz/M/Background.PNG", "fundo")
        chart("raiz/M")

        assertEquals("fundo", loader().load("id")?.decodeToString())
    }

    @Test
    fun withoutBackgroundTheAlbumCoverIsUsed() = runTest {
        source.file("raiz/M/album.jpeg", "capa")
        chart("raiz/M")

        assertEquals("capa", loader().load("id")?.decodeToString())
    }

    @Test
    fun noImageOrNoChartGivesNothing() = runTest {
        source.file("raiz/M/song.ogg", "música")
        chart("raiz/M")

        assertNull(loader().load("id"))
        assertNull(loader().load("outro"))
    }

    @Test
    fun anImageTooLargeIsSkipped() = runTest {
        source.file("raiz/M/background.jpg", ByteArray(9 * 1024 * 1024))
        chart("raiz/M")

        assertNull(loader().load("id"))
    }
}
```
(Para `.sng`, o `FolderSource` do app já lista os arquivos do pacote como uma pasta; o `FolderChartTrackLoaderTest` cobre essa leitura.)

- [ ] **Step 2: Rodar e ver falhar** — `./scripts/gw :core:data:testDebugUnitTest --tests '*ChartArtLoader*' --console=plain` → "Unresolved reference: FolderChartArtLoader".

- [ ] **Step 3: Implementar**
```kotlin
package com.rodrigomachado.riff.core.data.game

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.IoDispatcher
import com.rodrigomachado.riff.core.data.folder.FileTooLargeException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** A imagem do mapa (fundo, senão capa), em bytes, para mostrar atrás da pista. */
interface ChartArtLoader {
    suspend fun load(chartId: String): ByteArray?
}

/** `background.*` antes de `album.*`; só jpg, jpeg e png. */
object ChartArtFinder {
    private val EXTENSIONS = setOf("jpg", "jpeg", "png")

    fun find(files: List<FolderEntry>): FolderEntry? {
        fun named(base: String) = files.firstOrNull {
            !it.isDirectory &&
                it.name.substringBeforeLast('.').equals(base, ignoreCase = true) &&
                it.name.substringAfterLast('.', "").lowercase() in EXTENSIONS
        }
        return named("background") ?: named("album")
    }
}

class FolderChartArtLoader @Inject constructor(
    private val source: FolderSource,
    private val charts: ChartRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ChartArtLoader {
    @Suppress("SwallowedException") // sem imagem, a pista fica com o fundo de sempre
    override suspend fun load(chartId: String): ByteArray? = withContext(io) {
        val chart = charts.getChart(chartId) ?: return@withContext null
        try {
            val entry = ChartArtFinder.find(source.listChildren(chart.folderUri)) ?: return@withContext null
            source.read(entry, MAX_BYTES)
        } catch (e: FileTooLargeException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    private companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
```
Se `listChildren` lançar `FolderAccessLostException` (um `Exception`, não `IOException`), capturar também e devolver `null`.

- [ ] **Step 4: Rodar e ver passar** — `./scripts/gw :core:data:testDebugUnitTest detekt --console=plain` → PASS.
- [ ] **Step 5: Commit** — `feat: ler a imagem do mapa (fundo, senão capa)`

---

### Task 3: Texturas da pista (`HighwayTexture`)

**Files:**
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/HighwayTexture.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/RockShapes.kt` (formas em `android.graphics.Path`, tamanho 1)
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/HighwayTextureTest.kt`

**Interfaces:**
- Produces:
  - `object HighwayTexture` com `const val WIDTH = 256`, `const val DEPTH = 512`, `const val TILE_DISTANCE = 2f`, `fun background(kind: HighwayBackground): Bitmap?` (nulo para STAGE) e `fun art(kind: HighwayArt): Bitmap?` (nulo para NONE, transparente fora das estampas);
  - `data class EdgeColors(val outer: Color, val inner: Color)` e `fun edgeColors(kind: HighwayBackground): EdgeColors`;
  - `object RockShapes` com funções `pick(path)`, `star(path, points, inner)`, `bolt(path)`, `flame(path)` que preenchem um `android.graphics.Path` em tamanho 1, centrado em (0,0), e `skull(canvas, paint, cx, cy, s, holes: Paint)`.

- [ ] **Step 1: Escrever o teste que falha**
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import android.graphics.Bitmap
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class HighwayTextureTest {
    @Test
    fun theStageOfTodayAndNoArtNeedNoTexture() {
        assertNull(HighwayTexture.background(HighwayBackground.STAGE))
        assertNull(HighwayTexture.art(HighwayArt.NONE))
    }

    @Test
    fun everyOtherBackgroundIsAnOpaqueTileOfTheRightSize() {
        for (kind in HighwayBackground.entries - HighwayBackground.STAGE) {
            val tile = assertNotNull(HighwayTexture.background(kind))!!
            assertEquals(kind.name, HighwayTexture.WIDTH, tile.width)
            assertEquals(kind.name, HighwayTexture.DEPTH, tile.height)
            assertEquals(kind.name, 255, tile.getPixel(tile.width / 2, tile.height / 2) ushr 24)
        }
    }

    @Test
    fun artIsTransparentAroundTheStamps() {
        for (kind in HighwayArt.entries - HighwayArt.NONE) {
            val tile = assertNotNull(HighwayTexture.art(kind))!!
            val alphas = IntArray(tile.width * tile.height).also { tile.getPixels(it, 0, tile.width, 0, 0, tile.width, tile.height) }
                .map { it ushr 24 }
            assertTrue(kind.name, alphas.any { it == 0 }) // há área livre
            assertTrue(kind.name, alphas.any { it in 1..120 }) // e estampa clara (não opaca)
        }
    }

    @Test
    fun tilesAreTheSameEveryTime() {
        val a = HighwayTexture.background(HighwayBackground.LEATHER)!!
        val b = HighwayTexture.background(HighwayBackground.LEATHER)!!
        assertTrue(a.sameAs(b))
    }

    @Test
    fun theTileRepeatsWithoutAVisibleSeam() {
        for (kind in HighwayBackground.entries - HighwayBackground.STAGE) {
            val tile = HighwayTexture.background(kind)!!
            val difference = (0 until tile.width).map { x ->
                channelDistance(tile.getPixel(x, 0), tile.getPixel(x, tile.height - 1))
            }.average()
            // A linha de cima continua a de baixo: sem degrau na emenda.
            assertTrue("${kind.name}: $difference", difference < 40)
        }
    }

    private fun channelDistance(a: Int, b: Int): Int =
        (0..2).sumOf { abs(((a shr (it * 8)) and 0xFF) - ((b shr (it * 8)) and 0xFF)) } / 3
}
```

- [ ] **Step 2: Rodar e ver falhar** — `./scripts/gw :core:ui:testDebugUnitTest --tests '*HighwayTexture*' --console=plain` → "Unresolved reference: HighwayTexture".

- [ ] **Step 3: Implementar**

`RockShapes.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Formas de rock em tamanho 1 (de −1 a 1), centradas em (0, 0), com y para baixo. */
object RockShapes {
    /** Palheta com a ponta para baixo. */
    fun pick(path: Path) {
        path.rewind()
        path.moveTo(0f, 1.15f)
        path.cubicTo(-0.55f, 0.75f, -1f, 0.1f, -0.95f, -0.45f)
        path.cubicTo(-0.9f, -0.95f, 0.9f, -0.95f, 0.95f, -0.45f)
        path.cubicTo(1f, 0.1f, 0.55f, 0.75f, 0f, 1.15f)
        path.close()
    }

    fun star(path: Path, points: Int = 5, inner: Float = 0.45f) {
        path.rewind()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) 1f else inner
            val a = -PI / 2 + i * PI / points
            val x = (r * cos(a)).toFloat()
            val y = (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    fun bolt(path: Path) {
        val pts = floatArrayOf(0.15f, -1f, -0.45f, 0.1f, -0.02f, 0.1f, -0.2f, 1f, 0.5f, -0.15f, 0.05f, -0.15f, 0.3f, -1f)
        path.rewind()
        path.moveTo(pts[0], pts[1])
        for (i in 2 until pts.size step 2) path.lineTo(pts[i], pts[i + 1])
        path.close()
    }

    /** Língua de fogo com a base em y = 1. */
    fun flame(path: Path) {
        path.rewind()
        path.moveTo(-1f, 1f)
        path.cubicTo(-1f, 0.2f, -0.6f, -0.1f, -0.45f, -0.7f)
        path.cubicTo(-0.25f, -0.2f, -0.1f, -0.3f, 0f, -1f)
        path.cubicTo(0.15f, -0.4f, 0.35f, -0.3f, 0.5f, -0.75f)
        path.cubicTo(0.65f, -0.1f, 1f, 0.2f, 1f, 1f)
        path.close()
    }

    /** Caveira de frente: crânio, mandíbula, olhos, nariz e dentes vazados com [holes]. */
    fun skull(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float, holes: Paint) {
        canvas.drawOval(RectF(cx - s, cy - s, cx + s, cy + s * 0.75f), paint)
        canvas.drawRoundRect(RectF(cx - s * 0.55f, cy + s * 0.4f, cx + s * 0.55f, cy + s * 1.05f), s * 0.15f, s * 0.15f, paint)
        for (dx in floatArrayOf(-0.4f, 0.4f)) {
            canvas.drawOval(RectF(cx + dx * s - s * 0.27f, cy - s * 0.05f, cx + dx * s + s * 0.27f, cy + s * 0.42f), holes)
        }
        val nose = Path().apply {
            moveTo(cx, cy + s * 0.42f)
            lineTo(cx - s * 0.12f, cy + s * 0.62f)
            lineTo(cx + s * 0.12f, cy + s * 0.62f)
            close()
        }
        canvas.drawPath(nose, holes)
        val teeth = Paint(holes).apply { strokeWidth = max(1f, s * 0.06f) }
        for (dx in floatArrayOf(-0.3f, -0.1f, 0.1f, 0.3f)) {
            canvas.drawLine(cx + dx * s, cy + s * 0.75f, cx + dx * s, cy + s * 1.02f, teeth)
        }
    }
}
```
(`skull` aloca formas, mas só é chamada na geração das texturas e das miniaturas, fora do laço de quadros; as notas Caveiras usam a versão Compose do Task 5.)

`HighwayTexture.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import kotlin.random.Random

/** Cores da moldura da pista (aro de fora e filete de dentro) para cada fundo. */
data class EdgeColors(val outer: Color, val inner: Color)

fun edgeColors(kind: HighwayBackground): EdgeColors = when (kind) {
    HighwayBackground.STAGE -> EdgeColors(RiffColors.TourRed, RiffColors.Paper)
    HighwayBackground.AMPLIFIER -> EdgeColors(Color(0xFFC8AA5A), Color(0xFF282828))
    HighwayBackground.LEATHER -> EdgeColors(Color(0xFF96969B), Color(0xFF3C2314))
    HighwayBackground.FRETBOARD -> EdgeColors(Color(0xFFE6E1D2), Color(0xFF28190F))
    HighwayBackground.FLAMES -> EdgeColors(Color(0xFFFF7800), Color(0xFF1E0A05))
    HighwayBackground.NEON -> EdgeColors(Color(0xFFFF28C8), Color(0xFF28DCFF))
    HighwayBackground.DIAMOND_PLATE -> EdgeColors(Color(0xFFC8C8CD), Color(0xFF3C3C41))
}

/**
 * Ladrilhos da pista, [WIDTH] × [DEPTH] px, que se repetem na profundidade a cada [TILE_DISTANCE] (unidade da
 * `HighwayGeometry`). Gerados uma vez por escolha, com semente fixa: o mesmo desenho toda vez. Tudo em código,
 * nada do Guitar Hero.
 */
object HighwayTexture {
    const val WIDTH = 256
    const val DEPTH = 512
    const val TILE_DISTANCE = 2f
    private const val SEED = 7

    fun background(kind: HighwayBackground): Bitmap? {
        if (kind == HighwayBackground.STAGE) return null
        val bitmap = Bitmap.createBitmap(WIDTH, DEPTH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val random = Random(SEED)
        when (kind) {
            HighwayBackground.AMPLIFIER -> amplifier(canvas, random)
            HighwayBackground.LEATHER -> leather(canvas, random)
            HighwayBackground.FRETBOARD -> fretboard(canvas, random)
            HighwayBackground.FLAMES -> flames(canvas, random)
            HighwayBackground.NEON -> neon(canvas, random)
            HighwayBackground.DIAMOND_PLATE -> diamondPlate(canvas, random)
            HighwayBackground.STAGE -> Unit
        }
        return bitmap
    }

    fun art(kind: HighwayArt): Bitmap? {
        if (kind == HighwayArt.NONE) return null
        val bitmap = Bitmap.createBitmap(WIDTH, DEPTH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = STAMP }
        val holes = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        stamps(kind, canvas, ink, holes)
        return bitmap
    }

    // ---- fundos (todos repetem na profundidade: o que passa da borda de baixo volta em cima) ----

    private fun speckle(canvas: Canvas, random: Random, base: Int, spread: Int, size: Float) {
        canvas.drawColor(base)
        val paint = Paint()
        repeat(WIDTH * DEPTH / 40) {
            val v = random.nextInt(-spread, spread + 1)
            paint.color = shift(base, v)
            val x = random.nextFloat() * WIDTH
            val y = random.nextFloat() * DEPTH
            canvas.drawRect(x, y, x + size, y + size, paint)
        }
    }

    private fun amplifier(canvas: Canvas, random: Random) {
        speckle(canvas, random, 0xFF1E1E20.toInt(), 14, 2f)
        val gold = Paint().apply { color = 0xFF78643C.toInt(); strokeWidth = 2f }
        val dim = Paint().apply { color = 0xFF5A4B2D.toInt(); strokeWidth = 1f }
        for (y in 0 until DEPTH step 16) canvas.drawLine(0f, y.toFloat(), WIDTH.toFloat(), y.toFloat(), gold)
        for (x in 0 until WIDTH step 16) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), DEPTH.toFloat(), dim)
    }

    private fun leather(canvas: Canvas, random: Random) {
        speckle(canvas, random, 0xFF5A3218.toInt(), 18, 3f)
        val blotch = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL) }
        repeat(14) {
            blotch.color = if (random.nextBoolean()) 0x30000000 else 0x20FFDCA0
            val x = random.nextFloat() * WIDTH
            val y = random.nextFloat() * DEPTH
            for (dy in intArrayOf(-DEPTH, 0, DEPTH)) canvas.drawCircle(x, y + dy, 30f + random.nextFloat() * 30f, blotch)
        }
        val rivet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBEBEC3.toInt() }
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAFAFA.toInt() }
        for (y in 32 until DEPTH step 64) {
            for (x in floatArrayOf(14f, WIDTH - 14f)) {
                canvas.drawCircle(x, y.toFloat(), 8f, rivet)
                canvas.drawCircle(x - 2.5f, y - 3f, 3f, shine)
            }
        }
    }

    private fun fretboard(canvas: Canvas, random: Random) {
        canvas.drawColor(0xFF3E2414.toInt())
        val grain = Paint()
        repeat(90) {
            grain.color = if (random.nextBoolean()) 0x22000000 else 0x18FFC896
            grain.strokeWidth = 1f + random.nextFloat() * 2f
            val x = random.nextFloat() * WIDTH
            canvas.drawLine(x, 0f, x + random.nextFloat() * 6f - 3f, DEPTH.toFloat(), grain)
        }
        val fret = Paint().apply { color = 0xFFC8C8CD.toInt() }
        val inlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEBE8DE.toInt() }
        for (i in 0 until 4) {
            val y = i * DEPTH / 4f
            canvas.drawRect(0f, y, WIDTH.toFloat(), y + 5f, fret)
            if (i % 2 == 1) canvas.drawCircle(WIDTH / 2f, y + DEPTH / 8f, 11f, inlay)
        }
    }

    private fun flames(canvas: Canvas, random: Random) {
        canvas.drawColor(0xFF0E0A0A.toInt())
        val path = Path()
        RockShapes.flame(path)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL) }
        val colors = intArrayOf(0xFFC81E0A.toInt(), 0xFFFF7800.toInt(), 0xFFFFD23C.toInt())
        val m = Matrix()
        for (side in 0..1) {
            for (k in 0 until 5) {
                val h = 70f + random.nextFloat() * 60f
                val w = 24f + random.nextFloat() * 16f
                val baseY = k * DEPTH / 5f + DEPTH / 10f
                val x = if (side == 0) w * 0.6f else WIDTH - w * 0.6f
                for ((layer, color) in colors.withIndex()) {
                    val f = 1f - layer * 0.28f
                    glow.color = color
                    for (dy in intArrayOf(-DEPTH, 0, DEPTH)) {
                        val copy = Path(path)
                        m.reset()
                        m.postScale(w * f, h * f / 2f)
                        m.postTranslate(x, baseY + dy)
                        copy.transform(m)
                        canvas.drawPath(copy, glow)
                    }
                }
            }
        }
    }

    private fun neon(canvas: Canvas, random: Random) {
        canvas.drawColor(0xFF1E0E36.toInt())
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
        repeat(4) { k ->
            val path = Path()
            var x = 40f + random.nextFloat() * (WIDTH - 80f)
            var y = random.nextFloat() * DEPTH
            path.moveTo(x, y)
            repeat(5) {
                x = (x + (if (random.nextBoolean()) 1 else -1) * (15f + random.nextFloat() * 30f)).coerceIn(10f, WIDTH - 10f)
                y += 30f + random.nextFloat() * 40f
                path.lineTo(x, y)
            }
            val color = if (k % 2 == 0) 0xFFFF28C8.toInt() else 0xFF28DCFF.toInt()
            glow.color = color
            core.color = 0xFFFFFFFF.toInt()
            for (dy in intArrayOf(-DEPTH, 0)) {
                val shifted = Path(path).apply { offset(0f, dy.toFloat()) }
                canvas.drawPath(shifted, glow)
                canvas.drawPath(shifted, core)
            }
        }
    }

    private fun diamondPlate(canvas: Canvas, random: Random) {
        speckle(canvas, random, 0xFF6E6E72.toInt(), 12, 1f)
        val raised = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA5A5AA.toInt() }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF46464A.toInt() }
        val step = 32
        for (row in 0 until DEPTH / step) {
            for (col in -1..WIDTH / step) {
                val cx = col * step + if (row % 2 == 1) step / 2f else 0f
                val cy = row * step + step / 2f
                canvas.drawPath(diamond(cx + 1.5f, cy + 1.5f), shadow)
                canvas.drawPath(diamond(cx, cy), raised)
            }
        }
    }

    private fun diamond(cx: Float, cy: Float) = Path().apply {
        moveTo(cx - 11f, cy + 3f)
        lineTo(cx + 8f, cy - 8f)
        lineTo(cx + 11f, cy - 3f)
        lineTo(cx - 8f, cy + 8f)
        close()
    }

    // ---- estampas (claras, sobre fundo transparente) ----

    private val STAMP = 0x3CEADCBA // papel com ~24 % de opacidade

    private fun stamps(kind: HighwayArt, canvas: Canvas, ink: Paint, holes: Paint) {
        val cx = WIDTH / 2f
        when (kind) {
            HighwayArt.WINGED_SKULL -> {
                wings(canvas, ink, cx, DEPTH / 2f, 40f)
                RockShapes.skull(canvas, ink, cx, DEPTH / 2f, 40f, holes)
            }
            HighwayArt.BOLTS -> {
                shape(canvas, ink, cx - 60f, DEPTH * 0.3f, 50f) { RockShapes.bolt(it) }
                shape(canvas, ink, cx + 60f, DEPTH * 0.78f, 50f) { RockShapes.bolt(it) }
            }
            HighwayArt.STARS -> for ((dx, dy, s) in listOf(Triple(-70f, 0.15f, 26f), Triple(10f, 0.4f, 40f), Triple(75f, 0.62f, 24f), Triple(-40f, 0.82f, 20f))) {
                shape(canvas, ink, cx + dx, DEPTH * dy, s) { RockShapes.star(it) }
            }
            HighwayArt.GUITARS -> {
                guitar(canvas, ink, holes, cx - 60f, DEPTH * 0.32f, 34f)
                guitar(canvas, ink, holes, cx + 60f, DEPTH * 0.8f, 34f)
            }
            HighwayArt.PICKS -> for ((dx, dy) in listOf(-60f to 0.22f, 60f to 0.55f, -10f to 0.86f)) {
                shape(canvas, ink, cx + dx, DEPTH * dy, 40f) { RockShapes.pick(it) }
                shape(canvas, holes, cx + dx, DEPTH * dy, 20f) { RockShapes.bolt(it) }
            }
            HighwayArt.TATTOO -> tattoo(canvas, ink, cx, DEPTH / 2f, 46f)
            HighwayArt.NONE -> Unit
        }
    }

    private fun shape(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float, build: (Path) -> Unit) {
        val path = Path().also(build)
        path.transform(Matrix().apply { postScale(s, s); postTranslate(cx, cy) })
        canvas.drawPath(path, paint)
    }

    private fun wings(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float) {
        for (side in intArrayOf(-1, 1)) {
            for (k in 0 until 4) {
                val y = cy - s * 0.3f + k * s * 0.28f
                val x0 = cx + side * s
                canvas.drawPath(
                    Path().apply {
                        moveTo(x0, y)
                        lineTo(x0 + side * s * (2.2f - k * 0.35f), y - s * 0.35f)
                        lineTo(x0 + side * s * (2.0f - k * 0.35f), y + s * 0.12f)
                        close()
                    },
                    paint,
                )
            }
        }
    }

    private fun guitar(canvas: Canvas, paint: Paint, holes: Paint, cx: Float, cy: Float, s: Float) {
        canvas.drawOval(RectF(cx - s * 0.75f, cy + s * 0.15f, cx + s * 0.75f, cy + s * 1.4f), paint)
        canvas.drawOval(RectF(cx - s * 0.55f, cy - s * 0.45f, cx + s * 0.55f, cy + s * 0.6f), paint)
        canvas.drawRect(cx - s * 0.12f, cy - s * 1.7f, cx + s * 0.12f, cy, paint)
        canvas.drawRect(cx - s * 0.25f, cy - s * 2.2f, cx + s * 0.25f, cy - s * 1.7f, paint)
        canvas.drawOval(RectF(cx - s * 0.2f, cy + s * 0.45f, cx + s * 0.2f, cy + s * 0.85f), holes)
    }

    private fun tattoo(canvas: Canvas, paint: Paint, cx: Float, cy: Float, s: Float) {
        canvas.drawOval(RectF(cx - s, cy - s * 0.6f, cx, cy + s * 0.3f), paint)
        canvas.drawOval(RectF(cx, cy - s * 0.6f, cx + s, cy + s * 0.3f), paint)
        canvas.drawPath(
            Path().apply {
                moveTo(cx - s * 0.97f, cy - s * 0.05f)
                lineTo(cx + s * 0.97f, cy - s * 0.05f)
                lineTo(cx, cy + s * 1.1f)
                close()
            },
            paint,
        )
        val blade = Paint(paint).apply { strokeWidth = s * 0.16f; strokeCap = Paint.Cap.ROUND }
        canvas.drawLine(cx - s * 1.6f, cy + s * 0.9f, cx + s * 1.5f, cy - s * 1.05f, blade)
    }

    private fun shift(color: Int, by: Int): Int {
        fun ch(shiftBits: Int) = (((color shr shiftBits) and 0xFF) + by).coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
```
O `speckle`, as linhas e os desenhos com `dy` em −DEPTH/0/DEPTH garantem a repetição sem emenda. Se o teste da emenda acusar algum fundo, corrigir o gerador desse fundo (desenhar a parte que passa da borda também do outro lado). Se o detekt reclamar de `TooManyFunctions` ou `LongMethod`, separar as estampas em `HighwayStamps.kt` e anotar o Ruling.

- [ ] **Step 4: Rodar e ver passar** — `./scripts/gw :core:ui:testDebugUnitTest detekt --console=plain` → PASS.
- [ ] **Step 5: Commit** — `feat: texturas de rock da pista (fundos e estampas) geradas em código`

---

### Task 4: Pista com fundo e estampa rolando

**Files:**
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StageCache.kt`, `StageDrawing.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/StageRenderer.kt` (rolagem), `GameplayScreen.kt` (`stageCache.look = state.settings.trackLook`)
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/StageScreenshotTest.kt` (novos), `StageCacheTest.kt`

**Interfaces:**
- Consumes: `HighwayTexture`, `edgeColors`, `TrackLook`.
- Produces: `StageCache.look: TrackLook` (var; trocar refaz as texturas na próxima `update`) e `StageCache.scrollDistance: Float` (var; distância percorrida pela música, `songTimeMs / msPerUnit`).

- [ ] **Step 1: Escrever os testes que falham**

`StageCacheTest.kt`:
```kotlin
    @Test
    fun changingTheLookRebuildsTheTexturesOnlyThen() {
        val cache = StageCache()
        cache.update(Size(400f, 800f), 1f)
        assertNull(cache.backgroundShaderForTest)

        cache.look = TrackLook.DEFAULT.copy(background = HighwayBackground.NEON)
        cache.update(Size(400f, 800f), 1f)
        val first = cache.backgroundShaderForTest
        cache.update(Size(400f, 800f), 1f)

        assertNotNull(first)
        assertSame(first, cache.backgroundShaderForTest)
    }
```
(`backgroundShaderForTest` é `internal val … get() = backgroundShader`.)

`StageScreenshotTest.kt`, uma imagem com as 7 pistas lado a lado e outra com as 7 estampas:
```kotlin
    @Test
    fun everyBackground() = lookSheet("Stage_backgrounds", HighwayBackground.entries.map { TrackLook.DEFAULT.copy(background = it) })

    @Test
    fun everyArt() = lookSheet("Stage_art", HighwayArt.entries.map { TrackLook.DEFAULT.copy(art = it) })

    private fun lookSheet(name: String, looks: List<TrackLook>) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme {
                Row(Modifier.fillMaxSize()) {
                    looks.forEach { look ->
                        Canvas(Modifier.weight(1f).fillMaxSize()) {
                            val cache = StageCache()
                            cache.look = look
                            cache.scrollDistance = 0.7f
                            cache.update(size, density)
                            drawHighway(cache)
                        }
                    }
                }
            }
        }
    }
```
(`@Config` desta classe: `qualifiers = "pt-rBR-w720dp-h400dp-xhdpi"` só nestes dois testes, com `@Config(qualifiers = …)` no método.)

- [ ] **Step 2: Rodar e ver falhar** — `./scripts/gw :core:ui:testDebugUnitTest --console=plain` → "Unresolved reference: look".

- [ ] **Step 3: Implementar**

`StageCache.kt`:
```kotlin
    /** O visual escolhido; trocar refaz as texturas na próxima [update]. */
    var look: TrackLook = TrackLook.DEFAULT
        set(value) {
            if (value != field) texturesFor = null
            field = value
        }

    /** Quanto a música andou, em unidades de distância: a textura rola junto com os trastes. */
    var scrollDistance = 0f

    private var texturesFor: TrackLook? = null
    internal var backgroundShader: BitmapShader? = null
        private set
    internal var artShader: BitmapShader? = null
        private set
    internal val backgroundShaderForTest get() = backgroundShader
    internal var edges = edgeColors(HighwayBackground.STAGE)
        private set
    internal val texturePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
    internal val textureMatrix = android.graphics.Matrix()
    internal val androidOutline: android.graphics.Path get() = outline.asAndroidPath()
    internal var fade: Brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
        private set
    private val polySrc = FloatArray(8)
    private val polyDst = FloatArray(8)

    private fun rebuildTexturesIfNeeded() {
        if (texturesFor == look) return
        texturesFor = look
        backgroundShader = HighwayTexture.background(look.background)?.let { shader(it) }
        artShader = HighwayTexture.art(look.art)?.let { shader(it) }
        edges = edgeColors(look.background)
    }

    private fun shader(bitmap: Bitmap) = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT)

    /**
     * Matriz do ladrilho para a pista em perspectiva, com a rolagem: o retângulo do ladrilho (largura toda; da
     * borda de baixo da pista ao fundo) vai para o trapézio. `setPolyToPoly` com 4 pontos é exata para um plano.
     */
    internal fun updateTextureMatrix() {
        val g = geometry
        val near = g.nearDistance
        val far = g.visibleDistance
        val perUnit = HighwayTexture.DEPTH / HighwayTexture.TILE_DISTANCE
        val vNear = -(near + scrollDistance) * perUnit
        val vFar = -(far + scrollDistance) * perUnit
        val w = HighwayTexture.WIDTH.toFloat()
        polySrc[0] = 0f; polySrc[1] = vNear; polySrc[2] = w; polySrc[3] = vNear
        polySrc[4] = w; polySrc[5] = vFar; polySrc[6] = 0f; polySrc[7] = vFar
        polyDst[0] = g.leftAt(near); polyDst[1] = g.yAt(near)
        polyDst[2] = g.leftAt(near) + g.widthAt(near); polyDst[3] = g.yAt(near)
        polyDst[4] = g.leftAt(far) + g.widthAt(far); polyDst[5] = g.yAt(far)
        polyDst[6] = g.leftAt(far); polyDst[7] = g.yAt(far)
        textureMatrix.setPolyToPoly(polySrc, 0, polyDst, 0, 4)
        backgroundShader?.setLocalMatrix(textureMatrix)
        artShader?.setLocalMatrix(textureMatrix)
    }
```
Em `update`, chamar `rebuildTexturesIfNeeded()` sempre (antes do retorno antecipado) e calcular `fade = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent, startY = g.yAt(far), endY = g.yAt(near))`.

`StageDrawing.kt`, `drawHighway`:
```kotlin
fun DrawScope.drawHighway(cache: StageCache) {
    val geometry = cache.geometry
    val far = geometry.visibleDistance
    val near = geometry.nearDistance
    cache.updateTextureMatrix()
    val background = cache.backgroundShader
    if (background == null) {
        drawStageSurface(cache) // o de sempre: degradê e trama
    } else {
        drawTexture(cache, background)
        drawPath(cache.outline, cache.fade)
    }
    cache.artShader?.let { drawTexture(cache, it) }
    val line = RiffColors.Paper.copy(alpha = LINE_ALPHA)
    for (lane in 1 until geometry.laneCount) {
        along(geometry, lane.toFloat() / geometry.laneCount, line, cache.laneLineWidth)
    }
    for (i in 0 until cache.fretCount) {
        val fret = cache.fretDistances[i]
        if (fret in near..far) across(geometry, fret, line, cache.fretLineWidth)
    }
    drawPath(cache.outline, cache.edges.outer, style = cache.outerStroke)
    drawPath(cache.outline, cache.edges.inner, style = cache.innerStroke)
}

/** A superfície de sempre (Palco): degradê escuro e trama fina. */
private fun DrawScope.drawStageSurface(cache: StageCache) {
    val geometry = cache.geometry
    drawPath(cache.outline, cache.highwayBrush)
    val weave = RiffColors.Paper.copy(alpha = WEAVE_ALPHA)
    for (k in 0..WEAVE_LONGITUDINAL) along(geometry, k.toFloat() / WEAVE_LONGITUDINAL, weave, 1f)
    var distance = geometry.nearDistance
    while (distance < geometry.visibleDistance) {
        across(geometry, distance, weave, 1f)
        distance += WEAVE_STEP
    }
}

private fun DrawScope.drawTexture(cache: StageCache, shader: android.graphics.Shader) {
    cache.texturePaint.shader = shader
    drawIntoCanvas { it.nativeCanvas.drawPath(cache.androidOutline, cache.texturePaint) }
}
```
`StageRenderer.kt`, em `drawStage`, antes de `drawHighway(cache)`: `cache.scrollDistance = t / msPerUnit`. Com `t` em ms, a textura anda uma unidade de distância a cada `msPerUnit` ms, na velocidade das notas.

`GameplayScreen.kt`: dentro do `Canvas`, antes de `drawStage`: `stageCache.look = state.settings.trackLook`. A atribuição é barata quando o visual não muda.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:ui:testDebugUnitTest :core:ui:recordRoborazziDebug :feature:gameplay:testDebugUnitTest detekt --console=plain`, depois `git status --short '*screenshots*'`.
Expected:
- PASS;
- só aparecem `Stage_backgrounds.png` e `Stage_art.png` (novos); `Stage.png`, `Stage_closeup.png` e `Gameplay_stage*.png` não mudam (Review Focus 1).

Conferir as imagens novas: as sete pistas e as sete estampas legíveis, em perspectiva.

- [ ] **Step 5: Commit** — `feat: fundo e estampa da pista rolando com a música`

---

### Task 5: Design das notas e dos botões

**Files:**
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StyledPieces.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StageDrawing.kt` (`drawGem`/`drawFretButton` despacham pelo estilo do `cache.look`)
- Modify: `StageCache.kt` (formas Compose em tamanho 1: `pickPath`, `starPath`, `boltPath`, `flamePath`, `starInnerPath`)
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/StageScreenshotTest.kt`, `StageDrawingTest.kt`

**Interfaces:**
- Consumes: `NoteStyle`, `FretStyle`, `StageCache.look`.
- Produces: `drawGem(...)` e `drawFretButton(...)` com a mesma assinatura de hoje; o estilo vem de `cache.look`.

- [ ] **Step 1: Escrever os testes que falham**

`StageScreenshotTest.kt`:
```kotlin
    @Test
    @Config(qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
    fun everyNoteAndButtonStyle() {
        captureRoboImage("src/test/screenshots/Stage_pieces.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val rows = NoteStyle.entries.size + FretStyle.entries.size
                        val rowHeight = size.height / rows
                        val cache = StageCache()
                        cache.update(size, density)
                        val r = size.width / 14f
                        NoteStyle.entries.forEachIndexed { row, style ->
                            cache.look = TrackLook.DEFAULT.copy(notes = style)
                            for (lane in 0 until 5) {
                                val center = Offset(size.width * (lane + 1) / 6f, rowHeight * (row + 0.6f))
                                drawGem(cache, center, r, lane, missed = lane == 3, hopo = lane == 1)
                            }
                        }
                        FretStyle.entries.forEachIndexed { i, style ->
                            cache.look = TrackLook.DEFAULT.copy(frets = style)
                            val row = NoteStyle.entries.size + i
                            for (lane in 0 until 5) {
                                val center = Offset(size.width * (lane + 1) / 6f, rowHeight * (row + 0.5f))
                                drawFretButton(cache, center, r, lane, pressed = lane == 2)
                            }
                        }
                    }
                }
            }
        }
    }
```
Em cada linha: a trilha 3 aparece perdida (esmaecida), a 1 é HOPO (centro branco) e o botão 2 aparece pressionado (Review Focus 5).

`StageDrawingTest.kt` (o arquivo já desenha num bitmap com `GraphicsMode.NATIVE`):
```kotlin
    @Test
    fun aMissedNoteIsFainterInEveryStyle() {
        for (style in NoteStyle.entries) {
            val cache = StageCache().apply { look = TrackLook.DEFAULT.copy(notes = style) }
            val normal = renderGemAlpha(cache, missed = false)
            val missed = renderGemAlpha(cache, missed = true)
            assertTrue("$style: $missed < $normal", missed < normal)
        }
    }
```
`renderGemAlpha` desenha uma nota numa `ImageBitmap` 100×100 transparente, com `CanvasDrawScope`, e soma o alfa dos pixels. Se o arquivo já tiver um auxiliar de bitmap, reaproveitar.

- [ ] **Step 2: Rodar e ver falhar** — `./scripts/gw :core:ui:testDebugUnitTest --console=plain` → os estilos ainda saem como discos (o teste do alfa passa; o screenshot novo é gravado no passo 4). O RED desta tarefa é a compilação de `StyledPieces` referenciada no `drawGem`: escrever primeiro a chamada `when (cache.look.notes)` no `drawGem` e ver falhar por "Unresolved reference: drawPickGem".

- [ ] **Step 3: Implementar**

`StageCache.kt`, formas Compose prontas (uma vez):
```kotlin
    internal val pickPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(0f, 1.15f)
        cubicTo(-0.55f, 0.75f, -1f, 0.1f, -0.95f, -0.45f)
        cubicTo(-0.9f, -0.95f, 0.9f, -0.95f, 0.95f, -0.45f)
        cubicTo(1f, 0.1f, 0.55f, 0.75f, 0f, 1.15f)
        close()
    }
    internal val starPath = starOf(inner = 0.45f)
    internal val boltPath = androidx.compose.ui.graphics.Path().apply {
        val p = floatArrayOf(0.15f, -1f, -0.45f, 0.1f, -0.02f, 0.1f, -0.2f, 1f, 0.5f, -0.15f, 0.05f, -0.15f, 0.3f, -1f)
        moveTo(p[0], p[1])
        for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
        close()
    }
    internal val flamePath = androidx.compose.ui.graphics.Path().apply {
        moveTo(-1f, 1f)
        cubicTo(-1f, 0.2f, -0.6f, -0.1f, -0.45f, -0.7f)
        cubicTo(-0.25f, -0.2f, -0.1f, -0.3f, 0f, -1f)
        cubicTo(0.15f, -0.4f, 0.35f, -0.3f, 0.5f, -0.75f)
        cubicTo(0.65f, -0.1f, 1f, 0.2f, 1f, 1f)
        close()
    }
```
com `private fun starOf(inner: Float)` gerando 10 pontos como em `RockShapes.star`.

`StyledPieces.kt`: cada estilo de nota é desenhado em tamanho 1 (já transladado e escalado pelo `drawGem`), "em pé" na pista (de frente para o jogador), com sombra, cor e brilho:
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp

private val SHADOW = Color.Black.copy(alpha = 0.55f)

/** Forma pronta com sombra deslocada, a cor e um brilho menor por cima. */
private fun DrawScope.solid(path: Path, color: Color, highlight: Float = 0.38f) {
    translate(0.06f, 0.1f) { drawPath(path, SHADOW) }
    drawPath(path, color)
    scale(highlight, highlight, Offset(0f, -0.1f)) { drawPath(path, lerp(color, Color.White, 0.55f)) }
}

internal fun DrawScope.drawPickGem(cache: StageCache, color: Color) = solid(cache.pickPath, color)

internal fun DrawScope.drawStarGem(cache: StageCache, color: Color) = solid(cache.starPath, color, 0.45f)

internal fun DrawScope.drawBoltGem(cache: StageCache, color: Color) {
    translate(0.08f, 0.08f) { drawPath(cache.boltPath, SHADOW) }
    drawPath(cache.boltPath, color)
}

internal fun DrawScope.drawFlameGem(cache: StageCache, color: Color) {
    drawPath(cache.flamePath, lerp(color, Color.Black, 0.45f))
    scale(0.78f, 0.78f, Offset(0f, 1f)) { drawPath(cache.flamePath, color) }
    scale(0.45f, 0.45f, Offset(0f, 1f)) { drawPath(cache.flamePath, lerp(color, Color.White, 0.6f)) }
}

internal fun DrawScope.drawSkullGem(color: Color) {
    val bone = lerp(color, Color.White, 0.15f)
    val dark = lerp(color, Color.Black, 0.75f)
    drawOval(SHADOW, Offset(-0.95f, -0.82f), Size(1.9f, 1.62f))
    drawOval(bone, Offset(-1f, -0.95f), Size(2f, 1.65f))
    drawRoundRect(bone, Offset(-0.55f, 0.35f), Size(1.1f, 0.6f), androidx.compose.ui.geometry.CornerRadius(0.15f))
    drawOval(dark, Offset(-0.67f, -0.1f), Size(0.54f, 0.47f))
    drawOval(dark, Offset(0.13f, -0.1f), Size(0.54f, 0.47f))
    for (dx in floatArrayOf(-0.3f, -0.1f, 0.1f, 0.3f)) drawLine(dark, Offset(dx, 0.7f), Offset(dx, 0.95f), 0.06f)
}
```
Botões (deitados na linha de acerto, achatados por `TILT`; aceso = brilho na cor da trilha):
```kotlin
internal fun DrawScope.drawPedalButton(color: Color, pressed: Boolean) {
    scale(1f, TILT, Offset.Zero) {
        drawRoundRect(SHADOW, Offset(-0.9f, -0.8f), Size(1.8f, 1.8f), androidx.compose.ui.geometry.CornerRadius(0.2f))
        drawRoundRect(color, Offset(-0.9f, -0.9f), Size(1.8f, 1.8f), androidx.compose.ui.geometry.CornerRadius(0.2f))
        drawCircle(Color(0xFFC8C8CD), 0.45f, Offset.Zero)
        drawCircle(if (pressed) Color.White else Color(0xFFEFEFF3), 0.3f, Offset.Zero)
        drawCircle(if (pressed) Color(0xFFFF3030) else Color(0xFF5A1010), 0.11f, Offset(0.6f, -0.68f))
    }
}

internal fun DrawScope.drawKnobButton(color: Color, pressed: Boolean) {
    scale(1f, TILT, Offset.Zero) {
        for (k in 0..10) {
            val a = Math.toRadians(135.0 + k * 27).toFloat()
            drawLine(Color(0xFFEADCBA), Offset(kotlin.math.cos(a) * 0.82f, kotlin.math.sin(a) * 0.82f), Offset(kotlin.math.cos(a) * 1f, kotlin.math.sin(a) * 1f), 0.06f)
        }
        drawCircle(Color(0xFF1E1E1E), 0.7f, Offset.Zero)
        drawCircle(if (pressed) lerp(color, Color.White, 0.35f) else color, 0.56f, Offset.Zero)
        drawLine(Color.White, Offset.Zero, Offset(0.42f, -0.3f), 0.09f)
    }
}

internal fun DrawScope.drawSpeakerButton(color: Color, pressed: Boolean) {
    scale(1f, TILT, Offset.Zero) {
        drawCircle(Color(0xFF282828), 1f, Offset.Zero)
        drawCircle(lerp(color, Color.Black, if (pressed) 0.1f else 0.45f), 0.85f, Offset.Zero)
        drawCircle(color, 0.65f, Offset.Zero, style = Stroke(0.06f))
        drawCircle(color, 0.45f, Offset.Zero, style = Stroke(0.06f))
        drawCircle(if (pressed) Color.White else color, 0.22f, Offset.Zero)
    }
}

internal fun DrawScope.drawPickButton(cache: StageCache, color: Color, pressed: Boolean) {
    scale(1f, TILT, Offset.Zero) {
        drawPath(cache.pickPath, Color(0xFFE6E6E6))
        scale(0.8f, 0.8f, Offset.Zero) { drawPath(cache.pickPath, color) }
        scale(0.42f, 0.42f, Offset.Zero) { drawPath(cache.pickPath, if (pressed) Color.White else Color(0xFF0F0F0F)) }
    }
}
```
`drawGem`: depois de calcular `color`, despachar:
```kotlin
    if (cache.look.notes != NoteStyle.DISCS) {
        withTransform({
            translate(center.x, center.y - radius * 0.4f)
            scale(radius * 0.85f, radius * 0.85f, Offset.Zero)
        }) {
            when (cache.look.notes) {
                NoteStyle.PICKS -> drawPickGem(cache, color)
                NoteStyle.SKULLS -> drawSkullGem(color)
                NoteStyle.STARS -> drawStarGem(cache, color)
                NoteStyle.BOLTS -> drawBoltGem(cache, color)
                NoteStyle.FLAMES -> drawFlameGem(cache, color)
                NoteStyle.DISCS -> Unit
            }
            if (hopo) drawCircle(Color.White.copy(alpha = 0.9f), 0.22f, Offset.Zero)
        }
        return
    }
```
Com DISCS, o corpo de hoje continua igual.

`drawFretButton`: o brilho de pressionado (`if (pressed) oval(color.copy(alpha = 0.35f), …)`) vale para todos os estilos. Depois dele:
```kotlin
        when (cache.look.frets) {
            FretStyle.PEDALS -> drawPedalButton(color, pressed)
            FretStyle.KNOBS -> drawKnobButton(color, pressed)
            FretStyle.SPEAKERS -> drawSpeakerButton(color, pressed)
            FretStyle.PICKS -> drawPickButton(cache, color, pressed)
            FretStyle.DISCS -> { /* as quatro linhas de hoje */ }
        }
```
Os desenhos em tamanho 1 usam traços com `strokeWidth` relativo (0.06). Se o detekt reclamar de complexidade em `drawGem` ou `drawFretButton`, mover o `when` para `drawStyledGem` ou `drawStyledButton` em `StyledPieces.kt`.

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw :core:ui:testDebugUnitTest :core:ui:recordRoborazziDebug detekt --console=plain`, depois `git status --short '*screenshots*'`.
Expected:
- PASS;
- só `Stage_pieces.png` aparece como novo; os screenshots de hoje não mudam.

Conferir a imagem: cores das trilhas certas, nota perdida esmaecida e HOPO com centro branco em todos os estilos.

- [ ] **Step 5: Commit** — `feat: design das notas e dos botões (palhetas, caveiras, estrelas, raios, chamas, pedais, knobs, alto-falantes)`

---

### Task 6: Imagem do mapa na partida

**Files:**
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModel.kt` (injeta `ChartArtLoader`; `Ready.art: ImageBitmap?`)
- Create: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/ChartArtDecoder.kt`
- Modify: `GameplayScreen.kt` (imagem + véu atrás do `Canvas`)
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/ChartArtDecoderTest.kt`, `GameplayViewModelTest.kt`, `GameplayScreenshotTest.kt`

**Interfaces:**
- Consumes: `ChartArtLoader` (Task 2), `Settings.showChartImage` (Task 1).
- Produces: `object ChartArtDecoder { fun decode(bytes: ByteArray, maxSide: Int = 1280): ImageBitmap? }`; `GameplayUiState.Ready.art: ImageBitmap? = null`.

- [ ] **Step 1: Escrever os testes que falham**

`ChartArtDecoderTest.kt` (Robolectric, `GraphicsMode.NATIVE`):
```kotlin
    @Test
    fun aBigImageIsDecodedSmall() {
        val big = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().also { big.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val image = ChartArtDecoder.decode(bytes, maxSide = 1280)!!

        assertTrue("${image.width}x${image.height}", image.width <= 2000 && image.height <= 2000)
    }

    @Test
    fun garbageIsIgnored() {
        assertNull(ChartArtDecoder.decode(byteArrayOf(1, 2, 3)))
    }
```
`GameplayViewModelTest.kt` (o ViewModel ganha o `FakeArtLoader` dos testes):
```kotlin
class FakeArtLoader(var bytes: ByteArray? = null) : ChartArtLoader {
    override suspend fun load(chartId: String) = bytes
}

    @Test
    fun withTheOptionOnTheMapImageComesWithTheMatch() = runTest {
        art.bytes = pngBytes(64, 64)
        val vm = viewModel()
        advanceUntilIdle()

        assertNotNull((vm.uiState.value as GameplayUiState.Ready).art)
    }

    @Test
    fun withTheOptionOffThereIsNoImage() = runTest {
        art.bytes = pngBytes(64, 64)
        settings.settings.value = Settings.DEFAULT.copy(showChartImage = false)
        val vm = viewModel()
        advanceUntilIdle()

        assertNull((vm.uiState.value as GameplayUiState.Ready).art)
    }
```
`pngBytes` comprime um `Bitmap` vazio. Se o teste do ViewModel não rodar com Robolectric, anotar a classe com `@RunWith(RobolectricTestRunner::class)` e `@GraphicsMode(NATIVE)` e registrar o Ruling. Os nomes `settings` e `viewModel()` seguem os do arquivo; adaptar e anotar o Ruling se forem outros.

`GameplayScreenshotTest.kt`: `Gameplay_stage_art.png` com `art` sendo uma imagem gerada no teste (um degradê colorido), para ver o véu e a imagem atrás da pista.

- [ ] **Step 2: Rodar e ver falhar** — `./scripts/gw :feature:gameplay:testDebugUnitTest --console=plain` → "Unresolved reference: ChartArtDecoder".

- [ ] **Step 3: Implementar**

`ChartArtDecoder.kt`:
```kotlin
package com.rodrigomachado.riff.feature.gameplay

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** A imagem do mapa, já reduzida (no máximo ~2× [maxSide] no lado maior): fundo da pista, não foto. */
object ChartArtDecoder {
    fun decode(bytes: ByteArray, maxSide: Int = 1280): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}
```
`GameplayViewModel`: o construtor ganha `private val art: ChartArtLoader`. Ao montar o `Ready`:
```kotlin
            art = if (settings.showChartImage) art.load(chartId)?.let(ChartArtDecoder::decode) else null,
```
A decodificação roda dentro da corrotina de preparo, que já roda fora do laço de quadros.

`GameplayScreen.kt`: dentro do `Box`, antes do `Canvas`:
```kotlin
        state.art?.let { image ->
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ART_VEIL)))
        }
```
com `private const val ART_VEIL = 0.65f`.

- [ ] **Step 4: Rodar e ver passar** — `./scripts/gw :feature:gameplay:testDebugUnitTest :feature:gameplay:recordRoborazziDebug detekt --console=plain` → PASS; `Gameplay_stage_art.png` novo, os outros sem mudança.
- [ ] **Step 5: Commit** — `feat: imagem do mapa atrás da pista`

---

### Task 7: Configurações

**Files:**
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StagePreviews.kt`
- Create: `feature/settings/src/main/kotlin/com/rodrigomachado/riff/feature/settings/TrackLookSection.kt`
- Modify: `SettingsScreen.kt`, `SettingsViewModel.kt`, `AiSeparationSection.kt` (novo nome do interruptor), strings (`values` e `values-en`)
- Test: `feature/settings/src/test/kotlin/com/rodrigomachado/riff/feature/settings/SettingsContentTest.kt`, `SettingsViewModelTest.kt`, `SettingsScreenshotTest.kt`

**Interfaces:**
- Consumes: `drawHighway`, `drawGem`, `drawFretButton`, `StageCache`, `TrackLook`.
- Produces:
  - `:core:ui`: `@Composable fun HighwayPreview(look: TrackLook, modifier: Modifier)`, `@Composable fun NotesPreview(style: NoteStyle, modifier: Modifier)` e `@Composable fun FretsPreview(style: FretStyle, modifier: Modifier)`;
  - `SettingsViewModel`: `setHighwayBackground`, `setHighwayArt`, `setNoteStyle`, `setFretStyle` e `setShowChartImage`;
  - `SettingsContent`: `onTrackLook: (TrackLook) -> Unit = {}` e `onShowChartImage: (Boolean) -> Unit = {}`.

- [ ] **Step 1: Escrever os testes que falham**

`SettingsContentTest.kt`:
```kotlin
    @Test
    fun choosingABackgroundOpensTheListAndSavesIt() {
        val looks = mutableListOf<TrackLook>()
        showLook(onTrackLook = { looks += it })

        composeRule.onNodeWithText("Fundo da pista").performScrollTo().performClick()
        composeRule.onNodeWithText("Neon").performClick()

        assertEquals(listOf(TrackLook.DEFAULT.copy(background = HighwayBackground.NEON)), looks)
        assertEquals(listOf(MenuSound.NAVIGATE, MenuSound.NAVIGATE), sounds)
    }

    @Test
    fun theFourChoicesShowTheCurrentNames() {
        showLook(settings = Settings.DEFAULT.copy(noteStyle = NoteStyle.SKULLS, fretStyle = FretStyle.KNOBS))

        composeRule.onNodeWithText("Palco").performScrollTo().assertExists()
        composeRule.onNodeWithText("Sem desenho").performScrollTo().assertExists()
        composeRule.onNodeWithText("Caveiras").performScrollTo().assertExists()
        composeRule.onNodeWithText("Knobs de amplificador").performScrollTo().assertExists()
    }

    @Test
    fun theMapImageAndAskAlwaysSwitchesAreThere() {
        val events = mutableListOf<String>()
        showLook(onShowChartImage = { events += "image:$it" })

        composeRule.onNodeWithText("Imagem do mapa").performScrollTo().assertExists()
        composeRule.onNodeWithTag(SettingsTestTags.CHART_IMAGE).performScrollTo().performClick()
        composeRule.onNodeWithText("Perguntar sempre se separa").performScrollTo().assertExists()

        assertEquals(listOf("image:false"), events)
    }
```
`showLook(settings = Settings.DEFAULT, onTrackLook = {}, onShowChartImage = {})` é um auxiliar como o `show` existente, que repassa esses parâmetros ao `SettingsContent`. Os testes que procuravam "Perguntar de novo" passam a procurar "Perguntar sempre se separa".

`SettingsViewModelTest.kt`:
```kotlin
    @Test
    fun theLookChoicesAreSaved() = runTest {
        val vm = viewModel()

        vm.setHighwayBackground(HighwayBackground.FLAMES)
        vm.setHighwayArt(HighwayArt.STARS)
        vm.setNoteStyle(NoteStyle.PICKS)
        vm.setFretStyle(FretStyle.PEDALS)
        vm.setShowChartImage(false)
        advanceUntilIdle()

        val saved = repository.settings.value
        assertEquals(TrackLook(HighwayBackground.FLAMES, HighwayArt.STARS, NoteStyle.PICKS, FretStyle.PEDALS), saved.trackLook)
        assertEquals(false, saved.showChartImage)
    }
```

- [ ] **Step 2: Rodar e ver falhar** — `./scripts/gw :feature:settings:testDebugUnitTest --console=plain` → falha de compilação (`onTrackLook`, `setHighwayBackground`).

- [ ] **Step 3: Implementar**

`StagePreviews.kt` (`:core:ui`):
```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.TrackLook

/** Miniatura da pista com o visual dado (as Configurações mostram o que vai aparecer no jogo). */
@Composable
fun HighwayPreview(look: TrackLook, modifier: Modifier = Modifier) {
    val cache = remember(look) { StageCache().also { it.look = look; it.scrollDistance = 0.7f } }
    Canvas(modifier) {
        val geometry = cache.update(size, density)
        drawHighway(cache)
        val r = geometry.laneWidthAtStrike * 0.42f
        for ((lane, d) in listOf(1 to 2.4f, 3 to 1.4f)) drawGem(cache, geometry.laneCenter(lane, d), r * geometry.scaleAt(d), lane)
        for (lane in 0 until 5) drawFretButton(cache, geometry.laneCenter(lane, 0f), r, lane, pressed = false)
    }
}

@Composable
fun NotesPreview(style: NoteStyle, modifier: Modifier = Modifier) {
    val cache = remember(style) { StageCache().also { it.look = TrackLook.DEFAULT.copy(notes = style) } }
    Canvas(modifier) {
        val r = size.height * 0.32f
        for (lane in 0 until 5) drawGem(cache, Offset(size.width * (lane + 0.5f) / 5f, size.height * 0.62f), r, lane)
    }
}

@Composable
fun FretsPreview(style: FretStyle, modifier: Modifier = Modifier) {
    val cache = remember(style) { StageCache().also { it.look = TrackLook.DEFAULT.copy(frets = style) } }
    Canvas(modifier) {
        val r = size.height * 0.4f
        for (lane in 0 until 5) drawFretButton(cache, Offset(size.width * (lane + 0.5f) / 5f, size.height / 2f), r, lane, pressed = false)
    }
}
```
(As notas fora de uma pista precisam do `StageCache` só pelas formas e pincéis; `update` não é chamado nelas.)

`TrackLookSection.kt` (`:feature:settings`):
```kotlin
/** As quatro escolhas do visual da pista, cada uma abrindo a lista com miniaturas. */
@Composable
internal fun TrackLookSection(look: TrackLook, onLook: (TrackLook) -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    ChoiceRow(stringResource(R.string.settings_highway_background), backgroundName(look.background)) { open = "background" }
    ChoiceRow(stringResource(R.string.settings_highway_art), artName(look.art)) { open = "art" }
    ChoiceRow(stringResource(R.string.settings_note_style), noteName(look.notes)) { open = "notes" }
    ChoiceRow(stringResource(R.string.settings_fret_style), fretName(look.frets)) { open = "frets" }
    val close = { open = null }
    when (open) {
        "background" -> ChoiceDialog(stringResource(R.string.settings_highway_background), HighwayBackground.entries, look.background, { backgroundName(it) }, { HighwayPreview(look.copy(background = it), Modifier.size(56.dp, 96.dp)) }, close) { onLook(look.copy(background = it)) }
        "art" -> ChoiceDialog(stringResource(R.string.settings_highway_art), HighwayArt.entries, look.art, { artName(it) }, { HighwayPreview(look.copy(art = it), Modifier.size(56.dp, 96.dp)) }, close) { onLook(look.copy(art = it)) }
        "notes" -> ChoiceDialog(stringResource(R.string.settings_note_style), NoteStyle.entries, look.notes, { noteName(it) }, { NotesPreview(it, Modifier.size(150.dp, 40.dp)) }, close) { onLook(look.copy(notes = it)) }
        "frets" -> ChoiceDialog(stringResource(R.string.settings_fret_style), FretStyle.entries, look.frets, { fretName(it) }, { FretsPreview(it, Modifier.size(150.dp, 40.dp)) }, close) { onLook(look.copy(frets = it)) }
    }
}

@Composable
private fun ChoiceRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = withMenuSound(MenuSound.NAVIGATE, onClick)).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = RiffColors.PaperDim)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    name: @Composable (T) -> String,
    preview: @Composable (T) -> Unit,
    onClose: () -> Unit,
    onChoose: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(options) { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = withMenuSound(MenuSound.NAVIGATE) { onChoose(option); onClose() }),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        preview(option)
                        Text(name(option))
                    }
                }
            }
        },
        confirmButton = {},
    )
}
```
Os nomes (`backgroundName`, `artName`, `noteName` e `fretName`) são `@Composable` com `when` para `stringResource` das strings da seção Global Constraints, por exemplo `settings_bg_stage` = "Palco" e `settings_bg_amplifier` = "Amplificador"; em inglês: "Stage", "Amplifier", "Leather and rivets", "Fretboard", "Flames", "Neon", "Diamond plate", "No art", "Winged skull", "Bolts", "Stars", "Guitars", "Picks", "Tattoo", "Discs", "Skulls", "Effect pedals", "Amp knobs", "Speakers".

`SettingsScreen.kt`:
- na seção Jogo, depois do modo canhoto, entram `TrackLookSection(settings.trackLook, onTrackLook)` e um `SwitchRow` "Imagem do mapa" (`tag = SettingsTestTags.CHART_IMAGE = "settings_chart_image"`, dica "Mostra a capa ou a imagem de fundo do mapa atrás da pista.") ligado a `onShowChartImage`;
- `SettingsScreen` liga `onTrackLook` aos quatro setters, que ficam num só no ViewModel:
```kotlin
    fun setTrackLook(look: TrackLook) = save {
        it.copy(highwayBackground = look.background, highwayArt = look.art, noteStyle = look.notes, fretStyle = look.frets)
    }
```
mais `setHighwayBackground(b) = setTrackLook(currentLook().copy(background = b))` e os análogos, que o teste do ViewModel usa;
- `fun setShowChartImage(show: Boolean) = save { it.copy(showChartImage = show) }`;
- `AiSeparationSection`: o título usa a string `settings_ai_ask_again` com o texto novo "Perguntar sempre se separa" (em inglês, "Always ask to separate").

O `SettingsScreenshotTest` muda: a lista ficou mais longa. Regravar e conferir que só a parte de baixo mudou.

- [ ] **Step 4: Rodar e ver passar** — `./scripts/gw :feature:settings:testDebugUnitTest :feature:settings:recordRoborazziDebug detekt lint --console=plain` → PASS. Se `SettingsViewModel` passar de 11 funções, a supressão `TooManyFunctions` que já existe cobre; senão, atualizar o comentário dela.
- [ ] **Step 5: Commit** — `feat: Configurações com fundo, design da pista, das notas e dos botões e imagem do mapa`

---

### Task 8: README, desempenho, aparelho

- [ ] **Step 1: README.** Em "Jogar", acrescentar os quatro visuais e a imagem do mapa. Em "Configurações", listar as novas opções. Na seção da guitarra por IA, trocar o nome do interruptor por "Perguntar sempre se separa" (ligado de fábrica).
- [ ] **Step 2: suíte completa** + `scripts/conferir_r8_spotify.sh`; instalar o release.
- [ ] **Step 3: no aparelho.**
  - Passar por cada fundo, cada design de pista, de notas e de botões numa partida: a pista rola com a música, sem emenda visível, e as notas e botões ficam legíveis.
  - Ligar a combinação mais pesada (Chamas + Caveira alada + Caveiras + Alto-falantes + imagem do mapa) e rodar `scripts/desempenho.sh medir`. Meta: 120 fps e poucos quadros atrasados, como hoje; anotar os números.
  - Conferir a imagem do mapa com a From Zero (tem `background.jpg`; baixar de novo pela Pesquisa se preciso) e com um mapa só com capa.
- [ ] **Step 4: Baseline Profile:** regerar (`scripts/desempenho.sh perfil`) e reinstalar o release.
- [ ] **Step 5: Commit** — `docs: README do visual da pista`
