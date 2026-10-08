# Riff Dados e Biblioteca: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** persistir mapas, casamentos, recordes e preferências; importar uma pasta de mapas do Clone Hero escolhida pelo seletor do Android; ter o algoritmo de casamento entre faixa do Spotify e mapa; e mostrar a tela da Biblioteca no visual "Pôster de turnê".

**Architecture:** quatro módulos novos. `:core:database` (Room) e `:core:datastore` (DataStore de preferências) guardam os dados. `:core:data` junta tudo: repositório de mapas, acesso a pastas por SAF atrás de uma interface (`FolderSource`) e o importador, que é testado com uma árvore de pastas em memória. `:feature:library` tem a tela e o ViewModel. O casamento faixa↔mapa é Kotlin puro em `:core:chart`. O Hilt entra neste plano para ligar tudo.

**Tech Stack:** Room 2.8.5, DataStore Preferences 1.2.1, DocumentFile 1.1.0, Hilt 2.60.1 com KSP 2.3.12, Lifecycle 2.11.0, androidx.hilt 1.4.0, Coroutines 1.11.0, Turbine 1.2.1, Robolectric 4.17, Roborazzi 1.76.0.

**Spec:** `docs/superpowers/specs/2026-10-05-riff-design.md` (seções 4, 5, 7 e 9) e `docs/superpowers/specs/2026-10-07-identidade-visual-design.md` (visual). Este plano cobre o passo 4 da seção 11 da especificação principal.

## Global Constraints

- Pacote base `com.rodrigomachado.riff`; namespace de cada módulo = pacote base + caminho do módulo com pontos (`com.rodrigomachado.riff.core.data`, `com.rodrigomachado.riff.feature.library`).
- Dependências permitidas (spec §4): `feature → core:data, core:ui, core:model`; `core:data → core:database, core:datastore, core:chart, core:model`. Uma feature nunca depende de outra feature.
- Casamento (spec §5): normalização de título e artista (minúsculas, sem acento, sem `(feat. …)`, `- Remastered …`, `- Live …` e pontuação); pontuação = `0,6 × sim(título) + 0,4 × sim(artista)` com Jaro-Winkler; duração diferente em mais de 5 s tira 0,1; ≥ 0,92 é automático, de 0,75 a 0,92 pede confirmação, abaixo de 0,75 não há casamento.
- Room (spec §5): tabelas `charts`, `chart_difficulties`, `track_matches` (chave `spotifyTrackUri`) e `scores`. `Chart.id` é o SHA-1 do arquivo de notas. As notas não vão para o banco.
- Erros (spec §7): um mapa inválido não interrompe a importação das outras pastas; o relatório diz quantos foram importados e quais falharam, e por quê. Perder o acesso à pasta vira um estado de erro com ação para escolher de novo, nunca um crash.
- Visual: tela no estilo "Pôster de turnê" com os componentes do `:core:ui` (`StageBackground`, `PosterTitle`, `PosterButton`, `TrackCard`, `StatusBadge`, `EmptyState`, `ErrorState`, `LoadingState`). Texto visível em resources, `values/` em pt-BR e `values-en/`.
- Testes de lógica Android (ViewModel, importador, repositórios) usam JUnit 4, porque os módulos Android não rodam JUnit 5; `:core:chart` continua em JUnit 5.
- Testes Robolectric: SDK 35 fixado por `src/test/resources/robolectric.properties` em cada módulo que usar Robolectric.
- No WSL, o Gradle roda por `./scripts/gw <tasks>`. Antes de cada commit: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt`.
- Todo commit termina com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Decisões deste plano (não estavam na especificação)

- **A Biblioteca vira a tela inicial** e o catálogo do design system sai do app. A navegação type-safe entra no plano seguinte, quando houver uma segunda tela.
- **O casamento manual** (escolher o mapa de uma faixa) depende de saber a faixa do Spotify, então a tela dele vai para o plano do Spotify. Aqui entram o algoritmo e a tabela `track_matches`.
- **Reimportar remove da biblioteca os mapas cujas pastas sumiram.** Cada importação tem um `scanId`; ao final, o que não foi visto nessa importação é apagado. Assim não há limite de quantidade (a alternativa `NOT IN (lista de ids)` quebra acima de 999 mapas no SQLite).
- **Proteções do importador:** arquivo de notas maior que 16 MB é recusado sem ser lido, e a busca por subpastas para em 8 níveis.

## Review Focus

1. **Biblioteca com milhares de mapas:** reimportar apaga só os que sumiram, sem estourar o limite de variáveis do SQLite. Teste na Task 3.
2. **Pasta que perdeu a permissão ou foi apagada entre uma sessão e outra:** a tela mostra o erro com ação de escolher de novo, a biblioteca existente continua lá, nada quebra. Testes nas Tasks 6 e 7.
3. **Arquivo enorme ou lixo com nome `notes.chart`:** vira uma falha no relatório, sem estourar a memória. Teste na Task 6.
4. **Preferência corrompida** (volume 5,0, dificuldade com nome que não existe, velocidade negativa): o app lê os valores padrão em vez de quebrar. Teste na Task 4.
5. **Reimportar a mesma pasta:** nada duplica, e pastas removidas somem da lista. Teste na Task 6.

---

### Task 1: Build: Hilt, KSP, Room e convention plugins novos

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/AndroidHiltConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/AndroidRoomConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/AndroidFeatureConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/Libs.kt`

**Interfaces:**
- Produces: plugins `riff.android.hilt` (KSP + Hilt + `hilt-android`), `riff.android.room` (plugin do Room, KSP, `room-runtime`, `room-ktx`, esquemas em `schemas/`) e `riff.android.feature` (library compose + hilt + dependências comuns de feature e de teste). Aliases no catálogo: `libs.plugins.ksp`, `libs.plugins.hilt`, `libs.plugins.room`, `libs.plugins.riff.android.hilt`, `libs.plugins.riff.android.room`, `libs.plugins.riff.android.feature`.

- [ ] **Step 1: Catálogo**

Em `gradle/libs.versions.toml`, acrescente em `[versions]`:
```toml
room = "2.8.5"
hilt = "2.60.1"
ksp = "2.3.12"
datastore = "1.2.1"
documentfile = "1.1.0"
lifecycle = "2.11.0"
androidxHilt = "1.4.0"
coroutines = "1.11.0"
turbine = "1.2.1"
androidxTestCore = "1.7.0"
```
Em `[libraries]`:
```toml
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-documentfile = { module = "androidx.documentfile:documentfile", version.ref = "documentfile" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-hilt-lifecycle-viewmodel-compose = { module = "androidx.hilt:hilt-lifecycle-viewmodel-compose", version.ref = "androidxHilt" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
ksp-gradlePlugin = { module = "com.google.devtools.ksp:symbol-processing-gradle-plugin", version.ref = "ksp" }
hilt-gradlePlugin = { module = "com.google.dagger:hilt-android-gradle-plugin", version.ref = "hilt" }
room-gradlePlugin = { module = "androidx.room:room-gradle-plugin", version.ref = "room" }
```
Em `[plugins]`:
```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
room = { id = "androidx.room", version.ref = "room" }
riff-android-hilt = { id = "riff.android.hilt" }
riff-android-room = { id = "riff.android.room" }
riff-android-feature = { id = "riff.android.feature" }
```

- [ ] **Step 2: build-logic**

`build-logic/convention/build.gradle.kts`: no bloco `dependencies`, acrescente
```kotlin
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
```
e, em `gradlePlugin { plugins { ... } }`:
```kotlin
        register("androidHilt") {
            id = "riff.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "riff.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidFeature") {
            id = "riff.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
```

`build-logic/convention/src/main/kotlin/Libs.kt`:
```kotlin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal fun Project.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    extensions.getByType<VersionCatalogsExtension>().named("libs").findLibrary(alias).get()
```

`AndroidHiltConventionPlugin.kt`:
```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
            dependencies {
                add("implementation", lib("hilt-android"))
                add("ksp", lib("hilt-compiler"))
            }
        }
    }
}
```

`AndroidRoomConventionPlugin.kt`:
```kotlin
import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<RoomExtension> { schemaDirectory("$projectDir/schemas") }
            dependencies {
                add("implementation", lib("androidx-room-runtime"))
                add("implementation", lib("androidx-room-ktx"))
                add("ksp", lib("androidx-room-compiler"))
            }
        }
    }
}
```

`AndroidFeatureConventionPlugin.kt`:
```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("riff.android.library.compose")
            pluginManager.apply("riff.android.hilt")
            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:data"))
                add("implementation", lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", lib("androidx-lifecycle-runtime-compose"))
                add("implementation", lib("androidx-hilt-lifecycle-viewmodel-compose"))
                add("testImplementation", lib("junit4"))
                add("testImplementation", lib("kotlinx-coroutines-test"))
                add("testImplementation", lib("turbine"))
                add("testImplementation", lib("robolectric"))
                add("testImplementation", lib("roborazzi"))
                add("testImplementation", lib("roborazzi-compose"))
                add("testImplementation", lib("androidx-compose-ui-test-junit4"))
                add("debugImplementation", lib("androidx-compose-ui-test-manifest"))
            }
        }
    }
}
```

- [ ] **Step 3: Raiz**

`build.gradle.kts`: no bloco `plugins`, acrescente
```kotlin
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
```

- [ ] **Step 4: Build**

Run: `./scripts/gw help` e depois `./scripts/gw :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` nos dois (o build-logic compila com os plugins novos; nenhum módulo os usa ainda).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "build: Hilt, KSP, Room e convention plugins de hilt, room e feature" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Casamento entre faixa e mapa (`:core:chart`)

