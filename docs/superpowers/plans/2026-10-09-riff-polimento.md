# Polimento (desempenho, Baseline Profile e README) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Medir o desempenho do Riff no Galaxy S25, tirar as alocações por quadro da pista, gerar o Baseline Profile e reescrever o README como guia de uso.

**Architecture:** Um módulo `:baselineprofile` (`com.android.test`) percorre o app com UiAutomator: gera o Baseline Profile e mede abertura e quadros com Macrobenchmark, antes e depois das correções. As correções concentram formas e pincéis da pista num `StageCache` (`:core:ui`) refeito só quando o tamanho da tela muda; gemas, alvos e chamas são desenhados com pincéis em "tamanho 1" e transformação; o placar passa a ler um `HudState` que só muda quando os valores mudam.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1, Compose (BOM 2026.09.00), `androidx.baselineprofile` 1.5.0, `benchmark-macro-junit4` 1.5.0, UiAutomator 2.4.0, `profileinstaller` 1.4.1, Robolectric + Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-09-polimento-design.md`

## Global Constraints

- "60 fps no gameplay em um aparelho intermediário, sem alocar memória no loop de frame." (spec principal)
- Meta no percurso da partida, com perfil, depois das correções: `frameDurationCpuMs` p90 abaixo de 8,3 ms e `frameOverrunMs` p99 igual ou menor que antes (spec §4).
- Aparência igual: `Gameplay_stage`, `Stage`, `Stage_closeup` e `Stage_flame_frames` continuam passando no `verifyRoborazziDebug`; regravar só com a diferença conferida à mão e um `Ruling:` no ledger (spec §3).
- A medição não pode apagar os dados do usuário (biblioteca, recordes, ajustes): o app medido é assinado com a chave de debug e os APKs ficam instalados depois da execução; a versão de debug é reinstalada no fim (spec §2).
- README e Baseline Profile são documentos vivos (spec §1, §5, §6).
- Strings em resources (pt-BR padrão, en segunda língua); detekt (MaxLineLength 120, LongMethod 60, ReturnCount 4, ComplexCondition 3, TooManyFunctions 11) e lint sem erros; testes Robolectric com `sdk = [35]` nos screenshots.
- Comandos de build pelo `./scripts/gw`; aparelho `RQCY106ML5P`, adb em `/mnt/c/Users/Rodrigo Machado/AppData/Local/Android/Sdk/platform-tools/adb.exe`, `install --user 0`.
- Nunca usar assets do Guitar Hero nem enviar nada para fora; o repositório não tem remote.

## Review Focus

1. **Rodar a medição apaga os dados do jogador** (o `connectedAndroidTest` desinstala o app no fim): o script passa `android.injected.androidTest.leaveApksInstalledAfterRun=true` e faz cópia de segurança antes. Verificação: Task 3, Step 6 confere a biblioteca e os ajustes no aparelho depois da medição.
2. **Canhoto com pincéis por trilha**: a cor da gema vem da trilha lógica e a posição da trilha na tela; trocar uma pela outra pinta as notas erradas. Teste: `GameplayScreenshotTest.stageMidSongLeftHandedWithAMissedNote` gravado antes das correções (Task 4, Step 1) e verificado nas Tasks 4–7.
3. **Gema perdida perde o tom esmaecido** (o alfa era aplicado à cor antes de montar os degradês). Teste: o mesmo screenshot da linha 2 tem uma nota perdida.
4. **Tela muda de tamanho no meio da partida** (rotação, tela dividida): o cache precisa refazer geometria e contorno. Teste: `StageCacheTest.aNewSizeRebuildsTheGeometryAndTheHighway` (Task 4).
5. **Velocidade mínima das notas (0,5×) aumenta os trastes visíveis**: o array dos trastes não pode estourar. Teste: `StageRendererTest.moreFretsThanTheArrayHoldsAreCappedWithoutCrashing` (Task 4).

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
|---|---|
| `baselineprofile/build.gradle.kts` | Módulo de teste: plugins, alvo `:app`, dependências |
| `baselineprofile/src/main/kotlin/.../Journeys.kt` | Percursos: abertura, biblioteca, partida |
| `baselineprofile/src/main/kotlin/.../BaselineProfileGenerator.kt` | Gera o perfil |
| `baselineprofile/src/main/kotlin/.../StartupBenchmark.kt` | Mede a abertura |
| `baselineprofile/src/main/kotlin/.../GameplayBenchmark.kt` | Mede os quadros da partida |
| `scripts/desempenho.sh` | Cópia de segurança, gerar perfil, medir, resumo, reinstalar debug |
| `scripts/resumo_benchmark.py` | Lê o JSON do Macrobenchmark e imprime p50/p90/p99 |
| `core/ui/.../stage/StageCache.kt` | Formas e pincéis prontos da pista |
| `core/ui/.../stage/StageDrawing.kt` | Desenho da pista, gemas, alvos, chama (sem alocar) |
| `core/ui/.../stage/Flame.kt` | Quadro da chama sem objeto novo |
| `feature/gameplay/.../StageRenderer.kt` | Desenho da partida com o cache; trastes num array |
| `feature/gameplay/.../HudState.kt` | Valores do placar em estado do Compose, escritos só quando mudam |
| `feature/gameplay/.../GameplayScreen.kt` | `remember` do cache e do `HudState`; placar lê o `HudState` |
| `README.md` | Guia de uso e desenvolvimento |

---

### Task 1: Etiquetas para os percursos automáticos

**Files:**
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/MainActivity.kt`
- Modify: `feature/nowplaying/src/main/kotlin/com/rodrigomachado/riff/feature/nowplaying/NowPlayingScreen.kt`
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryScreen.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/DifficultyDialog.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayScreen.kt`
- Test: `NowPlayingContentTest`, `LibraryContentTest`, `DifficultyDialogTest`, `GameStageNoticeTest`

**Interfaces:**
- Produces (os percursos da Task 2 usam estas strings literais, porque o módulo de teste não enxerga as classes dos módulos):
  - `NowPlayingTestTags.OPEN_LIBRARY = "nowplaying_open_library"`
  - `LibraryTestTags.LIST = "library_list"`
  - `DifficultyDialogTestTags.OPTION = "difficulty_option"` (todas as opções; a primeira é a de menor dificuldade)
  - `GameplayTestTags.STAGE = "gameplay_stage"` (já existe), `GameplayTestTags.PAUSE = "gameplay_pause"`, `GameplayTestTags.EXIT = "gameplay_exit"`
  - As etiquetas viram `resource-id` para o UiAutomator (`testTagsAsResourceId = true` na raiz da `MainActivity` e no diálogo, que é outra janela).

- [ ] **Step 1: Write the failing tests**

Em `NowPlayingContentTest` (o `show` e o estado já existem no arquivo):

```kotlin
    @Test
    fun theLibraryButtonHasATagForTheAutomatedJourneys() {
        show(NowPlayingUiState.Unavailable(SpotifyError.NOT_INSTALLED))

        composeRule.onNodeWithTag(NowPlayingTestTags.OPEN_LIBRARY).performClick()

        assertTrue(libraryOpened)
    }
```

Em `LibraryContentTest`:

```kotlin
    @Test
    fun theChartListHasATagForTheAutomatedJourneys() {
        show(LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(chart("a", title = "Música"))))

        composeRule.onNodeWithTag(LibraryTestTags.LIST).assertExists()
    }
```

Em `DifficultyDialogTest`:

```kotlin
    @Test
    fun everyOptionHasTheSameTagAndTheFirstIsTheEasiest() {
        show()

        val options = composeRule.onAllNodesWithTag(DifficultyDialogTestTags.OPTION)
        options.assertCountEquals(2)
        options[0].performClick()

        assertEquals(0, chosen)
    }
```

Em `GameStageNoticeTest`:

```kotlin
    @Test
    fun pauseAndExitHaveTagsForTheAutomatedJourneys() {
        var paused = false
        var exited = false
        val session = GameSession(listOf(note(60_000)), FakeSongPlayer(), FakeSfxPlayer(), FakeTime(), audioOffsetMs = 0)
        var isPaused by mutableStateOf(false)
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    GameplayUiState.Ready(
                        "Música",
                        "Banda",
                        Difficulty.EXPERT,
                        session,
                        Settings.DEFAULT,
                        isPaused = isPaused,
                    ),
                    reducedMotion = true,
                    onPause = {
                        paused = true
                        isPaused = true
                    },
                    onResume = {},
                    onExit = { exited = true },
                    onSessionFinished = {},
                    animate = false,
                )
            }
        }

        composeRule.onNodeWithTag(GameplayTestTags.PAUSE).performClick()
        composeRule.onNodeWithTag(GameplayTestTags.EXIT).performClick()

        assertTrue(paused)
        assertTrue(exited)
    }
```

Imports que faltarem: `androidx.compose.ui.test.onNodeWithTag`, `onAllNodesWithTag`, `assertCountEquals`, `androidx.compose.runtime.getValue/setValue/mutableStateOf`, `org.junit.Assert.assertTrue`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./scripts/gw :feature:nowplaying:testDebugUnitTest :feature:library:testDebugUnitTest :core:ui:testDebugUnitTest :feature:gameplay:testDebugUnitTest --continue -q`
Expected: FAIL na compilação ("Unresolved reference 'NowPlayingTestTags'", "'LibraryTestTags'", "'DifficultyDialogTestTags'", "'PAUSE'").

- [ ] **Step 3: Implement**

`NowPlayingScreen.kt`: objeto de etiquetas no topo do arquivo e a etiqueta no botão:

```kotlin
object NowPlayingTestTags {
    const val OPEN_LIBRARY = "nowplaying_open_library"
}
```

```kotlin
            TextButton(onClick = onOpenLibrary, modifier = Modifier.testTag(NowPlayingTestTags.OPEN_LIBRARY)) {
                Text(stringResource(R.string.nowplaying_library), color = RiffColors.Paper)
            }
```

`LibraryScreen.kt`:

```kotlin
object LibraryTestTags {
    const val LIST = "library_list"
}
```

```kotlin
    LazyColumn(
        modifier = Modifier.testTag(LibraryTestTags.LIST),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
```

`DifficultyDialog.kt`:

```kotlin
object DifficultyDialogTestTags {
    /** Todas as opções; a primeira é a de menor dificuldade. */
    const val OPTION = "difficulty_option"
}
```

No `Column` do diálogo, acrescente ao modificador existente `.semantics { testTagsAsResourceId = true }` (o diálogo é outra janela: a propriedade da `MainActivity` não chega aqui) e nas opções:

```kotlin
            options.forEach { option ->
                PosterButton(
                    label(option),
                    onClick = { onChoose(option) },
                    modifier = Modifier.testTag(DifficultyDialogTestTags.OPTION),
                )
            }
```

`GameplayScreen.kt`, em `GameplayTestTags`:

```kotlin
object GameplayTestTags {
    const val STAGE = "gameplay_stage"
    const val PAUSE = "gameplay_pause"
    const val EXIT = "gameplay_exit"
}
```

e nos botões: `TextButton(onClick = onPause, modifier = Modifier.testTag(GameplayTestTags.PAUSE))` no placar e `TextButton(onClick = onExit, modifier = Modifier.testTag(GameplayTestTags.EXIT))` no menu de pausa.

`MainActivity.kt`: no `StageBackground` da raiz,

```kotlin
                StageBackground(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
```

Imports: `androidx.compose.ui.platform.testTag`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.semantics.testTagsAsResourceId`.

- [ ] **Step 4: Run tests to verify they pass**

Run: o mesmo do Step 2.
Expected: PASS (BUILD SUCCESSFUL).

- [ ] **Step 5: Commit**

```bash
git add app feature/nowplaying feature/library core/ui feature/gameplay
git commit -m "feat: etiquetas para os percursos automáticos de desempenho" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Módulo `:baselineprofile`, perfil no app e script de medição

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`
- Create: `baselineprofile/build.gradle.kts`
- Create: `baselineprofile/src/main/AndroidManifest.xml`
- Create: `baselineprofile/src/main/kotlin/com/rodrigomachado/riff/baselineprofile/Journeys.kt`
- Create: `baselineprofile/src/main/kotlin/com/rodrigomachado/riff/baselineprofile/BaselineProfileGenerator.kt`
- Create: `baselineprofile/src/main/kotlin/com/rodrigomachado/riff/baselineprofile/StartupBenchmark.kt`
- Create: `baselineprofile/src/main/kotlin/com/rodrigomachado/riff/baselineprofile/GameplayBenchmark.kt`
- Create: `scripts/desempenho.sh`, `scripts/resumo_benchmark.py`

**Interfaces:**
- Consumes: etiquetas da Task 1 (strings literais).
- Produces: `scripts/desempenho.sh perfil|medir|tudo` (usado nas Tasks 3 e 8); perfil gerado em `app/src/release/generated/baselineProfiles/` (local padrão do plugin para o variant `release`).

Este módulo roda só no aparelho: não tem teste unitário. O ciclo desta task é "compila, o detekt passa e o script mostra a ajuda"; a execução real é a Task 3.

- [ ] **Step 1: Versões e plugins no catálogo**

Em `gradle/libs.versions.toml`, em `[versions]`:

```toml
benchmark = "1.5.0"
profileinstaller = "1.4.1"
uiautomator = "2.4.0"
androidxTestExtJunit = "1.3.0"
```

em `[libraries]` (pule a de `androidx-test-ext-junit` se já existir com outro nome; use a existente):

```toml
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
androidx-benchmark-macro-junit4 = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "benchmark" }
androidx-test-uiautomator = { module = "androidx.test.uiautomator:uiautomator", version.ref = "uiautomator" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
```

em `[plugins]`:

```toml
android-test = { id = "com.android.test", version.ref = "agp" }
baselineprofile = { id = "androidx.baselineprofile", version.ref = "benchmark" }
```

`build.gradle.kts` (raiz), no bloco `plugins`:

```kotlin
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
```

`settings.gradle.kts`: `include(":baselineprofile")`.

- [ ] **Step 2: App com perfil e assinatura de debug no release**

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.riff.android.application.compose)
    alias(libs.plugins.riff.android.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.rodrigomachado.riff"
    defaultConfig {
        applicationId = "com.rodrigomachado.riff"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Uso pessoal: a mesma chave do debug deixa o app medido substituir o de debug sem apagar os dados.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // ... as existentes, mais:
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}
```

- [ ] **Step 3: O módulo**

`baselineprofile/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.rodrigomachado.riff.baselineprofile"
    // Mesmos valores de build-logic/.../RiffSdk.kt (não visível aqui); o gerador precisa de API 28+.
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
```

`baselineprofile/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

`Journeys.kt`:

```kotlin
package com.rodrigomachado.riff.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "com.rodrigomachado.riff"
const val TAG = "RiffDesempenho"

// Etiquetas das telas (testTag com testTagsAsResourceId); as classes ficam nos módulos do app.
private const val OPEN_LIBRARY = "nowplaying_open_library"
private const val LIBRARY_LIST = "library_list"
private const val DIFFICULTY_OPTION = "difficulty_option"
private const val STAGE = "gameplay_stage"
private const val PAUSE = "gameplay_pause"
private const val EXIT = "gameplay_exit"

private const val TIMEOUT_MS = 10_000L
private const val PLAY_MS = 20_000L
private const val TAP_EVERY_MS = 250L
private const val LANES = 5

// Linha de acerto e largura da pista nela (HighwayGeometry: strikeYFraction e strikeWidthFraction).
private const val STRIKE_Y_FRACTION = 0.82f
private const val STRIKE_WIDTH_FRACTION = 0.92f

/** Música do percurso da partida: argumento de instrumentação `riff.song`. */
fun songTitle(): String =
    InstrumentationRegistry.getArguments().getString("riff.song") ?: "Smells Like Teen Spirit"

/** Abertura: espera a tela inicial ("Tocando agora"). */
fun MacrobenchmarkScope.waitForHome() {
    checkNotNull(device.wait(Until.findObject(By.res(OPEN_LIBRARY)), TIMEOUT_MS)) { "a tela inicial não apareceu" }
}

/** Biblioteca: abre e rola a lista até o fim e de volta. */
fun MacrobenchmarkScope.browseLibrary() {
    device.findObject(By.res(OPEN_LIBRARY)).click()
    val list = checkNotNull(device.wait(Until.findObject(By.res(LIBRARY_LIST)), TIMEOUT_MS)) { "a biblioteca não abriu" }
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    list.fling(Direction.UP)
    device.waitForIdle()
}

/**
 * Partida, com a biblioteca aberta: escolhe [songTitle], a primeira dificuldade, toca 20 s numa trilha
 * diferente a cada 250 ms e sai pela pausa. Falso se a música não está na biblioteca.
 */
fun MacrobenchmarkScope.playSong(): Boolean {
    val song = device.wait(Until.findObject(By.text(songTitle())), TIMEOUT_MS) ?: return false
    song.click()
    checkNotNull(device.wait(Until.findObject(By.res(DIFFICULTY_OPTION)), TIMEOUT_MS)) { "sem diálogo de dificuldade" }
        .click()
    val stage = checkNotNull(device.wait(Until.findObject(By.res(STAGE)), TIMEOUT_MS)) { "a partida não abriu" }
    val bounds = stage.visibleBounds
    val strikeY = bounds.top + (bounds.height() * STRIKE_Y_FRACTION).toInt()
    val laneWidth = bounds.width() * STRIKE_WIDTH_FRACTION / LANES
    val left = bounds.left + bounds.width() * (1 - STRIKE_WIDTH_FRACTION) / 2
    val end = SystemClock.uptimeMillis() + PLAY_MS
    var lane = 0
    while (SystemClock.uptimeMillis() < end) {
        device.click((left + laneWidth * (lane + 0.5f)).toInt(), strikeY)
        lane = (lane + 1) % LANES
        SystemClock.sleep(TAP_EVERY_MS)
    }
    device.findObject(By.res(PAUSE))?.click()
    device.wait(Until.findObject(By.res(EXIT)), TIMEOUT_MS)?.click()
    device.waitForIdle()
    return true
}
```

`BaselineProfileGenerator.kt`:

```kotlin
package com.rodrigomachado.riff.baselineprofile

import android.util.Log
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Gera o Baseline Profile percorrendo abertura, biblioteca e uma partida (spec do polimento §2). */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        browseLibrary()
        if (!playSong()) Log.w(TAG, "'${songTitle()}' não está na biblioteca: perfil sem a partida")
    }
}
```

`StartupBenchmark.kt`:

```kotlin
package com.rodrigomachado.riff.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Abertura a frio, sem e com o perfil. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        waitForHome()
    }
}
```

`GameplayBenchmark.kt`:

```kotlin
package com.rodrigomachado.riff.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Quadros durante 20 s de partida, sem e com o perfil. */
@RunWith(AndroidJUnit4::class)
class GameplayBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun gameplayWithoutProfile() = gameplay(CompilationMode.None())

    @Test
    fun gameplayWithProfile() = gameplay(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun gameplay(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        iterations = 3,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForHome()
            browseLibrary()
        },
    ) {
        check(playSong()) { "'${songTitle()}' não está na biblioteca: importe a música ou passe -Priff.song" }
    }
}
```

- [ ] **Step 4: Script de medição e resumo**

`scripts/resumo_benchmark.py`:

```python
#!/usr/bin/env python3
"""Resumo do Macrobenchmark do Riff: p50/p90/p99 de cada métrica de cada teste.

Uso: python3 scripts/resumo_benchmark.py <pasta com *-benchmarkData.json>
"""
import glob
import json
import sys