**Files:**
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/match/TextNormalizer.kt`
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/match/JaroWinkler.kt`
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/match/TrackMatcher.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/match/TextNormalizerTest.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/match/JaroWinklerTest.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/match/TrackMatcherTest.kt`

**Interfaces:**
- Consumes: `Chart` (`:core:model`).
- Produces (pacote `com.rodrigomachado.riff.core.chart.match`):
  - `object TextNormalizer { fun normalize(text: String): String }`
  - `object JaroWinkler { fun similarity(a: String, b: String): Double }`
  - `data class TrackQuery(val title: String, val artist: String, val durationMs: Long?)`
  - `enum class MatchKind { AUTOMATIC, NEEDS_CONFIRMATION }`
  - `data class MatchCandidate(val chart: Chart, val confidence: Float, val kind: MatchKind)`
  - `object TrackMatcher { const val AUTOMATIC_THRESHOLD = 0.92f; const val SUGGESTION_THRESHOLD = 0.75f; fun score(query: TrackQuery, chart: Chart): Float; fun bestMatch(query: TrackQuery, charts: List<Chart>): MatchCandidate? }`

- [ ] **Step 1: Testes que falham**

`TextNormalizerTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart.match

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextNormalizerTest {
    private fun n(text: String) = TextNormalizer.normalize(text)

    @Test
    fun removesRemasterAndLiveSuffixes() {
        assertEquals("bohemian rhapsody", n("Bohemian Rhapsody - Remastered 2011"))
        assertEquals("tempo perdido", n("Tempo Perdido - Live"))
        assertEquals("tempo perdido", n("Tempo Perdido (Ao Vivo)"))
    }

    @Test
    fun removesFeaturingWithOrWithoutParentheses() {
        assertEquals("umbrella", n("Umbrella (feat. JAY-Z)"))
        assertEquals("song", n("Song feat. Someone Else"))
        assertEquals("song", n("Song [ft. Someone]"))
    }

    @Test
    fun stripsAccentsCaseAndPunctuation() {
        assertEquals("acao", n("Ação!"))
        assertEquals("ac dc", n("AC/DC"))
        assertEquals("guns n roses", n("Guns N' Roses"))
        assertEquals("rock and roll", n("Rock & Roll"))
    }

    @Test
    fun keepsWordsThatOnlyLookLikeQualifiers() {
        assertEquals("live and let die", n("Live and Let Die"))
    }
}
```

`JaroWinklerTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart.match

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JaroWinklerTest {
    @Test
    fun matchesReferenceValues() {
        assertEquals(0.961, JaroWinkler.similarity("martha", "marhta"), 0.001)
        assertEquals(0.840, JaroWinkler.similarity("dwayne", "duane"), 0.001)
        assertEquals(0.813, JaroWinkler.similarity("dixon", "dicksonx"), 0.001)
    }

    @Test
    fun handlesEqualAndEmptyStrings() {
        assertEquals(1.0, JaroWinkler.similarity("riff", "riff"), 0.0)
        assertEquals(0.0, JaroWinkler.similarity("", "riff"), 0.0)
        assertEquals(0.0, JaroWinkler.similarity("abc", "xyz"), 0.0)
    }
}
```

`TrackMatcherTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart.match

import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackMatcherTest {
    private fun chart(id: String, title: String, artist: String, durationMs: Long? = null) = Chart(
        id = id,
        title = title,
        artist = artist,
        album = null,
        charter = null,
        durationMs = durationMs,
        folderUri = "content://$id",
        format = ChartFormat.CHART,
        difficulties = setOf(Difficulty.EXPERT),
    )

    @Test
    fun exactTitleAndArtistIsAutomatic() {
        val result = TrackMatcher.bestMatch(TrackQuery("Bohemian Rhapsody", "Queen", null), listOf(chart("a", "Bohemian Rhapsody", "Queen")))
        assertEquals(MatchKind.AUTOMATIC, result?.kind)
        assertEquals(1f, result!!.confidence, 0.0001f)
    }

    @Test
    fun remasteredSpotifyTitleStillMatchesAutomatically() {
        val result = TrackMatcher.bestMatch(
            TrackQuery("Bohemian Rhapsody - Remastered 2011", "Queen", null),
            listOf(chart("a", "Bohemian Rhapsody", "Queen")),
        )
        assertEquals(MatchKind.AUTOMATIC, result?.kind)
    }

    @Test
    fun sameTitleByAnotherArtistIsNeverAutomatic() {
        val result = TrackMatcher.bestMatch(TrackQuery("Hurt", "Johnny Cash", null), listOf(chart("a", "Hurt", "Nine Inch Nails")))
        assertNotEquals(MatchKind.AUTOMATIC, result?.kind)
    }

    @Test
    fun durationFarOffLowersConfidenceToConfirmation() {
        val result = TrackMatcher.bestMatch(
            TrackQuery("Bohemian Rhapsody", "Queen", 355_000),
            listOf(chart("a", "Bohemian Rhapsody", "Queen", durationMs = 240_000)),
        )
        assertEquals(MatchKind.NEEDS_CONFIRMATION, result?.kind)
        assertEquals(0.9f, result!!.confidence, 0.0001f)
    }

    @Test
    fun durationWithinFiveSecondsIsNotPenalized() {
        val result = TrackMatcher.bestMatch(
            TrackQuery("Bohemian Rhapsody", "Queen", 354_000),
            listOf(chart("a", "Bohemian Rhapsody", "Queen", durationMs = 355_000)),
        )
        assertEquals(1f, result!!.confidence, 0.0001f)
    }

    @Test
    fun unrelatedOrEmptyLibraryHasNoMatch() {
        assertNull(TrackMatcher.bestMatch(TrackQuery("Garota de Ipanema", "Tom Jobim", null), listOf(chart("a", "Master of Puppets", "Metallica"))))
        assertNull(TrackMatcher.bestMatch(TrackQuery("Qualquer", "Banda", null), emptyList()))
    }

    @Test
    fun picksTheBestAmongSeveral() {
        val charts = listOf(chart("a", "Bohemian Like You", "The Dandy Warhols"), chart("b", "Bohemian Rhapsody", "Queen"))
        assertEquals("b", TrackMatcher.bestMatch(TrackQuery("Bohemian Rhapsody", "Queen", null), charts)?.chart?.id)
    }
}
```

Run: `./scripts/gw :core:chart:test`
Expected: falha de compilação (`Unresolved reference 'TextNormalizer'`, `'JaroWinkler'`, `'TrackMatcher'`).

- [ ] **Step 2: Implementar**

`TextNormalizer.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart.match

import java.text.Normalizer

/** Normaliza título e artista para comparar a faixa do Spotify com o mapa (spec §5). */
object TextNormalizer {
    private const val QUALIFIERS = "remaster(?:ed)?|live|ao vivo|version|versão|edit|mono|stereo"
    private val FEATURING_IN_BRACKETS = Regex("""\s*[(\[]\s*(?:feat\.?|ft\.?|featuring)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
    private val FEATURING_BARE = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)
    private val QUALIFIER_IN_BRACKETS = Regex("""\s*[(\[][^)\]]*\b(?:$QUALIFIERS)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
    private val QUALIFIER_AFTER_DASH = Regex("""\s+-\s+.*\b(?:$QUALIFIERS)\b.*$""", RegexOption.IGNORE_CASE)
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]+")

    fun normalize(text: String): String {
        val withoutExtras = text
            .replace(FEATURING_IN_BRACKETS, " ")
            .replace(FEATURING_BARE, "")
            .replace(QUALIFIER_IN_BRACKETS, " ")
            .replace(QUALIFIER_AFTER_DASH, "")
        val withoutAccents = Normalizer.normalize(withoutExtras, Normalizer.Form.NFD).replace(DIACRITICS, "")
        return withoutAccents
            .lowercase()
            .replace("&", " and ")
            .replace("'", "")
            .replace(NOT_ALPHANUMERIC, " ")
            .trim()
    }
}
```

`JaroWinkler.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart.match

import kotlin.math.max
import kotlin.math.min

/** Similaridade Jaro-Winkler entre 0 (nada em comum) e 1 (iguais). */
object JaroWinkler {
    private const val PREFIX_SCALE = 0.1
    private const val MAX_PREFIX = 4

    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val window = (max(a.length, b.length) / 2 - 1).coerceAtLeast(0)
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            for (j in max(0, i - window) until min(i + window + 1, b.length)) {
                if (!bMatched[j] && a[i] == b[j]) {
                    aMatched[i] = true
                    bMatched[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        var halfTranspositions = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[k]) k++
            if (a[i] != b[k]) halfTranspositions++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - halfTranspositions / 2.0) / m) / 3
        val prefix = a.zip(b).takeWhile { it.first == it.second }.size.coerceAtMost(MAX_PREFIX)
        return jaro + prefix * PREFIX_SCALE * (1 - jaro)
    }
}
```

`TrackMatcher.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart.match

import com.rodrigomachado.riff.core.model.Chart
import kotlin.math.abs

/** Faixa do Spotify a casar com um mapa. */
data class TrackQuery(val title: String, val artist: String, val durationMs: Long?)

enum class MatchKind { AUTOMATIC, NEEDS_CONFIRMATION }

data class MatchCandidate(val chart: Chart, val confidence: Float, val kind: MatchKind)

/** Casamento entre faixa e mapa (spec §5). */
object TrackMatcher {
    const val AUTOMATIC_THRESHOLD = 0.92f
    const val SUGGESTION_THRESHOLD = 0.75f
    private const val TITLE_WEIGHT = 0.6
    private const val ARTIST_WEIGHT = 0.4
    private const val DURATION_PENALTY = 0.1
    private const val DURATION_TOLERANCE_MS = 5_000

    fun score(query: TrackQuery, chart: Chart): Float {
        val title = JaroWinkler.similarity(TextNormalizer.normalize(query.title), TextNormalizer.normalize(chart.title))
        val artist = JaroWinkler.similarity(TextNormalizer.normalize(query.artist), TextNormalizer.normalize(chart.artist))
        var score = TITLE_WEIGHT * title + ARTIST_WEIGHT * artist
        val queryDuration = query.durationMs
        val chartDuration = chart.durationMs
        if (queryDuration != null && chartDuration != null && abs(queryDuration - chartDuration) > DURATION_TOLERANCE_MS) {
            score -= DURATION_PENALTY
        }
        return score.coerceIn(0.0, 1.0).toFloat()
    }

    fun bestMatch(query: TrackQuery, charts: List<Chart>): MatchCandidate? {
        val (chart, confidence) = charts.map { it to score(query, it) }.maxByOrNull { it.second } ?: return null
        if (confidence < SUGGESTION_THRESHOLD) return null
        val kind = if (confidence >= AUTOMATIC_THRESHOLD) MatchKind.AUTOMATIC else MatchKind.NEEDS_CONFIRMATION
        return MatchCandidate(chart, confidence, kind)
    }
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:chart:test koverVerify`
Expected: `BUILD SUCCESSFUL`, 13 testes novos passando.

```bash
git add -A
git commit -m "feat(core-chart): casamento entre faixa do Spotify e mapa (normalização e Jaro-Winkler)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Banco de dados (`:core:database`)

**Files:**
- Modify: `settings.gradle.kts` (`include(":core:database")`)
- Create: `core/database/build.gradle.kts`
- Create: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/Entities.kt`
- Create: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/ChartDao.kt`
- Create: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/TrackMatchDao.kt`
- Create: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/ScoreDao.kt`
- Create: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/RiffDatabase.kt`
- Create: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/DatabaseModule.kt`
- Create: `core/database/src/test/resources/robolectric.properties`
- Test: `core/database/src/test/kotlin/com/rodrigomachado/riff/core/database/DaoTest.kt`

**Interfaces:**
- Produces (pacote `com.rodrigomachado.riff.core.database`):
  - `ChartEntity(id, title, artist, album, charter, durationMs, folderUri, format: String, lastSeenScanId: Long)`, `ChartDifficultyEntity(chartId, difficulty: String)`, `ChartWithDifficulties(chart, difficulties)`, `TrackMatchEntity(spotifyTrackUri, chartId, confidence, isManual, trackOffsetMs)`, `ScoreEntity(id = 0, chartId, difficulty: String, score, accuracy, maxCombo, perfect, good, miss, playedAtEpochMs)`
  - `abstract class ChartDao { fun observeAll(): Flow<List<ChartWithDifficulties>>; suspend fun replace(chart, difficulties); suspend fun deleteNotSeenIn(scanId: Long) }`
  - `interface TrackMatchDao { suspend fun upsert(match); suspend fun get(spotifyTrackUri: String): TrackMatchEntity? }`
  - `interface ScoreDao { suspend fun insert(score): Long; suspend fun best(chartId: String, difficulty: String): ScoreEntity? }`
  - `RiffDatabase` (versão 1) e o módulo Hilt `DatabaseModule` (fornece o banco e os três DAOs).

- [ ] **Step 1: Módulo e teste que falha**

`core/database/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.room)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.database"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`core/database/src/test/resources/robolectric.properties`:
```properties
sdk=35
```

`DaoTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: RiffDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun chart(id: String, title: String = "Música $id", artist: String = "Banda", scanId: Long = 1) =
        ChartEntity(id, title, artist, null, null, null, "content://$id", "CHART", scanId)