PERCENTIS = ("P50", "P90", "P99")


def main(pasta):
    arquivos = glob.glob(f"{pasta}/**/*benchmarkData.json", recursive=True)
    if not arquivos:
        sys.exit(f"nenhum resultado em {pasta}")
    for arquivo in arquivos:
        for bench in json.load(open(arquivo))["benchmarks"]:
            print(f"{bench['className'].split('.')[-1]}.{bench['name']}")
            for nome, metrica in bench["metrics"].items():
                print(f"  {nome}: mediana {metrica['median']:.1f}")
            for nome, amostras in bench.get("sampledMetrics", {}).items():
                valores = " ".join(f"{p} {amostras[p]:.1f}" for p in PERCENTIS if p in amostras)
                print(f"  {nome}: {valores}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "baselineprofile/build/outputs")
```

`scripts/desempenho.sh`:

```bash
#!/usr/bin/env bash
# Gera o Baseline Profile e/ou mede o desempenho no aparelho conectado (spec do polimento §2).
# Uso: scripts/desempenho.sh perfil|medir|tudo
# O app medido substitui o de debug (mesma chave, mesmos dados); no fim o debug volta.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-/mnt/c/Users/Rodrigo Machado/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
SERIAL="${ANDROID_SERIAL:-RQCY106ML5P}"
PACOTE=com.rodrigomachado.riff
COPIA="build/copia-dados-riff"
# Sem isto, o connectedAndroidTest desinstala o app no fim e apaga biblioteca, recordes e ajustes.
MANTER=(-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true)

copia_de_seguranca() {
    mkdir -p "$COPIA"
    for arquivo in databases/riff.db databases/riff.db-wal databases/riff.db-shm \
        files/datastore/riff_settings.preferences_pb; do
        "$ADB" -s "$SERIAL" exec-out run-as "$PACOTE" cat "$arquivo" > "$COPIA/$(basename "$arquivo")" 2>/dev/null || true
    done
    echo "cópia dos dados em $COPIA"
}

perfil() {
    ./scripts/gw :app:generateReleaseBaselineProfile "${MANTER[@]}"
}

medir() {
    ./scripts/gw :baselineprofile:connectedBenchmarkReleaseAndroidTest "${MANTER[@]}" \
        -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
    python3 scripts/resumo_benchmark.py baselineprofile/build/outputs
}

volta_debug() {
    ./scripts/gw :app:assembleDebug -q
    "$ADB" -s "$SERIAL" install -r --user 0 "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"
}

case "${1:-}" in
    perfil) copia_de_seguranca; perfil; volta_debug ;;
    medir) copia_de_seguranca; medir; volta_debug ;;
    tudo) copia_de_seguranca; perfil; medir; volta_debug ;;
    *) echo "uso: $0 perfil|medir|tudo"; exit 2 ;;
esac
```

`chmod +x scripts/desempenho.sh`.

- [ ] **Step 5: Verify it builds**

Run: `./scripts/gw :baselineprofile:assemble :app:assembleRelease detekt -q`
Expected: BUILD SUCCESSFUL. Se o plugin `androidx.baselineprofile` 1.5.0 recusar o AGP 9.4.1, tente `1.6.0-alpha01` (a mais nova publicada) e registre `Ruling:` no ledger; se nenhuma funcionar, pare e avise (decisão do usuário).

Run: `./scripts/desempenho.sh`
Expected: `uso: scripts/desempenho.sh perfil|medir|tudo` e saída 2.

- [ ] **Step 6: Full suite**

Run: `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts settings.gradle.kts app/build.gradle.kts baselineprofile scripts/desempenho.sh scripts/resumo_benchmark.py
git commit -m "build: módulo baselineprofile, profileinstaller e script de medição" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Medição "antes" e primeiro perfil (precisa do usuário)

**Files:**
- Create: `app/src/release/generated/baselineProfiles/baseline-prof.txt` e `startup-prof.txt` (gerados)

**Interfaces:**
- Consumes: `scripts/desempenho.sh` (Task 2).
- Produces: números "antes" no ledger (abertura e partida, sem e com perfil).

- [ ] **Step 1: Pedir ao usuário** o celular conectado, desbloqueado, com a tela ligada, sem economia de bateria, e com "Smells Like Teen Spirit" na biblioteca. Confirmar `adb devices` com `RQCY106ML5P device`.

- [ ] **Step 2: Gerar o perfil e medir**

Run: `./scripts/desempenho.sh tudo 2>&1 | tee build/desempenho-antes.log`
Expected: BUILD SUCCESSFUL nas duas etapas, o resumo com `StartupBenchmark.startupWithoutProfile/WithProfile` (`timeToInitialDisplayMs`) e `GameplayBenchmark.gameplayWithoutProfile/WithProfile` (`frameDurationCpuMs` e `frameOverrunMs` com P50/P90/P99), e "Success" na reinstalação do debug.

Se o Macrobenchmark recusar por um aviso de ambiente (por exemplo `UNLOCKED`, `LOW-BATTERY`, `ACTIVITY-MISSING`), leia a mensagem: ajuste o aparelho se for fácil; se não, rode de novo com `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=<NOME>` (adicione ao `MANTER` do script) e registre `Ruling:`.

- [ ] **Step 3: Conferir o perfil**

Run: `ls app/src/release/generated/baselineProfiles/ && wc -l app/src/release/generated/baselineProfiles/*.txt && grep -c "com/rodrigomachado/riff/feature/gameplay" app/src/release/generated/baselineProfiles/baseline-prof.txt`
Expected: `baseline-prof.txt` (e `startup-prof.txt`) com milhares de linhas, e linhas do gameplay (> 0). Se der 0, o percurso da partida não rodou: olhe o logcat (`RiffDesempenho`).

- [ ] **Step 4: Registrar os números**

Append no ledger: `Task 3: antes — abertura sem/com perfil: <ms>/<ms>; partida sem perfil frameDurationCpuMs P50/P90/P99 <..>, frameOverrunMs P99 <..>; com perfil <..>`.

- [ ] **Step 5: Build com o perfil**