    @Test
    fun replaceStoresChartWithItsDifficulties() = runTest {
        db.chartDao().replace(chart("a"), listOf(ChartDifficultyEntity("a", "EASY"), ChartDifficultyEntity("a", "EXPERT")))

        val stored = db.chartDao().observeAll().first().single()
        assertEquals("a", stored.chart.id)
        assertEquals(setOf("EASY", "EXPERT"), stored.difficulties.map { it.difficulty }.toSet())
    }

    @Test
    fun replacingAgainUpdatesWithoutDuplicating() = runTest {
        db.chartDao().replace(chart("a", title = "Antigo"), listOf(ChartDifficultyEntity("a", "EASY")))
        db.chartDao().replace(chart("a", title = "Novo"), listOf(ChartDifficultyEntity("a", "HARD")))

        val stored = db.chartDao().observeAll().first().single()
        assertEquals("Novo", stored.chart.title)
        assertEquals(listOf("HARD"), stored.difficulties.map { it.difficulty })
    }

    @Test
    fun listIsSortedByArtistThenTitleIgnoringCase() = runTest {
        db.chartDao().replace(chart("1", title = "b", artist = "zeca"), emptyList())
        db.chartDao().replace(chart("2", title = "B", artist = "Ana"), emptyList())
        db.chartDao().replace(chart("3", title = "a", artist = "ana"), emptyList())

        assertEquals(listOf("3", "2", "1"), db.chartDao().observeAll().first().map { it.chart.id })
    }

    @Test
    fun deleteNotSeenInRemovesOldChartsAndTheirDependents() = runTest {
        db.chartDao().replace(chart("velho", scanId = 1), listOf(ChartDifficultyEntity("velho", "EASY")))
        db.chartDao().replace(chart("novo", scanId = 2), emptyList())
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:1", "velho", 1f, false, 0))
        db.scoreDao().insert(ScoreEntity(chartId = "velho", difficulty = "EASY", score = 100, accuracy = 1f, maxCombo = 1, perfect = 1, good = 0, miss = 0, playedAtEpochMs = 0))

        db.chartDao().deleteNotSeenIn(scanId = 2)

        assertEquals(listOf("novo"), db.chartDao().observeAll().first().map { it.chart.id })
        assertNull(db.trackMatchDao().get("spotify:track:1"))
        assertNull(db.scoreDao().best("velho", "EASY"))
    }

    @Test
    fun handlesLibrariesLargerThanTheSqliteVariableLimit() = runTest {
        repeat(1_500) { db.chartDao().replace(chart("old-$it", scanId = 1), emptyList()) }
        db.chartDao().replace(chart("kept", scanId = 2), emptyList())

        db.chartDao().deleteNotSeenIn(scanId = 2)

        assertEquals(listOf("kept"), db.chartDao().observeAll().first().map { it.chart.id })
    }

    @Test
    fun trackMatchUpsertReplacesPreviousMatch() = runTest {
        db.chartDao().replace(chart("a"), emptyList())
        db.chartDao().replace(chart("b"), emptyList())
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:1", "a", 0.8f, false, 0))
        db.trackMatchDao().upsert(TrackMatchEntity("spotify:track:1", "b", 1f, true, -40))

        assertEquals(TrackMatchEntity("spotify:track:1", "b", 1f, true, -40), db.trackMatchDao().get("spotify:track:1"))
    }

    @Test
    fun bestScoreIsTheHighestForThatDifficulty() = runTest {
        db.chartDao().replace(chart("a"), emptyList())
        fun score(value: Long, difficulty: String) =
            ScoreEntity(chartId = "a", difficulty = difficulty, score = value, accuracy = 0.5f, maxCombo = 1, perfect = 0, good = 0, miss = 0, playedAtEpochMs = 0)
        db.scoreDao().insert(score(500, "EXPERT"))
        db.scoreDao().insert(score(900, "EXPERT"))
        db.scoreDao().insert(score(5_000, "EASY"))

        assertEquals(900L, db.scoreDao().best("a", "EXPERT")?.score)
    }
}
```

Em `settings.gradle.kts`, acrescente `include(":core:database")`.

Run: `./scripts/gw :core:database:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'RiffDatabase'`, `'ChartEntity'`).

- [ ] **Step 2: Entidades, DAOs, banco e módulo Hilt**

`Entities.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "charts")
data class ChartEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val charter: String?,
    val durationMs: Long?,
    val folderUri: String,
    val format: String,
    /** Importação em que o mapa foi visto por último; os não vistos na mais recente são apagados. */
    val lastSeenScanId: Long,
)

@Entity(
    tableName = "chart_difficulties",
    primaryKeys = ["chartId", "difficulty"],
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chartId")],
)
data class ChartDifficultyEntity(val chartId: String, val difficulty: String)

data class ChartWithDifficulties(
    @Embedded val chart: ChartEntity,
    @Relation(parentColumn = "id", entityColumn = "chartId") val difficulties: List<ChartDifficultyEntity>,
)

@Entity(
    tableName = "track_matches",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chartId")],
)
data class TrackMatchEntity(
    @PrimaryKey val spotifyTrackUri: String,
    val chartId: String,
    val confidence: Float,
    val isManual: Boolean,
    val trackOffsetMs: Int,
)

@Entity(
    tableName = "scores",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chartId", "difficulty")],
)
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chartId: String,
    val difficulty: String,
    val score: Long,
    val accuracy: Float,
    val maxCombo: Int,
    val perfect: Int,
    val good: Int,
    val miss: Int,
    val playedAtEpochMs: Long,
)
```

`ChartDao.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ChartDao {
    @Transaction
    @Query("SELECT * FROM charts ORDER BY artist COLLATE NOCASE, title COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<ChartWithDifficulties>>

    /** Grava o mapa e substitui suas dificuldades, numa transação. */
    @Transaction
    open suspend fun replace(chart: ChartEntity, difficulties: List<ChartDifficultyEntity>) {
        upsert(chart)
        deleteDifficulties(chart.id)
        insertDifficulties(difficulties)
    }

    @Query("DELETE FROM charts WHERE lastSeenScanId != :scanId")
    abstract suspend fun deleteNotSeenIn(scanId: Long)

    @Upsert
    abstract suspend fun upsert(chart: ChartEntity)

    @Query("DELETE FROM chart_difficulties WHERE chartId = :chartId")
    abstract suspend fun deleteDifficulties(chartId: String)

    @Insert
    abstract suspend fun insertDifficulties(difficulties: List<ChartDifficultyEntity>)
}
```

`TrackMatchDao.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TrackMatchDao {
    @Upsert
    suspend fun upsert(match: TrackMatchEntity)

    @Query("SELECT * FROM track_matches WHERE spotifyTrackUri = :spotifyTrackUri")
    suspend fun get(spotifyTrackUri: String): TrackMatchEntity?
}
```

`ScoreDao.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(score: ScoreEntity): Long

    @Query("SELECT * FROM scores WHERE chartId = :chartId AND difficulty = :difficulty ORDER BY score DESC LIMIT 1")
    suspend fun best(chartId: String, difficulty: String): ScoreEntity?
}
```

`RiffDatabase.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChartEntity::class, ChartDifficultyEntity::class, TrackMatchEntity::class, ScoreEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RiffDatabase : RoomDatabase() {
    abstract fun chartDao(): ChartDao

    abstract fun trackMatchDao(): TrackMatchDao

    abstract fun scoreDao(): ScoreDao
}
```

`DatabaseModule.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RiffDatabase =
        Room.databaseBuilder(context, RiffDatabase::class.java, "riff.db").build()

    @Provides
    fun chartDao(database: RiffDatabase): ChartDao = database.chartDao()

    @Provides
    fun trackMatchDao(database: RiffDatabase): TrackMatchDao = database.trackMatchDao()

    @Provides
    fun scoreDao(database: RiffDatabase): ScoreDao = database.scoreDao()
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:database:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 7 testes passando, e o esquema gerado em `core/database/schemas/com.rodrigomachado.riff.core.database.RiffDatabase/1.json`.