Run: `./scripts/gw :app:assembleRelease -q && unzip -l app/build/outputs/apk/release/app-release.apk | grep -c "baseline.prof"`
Expected: BUILD SUCCESSFUL e `1` ou mais (o perfil entrou no APK, em `assets/dexopt/`).

- [ ] **Step 6: Os dados do usuário continuam lá**

Run: `"$ADB" -s RQCY106ML5P exec-out run-as com.rodrigomachado.riff ls databases files/datastore`, depois abra o app (`monkey -p com.rodrigomachado.riff -c android.intent.category.LAUNCHER 1`) e a biblioteca pelo `/tmp/claude-1000/uip.sh`.
Expected: `riff.db` e `riff_settings.preferences_pb` presentes; a biblioteca lista "Smells Like Teen Spirit". Se algo sumiu, restaure de `build/copia-dados-riff/` (cat para `run-as`, com o app parado) e registre no ledger.

- [ ] **Step 7: Commit**

```bash
git add app/src/release/generated/baselineProfiles
git commit -m "perf: primeiro Baseline Profile (abertura, biblioteca e partida)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: `StageCache` e a pista sem alocar

**Files:**
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StageCache.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StageDrawing.kt` (`drawHighway`; cores passam a `internal`)
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/StageRenderer.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayScreen.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/StageCacheTest.kt` (novo), `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/StageRendererTest.kt` (novo), `StageScreenshotTest`, `GameplayScreenshotTest`

**Interfaces:**
- Produces:
  - `class StageCache` com `fun update(size: Size, density: Float): HighwayGeometry`, `val geometry: HighwayGeometry`, `val fretDistances: FloatArray`, `var fretCount: Int`; internos `outline`, `highwayBrush`, `outerStroke`, `innerStroke`, `laneLineWidth`, `fretLineWidth`, `chrome` (Tasks 5 e 6 acrescentam pincéis aqui).
  - `fun DrawScope.drawHighway(cache: StageCache)` (substitui `drawHighway(geometry, fretDistances)`).
  - `internal fun fillFretDistances(cache: StageCache, t: Long, visibleMs: Long, msPerUnit: Float)` em `StageRenderer.kt`.
  - `internal fun DrawScope.drawStage(cache: StageCache, session: GameSession, msPerUnit: Float, leftHanded: Boolean, reducedMotion: Boolean)`.

- [ ] **Step 1: Gravar a referência nova com o código de hoje** (Review Focus 2 e 3)

Em `GameplayScreenshotTest`, acrescente:

```kotlin
    @Test
    fun stageMidSongLeftHandedWithAMissedNote() {
        val time = FakeTime()
        val player = FakeSongPlayer().apply { positionMs = 3_700 }
        val notes = listOf(
            Note(3_500, 1, 0, false), // passa sem ser tocada: fica esmaecida
            Note(4_000, 2, 0, false),
            Note(4_450, 0, 0, false),
            Note(4_900, 1, 0, true),
            Note(5_350, 3, 900, false),
            Note(5_800, 4, 0, false),
        )
        val session = GameSession(notes, player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
            it.tick()
        }
        val settings = Settings.DEFAULT.copy(leftHanded = true)
        val state = GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, settings)
        captureRoboImage("src/test/screenshots/Gameplay_stage_lefthanded.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    GameStage(
                        state,
                        reducedMotion = false,
                        onPause = {},
                        onResume = {},
                        onExit = {},
                        onSessionFinished = {},
                        animate = false,
                    )
                }
            }
        }
    }
```

Run: `./scripts/gw :feature:gameplay:recordRoborazziDebug -q`, abra `feature/gameplay/src/test/screenshots/Gameplay_stage_lefthanded.png` (Read) e confira: pista espelhada (laranja à esquerda), uma gema vermelha esmaecida perto da linha de acerto. Commit só da imagem e do teste:

```bash
git add feature/gameplay/src/test
git commit -m "test: referência do palco canhoto com nota perdida, antes das otimizações" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 2: Write the failing tests**

`core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/stage/StageCacheTest.kt`:

```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StageCacheTest {
    @Test
    fun theSameSizeKeepsTheSameObjects() {
        val cache = StageCache()
        val geometry = cache.update(Size(720f, 1280f), density = 2f)
        val brush = cache.highwayBrush
        val stroke = cache.outerStroke

        assertSame(geometry, cache.update(Size(720f, 1280f), density = 2f))
        assertSame(brush, cache.highwayBrush)
        assertSame(stroke, cache.outerStroke)
    }

    @Test
    fun aNewSizeRebuildsTheGeometryAndTheHighway() {
        val cache = StageCache()
        val portrait = cache.update(Size(720f, 1280f), density = 2f)
        val brush = cache.highwayBrush

        val landscape = cache.update(Size(1280f, 720f), density = 2f)

        assertNotSame(portrait, landscape)
        assertNotSame(brush, cache.highwayBrush)
        assertEquals(1280f, landscape.size.width)
    }

    @Test
    fun aNewDensityRebuildsTheStrokes() {
        val cache = StageCache()
        cache.update(Size(720f, 1280f), density = 2f)

        cache.update(Size(720f, 1280f), density = 3f)

        assertEquals(21f, cache.outerStroke.width) // 7 dp
        assertEquals(3f, cache.laneLineWidth) // 1 dp
    }
}
```

`feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/StageRendererTest.kt`:

```kotlin
package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.ui.stage.StageCache
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StageRendererTest {
    @Test
    fun fretsEveryHalfSecondAheadOfTheSongTimeFillTheArray() {
        val cache = StageCache()

        fillFretDistances(cache, t = 1_200, visibleMs = 2_000, msPerUnit = 100f)

        assertEquals(4, cache.fretCount) // 1.500, 2.000, 2.500, 3.000
        assertArrayEquals(floatArrayOf(3f, 8f, 13f, 18f), cache.fretDistances.copyOf(4), 1e-4f)
    }

    @Test
    fun moreFretsThanTheArrayHoldsAreCappedWithoutCrashing() {
        val cache = StageCache()

        fillFretDistances(cache, t = 0, visibleMs = 1_000_000, msPerUnit = 1f)

        assertEquals(cache.fretDistances.size, cache.fretCount)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./scripts/gw :core:ui:testDebugUnitTest :feature:gameplay:testDebugUnitTest --continue -q`
Expected: FAIL na compilação ("Unresolved reference 'StageCache'", "'fillFretDistances'").

- [ ] **Step 4: Implement**

Em `StageDrawing.kt`, troque `private` por `internal` nas cores (`ChromeLight`, `ChromeMid`, `HighwayFar`, `HighwayNear`, `FlameRed`, `FlameOrange`, `FlameYellow`, `FlameCore`) e nas constantes `TILT`, `LINE_ALPHA`, `WEAVE_ALPHA`, `WEAVE_LONGITUDINAL`, `WEAVE_STEP`.

`StageCache.kt`:

```kotlin
package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/** Trastes que cabem no array: com a velocidade mínima (0,5×) aparecem uns 11. */
private const val MAX_FRETS = 32
private const val OUTER_STROKE_DP = 7f
private const val INNER_STROKE_DP = 3f
private const val FRET_LINE_DP = 2f

/**
 * Formas e pincéis da pista prontos, para desenhar cada quadro sem alocar (spec do polimento §3). O que
 * depende do tamanho da tela é refeito em [update] só quando o tamanho (ou a densidade) muda; gemas, alvos e
 * chamas usam pincéis em "tamanho 1", posicionados por transformação.
 */
class StageCache {
    private var builtFor: Size? = null
    private var builtDensity = 0f

    var geometry = HighwayGeometry(Size(1f, 1f))
        private set

    internal val outline = Path()
    internal var highwayBrush: Brush = Brush.verticalGradient(listOf(HighwayFar, HighwayNear))
        private set
    internal var outerStroke = Stroke(1f)
        private set
    internal var innerStroke = Stroke(1f)
        private set
    internal var laneLineWidth = 1f
        private set
    internal var fretLineWidth = 1f
        private set

    /** Trastes do quadro atual: as [fretCount] primeiras posições, em distância à frente da linha de acerto. */
    val fretDistances = FloatArray(MAX_FRETS)
    var fretCount = 0

    /** Aro cromado em tamanho 1 (de −1 a 1 na horizontal). */
    internal val chrome: Brush =
        Brush.horizontalGradient(listOf(ChromeLight, ChromeMid, ChromeLight), startX = -1f, endX = 1f)

    fun update(size: Size, density: Float): HighwayGeometry {
        if (size == builtFor && density == builtDensity) return geometry
        builtFor = size
        builtDensity = density
        val g = HighwayGeometry(size)
        geometry = g
        val far = g.visibleDistance
        val near = g.nearDistance
        outline.rewind()
        outline.moveTo(g.leftAt(far), g.yAt(far))
        outline.lineTo(g.leftAt(far) + g.widthAt(far), g.yAt(far))
        outline.lineTo(g.leftAt(near) + g.widthAt(near), g.yAt(near))
        outline.lineTo(g.leftAt(near), g.yAt(near))
        outline.close()
        highwayBrush = Brush.verticalGradient(listOf(HighwayFar, HighwayNear), g.yAt(far), g.yAt(near))
        outerStroke = Stroke(width = OUTER_STROKE_DP * density)
        innerStroke = Stroke(width = INNER_STROKE_DP * density)
        laneLineWidth = density
        fretLineWidth = FRET_LINE_DP * density
        return g
    }
}
```