```bash
git add -A
git commit -m "feat(core-database): Room com mapas, dificuldades, casamentos e recordes" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Preferências (`:core:datastore`)

**Files:**
- Modify: `settings.gradle.kts` (`include(":core:datastore")`)
- Create: `core/datastore/build.gradle.kts`
- Create: `core/datastore/src/main/kotlin/com/rodrigomachado/riff/core/datastore/RiffPreferences.kt`
- Create: `core/datastore/src/main/kotlin/com/rodrigomachado/riff/core/datastore/DataStoreModule.kt`
- Test: `core/datastore/src/test/kotlin/com/rodrigomachado/riff/core/datastore/RiffPreferencesTest.kt`

**Interfaces:**
- Consumes: `Settings`, `Difficulty` (`:core:model`).
- Produces: `class RiffPreferences(dataStore: DataStore<Preferences>)` com `val settings: Flow<Settings>`, `val libraryRootUri: Flow<String?>`, `suspend fun updateSettings(transform: (Settings) -> Settings)` e `suspend fun setLibraryRootUri(uri: String)`; e o módulo Hilt `DataStoreModule`, que fornece `RiffPreferences` como singleton.

- [ ] **Step 1: Módulo e testes que falham**

`core/datastore/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.datastore"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`RiffPreferencesTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RiffPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(folder.root, "test.preferences_pb") })

    @Test
    fun emptyStoreGivesDefaults() = runTest {
        val preferences = RiffPreferences(dataStore())
        assertEquals(Settings.DEFAULT, preferences.settings.first())
        assertNull(preferences.libraryRootUri.first())
    }

    @Test
    fun updatesArePersisted() = runTest {
        val preferences = RiffPreferences(dataStore())
        preferences.updateSettings { it.copy(audioOffsetMs = -180, leftHanded = true, defaultDifficulty = Difficulty.EXPERT, sfxVolume = 0.25f) }
        preferences.setLibraryRootUri("content://pasta")

        val settings = preferences.settings.first()
        assertEquals(-180, settings.audioOffsetMs)
        assertEquals(true, settings.leftHanded)
        assertEquals(Difficulty.EXPERT, settings.defaultDifficulty)
        assertEquals(0.25f, settings.sfxVolume)
        assertEquals("content://pasta", preferences.libraryRootUri.first())
    }

    @Test
    fun corruptedValuesFallBackToDefaultsInsteadOfCrashing() = runTest {
        val store = dataStore()
        store.edit {
            it[floatPreferencesKey("sfx_volume")] = 5f
            it[floatPreferencesKey("note_speed")] = -1f
            it[stringPreferencesKey("default_difficulty")] = "IMPOSSIVEL"
        }

        val settings = RiffPreferences(store).settings.first()
        assertEquals(1f, settings.sfxVolume)
        assertEquals(Settings.DEFAULT.noteSpeed, settings.noteSpeed)
        assertEquals(Settings.DEFAULT.defaultDifficulty, settings.defaultDifficulty)
    }
}
```

Em `settings.gradle.kts`, acrescente `include(":core:datastore")`.

Run: `./scripts/gw :core:datastore:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'RiffPreferences'`).

- [ ] **Step 2: Implementar**

`RiffPreferences.kt`:
```kotlin
package com.rodrigomachado.riff.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Preferências do jogador e pasta da biblioteca. Valores inválidos no disco viram o padrão, nunca um crash. */
class RiffPreferences(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    val libraryRootUri: Flow<String?> = dataStore.data.map { it[LIBRARY_ROOT_URI] }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[AUDIO_OFFSET_MS] = updated.audioOffsetMs
            prefs[NOTE_SPEED] = updated.noteSpeed
            prefs[LEFT_HANDED] = updated.leftHanded
            prefs[DEFAULT_DIFFICULTY] = updated.defaultDifficulty.name
            prefs[SFX_VOLUME] = updated.sfxVolume
        }
    }

    suspend fun setLibraryRootUri(uri: String) {
        dataStore.edit { it[LIBRARY_ROOT_URI] = uri }
    }

    private fun Preferences.toSettings(): Settings {
        val default = Settings.DEFAULT
        return Settings(
            audioOffsetMs = this[AUDIO_OFFSET_MS] ?: default.audioOffsetMs,
            noteSpeed = this[NOTE_SPEED]?.takeIf { it.isFinite() && it > 0f } ?: default.noteSpeed,
            leftHanded = this[LEFT_HANDED] ?: default.leftHanded,
            defaultDifficulty = this[DEFAULT_DIFFICULTY]
                ?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }
                ?: default.defaultDifficulty,
            sfxVolume = this[SFX_VOLUME]?.takeIf { !it.isNaN() }?.coerceIn(0f, 1f) ?: default.sfxVolume,
        )
    }

    private companion object {
        val AUDIO_OFFSET_MS = intPreferencesKey("audio_offset_ms")
        val NOTE_SPEED = floatPreferencesKey("note_speed")
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val DEFAULT_DIFFICULTY = stringPreferencesKey("default_difficulty")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val LIBRARY_ROOT_URI = stringPreferencesKey("library_root_uri")
    }
}
```

`DataStoreModule.kt`:
```kotlin
package com.rodrigomachado.riff.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): RiffPreferences =
        RiffPreferences(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("riff_settings") })
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:datastore:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 3 testes passando.

```bash
git add -A
git commit -m "feat(core-datastore): preferências e pasta da biblioteca com padrões contra valores corrompidos" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Dados: repositório de mapas, pastas e injeção (`:core:data`)

**Files:**
- Modify: `settings.gradle.kts` (`include(":core:data")`), `build.gradle.kts` (Kover)
- Create: `core/data/build.gradle.kts`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/ChartRepository.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/RoomChartRepository.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/folder/FolderSource.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/folder/SafFolderSource.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/LibraryFolderStore.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/DataModule.kt`
- Create: `core/data/src/test/resources/robolectric.properties`
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/RoomChartRepositoryTest.kt`

**Interfaces:**
- Consumes: `ChartDao`, entidades (Task 3); `RiffPreferences` (Task 4); `Chart`, `ChartFormat`, `Difficulty` (`:core:model`).
- Produces (pacote `com.rodrigomachado.riff.core.data`):
  - `interface ChartRepository { fun observeCharts(): Flow<List<Chart>>; suspend fun saveScanned(chart: Chart, scanId: Long); suspend fun removeNotSeenIn(scanId: Long) }` e `RoomChartRepository`
  - `interface LibraryFolderStore { val rootUri: Flow<String?>; suspend fun setRootUri(uri: String) }` e `PreferencesLibraryFolderStore`
  - `@Qualifier annotation class IoDispatcher`
  - Pacote `.folder`: `data class FolderEntry(val uri: String, val name: String, val isDirectory: Boolean, val sizeBytes: Long)`, `interface FolderSource { suspend fun listChildren(folderUri: String): List<FolderEntry>; suspend fun read(file: FolderEntry): ByteArray }`, `class FolderAccessLostException(message: String, cause: Throwable? = null) : Exception`, `interface FolderAccess { fun persistReadAccess(folderUri: String) }`, `SafFolderSource` e `SafFolderAccess`.

- [ ] **Step 1: Módulo e teste que falha**

`core/data/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.rodrigomachado.riff.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:chart"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    // O teste do repositório cria o banco em memória; o Room chega a :core:database só como implementation.
    testImplementation(libs.androidx.room.runtime)
}
```

`core/data/src/test/resources/robolectric.properties`:
```properties
sdk=35
```

`RoomChartRepositoryTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.RiffDatabase
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomChartRepositoryTest {
    private lateinit var db: RiffDatabase
    private lateinit var repository: RoomChartRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomChartRepository(db.chartDao())
    }

    @After
    fun tearDown() = db.close()

    private val chart = Chart(
        id = "sha1",
        title = "Through the Fire and Flames",
        artist = "DragonForce",
        album = "Inhuman Rampage",
        charter = "Alguém",
        durationMs = 441_000,
        folderUri = "content://tree/primary%3ACharts/document/primary%3ACharts%2FTTFAF",
        format = ChartFormat.MIDI,
        difficulties = setOf(Difficulty.HARD, Difficulty.EXPERT),
    )

    @Test
    fun savedChartComesBackIdentical() = runTest {
        repository.saveScanned(chart, scanId = 1)
        assertEquals(listOf(chart), repository.observeCharts().first())
    }

    @Test
    fun removeNotSeenInDropsChartsFromOlderScans() = runTest {
        repository.saveScanned(chart, scanId = 1)
        repository.saveScanned(chart.copy(id = "outro", title = "Outro"), scanId = 2)

        repository.removeNotSeenIn(scanId = 2)

        assertEquals(listOf("outro"), repository.observeCharts().first().map { it.id })
    }
}
```

Em `settings.gradle.kts`, acrescente `include(":core:data")`.

Run: `./scripts/gw :core:data:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'RoomChartRepository'`).

- [ ] **Step 2: Repositório de mapas**

`ChartRepository.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.Chart
import kotlinx.coroutines.flow.Flow

interface ChartRepository {
    /** Mapas da biblioteca, ordenados por artista e título. */
    fun observeCharts(): Flow<List<Chart>>

    /** Grava (ou atualiza) um mapa encontrado na importação [scanId]. */
    suspend fun saveScanned(chart: Chart, scanId: Long)

    /** Apaga os mapas que não apareceram na importação [scanId] (pastas que sumiram). */
    suspend fun removeNotSeenIn(scanId: Long)
}
```

`RoomChartRepository.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.database.ChartDao
import com.rodrigomachado.riff.core.database.ChartDifficultyEntity
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.ChartWithDifficulties
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomChartRepository @Inject constructor(private val dao: ChartDao) : ChartRepository {
    override fun observeCharts(): Flow<List<Chart>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun saveScanned(chart: Chart, scanId: Long) {
        dao.replace(chart.toEntity(scanId), chart.difficulties.map { ChartDifficultyEntity(chart.id, it.name) })
    }

    override suspend fun removeNotSeenIn(scanId: Long) = dao.deleteNotSeenIn(scanId)
}

private fun Chart.toEntity(scanId: Long) =
    ChartEntity(id, title, artist, album, charter, durationMs, folderUri, format.name, scanId)

/** Linha com formato desconhecido (versão futura do app) é ignorada em vez de quebrar a lista. */
private fun ChartWithDifficulties.toModel(): Chart? {
    val format = ChartFormat.entries.firstOrNull { it.name == chart.format } ?: return null
    return Chart(
        id = chart.id,
        title = chart.title,
        artist = chart.artist,
        album = chart.album,
        charter = chart.charter,
        durationMs = chart.durationMs,
        folderUri = chart.folderUri,
        format = format,
        difficulties = difficulties.mapNotNull { row -> Difficulty.entries.firstOrNull { it.name == row.difficulty } }.toSet(),
    )
}
```

- [ ] **Step 3: Pastas por SAF, pasta da biblioteca e injeção**

`folder/FolderSource.kt`:
```kotlin
package com.rodrigomachado.riff.core.data.folder

/** Arquivo ou subpasta dentro da pasta da biblioteca. */
data class FolderEntry(val uri: String, val name: String, val isDirectory: Boolean, val sizeBytes: Long)

/** Leitura de pastas. No Android é SAF; nos testes, uma árvore em memória. */
interface FolderSource {
    /** @throws FolderAccessLostException se a pasta não puder mais ser lida (permissão revogada ou pasta apagada). */
    suspend fun listChildren(folderUri: String): List<FolderEntry>

    suspend fun read(file: FolderEntry): ByteArray
}

/** Guarda a permissão de leitura da pasta escolhida para valer depois que o app reiniciar. */
interface FolderAccess {
    fun persistReadAccess(folderUri: String)
}

class FolderAccessLostException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