Em `StageDrawing.kt`, substitua `drawHighway` inteira por:

```kotlin
/** Braço em perspectiva: fundo escuro, trama, linhas de trilha e dos trastes do [cache], moldura de papel e vermelho. */
fun DrawScope.drawHighway(cache: StageCache) {
    val geometry = cache.geometry
    val far = geometry.visibleDistance
    val near = geometry.nearDistance
    drawPath(cache.outline, cache.highwayBrush)
    val weave = RiffColors.Paper.copy(alpha = WEAVE_ALPHA)
    for (k in 0..WEAVE_LONGITUDINAL) along(geometry, k.toFloat() / WEAVE_LONGITUDINAL, weave, 1f)
    var distance = near
    while (distance < far) {
        across(geometry, distance, weave, 1f)
        distance += WEAVE_STEP
    }
    val line = RiffColors.Paper.copy(alpha = LINE_ALPHA)
    for (lane in 1 until geometry.laneCount) {
        along(geometry, lane.toFloat() / geometry.laneCount, line, cache.laneLineWidth)
    }
    for (i in 0 until cache.fretCount) {
        val fret = cache.fretDistances[i]
        if (fret in near..far) across(geometry, fret, line, cache.fretLineWidth)
    }
    drawPath(cache.outline, RiffColors.TourRed, style = cache.outerStroke)
    drawPath(cache.outline, RiffColors.Paper, style = cache.innerStroke)
}

private fun DrawScope.across(geometry: HighwayGeometry, distance: Float, color: Color, width: Float) = drawLine(
    color,
    Offset(geometry.leftAt(distance), geometry.yAt(distance)),
    Offset(geometry.leftAt(distance) + geometry.widthAt(distance), geometry.yAt(distance)),
    width,
)

private fun DrawScope.along(geometry: HighwayGeometry, fraction: Float, color: Color, width: Float) {
    val far = geometry.visibleDistance
    val near = geometry.nearDistance
    drawLine(
        color,
        Offset(geometry.leftAt(far) + geometry.widthAt(far) * fraction, geometry.yAt(far)),
        Offset(geometry.leftAt(near) + geometry.widthAt(near) * fraction, geometry.yAt(near)),
        width,
    )
}
```

Nesta task, `drawGem` e `drawFretButton` continuam como estão (com `chrome(...)`); a Task 5 os troca.

Em `StageRenderer.kt`:

```kotlin
/** Desenha a pista no instante atual da [session], com formas e pincéis do [cache]. */
internal fun DrawScope.drawStage(
    cache: StageCache,
    session: GameSession,
    msPerUnit: Float,
    leftHanded: Boolean,
    reducedMotion: Boolean,
) {
    val geometry = cache.update(size, density)
    val t = session.songTimeMs
    val visibleMs = (geometry.visibleDistance * msPerUnit).toLong()
    fillFretDistances(cache, t, visibleMs, msPerUnit)
    drawHighway(cache)
    // ... resto igual
}
```

e substitua `fretDistances` por:

```kotlin
/** Trastes a cada [FRET_SPACING_MS] de música, rolando junto com as notas, no array do [cache] (sem listas). */
internal fun fillFretDistances(cache: StageCache, t: Long, visibleMs: Long, msPerUnit: Float) {
    var fret = (t / FRET_SPACING_MS + 1) * FRET_SPACING_MS
    var count = 0
    while (fret - t <= visibleMs && count < cache.fretDistances.size) {
        cache.fretDistances[count++] = (fret - t) / msPerUnit
        fret += FRET_SPACING_MS
    }
    cache.fretCount = count
}
```

Em `GameplayScreen.kt` (`GameStage`): `val stageCache = remember { StageCache() }` e no `Canvas` `drawStage(stageCache, session, msPerUnit, leftHanded, reducedMotion)`.

Em `StageScreenshotTest.stageWithGemsButtonsFlameAndHud`, troque as duas primeiras linhas do `Canvas` por:

```kotlin
                        val cache = StageCache()
                        val geometry = cache.update(size, density)
                        floatArrayOf(0.6f, 1.6f, 2.6f, 3.6f, 4.6f).copyInto(cache.fretDistances)
                        cache.fretCount = 5
                        drawHighway(cache)
```

- [ ] **Step 5: Run tests and the screenshots**

Run: `./scripts/gw :core:ui:testDebugUnitTest :feature:gameplay:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:gameplay:verifyRoborazziDebug --continue -q`
Expected: PASS. Se um screenshot falhar, rode `./scripts/gw :core:ui:compareRoborazziDebug` (ou do gameplay), abra o `*_compare.png` em `build/outputs/roborazzi/`, confira se a diferença é só de borda (antisserrilhado) e, se for, regrave com `recordRoborazziDebug` e registre `Ruling:`; se a pista mudou de verdade, corrija o código.

- [ ] **Step 6: Commit**

```bash
git add core/ui feature/gameplay
git commit -m "perf: pista desenhada com StageCache, sem objetos novos por quadro" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Gemas e alvos com pincéis prontos

**Files:**
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StageCache.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StageDrawing.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/StageRenderer.kt`
- Test: `StageCacheTest`, `StageDrawingTest`, `StageScreenshotTest`

**Interfaces:**
- Consumes: `StageCache` (Task 4).
- Produces:
  - `fun DrawScope.drawGem(cache: StageCache, center: Offset, radius: Float, lane: Int, missed: Boolean = false, hopo: Boolean = false)`
  - `fun DrawScope.drawFretButton(cache: StageCache, center: Offset, radius: Float, lane: Int, pressed: Boolean)`
  - `const val MISSED_GEM_ALPHA = 0.4f` em `StageDrawing.kt` (sai o `MISSED_ALPHA` do renderer).
  - Internos no cache: `fun gemCap(lane: Int, missed: Boolean): Brush`, `fun fretInner(lane: Int, pressed: Boolean): Brush`.

- [ ] **Step 1: Write the failing test**

Em `StageCacheTest`:

```kotlin
    @Test
    fun everyLaneHasItsGemAndButtonBrushesReadyAhead() {
        val cache = StageCache()

        for (lane in 0 until 5) {
            assertNotSame(cache.gemCap(lane, missed = false), cache.gemCap(lane, missed = true))
            assertNotSame(cache.fretInner(lane, pressed = true), cache.fretInner(lane, pressed = false))
            assertSame(cache.gemCap(lane, missed = false), cache.gemCap(lane, missed = false))
        }
    }
```

Atualize `StageDrawingTest` para a assinatura nova (é o teste de raio zero/NaN):

```kotlin
                val cache = StageCache()
                listOf(0f, -1f, Float.NaN).forEach { radius ->
                    drawGem(cache, Offset(50f, 50f), radius, lane = 0)
                    drawFretButton(cache, Offset(50f, 50f), radius, lane = 1, pressed = true)
                    drawFretButton(cache, Offset(50f, 50f), radius, lane = 1, pressed = false)
                }
```

e em `StageScreenshotTest`: no `stageWithGemsButtonsFlameAndHud`,
`drawGem(cache, geometry.laneCenter(lane, distance), gemRadius * geometry.scaleAt(distance), lane, hopo = lane == 1)` e
`drawFretButton(cache, geometry.laneCenter(lane, 0f), gemRadius, lane, pressed = lane == 2)`; no `gemAndButtonCloseUp`, crie `val cache = StageCache()` e use `lane = 1` no lugar de `red`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./scripts/gw :core:ui:testDebugUnitTest --continue -q`
Expected: FAIL na compilação ("Unresolved reference 'gemCap'", assinatura de `drawGem`).

- [ ] **Step 3: Implement**

No `StageCache`, acrescente:

```kotlin
    private val gemCaps = Array(LANES * 2) { i ->
        val color = LaneColors.forLane(i / 2)
        unitGemCap(if (i % 2 == 1) color.copy(alpha = MISSED_GEM_ALPHA) else color)
    }
    private val fretLit = Array(LANES) {
        Brush.radialGradient(0f to Color.White, 1f to LaneColors.forLane(it), center = Offset.Zero, radius = FRET_INNER)
    }
    private val fretIdle = Brush.radialGradient(
        0f to Color(0xFF222222),
        1f to Color(0xFF0C0C0C),
        center = Offset.Zero,
        radius = FRET_INNER,
    )

    internal fun gemCap(lane: Int, missed: Boolean): Brush = gemCaps[lane * 2 + if (missed) 1 else 0]

    internal fun fretInner(lane: Int, pressed: Boolean): Brush = if (pressed) fretLit[lane] else fretIdle
```

com, no topo do arquivo:

```kotlin
private const val LANES = 5

/** Tampa da gema em tamanho 1: o mesmo degradê de antes, com o centro do brilho no canto de cima. */
private fun unitGemCap(color: Color) = Brush.radialGradient(
    0f to Color.White,
    0.45f to color,
    1f to lerp(color, Color.Black, 0.6f),
    center = Offset(-0.25f, -GEM_SIDE - TILT * 0.3f),
    radius = 0.8f,
)
```

e em `StageDrawing.kt` (internas, usadas pelo cache):

```kotlin
/** Gema perdida: só as partes coloridas esmaecem (sombra e aro cromado não). */
const val MISSED_GEM_ALPHA = 0.4f
internal const val GEM_SIDE = 0.32f
internal const val FRET_INNER = 0.6f
```

Substitua `drawGem` e `drawFretButton` por versões em tamanho 1 (apague a função `chrome(...)` e os dois `oval` com `Brush`/`Color` continuam):

```kotlin
/**
 * Gema: disco visto de lado, apoiado em [center] (o ponto da trilha). [radius] é o raio horizontal. Desenhada
 * em tamanho 1 e escalada, com os pincéis prontos do [cache]: nada é criado por quadro.
 */
fun DrawScope.drawGem(
    cache: StageCache,
    center: Offset,
    radius: Float,
    lane: Int,
    missed: Boolean = false,
    hopo: Boolean = false,
) {
    if (!(radius > 0f)) return // zero, negativo ou NaN: nada a desenhar
    val base = LaneColors.forLane(lane)
    val color = if (missed) base.copy(alpha = MISSED_GEM_ALPHA) else base
    val top = Offset(0f, -GEM_SIDE)
    withTransform({
        translate(center.x, center.y)
        scale(radius, radius, Offset.Zero)
    }) {
        oval(Color.Black.copy(alpha = 0.6f), Offset(0f, GEM_SIDE * 0.6f), 1.05f, TILT * 1.05f)
        oval(lerp(color, Color.Black, 0.35f), Offset.Zero, 1f, TILT)
        drawRect(color, Offset(-1f, top.y), Size(2f, GEM_SIDE))
        oval(cache.chrome, top, 1f, TILT)
        oval(cache.gemCap(lane, missed), top, 0.72f, TILT * 0.72f)
        if (hopo) oval(Color.White.copy(alpha = 0.85f), top, 0.32f, TILT * 0.32f)
    }
}

/** Alvo deitado na linha de acerto: anel com aro cromado e centro escuro; pressionado, acende na cor da trilha. */
fun DrawScope.drawFretButton(cache: StageCache, center: Offset, radius: Float, lane: Int, pressed: Boolean) {
    if (!(radius > 0f)) return
    val color = LaneColors.forLane(lane)
    withTransform({
        translate(center.x, center.y)
        scale(radius, radius, Offset.Zero)
    }) {
        if (pressed) oval(color.copy(alpha = 0.35f), Offset.Zero, 1.35f, TILT * 1.6f)
        oval(Color.Black, Offset(0f, TILT * 0.35f), 1f, TILT)
        oval(cache.chrome, Offset.Zero, 1f, TILT)
        oval(color, Offset.Zero, 0.82f, TILT * 0.82f)
        oval(cache.fretInner(lane, pressed), Offset.Zero, FRET_INNER, TILT * FRET_INNER)
    }
}
```

Imports novos em `StageDrawing.kt`: `androidx.compose.ui.graphics.drawscope.withTransform`, `com.rodrigomachado.riff.core.ui.theme.LaneColors`.

Em `StageRenderer.kt`: apague `MISSED_ALPHA`; em `drawNote`,
`drawGem(cache, geometry.laneCenter(lane, head), radius * geometry.scaleAt(head), note.lane, missed = judgment == Judgment.MISS, hopo = note.isHopo)` (cor pela trilha **lógica** `note.lane`, posição pela trilha da tela `lane`); em `drawTargetsAndFlames`, `drawFretButton(cache, center, radius, lane, session.isPressed(lane))` (aqui `lane` é a lógica e `center` usa `screenLane`). Passe `cache` de `drawStage` para `drawNote` e `drawTargetsAndFlames`.

- [ ] **Step 4: Run tests and the screenshots**

Run: `./scripts/gw :core:ui:testDebugUnitTest :feature:gameplay:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:gameplay:verifyRoborazziDebug --continue -q`
Expected: PASS, incluindo `Gameplay_stage_lefthanded` (cores certas no canhoto e gema perdida esmaecida). Diferença de screenshot: mesmo procedimento da Task 4, Step 5.

- [ ] **Step 5: Commit**

```bash
git add core/ui feature/gameplay
git commit -m "perf: gemas e alvos com pincéis prontos em tamanho 1" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Chama sem alocar

**Files:**
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/Flame.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StageCache.kt`
- Modify: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/stage/StageDrawing.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/StageRenderer.kt`
- Test: `FlameTest`, `StageScreenshotTest`

**Interfaces:**
- Produces: `fun flameAlpha(progress: Float): Float`, `fun flameScaleY(progress: Float): Float` (sem objeto; `flameFrame` continua para os testes e passa a usá-las); `fun DrawScope.drawFlame(cache: StageCache, base: Offset, width: Float, height: Float, progress: Float)`.

- [ ] **Step 1: Write the failing test**

Em `FlameTest` (JUnit do `:core:ui`):

```kotlin
    @Test
    fun alphaAndHeightWithoutCreatingAFrameMatchTheKeyframes() {
        for (progress in listOf(0f, 0.1f, 0.15f, 0.3f, 0.5f, 0.75f, 1f, 1.5f, Float.NaN)) {
            val frame = flameFrame(progress)
            assertEquals(frame.alpha, flameAlpha(progress), 1e-6f)
            assertEquals(frame.scaleY, flameScaleY(progress), 1e-6f)
        }
    }
```