`folder/SafFolderSource.kt`:
```kotlin
package com.rodrigomachado.riff.core.data.folder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject

class SafFolderSource @Inject constructor(@ApplicationContext private val context: Context) : FolderSource {
    override suspend fun listChildren(folderUri: String): List<FolderEntry> {
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
        if (folder == null || !folder.isDirectory || !folder.canRead()) {
            throw FolderAccessLostException("sem acesso à pasta $folderUri")
        }
        return try {
            folder.listFiles().map { FolderEntry(it.uri.toString(), it.name.orEmpty(), it.isDirectory, it.length()) }
        } catch (e: SecurityException) {
            throw FolderAccessLostException("permissão revogada para $folderUri", e)
        }
    }

    override suspend fun read(file: FolderEntry): ByteArray =
        context.contentResolver.openInputStream(Uri.parse(file.uri))?.use { it.readBytes() }
            ?: throw IOException("não foi possível abrir ${file.name}")
}

class SafFolderAccess @Inject constructor(@ApplicationContext private val context: Context) : FolderAccess {
    override fun persistReadAccess(folderUri: String) {
        context.contentResolver.takePersistableUriPermission(Uri.parse(folderUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
```

`LibraryFolderStore.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.datastore.RiffPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Pasta escolhida como raiz da biblioteca. */
interface LibraryFolderStore {
    val rootUri: Flow<String?>

    suspend fun setRootUri(uri: String)
}

class PreferencesLibraryFolderStore @Inject constructor(private val preferences: RiffPreferences) : LibraryFolderStore {
    override val rootUri: Flow<String?> = preferences.libraryRootUri

    override suspend fun setRootUri(uri: String) = preferences.setLibraryRootUri(uri)
}
```

`DataModule.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.data.folder.SafFolderAccess
import com.rodrigomachado.riff.core.data.folder.SafFolderSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds
    abstract fun chartRepository(impl: RoomChartRepository): ChartRepository

    @Binds
    abstract fun libraryFolderStore(impl: PreferencesLibraryFolderStore): LibraryFolderStore

    @Binds
    abstract fun folderSource(impl: SafFolderSource): FolderSource

    @Binds
    abstract fun folderAccess(impl: SafFolderAccess): FolderAccess
}

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
```

- [ ] **Step 4: Kover**

`build.gradle.kts` (raiz): no bloco `dependencies`, acrescente `kover(project(":core:data"))`. No bloco `kover`, troque `reports {` por:
```kotlin
    reports {
        filters {
            excludes {
                // Código gerado e cola com o Android, verificada no aparelho e não em teste unitário.
                classes("*_Factory", "*_Factory\$*", "*_Impl", "*_Impl\$*", "*Module", "*Module_*", "Hilt_*", "*_HiltModules*")
                classes("com.rodrigomachado.riff.core.data.folder.SafFolderSource", "com.rodrigomachado.riff.core.data.folder.SafFolderAccess")
                annotatedBy("dagger.Module")
            }
        }
```
(o `verify { rule { minBound(70) } }` existente continua dentro de `reports`.)

- [ ] **Step 5: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:data:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 2 testes passando. (`koverVerify` roda no fim da Task 6, quando o importador estiver testado.)

```bash
git add -A
git commit -m "feat(core-data): repositório de mapas, acesso a pastas por SAF e pasta da biblioteca" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Importador de pastas

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/importer/ChartImporter.kt`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/DataModule.kt` (fornece o importador)
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/importer/FolderChartImporterTest.kt`
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/importer/Fakes.kt`

**Interfaces:**
- Consumes: `ChartLoader`, `ChartFiles`, `ChartParseException` (`:core:chart`); `ChartRepository`, `FolderSource`, `FolderEntry`, `FolderAccessLostException`, `IoDispatcher` (Task 5).
- Produces (pacote `com.rodrigomachado.riff.core.data.importer`):
  - `data class ImportFailure(val folderName: String, val reason: String)`
  - `data class ImportReport(val importedCount: Int, val failures: List<ImportFailure>)`
  - `interface ChartImporter { suspend fun import(rootUri: String, onProgress: (scannedFolders: Int) -> Unit = {}): ImportReport }`. Lança `FolderAccessLostException` se a raiz não puder ser lida; nesse caso a biblioteca não é alterada.
  - `class FolderChartImporter(source: FolderSource, repository: ChartRepository, dispatcher: CoroutineDispatcher, now: () -> Long) : ChartImporter`, com `MAX_FILE_BYTES = 16 MiB` e `MAX_DEPTH = 8`.

- [ ] **Step 1: Fakes e testes que falham**

`importer/Fakes.kt` (em `src/test`):
```kotlin
package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.model.Chart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Árvore de pastas em memória: "raiz/Rock/Musica" vira uma pasta; arquivos têm conteúdo e tamanho declarado. */
class FakeFolderSource : FolderSource {
    private val files = mutableMapOf<String, ByteArray>()
    private val declaredSizes = mutableMapOf<String, Long>()
    val revoked = mutableSetOf<String>()
    val reads = mutableListOf<String>()

    fun file(path: String, content: String) = file(path, content.toByteArray())

    fun file(path: String, content: ByteArray, declaredSize: Long = content.size.toLong()) {
        files[path] = content
        declaredSizes[path] = declaredSize
    }

    fun remove(prefix: String) {
        files.keys.removeAll { it == prefix || it.startsWith("$prefix/") }
    }

    override suspend fun listChildren(folderUri: String): List<FolderEntry> {
        if (folderUri in revoked) throw FolderAccessLostException("revogada: $folderUri")
        val prefix = "$folderUri/"
        val names = files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct()
        if (names.isEmpty() && files.keys.none { it.startsWith(folderUri) }) throw FolderAccessLostException("não existe: $folderUri")
        return names.map { name ->
            val path = prefix + name
            val isDirectory = path !in files
            FolderEntry(path, name, isDirectory, if (isDirectory) 0 else declaredSizes.getValue(path))
        }
    }

    override suspend fun read(file: FolderEntry): ByteArray {
        reads += file.uri
        return files.getValue(file.uri)
    }
}

class FakeChartRepository : ChartRepository {
    private val stored = MutableStateFlow<Map<String, Pair<Chart, Long>>>(emptyMap())
    val removedScans = mutableListOf<Long>()

    val charts: List<Chart> get() = stored.value.values.map { it.first }.sortedBy { it.title }

    override fun observeCharts(): Flow<List<Chart>> = stored.map { map -> map.values.map { it.first } }

    override suspend fun saveScanned(chart: Chart, scanId: Long) {
        stored.value = stored.value + (chart.id to (chart to scanId))
    }

    override suspend fun removeNotSeenIn(scanId: Long) {
        removedScans += scanId
        stored.value = stored.value.filterValues { it.second == scanId }
    }
}

fun chartText(title: String?, artist: String?, notes: String = "  192 = N 0 0") = buildString {
    appendLine("[Song]")
    appendLine("{")
    if (title != null) appendLine("  Name = \"$title\"")
    if (artist != null) appendLine("  Artist = \"$artist\"")
    appendLine("}")
    appendLine("[ExpertSingle]")
    appendLine("{")
    appendLine(notes)
    appendLine("}")
}
```

`importer/FolderChartImporterTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderChartImporterTest {
    private val source = FakeFolderSource()
    private val repository = FakeChartRepository()
    private var clock = 0L

    private fun TestScope.importer() =
        FolderChartImporter(source, repository, StandardTestDispatcher(testScheduler)) { ++clock }

    @Test
    fun importsEveryChartFolderInTheTreeAndReportsProgress() = runTest {
        source.file("raiz/Rock/Musica A/notes.chart", chartText("Música A", "Banda A"))
        source.file("raiz/Rock/Musica A/song.ini", "[song]\nname = Música A do ini")
        source.file("raiz/Musica B/notes.chart", chartText("Música B", "Banda B"))
        val progress = mutableListOf<Int>()

        val report = importer().import("raiz") { progress += it }

        assertEquals(ImportReport(importedCount = 2, failures = emptyList()), report)
        assertEquals(listOf("Música A do ini", "Música B"), repository.charts.map { it.title })
        assertEquals(setOf(Difficulty.EXPERT), repository.charts.first().difficulties)
        assertEquals("raiz/Rock/Musica A", repository.charts.first().folderUri)
        assertEquals(listOf(1, 2), progress)
    }

    @Test
    fun brokenChartIsReportedAndTheOthersStillImport() = runTest {
        source.file("raiz/Boa/notes.chart", chartText("Boa", "Banda"))
        source.file("raiz/Quebrada/notes.chart", chartText("Quebrada", "Banda", notes = "  abc = N 0 0"))

        val report = importer().import("raiz")

        assertEquals(1, report.importedCount)
        assertEquals("Quebrada", report.failures.single().folderName)
        assertTrue(report.failures.single().reason.contains("linha"))
        assertEquals(listOf("Boa"), repository.charts.map { it.title })
    }

    @Test
    fun reimportDoesNotDuplicateAndDropsFoldersThatDisappeared() = runTest {
        source.file("raiz/A/notes.chart", chartText("A", "Banda"))
        source.file("raiz/B/notes.chart", chartText("B", "Banda"))
        importer().import("raiz")

        source.remove("raiz/B")
        val report = importer().import("raiz")

        assertEquals(1, report.importedCount)
        assertEquals(listOf("A"), repository.charts.map { it.title })
    }

    @Test
    fun lostAccessToTheRootThrowsAndKeepsTheLibrary() = runTest {
        source.file("raiz/A/notes.chart", chartText("A", "Banda"))
        importer().import("raiz")
        source.revoked += "raiz"

        val error = runCatching { importer().import("raiz") }.exceptionOrNull()

        assertTrue(error is FolderAccessLostException)
        assertEquals(listOf("A"), repository.charts.map { it.title })
        assertEquals(1, repository.removedScans.size) // só a primeira importação limpou
    }

    @Test
    fun oversizedNotesFileIsRejectedWithoutBeingRead() = runTest {
        source.file("raiz/Gigante/notes.chart", ByteArray(4), declaredSize = 200L * 1024 * 1024)

        val report = importer().import("raiz")

        assertEquals(0, report.importedCount)
        assertTrue(report.failures.single().reason.contains("grande demais"))
        assertFalse(source.reads.any { it.contains("Gigante") })
    }

    @Test
    fun stopsDescendingPastTheMaximumDepth() = runTest {
        val deep = (1..10).joinToString("/") { "n$it" }
        source.file("raiz/$deep/notes.chart", chartText("Funda", "Banda"))
        source.file("raiz/Rasa/notes.chart", chartText("Rasa", "Banda"))

        val report = importer().import("raiz")

        assertEquals(listOf("Rasa"), repository.charts.map { it.title })
        assertEquals(1, report.importedCount)
    }

    @Test
    fun missingTitleUsesFolderNameAndMissingArtistIsBlank() = runTest {
        source.file("raiz/Pasta Sem Nome/notes.chart", chartText(title = null, artist = null))

        importer().import("raiz")

        val chart = repository.charts.single()
        assertEquals("Pasta Sem Nome", chart.title)
        assertEquals("", chart.artist)
    }

    @Test
    fun rootThatIsItselfASongFolderIsImported() = runTest {
        source.file("raiz/notes.chart", chartText("Direto", "Banda"))

        assertEquals(1, importer().import("raiz").importedCount)
    }
}
```

Run: `./scripts/gw :core:data:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'FolderChartImporter'`, `'ImportReport'`).

- [ ] **Step 2: Implementar**

`importer/ChartImporter.kt`:
```kotlin
package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.chart.ChartLoader
import com.rodrigomachado.riff.core.chart.ChartParseException
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.model.Chart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLDecoder

data class ImportFailure(val folderName: String, val reason: String)

data class ImportReport(val importedCount: Int, val failures: List<ImportFailure>)

interface ChartImporter {
    /**
     * Varre a pasta [rootUri] e as subpastas, importa cada pasta de música e apaga da biblioteca
     * os mapas cujas pastas sumiram. [onProgress] recebe quantas pastas de música já foram lidas.
     *
     * @throws FolderAccessLostException se a raiz não puder ser lida; a biblioteca fica como estava.
     */
    suspend fun import(rootUri: String, onProgress: (scannedFolders: Int) -> Unit = {}): ImportReport
}

class FolderChartImporter(
    private val source: FolderSource,
    private val repository: ChartRepository,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : ChartImporter {

    override suspend fun import(rootUri: String, onProgress: (Int) -> Unit): ImportReport = withContext(dispatcher) {
        val scan = Scan(scanId = now(), onProgress = onProgress)
        val rootChildren = source.listChildren(rootUri)
        scan.visit(FolderEntry(rootUri, folderNameOf(rootUri), isDirectory = true, sizeBytes = 0), rootChildren, depth = 0)
        repository.removeNotSeenIn(scan.scanId)
        ImportReport(scan.imported, scan.failures)
    }

    private inner class Scan(val scanId: Long, val onProgress: (Int) -> Unit) {
        var imported = 0
        var scanned = 0
        val failures = mutableListOf<ImportFailure>()

        suspend fun visit(folder: FolderEntry, children: List<FolderEntry>, depth: Int) {
            if (children.any { !it.isDirectory && it.name.lowercase() in NOTES_FILES }) {
                importSong(folder, children)
                return
            }
            if (depth >= MAX_DEPTH) return
            for (child in children.filter { it.isDirectory }) {
                val grandChildren = try {
                    source.listChildren(child.uri)
                } catch (e: FolderAccessLostException) {
                    failures += ImportFailure(child.name, "sem acesso à pasta: ${e.message}")
                    continue
                }
                visit(child, grandChildren, depth + 1)
            }
        }

        private suspend fun importSong(folder: FolderEntry, children: List<FolderEntry>) {
            scanned++
            onProgress(scanned)
            try {
                repository.saveScanned(load(folder, children), scanId)
                imported++
            } catch (e: ChartParseException) {
                failures += ImportFailure(folder.name, e.message ?: "mapa inválido")
            } catch (e: IOException) {
                failures += ImportFailure(folder.name, "não foi possível ler: ${e.message}")
            }
        }
    }

    private suspend fun load(folder: FolderEntry, children: List<FolderEntry>): Chart {
        val contents = mutableMapOf<String, ByteArray>()
        for (name in READ_FILES) {
            val entry = children.firstOrNull { !it.isDirectory && it.name.equals(name, ignoreCase = true) } ?: continue
            if (entry.sizeBytes > MAX_FILE_BYTES) throw ChartParseException("${entry.name} grande demais (${entry.sizeBytes / BYTES_PER_MB} MB)")
            val bytes = source.read(entry)
            if (bytes.size > MAX_FILE_BYTES) throw ChartParseException("${entry.name} grande demais (${bytes.size / BYTES_PER_MB} MB)")
            contents[name] = bytes
        }
        val loaded = ChartLoader.load { name -> contents[name.lowercase()] }
        return Chart(
            id = loaded.id,
            title = loaded.info.title ?: folder.name,
            artist = loaded.info.artist.orEmpty(),
            album = loaded.info.album,
            charter = loaded.info.charter,
            durationMs = loaded.info.songLengthMs,
            folderUri = folder.uri,
            format = loaded.format,
            difficulties = loaded.tracks.keys,
        )
    }

    companion object {
        const val MAX_FILE_BYTES = 16L * 1024 * 1024
        const val MAX_DEPTH = 8
        private const val BYTES_PER_MB = 1024L * 1024
        private val NOTES_FILES = setOf("notes.chart", "notes.mid")
        private val READ_FILES = listOf("notes.chart", "notes.mid", "song.ini")

        /** Nome legível da raiz a partir do URI do SAF (`.../tree/primary%3ACharts` vira `Charts`). */
        private fun folderNameOf(uri: String): String =
            URLDecoder.decode(uri.substringAfterLast('/'), "UTF-8").substringAfterLast('/').substringAfterLast(':')
    }
}
```

`DataModule.kt`: no `object DataProvidesModule`, acrescente
```kotlin
    @Provides
    fun chartImporter(
        source: FolderSource,
        repository: ChartRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): ChartImporter = FolderChartImporter(source, repository, dispatcher, System::currentTimeMillis)
```
com os imports `com.rodrigomachado.riff.core.data.importer.ChartImporter` e `com.rodrigomachado.riff.core.data.importer.FolderChartImporter`.

- [ ] **Step 3: Testes, cobertura e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:data:testDebugUnitTest koverVerify`
Expected: `BUILD SUCCESSFUL`, 10 testes em `:core:data` e cobertura agregada de pelo menos 70%. Se o Kover não medir o módulo Android ou o filtro não pegar as classes do Hilt, ajuste o filtro segundo a mensagem e registre no ledger.

```bash
git add -A
git commit -m "feat(core-data): importador de pastas com relatório, limpeza de pastas sumidas e proteções" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: ViewModel da Biblioteca (`:feature:library`)

**Files:**
- Modify: `settings.gradle.kts` (`include(":feature:library")`)
- Create: `feature/library/build.gradle.kts`
- Create: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryViewModel.kt`
- Create: `feature/library/src/test/resources/robolectric.properties`
- Test: `feature/library/src/test/kotlin/com/rodrigomachado/riff/feature/library/LibraryViewModelTest.kt`
- Test: `feature/library/src/test/kotlin/com/rodrigomachado/riff/feature/library/Fakes.kt`

**Interfaces:**
- Consumes: `ChartRepository`, `LibraryFolderStore`, `FolderAccess`, `FolderAccessLostException`, `ChartImporter`, `ImportReport`, `ImportFailure` (Tasks 5 e 6).
- Produces:
  - `data class LibraryUiState(val isLoading: Boolean = true, val hasFolder: Boolean = false, val charts: List<Chart> = emptyList(), val isImporting: Boolean = false, val scannedFolders: Int = 0, val report: ImportReport? = null, val folderAccessLost: Boolean = false)`
  - `@HiltViewModel class LibraryViewModel` com `val uiState: StateFlow<LibraryUiState>`, `fun onFolderPicked(uri: String)`, `fun onRescan()` e `fun onReportDismissed()`.

- [ ] **Step 1: Módulo, fakes e testes que falham**

`feature/library/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.library"
}
```

`feature/library/src/test/resources/robolectric.properties`:
```properties
sdk=35
```

`Fakes.kt` (em `src/test`):
```kotlin
package com.rodrigomachado.riff.feature.library

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun chart(id: String, title: String = "Música $id", artist: String = "Banda", difficulties: Set<Difficulty> = setOf(Difficulty.EXPERT)) =
    Chart(id, title, artist, null, null, null, "content://$id", ChartFormat.CHART, difficulties)

class FakeChartRepository : ChartRepository {
    val charts = MutableStateFlow<List<Chart>>(emptyList())

    override fun observeCharts(): Flow<List<Chart>> = charts

    override suspend fun saveScanned(chart: Chart, scanId: Long) {
        charts.value = charts.value + chart
    }

    override suspend fun removeNotSeenIn(scanId: Long) = Unit
}

class FakeLibraryFolderStore(initial: String? = null) : LibraryFolderStore {
    override val rootUri = MutableStateFlow(initial)

    override suspend fun setRootUri(uri: String) {
        rootUri.value = uri
    }
}

class FakeFolderAccess : FolderAccess {
    val persisted = mutableListOf<String>()
    var failure: Exception? = null

    override fun persistReadAccess(folderUri: String) {
        failure?.let { throw it }
        persisted += folderUri
    }
}

/** Importador controlável: [nextResult] decide o que acontece; [gate] segura a importação no meio. */
class FakeChartImporter : ChartImporter {
    val calls = mutableListOf<String>()
    var nextResult: () -> ImportReport = { ImportReport(importedCount = 0, failures = emptyList()) }
    var progressToReport: Int? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun import(rootUri: String, onProgress: (Int) -> Unit): ImportReport {
        calls += rootUri
        progressToReport?.let(onProgress)
        gate?.await()
        return nextResult()
    }
}
```

`LibraryViewModelTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.library

import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val repository = FakeChartRepository()
    private val folderStore = FakeLibraryFolderStore()
    private val folderAccess = FakeFolderAccess()
    private val importer = FakeChartImporter()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): LibraryViewModel {
        val viewModel = LibraryViewModel(repository, folderStore, importer, folderAccess)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun withoutFolderAsksToChooseOne() = runTest {
        val state = viewModel().uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasFolder)
    }

    @Test
    fun pickingAFolderPersistsAccessRemembersItAndImports() = runTest {
        importer.nextResult = {
            repository.charts.value = listOf(chart("a"))
            ImportReport(importedCount = 1, failures = listOf(ImportFailure("Quebrada", "linha 3: nota inválida")))
        }
        val viewModel = viewModel()

        viewModel.onFolderPicked("content://pasta")

        val state = viewModel.uiState.value
        assertEquals(listOf("content://pasta"), folderAccess.persisted)
        assertEquals("content://pasta", folderStore.rootUri.value)
        assertEquals(listOf("content://pasta"), importer.calls)
        assertTrue(state.hasFolder)
        assertFalse(state.isImporting)
        assertEquals(listOf("a"), state.charts.map { it.id })
        assertEquals(1, state.report?.importedCount)
    }

    @Test
    fun importingShowsProgress() = runTest {
        importer.gate = CompletableDeferred()
        importer.progressToReport = 3
        val viewModel = viewModel()

        viewModel.onFolderPicked("content://pasta")

        assertTrue(viewModel.uiState.value.isImporting)
        assertEquals(3, viewModel.uiState.value.scannedFolders)
        importer.gate!!.complete(Unit)
        assertFalse(viewModel.uiState.value.isImporting)
    }

    @Test
    fun rescanImportsTheRememberedFolder() = runTest {
        folderStore.rootUri.value = "content://guardada"
        val viewModel = viewModel()

        viewModel.onRescan()

        assertEquals(listOf("content://guardada"), importer.calls)
    }

    @Test
    fun lostAccessShowsErrorAndKeepsTheList() = runTest {
        folderStore.rootUri.value = "content://pasta"
        repository.charts.value = listOf(chart("a"))
        importer.nextResult = { throw FolderAccessLostException("revogada") }
        val viewModel = viewModel()

        viewModel.onRescan()

        assertTrue(viewModel.uiState.value.folderAccessLost)
        assertEquals(listOf("a"), viewModel.uiState.value.charts.map { it.id })
    }

    @Test
    fun folderThatCannotBePersistedShowsError() = runTest {
        folderAccess.failure = SecurityException("não persistível")
        val viewModel = viewModel()

        viewModel.onFolderPicked("content://pasta")

        assertTrue(viewModel.uiState.value.folderAccessLost)
        assertTrue(importer.calls.isEmpty())
    }

    @Test
    fun dismissingTheReportClearsIt() = runTest {
        folderStore.rootUri.value = "content://pasta"
        importer.nextResult = { ImportReport(importedCount = 2, failures = emptyList()) }
        val viewModel = viewModel()
        viewModel.onRescan()

        viewModel.onReportDismissed()

        assertNull(viewModel.uiState.value.report)
    }
}
```

Em `settings.gradle.kts`, acrescente `include(":feature:library")`.

Run: `./scripts/gw :feature:library:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'LibraryViewModel'`).