(Ajuste os imports de asserção ao estilo do arquivo: JUnit 4 `org.junit.Assert.assertEquals`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/gw :core:ui:testDebugUnitTest --tests '*FlameTest*' -q`
Expected: FAIL ("Unresolved reference 'flameAlpha'").

- [ ] **Step 3: Implement**

`Flame.kt`:

```kotlin
/** Quadro da chama em [progress] (0 a 1 ao longo de [FLAME_DURATION_MS]). Fora disso, ou NaN, não desenha nada. */
fun flameFrame(progress: Float): FlameFrame =
    if (progress !in 0f..1f) NOTHING else FlameFrame(flameAlpha(progress), flameScaleY(progress))

/** Opacidade da chama em [progress], sem criar objeto (o desenho chama a cada quadro). */
fun flameAlpha(progress: Float): Float = keyframe(progress, alpha = true)

/** Altura relativa da chama em [progress], sem criar objeto. */
fun flameScaleY(progress: Float): Float = keyframe(progress, alpha = false)

private fun keyframe(progress: Float, alpha: Boolean): Float {
    if (progress !in 0f..1f) return 0f
    val index = KEYFRAMES.indexOfLast { it.first <= progress }.coerceAtMost(KEYFRAMES.lastIndex - 1)
    val (start, from) = KEYFRAMES[index]
    val (end, to) = KEYFRAMES[index + 1]
    val t = (progress - start) / (end - start)
    return if (alpha) lerp(from.alpha, to.alpha, t) else lerp(from.scaleY, to.scaleY, t)
}
```

No `StageCache`:

```kotlin
    /** Uma forma por camada da chama, refeita no lugar (`rewind`) a cada desenho. */
    internal val flamePaths = Array(FLAME_LAYERS) { Path() }

    /** Degradês das camadas em altura 1: de y = −1 (ponta) a y = 0 (base). */
    internal val flameBrushes = arrayOf(
        Brush.verticalGradient(
            0f to FlameRed.copy(alpha = 0f), 0.3f to FlameRed, 0.7f to FlameOrange, 1f to FlameYellow,
            startY = -1f, endY = 0f,
        ),
        Brush.verticalGradient(
            0f to FlameYellow.copy(alpha = 0f), 0.4f to FlameYellow, 1f to FlameCore,
            startY = -1f, endY = 0f,
        ),
        Brush.verticalGradient(0f to FlameYellow.copy(alpha = 0f), 1f to FlameYellow, startY = -1f, endY = 0f),
    )
```

com `internal const val FLAME_LAYERS = 3` em `StageDrawing.kt`.

Em `StageDrawing.kt`, substitua `drawFlame`, `SPARKS` e `flameTongue`:

```kotlin
/** Largura, altura e deslocamento de cada camada da chama, em frações da largura e da altura. */
private val FLAME_LAYER_SHAPE = floatArrayOf(1f, 1f, 0f, 0.55f, 0.7f, 0f, 0.3f, 0.4f, -0.15f)

/** Faíscas: pares (deslocamento lateral, altura relativa de partida). */
private val SPARKS = floatArrayOf(-0.45f, 0.75f, 0.1f, 0.95f, 0.4f, 0.62f)

/** Chama do acerto saindo de [base] (centro do botão), no quadro dado por [progress] (0 a 1). */
fun DrawScope.drawFlame(cache: StageCache, base: Offset, width: Float, height: Float, progress: Float) {
    val alpha = flameAlpha(progress)
    if (alpha <= 0f) return
    val h = height * flameScaleY(progress)
    for (layer in 0 until FLAME_LAYERS) {
        val layerWidth = width * FLAME_LAYER_SHAPE[layer * 3]
        val layerHeight = h * FLAME_LAYER_SHAPE[layer * 3 + 1]
        val path = cache.flamePaths[layer]
        path.flameTongue(base.x + width * FLAME_LAYER_SHAPE[layer * 3 + 2], layerWidth, layerHeight)
        withTransform({
            translate(0f, base.y)
            scale(1f, layerHeight, Offset.Zero)
        }) {
            drawPath(path, cache.flameBrushes[layer], alpha = alpha)
        }
    }
    for (i in SPARKS.indices step 2) {
        val spark = Offset(
            base.x + SPARKS[i] * width * (0.5f + progress),
            base.y - h * (SPARKS[i + 1] + 0.6f * progress),
        )
        drawCircle(FlameYellow, width * 0.05f, spark, alpha = alpha)
    }
}

/**
 * Língua de fogo com a base em y = 0 e a ponta em y = −1 (altura 1; x em px), desenhada com escala vertical
 * [height]: a mesma forma de antes, refeita no lugar (sem `Path` novo).
 */
private fun Path.flameTongue(centerX: Float, width: Float, height: Float) {
    rewind()
    val r = width / 2
    val rUp = r / height // o raio da base, medido em alturas
    moveTo(centerX, -1f)
    cubicTo(centerX + r * 0.2f, -0.65f, centerX + r, -0.5f, centerX + r, -rUp)
    cubicTo(centerX + r, -rUp * 0.45f, centerX + r * 0.55f, 0f, centerX, 0f)
    cubicTo(centerX - r * 0.55f, 0f, centerX - r, -rUp * 0.45f, centerX - r, -rUp)
    cubicTo(centerX - r, -0.5f, centerX - r * 0.2f, -0.65f, centerX, -1f)
    close()
}
```

(A camada 3 da versão anterior tinha deslocamento `-width * 0.15f`: é o `-0.15f` em `FLAME_LAYER_SHAPE`.)

Em `StageRenderer.kt`, `drawFlame(cache, geometry.laneCenter(...), ..., progress)`; em `StageScreenshotTest`, os dois `drawFlame` ganham o `cache` (no `flameKeyframes`, crie `val cache = StageCache()` no `Canvas`).

- [ ] **Step 4: Run tests and the screenshots**

Run: `./scripts/gw :core:ui:testDebugUnitTest :feature:gameplay:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:gameplay:verifyRoborazziDebug --continue -q`
Expected: PASS (em especial `Stage_flame_frames` e `Gameplay_stage`). Diferença: procedimento da Task 4, Step 5.

- [ ] **Step 5: Commit**

```bash
git add core/ui feature/gameplay
git commit -m "perf: chama refeita no lugar, sem Path nem degradê por quadro" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Placar sem recompor a cada quadro e faixa visível sem objeto

**Files:**
- Create: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/HudState.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayScreen.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/StageRenderer.kt`
- Modify: `core/game/src/main/kotlin/com/rodrigomachado/riff/core/game/GameEngine.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/HudStateTest.kt` (novo), `GameEngineTest`

**Interfaces:**
- Produces: `GameEngine.combo: Int` (leitura pública; `engine.state` cria um objeto a cada chamada); `internal class HudState { score; multiplier; combo; judgment; showingJudgment; showingNotice; fun update(session: GameSession, withoutSeparateGuitar: Boolean) }`.

A spec (§8) pede um "contador de recomposições" no placar. O teste abaixo mede a causa: sem escrita em estado do Compose não há recomposição, e o `HudState` só escreve quando um valor muda (observador global de escritas do `Snapshot`).

- [ ] **Step 1: Write the failing tests**

Em `GameEngineTest`:

```kotlin
    @Test
    fun theComboCanBeReadWithoutBuildingTheWholeState() {
        val engine = GameEngine(listOf(note(1_000), note(2_000)))
        engine.onPress(0, 1_000)
        engine.onPress(0, 2_000)

        assertEquals(2, engine.combo)
        assertEquals(engine.state.combo, engine.combo)
    }
```

`HudStateTest.kt`:

```kotlin
package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.snapshots.Snapshot
import com.rodrigomachado.riff.core.model.Judgment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudStateTest {
    private val time = FakeTime()
    private val player = FakeSongPlayer()

    private fun writesDuring(block: () -> Unit): Int {
        var writes = 0
        val handle = Snapshot.registerGlobalWriteObserver { writes++ }
        try {
            block()
        } finally {
            handle.dispose()
        }
        return writes
    }

    @Test
    fun nothingChangedMeansNothingIsWrittenSoTheScoreDoesNotRecompose() {
        val session = GameSession(listOf(note(5_000)), player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
        }
        val hud = HudState()
        hud.update(session, withoutSeparateGuitar = false)

        assertEquals(0, writesDuring { hud.update(session, withoutSeparateGuitar = false) })
    }

    @Test
    fun aHitUpdatesScoreComboAndJudgment() {
        player.positionMs = 1_000
        val session = GameSession(listOf(note(1_000)), player, FakeSfxPlayer(), time, audioOffsetMs = 0).also {
            it.start()
        }
        val hud = HudState()
        hud.update(session, withoutSeparateGuitar = false)

        session.press(0)
        val writes = writesDuring { hud.update(session, withoutSeparateGuitar = false) }

        assertTrue(writes > 0)
        assertEquals(1, hud.combo)
        assertEquals(Judgment.PERFECT, hud.judgment)
        assertTrue(hud.showingJudgment)
        assertTrue(hud.score > 0)
    }
}
```

(`note(...)` é o helper que os testes do gameplay já usam; se for privado de outro arquivo, copie: `private fun note(timeMs: Long, lane: Int = 0) = Note(timeMs, lane, 0, false)`.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./scripts/gw :core:game:test :feature:gameplay:testDebugUnitTest --continue -q`
Expected: FAIL na compilação ("Unresolved reference 'combo'" / "'HudState'").

- [ ] **Step 3: Implement**

`GameEngine.kt`: troque `private var combo = 0` por

```kotlin
    /** Acertos seguidos; leitura sem montar o [state] (o placar lê a cada quadro). */
    var combo = 0
        private set
```

`HudState.kt`:

```kotlin
package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rodrigomachado.riff.core.model.Judgment

internal const val JUDGMENT_VISIBLE_MS = 500L
internal const val NOTICE_MS = 4_000L

/**
 * O que o placar mostra, em estado do Compose. [update] roda a cada quadro, mas só escreve o que mudou: o
 * placar recompõe quando a pontuação, o combo ou o julgamento mudam, não a cada quadro.
 */
internal class HudState {
    var score by mutableLongStateOf(0L)
        private set
    var multiplier by mutableIntStateOf(1)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var judgment by mutableStateOf<Judgment?>(null)
        private set
    var showingJudgment by mutableStateOf(false)
        private set
    var showingNotice by mutableStateOf(false)
        private set

    fun update(session: GameSession, withoutSeparateGuitar: Boolean) {
        val engine = session.engine
        score = engine.score
        multiplier = engine.multiplier
        combo = engine.combo
        judgment = session.lastJudgment
        showingJudgment = judgment != null && session.judgmentAgeMs() < JUDGMENT_VISIBLE_MS
        showingNotice = withoutSeparateGuitar && session.songTimeMs < NOTICE_MS
    }
}
```

Antes de criar, leia o valor atual de `NOTICE_MS` e `JUDGMENT_VISIBLE_MS` em `GameplayScreen.kt` e use os mesmos (apague as `private const` de lá).

`GameplayScreen.kt` (`GameStage`):

```kotlin
    val hud = remember(session) { HudState() }
    hud.update(session, state.withoutSeparateGuitar) // primeiro quadro e screenshots (animate = false)
```

no loop de quadros, logo depois de `session.tick()`: `hud.update(session, state.withoutSeparateGuitar)`. Use `rememberUpdatedState` para `state.withoutSeparateGuitar` se o detekt/compose reclamar de valor capturado.

Reescreva `Hud` para ler o `HudState`, sem o parâmetro `frame` na composição; o salto do julgamento passa a ser lido no `graphicsLayer` (fase de desenho, sem recompor):

```kotlin
@Composable
private fun Hud(
    session: GameSession,
    hud: HudState,
    frame: () -> Long,
    reducedMotion: Boolean,
    onPause: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.padding(Spacing.md)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            ScoreText(hud.score)
            TextButton(onClick = onPause, modifier = Modifier.testTag(GameplayTestTags.PAUSE)) {
                Text(stringResource(R.string.gameplay_pause), color = RiffColors.Paper)
            }
            MultiplierStamp(hud.multiplier)
        }
        if (hud.combo >= COMBO_MIN_TO_SHOW) {
            Text(
                stringResource(R.string.gameplay_combo, hud.combo),
                style = MaterialTheme.typography.labelLarge,
                color = RiffColors.Paper,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.xl * 2),
            )
        }
        val judgment = hud.judgment
        if (judgment != null && hud.showingJudgment) {
            val label = if (judgment == Judgment.PERFECT) {
                R.string.gameplay_judgment_perfect
            } else {
                R.string.gameplay_judgment_good
            }
            JudgmentText(
                stringResource(label),
                Modifier.align(Alignment.Center).graphicsLayer {
                    frame() // relê a cada quadro só esta camada, sem recompor o placar
                    val bounce = judgmentBounce(session.judgmentAgeMs(), reducedMotion)
                    scaleX = bounce
                    scaleY = bounce
                },
            )
        }
        if (hud.showingNotice) {
            NoSeparateGuitarNotice(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl * 3))
        }
    }
}

private fun judgmentBounce(ageMs: Long, reducedMotion: Boolean): Float =
    if (reducedMotion) 1f else 1f + JUDGMENT_BOUNCE_SCALE * (1f - (ageMs / JUDGMENT_BOUNCE_MS).coerceAtMost(1f))
```

e a chamada em `GameStage`: `Hud(session, hud, { frame }, reducedMotion, onPause, Modifier.fillMaxSize())`. Confira `ScoreText` (aceita `Long`? se for `Int`, mantenha a conversão que existia).

`StageRenderer.kt`, em `drawStage`, troque a faixa por dois índices (sem `IntRange`):

```kotlin
    val notes = session.engine.notes
    val first = NoteTimeline.firstAtOrAfter(notes, t - session.lookBehindMs)
    val end = NoteTimeline.firstAtOrAfter(notes, t + visibleMs + 1)
    // Do fundo para a frente, para as notas mais próximas ficarem por cima.
    for (i in end - 1 downTo first) {
        drawNote(cache, geometry, session, i, (notes[i].timeMs - t) / msPerUnit, msPerUnit, radius, leftHanded)
    }
```

- [ ] **Step 4: Run tests and the screenshots**

Run: `./scripts/gw :core:game:test :feature:gameplay:testDebugUnitTest :feature:gameplay:verifyRoborazziDebug --continue -q`
Expected: PASS (inclusive os testes de tela da partida: aviso sem guitarra separada, pausa, voltar, e os screenshots).

- [ ] **Step 5: Full suite and commit**

Run: `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add core/game feature/gameplay
git commit -m "perf: placar recompõe só quando muda; notas visíveis sem objeto por quadro" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Medição "depois" e perfil novo (precisa do usuário)

**Files:**
- Modify: `app/src/release/generated/baselineProfiles/*.txt` (regerados)

- [ ] **Step 1: Pedir ao usuário** o celular nas mesmas condições da Task 3 (conectado, desbloqueado, tela ligada, sem economia de bateria).

- [ ] **Step 2: Gerar e medir**

Run: `./scripts/desempenho.sh tudo 2>&1 | tee build/desempenho-depois.log`
Expected: BUILD SUCCESSFUL, resumo com os quatro testes e reinstalação do debug com "Success".

- [ ] **Step 3: Comparar com a meta**

Compare com os números da Task 3 no ledger. Expected: `GameplayBenchmark.gameplayWithProfile` com `frameDurationCpuMs` P90 < 8,3 ms e `frameOverrunMs` P99 ≤ o de antes. Se não cumprir, registre os números, investigue com `superpowers:systematic-debugging` (sem o Android Studio aqui: use `adb shell dumpsys gfxinfo com.rodrigomachado.riff framestats` durante uma partida manual para achar os quadros lentos) e decida com o usuário antes de seguir.

- [ ] **Step 4: Dados do usuário e app de debug**

Mesmo procedimento da Task 3, Step 6. Expected: dados presentes, biblioteca com a música, app de debug instalado.

- [ ] **Step 5: Commit**

```bash
git add app/src/release/generated/baselineProfiles
git commit -m "perf: Baseline Profile regerado depois das otimizações da pista" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: README final e status das especificações

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-10-05-riff-design.md` (§11 passo 8 concluído), `docs/superpowers/specs/2026-10-09-polimento-design.md` (status)

- [ ] **Step 1: Reescrever o README** com as seções da spec do polimento §5, nesta ordem, mantendo o conteúdo técnico atual (Requisitos, Comandos, Módulos, Spotify, Screenshots) dentro das seções novas:

1. `# Riff` + parágrafo: o que é; os dois modos (biblioteca: para jogar, com guitarra separada, retrocesso de 0,5 s ao continuar; Spotify: para ouvir e tocar junto, alinhado automaticamente pelo começo da música do mapa).
2. `## Instalar e configurar`: Requisitos + Spotify (texto atual, revisado).
3. `## Importar mapas`: escolher a pasta na Biblioteca; uma subpasta por música do Clone Hero com `notes.chart` ou `notes.mid`, `song.ini` e o áudio (`song.ogg`/`.opus`/`.mp3`/`.wav`); guitarra separada com `guitar.*` mais uma faixa de acompanhamento (`song`, `rhythm`, `bass`, `drums`, `drums_1`…`drums_4`, `vocals`, `vocals_1`, `vocals_2`, `keys`, `crowd`); `.sng` (Encore) ainda não é lido; não usar mapas da Harmonix/Neversoft (rips de Rock Band/Guitar Hero).
4. `## Jogar`: escolha da dificuldade; Pausar; sincronia desta música (−1 s, −10 ms, +10 ms, +1 s; salva por música); calibração do atraso (para fone Bluetooth) e Zerar; Configurações (volumes geral, de acerto e de erro; velocidade das notas; canhoto).
5. `## Desenvolvimento`: Comandos (atuais), Módulos (atual + `:baselineprofile`), Screenshots (atual), e `### Desempenho`: o que o perfil faz; `scripts/desempenho.sh perfil|medir|tudo` (celular conectado e desbloqueado, música do percurso na biblioteca, dados preservados); quando regenerar (mudanças grandes em telas ou no gameplay); tabela com os números da Task 3 e da Task 8 (abertura sem/com perfil; partida P50/P90/P99 de `frameDurationCpuMs` e P99 de `frameOverrunMs`), com data e aparelho (Galaxy S25, Android 16).
6. `## Créditos e licenças`: sons de erro com gravações CC0 do Freesound (`third_party/README.md`); som de acerto sintetizado; nada do Guitar Hero (regra do projeto).
7. `## Status`: roteiro da especificação principal concluído (2026-10-09); pendências: pontos menores das revisões (lista curta) e ideias (`.sng`, separação da guitarra no app). E a linha: "Cada mudança no uso do app atualiza a seção correspondente deste README."

- [ ] **Step 2: Status nas especificações**

`2026-10-05-riff-design.md` §11 passo 8: acrescente "(concluído em 2026-10-09; ver `2026-10-09-polimento-design.md`)". `2026-10-09-polimento-design.md`: `Status: implementado` e uma linha com os números finais (os mesmos da tabela do README).

- [ ] **Step 3: Verify**

Run: `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add README.md docs/superpowers/specs
git commit -m "docs: README final como guia de uso; roteiro da especificação concluído" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