- [ ] **Step 2: Implementar**

`LibraryViewModel.kt`:
```kotlin
package com.rodrigomachado.riff.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Chart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val isLoading: Boolean = true,
    val hasFolder: Boolean = false,
    val charts: List<Chart> = emptyList(),
    val isImporting: Boolean = false,
    val scannedFolders: Int = 0,
    val report: ImportReport? = null,
    val folderAccessLost: Boolean = false,
)

private data class ImportState(
    val isImporting: Boolean = false,
    val scannedFolders: Int = 0,
    val report: ImportReport? = null,
    val folderAccessLost: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    chartRepository: ChartRepository,
    private val folderStore: LibraryFolderStore,
    private val importer: ChartImporter,
    private val folderAccess: FolderAccess,
) : ViewModel() {
    private val importState = MutableStateFlow(ImportState())

    val uiState: StateFlow<LibraryUiState> =
        combine(chartRepository.observeCharts(), folderStore.rootUri, importState) { charts, rootUri, import ->
            LibraryUiState(
                isLoading = false,
                hasFolder = rootUri != null,
                charts = charts,
                isImporting = import.isImporting,
                scannedFolders = import.scannedFolders,
                report = import.report,
                folderAccessLost = import.folderAccessLost,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    @Suppress("SwallowedException") // a exceção vira o estado de erro da tela
    fun onFolderPicked(uri: String) {
        try {
            folderAccess.persistReadAccess(uri)
        } catch (e: SecurityException) {
            importState.value = ImportState(folderAccessLost = true)
            return
        }
        viewModelScope.launch {
            folderStore.setRootUri(uri)
            runImport(uri)
        }
    }

    fun onRescan() {
        viewModelScope.launch { folderStore.rootUri.first()?.let { runImport(it) } }
    }

    fun onReportDismissed() {
        importState.update { it.copy(report = null) }
    }

    @Suppress("SwallowedException") // a exceção vira o estado de erro da tela
    private suspend fun runImport(uri: String) {
        if (importState.value.isImporting) return
        importState.value = ImportState(isImporting = true)
        importState.value = try {
            val report = importer.import(uri) { scanned -> importState.update { it.copy(scannedFolders = scanned) } }
            ImportState(report = report)
        } catch (e: FolderAccessLostException) {
            ImportState(folderAccessLost = true)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :feature:library:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 7 testes passando.

```bash
git add -A
git commit -m "feat(library): ViewModel da biblioteca com importação, progresso, relatório e perda de acesso" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Tela da Biblioteca

**Files:**
- Create: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryScreen.kt`
- Create: `feature/library/src/main/res/values/strings.xml`, `feature/library/src/main/res/values-en/strings.xml`
- Test: `feature/library/src/test/kotlin/com/rodrigomachado/riff/feature/library/LibraryContentTest.kt`
- Test: `feature/library/src/test/kotlin/com/rodrigomachado/riff/feature/library/LibraryScreenshotTest.kt`

**Interfaces:**
- Consumes: `LibraryUiState`, `LibraryViewModel` (Task 7); componentes do `:core:ui`.
- Produces: `@Composable fun LibraryScreen(modifier: Modifier = Modifier, viewModel: LibraryViewModel = hiltViewModel())` e o composable sem estado `LibraryContent(state, onPickFolder, onRescan, onReportDismissed, modifier)`.

- [ ] **Step 1: Strings**

`feature/library/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="library_title">Biblioteca</string>
    <string name="library_no_folder_title">Nenhuma pasta escolhida</string>
    <string name="library_no_folder_message">Escolha a pasta onde ficam seus mapas do Clone Hero.</string>
    <string name="library_choose_folder">Escolher pasta</string>
    <string name="library_empty_title">Nenhum mapa encontrado</string>
    <string name="library_empty_message">A pasta não tem subpastas com notes.chart ou notes.mid.</string>
    <string name="library_choose_another_folder">Escolher outra pasta</string>
    <string name="library_importing">Lendo pastas… %1$d</string>
    <string name="library_access_lost">Perdi o acesso à pasta. Escolha-a de novo.</string>
    <string name="library_rescan">Reimportar</string>
    <string name="library_change_folder">Trocar pasta</string>
    <plurals name="library_report_imported">
        <item quantity="one">%d mapa importado</item>
        <item quantity="other">%d mapas importados</item>
    </plurals>
    <plurals name="library_report_failed">
        <item quantity="one">%d pasta com erro</item>
        <item quantity="other">%d pastas com erro</item>
    </plurals>
    <string name="library_report_ok">OK</string>
    <string name="library_unknown_artist">Artista desconhecido</string>
    <string name="library_difficulty_easy">Fácil</string>
    <string name="library_difficulty_medium">Médio</string>
    <string name="library_difficulty_hard">Difícil</string>
    <string name="library_difficulty_expert">Expert</string>
</resources>
```

`feature/library/src/main/res/values-en/strings.xml`:
```xml
<resources>
    <string name="library_title">Library</string>
    <string name="library_no_folder_title">No folder chosen</string>
    <string name="library_no_folder_message">Choose the folder that holds your Clone Hero charts.</string>
    <string name="library_choose_folder">Choose folder</string>
    <string name="library_empty_title">No charts found</string>
    <string name="library_empty_message">The folder has no subfolders with notes.chart or notes.mid.</string>
    <string name="library_choose_another_folder">Choose another folder</string>
    <string name="library_importing">Reading folders… %1$d</string>
    <string name="library_access_lost">Lost access to the folder. Please choose it again.</string>
    <string name="library_rescan">Rescan</string>
    <string name="library_change_folder">Change folder</string>
    <plurals name="library_report_imported">
        <item quantity="one">%d chart imported</item>
        <item quantity="other">%d charts imported</item>
    </plurals>
    <plurals name="library_report_failed">
        <item quantity="one">%d folder with errors</item>
        <item quantity="other">%d folders with errors</item>
    </plurals>
    <string name="library_report_ok">OK</string>
    <string name="library_unknown_artist">Unknown artist</string>
    <string name="library_difficulty_easy">Easy</string>
    <string name="library_difficulty_medium">Medium</string>
    <string name="library_difficulty_hard">Hard</string>
    <string name="library_difficulty_expert">Expert</string>
</resources>
```

- [ ] **Step 2: Testes que falham**

`LibraryContentTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class LibraryContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var picks = 0
    private var rescans = 0
    private var dismissals = 0

    private fun show(state: LibraryUiState) = composeRule.setContent {
        RiffTheme {
            LibraryContent(
                state = state,
                onPickFolder = { picks++ },
                onRescan = { rescans++ },
                onReportDismissed = { dismissals++ },
            )
        }
    }

    @Test
    fun withoutFolderOffersToChooseOne() {
        show(LibraryUiState(isLoading = false, hasFolder = false))

        composeRule.onNodeWithText("Nenhuma pasta escolhida").assertExists()
        composeRule.onNodeWithText("Escolher pasta").performClick()

        assertEquals(1, picks)
    }

    @Test
    fun listShowsChartsWithUnknownArtistFallbackAndHighestDifficulty() {
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("a", title = "Sem Artista", artist = "", difficulties = setOf(Difficulty.EASY, Difficulty.HARD))),
            ),
        )

        composeRule.onNodeWithText("Sem Artista").assertExists()
        composeRule.onNodeWithText("Artista desconhecido").assertExists()
        composeRule.onNodeWithText("DIFÍCIL").assertExists()
        composeRule.onNodeWithText("Reimportar").performClick()
        assertEquals(1, rescans)
    }

    @Test
    fun reportShowsCountsFailuresAndCanBeDismissed() {
        show(
            LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(chart("a")),
                report = ImportReport(importedCount = 1, failures = listOf(ImportFailure("Quebrada", "linha 3: nota inválida"))),
            ),
        )

        composeRule.onNodeWithText("1 mapa importado").assertExists()
        composeRule.onNodeWithText("1 pasta com erro").assertExists()
        composeRule.onNodeWithText("Quebrada: linha 3: nota inválida").assertExists()
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(1, dismissals)
    }

    @Test
    fun lostAccessAsksToChooseTheFolderAgain() {
        show(LibraryUiState(isLoading = false, hasFolder = true, folderAccessLost = true))

        composeRule.onNodeWithText("Perdi o acesso à pasta. Escolha-a de novo.").assertExists()
        composeRule.onNodeWithText("Tentar de novo").performClick()
        assertEquals(1, picks)
    }

    @Test
    fun importingShowsHowManyFoldersWereRead() {
        show(LibraryUiState(isLoading = false, hasFolder = true, isImporting = true, scannedFolders = 12))

        composeRule.onNodeWithText("Lendo pastas… 12").assertExists()
    }
}
```

`LibraryScreenshotTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.data.importer.ImportFailure
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class LibraryScreenshotTest {
    @Test
    fun listWithReport() = snapshot("Library_list") {
        LibraryContent(
            state = LibraryUiState(
                isLoading = false,
                hasFolder = true,
                charts = listOf(
                    chart("1", "Through the Fire and Flames", "DragonForce", setOf(Difficulty.HARD, Difficulty.EXPERT)),
                    chart("2", "Bohemian Rhapsody", "Queen", setOf(Difficulty.EASY, Difficulty.MEDIUM)),
                    chart("3", "Pasta Sem Nome", "", setOf(Difficulty.EASY)),
                ),
                report = ImportReport(3, listOf(ImportFailure("Quebrada", "linha 3: nota inválida: abc = N 0 0"))),
            ),
            onPickFolder = {},
            onRescan = {},
            onReportDismissed = {},
        )
    }

    @Test
    fun noFolder() = snapshot("Library_no_folder") {
        LibraryContent(LibraryUiState(isLoading = false), onPickFolder = {}, onRescan = {}, onReportDismissed = {})
    }

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme { StageBackground(Modifier.fillMaxSize()) { content() } }
        }
    }
}
```

Run: `./scripts/gw :feature:library:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference 'LibraryContent'`).

- [ ] **Step 3: Implementar**

`LibraryScreen.kt`:
```kotlin
package com.rodrigomachado.riff.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.data.importer.ImportReport
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

@Composable
fun LibraryScreen(modifier: Modifier = Modifier, viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onFolderPicked(it.toString()) }
    }
    LibraryContent(
        state = state,
        onPickFolder = { pickFolder.launch(null) },
        onRescan = viewModel::onRescan,
        onReportDismissed = viewModel::onReportDismissed,
        modifier = modifier,
    )
}

@Composable
fun LibraryContent(
    state: LibraryUiState,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onReportDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        PosterTitle(
            stringResource(R.string.library_title),
            Modifier.padding(top = Spacing.md),
            style = MaterialTheme.typography.displaySmall,
        )
        when {
            state.isLoading -> LoadingState()
            state.folderAccessLost -> ErrorState(stringResource(R.string.library_access_lost), onRetry = onPickFolder)
            !state.hasFolder -> EmptyState(
                title = stringResource(R.string.library_no_folder_title),
                message = stringResource(R.string.library_no_folder_message),
                actionLabel = stringResource(R.string.library_choose_folder),
                onAction = onPickFolder,
            )
            state.isImporting -> LoadingState(message = stringResource(R.string.library_importing, state.scannedFolders))
            state.charts.isEmpty() && state.report?.failures.isNullOrEmpty() -> EmptyState(
                title = stringResource(R.string.library_empty_title),
                message = stringResource(R.string.library_empty_message),
                actionLabel = stringResource(R.string.library_choose_another_folder),
                onAction = onPickFolder,
            )
            else -> ChartList(state.charts, state.report, onPickFolder, onRescan, onReportDismissed)
        }
    }
}

@Composable
private fun ChartList(
    charts: List<Chart>,
    report: ImportReport?,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onReportDismissed: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                PosterButton(stringResource(R.string.library_rescan), onClick = onRescan)
                TextButton(onClick = onPickFolder) {
                    Text(stringResource(R.string.library_change_folder), color = RiffColors.Paper)
                }
            }
        }
        report?.let { item { ImportReportCard(it, onReportDismissed) } }
        items(charts, key = { it.id }) { chart ->
            val highest = chart.difficulties.maxOrNull()
            TrackCard(
                title = chart.title,
                artist = chart.artist.ifBlank { stringResource(R.string.library_unknown_artist) },
                badge = if (highest == null) null else { { StatusBadge(stringResource(highest.label()), BadgeTone.NEUTRAL) } },
            )
        }
    }
}

@Composable
private fun ImportReportCard(report: ImportReport, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(RiffColors.Paper, MaterialTheme.shapes.small).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            pluralStringResource(R.plurals.library_report_imported, report.importedCount, report.importedCount),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Ink,
        )
        if (report.failures.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.library_report_failed, report.failures.size, report.failures.size),
                style = MaterialTheme.typography.titleSmall,
                color = RiffColors.StampError,
            )
            report.failures.forEach { failure ->
                Text("${failure.folderName}: ${failure.reason}", style = MaterialTheme.typography.bodySmall, color = RiffColors.Ink)
            }
        }
        PosterButton(stringResource(R.string.library_report_ok), onClick = onDismiss)
    }
}

private fun Difficulty.label(): Int = when (this) {
    Difficulty.EASY -> R.string.library_difficulty_easy
    Difficulty.MEDIUM -> R.string.library_difficulty_medium
    Difficulty.HARD -> R.string.library_difficulty_hard
    Difficulty.EXPERT -> R.string.library_difficulty_expert
}
```
(O texto "pastas com erro" usa o carimbo de erro sobre papel. Contraste é simétrico: dá os mesmos 6,7 do par "papel sobre carimbo de erro" que o `ContrastTest` já cobre.)

- [ ] **Step 4: Testes, screenshots e verificação visual**

Run: `./scripts/gw detekt -PdetektAutoCorrect`, depois `./scripts/gw :feature:library:recordRoborazziDebug` e `./scripts/gw detekt :feature:library:testDebugUnitTest :feature:library:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`; `LibraryContentTest` (5), `LibraryViewModelTest` (7) e 2 screenshots.

Abra `feature/library/src/test/screenshots/Library_list.png` e `Library_no_folder.png` com a ferramenta Read: letreiro "Biblioteca", botão "Reimportar", cartão de relatório em papel com a falha legível, cartões de música com o carimbo de dificuldade, "Artista desconhecido" no terceiro. Corrija e regrave se algo estiver ilegível.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(library): tela da biblioteca com escolha de pasta, lista, relatório e estados" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: App com Hilt e a Biblioteca como tela inicial

**Files:**
- Modify: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/com/rodrigomachado/riff/MainActivity.kt`
- Create: `app/src/main/kotlin/com/rodrigomachado/riff/RiffApplication.kt`
- Delete: `app/src/main/kotlin/com/rodrigomachado/riff/CatalogScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` (só sobra `app_name`)

**Interfaces:**
- Consumes: `LibraryScreen` (Task 8); módulos Hilt das Tasks 3 a 6.

- [ ] **Step 1: App**

`app/build.gradle.kts`: no bloco `plugins`, acrescente `alias(libs.plugins.riff.android.hilt)`; em `dependencies`, acrescente `implementation(project(":feature:library"))`.

`RiffApplication.kt`:
```kotlin
package com.rodrigomachado.riff

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RiffApplication : Application()
```

`AndroidManifest.xml`: no `<application`, acrescente `android:name=".RiffApplication"`.

`MainActivity.kt`:
```kotlin
package com.rodrigomachado.riff

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.feature.library.LibraryScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O tema é sempre escuro: os ícones das barras do sistema ficam claros
        // mesmo com o aparelho no modo claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    LibraryScreen(Modifier.safeDrawingPadding())
                }
            }
        }
    }
}
```

Apague `CatalogScreen.kt`. Em `values/strings.xml` deixe só `app_name`; em `values-en/strings.xml`, só `<resources />`.

- [ ] **Step 2: Verificação completa**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt lintDebug test koverVerify :core:ui:verifyRoborazziDebug :feature:library:verifyRoborazziDebug assembleDebug`
Expected: `BUILD SUCCESSFUL`, lint sem erros. O Hilt gera o grafo sem dependência faltando.

- [ ] **Step 3: Conferir no emulador com mapas de verdade**

Monte duas pastas de exemplo e envie para o emulador:
```bash
SC=<scratchpad>/riff-sample; rm -rf $SC; mkdir -p "$SC/RiffCharts/Banda - Musica Boa" "$SC/RiffCharts/Quebrada"
printf '[Song]\n{\n  Name = "Musica Boa"\n  Artist = "Banda Teste"\n}\n[ExpertSingle]\n{\n  192 = N 0 0\n  384 = N 1 0\n}\n' > "$SC/RiffCharts/Banda - Musica Boa/notes.chart"
printf '[ExpertSingle]\n{\n  abc = N 0 0\n}\n' > "$SC/RiffCharts/Quebrada/notes.chart"
ADB="/mnt/c/Users/Rodrigo Machado/AppData/Local/Android/Sdk/platform-tools/adb.exe"
"$ADB" push "$(wslpath -w "$SC/RiffCharts")" /sdcard/Download/
"$ADB" install -r 'app\build\outputs\apk\debug\app-debug.apk'
"$ADB" shell am start -n com.rodrigomachado.riff/.MainActivity
```
Toque em "Escolher pasta", navegue até `Download/RiffCharts` e confirme o acesso. Para dirigir o seletor pelo `adb`, use `"$ADB" shell uiautomator dump /sdcard/ui.xml`, leia o XML com `"$ADB" exec-out cat /sdcard/ui.xml`, encontre os `bounds` dos textos ("Download", "RiffCharts", "Use this folder"/"Usar esta pasta", "Allow"/"Permitir") e toque no centro com `"$ADB" shell input tap X Y`.

Capture a tela (`"$ADB" exec-out screencap -p > <scratchpad>/biblioteca.png`) e abra com a ferramenta Read. Esperado: relatório "1 chart imported / 1 folder with errors" (o emulador está em inglês) com "Quebrada: …", e o cartão "Musica Boa / Banda Teste" com o carimbo "EXPERT". Feche e reabra o app (`am force-stop` e `am start`): a lista continua lá, sem pedir a pasta de novo. Toque em "Rescan" e confira que nada duplica. Sem `FATAL EXCEPTION` no `logcat`.

Se não for possível dirigir o seletor pelo `adb`, registre no ledger e deixe essa conferência para o usuário.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(app): Hilt e a Biblioteca como tela inicial" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: README e verificação final

**Files:**
- Modify: `README.md`, `.github/workflows/ci.yml`

- [ ] **Step 1: README**

Na tabela de módulos do `README.md`, troque a linha do `:app` e acrescente as novas:
```markdown
| `:app` | Activity e Application com Hilt; abre na Biblioteca |
| `:core:database` | Room: mapas, dificuldades, casamentos com faixas do Spotify e recordes (esquemas em `schemas/`) |
| `:core:datastore` | Preferências do jogador e pasta da biblioteca |
| `:core:data` | Repositório de mapas, acesso a pastas por SAF e importador de mapas |
| `:feature:library` | Tela da Biblioteca: escolher pasta, importar, lista e relatório |
```
Na linha do `:core:chart`, acrescente ao fim: `, e o casamento entre faixa do Spotify e mapa`.
Na seção "Status", troque o parágrafo por:
```markdown
Fundação, identidade visual e biblioteca prontas. Próximos planos: gameplay com relógio local, integração com o Spotify (inclui o casamento manual), calibração e configurações.
```

- [ ] **Step 2: CI**

Em `.github/workflows/ci.yml`, a linha `run:` continua `./gradlew detekt lintDebug test koverVerify assembleDebug` (os módulos novos entram sozinhos pelo `test`). Nenhuma mudança se já estiver assim.

- [ ] **Step 3: Verificação e commit**

Run: `./scripts/gw detekt lintDebug test koverVerify :core:ui:verifyRoborazziDebug :feature:library:verifyRoborazziDebug assembleDebug`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A
git commit -m "docs: README com os módulos de dados e a biblioteca" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
