# Filtros da pesquisa e jogo sem guitarra separada — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Filtros Originais|Versões e "Só com guitarra separada" (com busca automática) na pesquisa do Encore, e mapas sem guitarra separada tocando inteiros, só com som de erro (o som de acerto sai do app).

**Architecture:** `VersionTitles` (em `:core:encore`) classifica títulos. O `SearchViewModel` passa a guardar todos os resultados carregados (`loaded`) e a marca de guitarra por md5 (`separate`), e calcula a lista visível pelos filtros; uma corrotina de "preenchimento" busca páginas e confere pacotes até os limites. O som de acerto some de ponta a ponta (`SfxPlayer`, `GameSession`, `Settings`, preferências, tela).

**Tech Stack:** Kotlin, Compose, Hilt, coroutines (`Semaphore`), JUnit 4 + Robolectric, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-13-filtros-e-sem-guitarra-design.md`

## Global Constraints

- Gradle só por `./scripts/gw`; `./scripts/gw --stop` antes de suítes longas.
- Detekt: MaxLineLength 120, LongMethod 60, ReturnCount 4, ComplexCondition 3, TooManyFunctions 11; `./scripts/gw detekt -PdetektAutoCorrect`.
- Suíte completa: `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --console=plain` e `scripts/conferir_r8_spotify.sh`.
- Commit encadeado na verificação; mensagem termina em `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Textos pt-BR com tradução en; identidade "Pôster de turnê"; sons do menu: os controles novos soam como navegar (`MenuSound.NAVIGATE`).
- Versões: `cover`, `remix`, `live`, `ao vivo`, `medley`, `mashup`, `acoustic`, palavra inteira, sem diferenciar maiúsculas; "Remaster(ed)" é original.
- Filtro de guitarra: para em **10** encontrados ou **100** conferidos; "Procurar mais" soma mais 100; no máximo 3 conferências ao mesmo tempo.
- README atualizado no mesmo branch.

## Review Focus

1. Busca nova (ou sair da tela) durante a procura automática: a procura antiga para e não mistura resultados de duas buscas — teste no Task 2.
2. Encore fora do ar no meio da procura: para, mostra o erro e mantém os já encontrados — teste no Task 2.
3. Trocar Originais/Versões com o filtro de guitarra ligado: reaplica sem perder a contagem e sem conferir de novo os já conferidos — teste no Task 2.
4. Uma preferência `hit_volume` antiga guardada no aparelho não quebra a leitura das Configurações — teste no Task 4.
5. Mapa com guitarra separada continua cortando a guitarra no erro e religando no acerto depois de tirar o som de acerto — teste existente do `GameSessionTest` (Task 4) roda inalterado.

---

### Task 1: Classificar versões pelo título (`:core:encore`)

**Files:**
- Create: `core/encore/src/main/kotlin/com/rodrigomachado/riff/core/encore/VersionTitles.kt`
- Test: `core/encore/src/test/kotlin/com/rodrigomachado/riff/core/encore/VersionTitlesTest.kt`

**Interfaces:**
- Produces: `object VersionTitles { fun isVersion(title: String): Boolean }`

- [ ] **Step 1: teste que falha**

```kotlin
package com.rodrigomachado.riff.core.encore

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionTitlesTest {
    @Test
    fun coversRemixesLiveAndMedleysAreVersions() {
        listOf(
            "Enter Sandman (Metallica cover)",
            "Fade to Black (Live at Tushino)",
            "Black Album Medley",
            "Numb (Remix)",
            "Céu Azul (Ao Vivo)",
            "Wonderwall - Acoustic",
            "Mashup of Hits",
        ).forEach { assertEquals(it, true, VersionTitles.isVersion(it)) }
    }

    @Test
    fun originalsAndRemastersAreNot() {
        listOf(
            "Battery",
            "Smells Like Teen Spirit",
            "Bohemian Rhapsody - Remastered 2011",
            "Livewire",
            "Discover",
            "Alive",
        ).forEach { assertEquals(it, false, VersionTitles.isVersion(it)) }
    }
}
```

- [ ] **Step 2:** `./scripts/gw :core:encore:testDebugUnitTest --tests '*VersionTitlesTest' --console=plain` → FAIL (compilação).

- [ ] **Step 3: implementar**

```kotlin
package com.rodrigomachado.riff.core.encore

/**
 * Título de uma versão (cover, remix, ao vivo, medley…) em vez da gravação original. Pelo título do Encore, palavra
 * inteira: "Livewire" e "Alive" não são versões; "Remastered" é a original.
 */
object VersionTitles {
    private val TERMS = Regex(
        """\b(cover|remix|live|ao vivo|medley|mashup|acoustic)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isVersion(title: String): Boolean = TERMS.containsMatchIn(title)
}
```

- [ ] **Step 4:** mesmo comando → PASS.
- [ ] **Step 5:** `./scripts/gw :core:encore:testDebugUnitTest detekt -q && git add core/encore && git commit -m "feat: classificar versões (cover, remix, ao vivo…) pelo título"`

---

### Task 2: Filtros e busca automática no `SearchViewModel`

**Files:**
- Modify: `feature/search/src/main/kotlin/com/rodrigomachado/riff/feature/search/SearchViewModel.kt`
- Test: `feature/search/src/test/kotlin/com/rodrigomachado/riff/feature/search/SearchViewModelTest.kt`

**Interfaces:**
- Consumes: `VersionTitles.isVersion` (Task 1).
- Produces (no `SearchUiState`): `versions: Boolean = false` (false = Originais), `onlySeparateGuitar: Boolean = false`, `guitarSearch: GuitarSearch? = null` com `data class GuitarSearch(val checked: Int, val limit: Int, val running: Boolean)`. No ViewModel: `fun showVersions(versions: Boolean)`, `fun onlySeparateGuitar(enabled: Boolean)`, `fun searchMoreWithGuitar()`.

Estado interno: `loaded: List<ResultItem>` (tudo que veio do Encore nesta busca, sem repetir md5, já sem rips) e `separate: MutableMap<String, Boolean?>` (resultado por md5; guardado durante a tela). `results` = `loaded` filtrado por `versions` e, com `onlySeparateGuitar`, só os com `separate[md5] == true`; cada item leva `separateGuitar = separate[md5]`.

Preenchimento (`fill()`, uma corrotina `filling` que a busca nova cancela):

```kotlin
private suspend fun fill() {
    var autoPages = 0
    while (true) {
        if (!state.value.onlySeparateGuitar) {
            // Página vazia depois dos filtros: busca a próxima (até 5 seguidas).
            if (visible().isNotEmpty() || !hasMore || autoPages >= MAX_AUTO_PAGES) return
            autoPages++
            loadNextPage()
            continue
        }
        val found = visible().size
        if (found >= GUITAR_TARGET || guitarChecked >= guitarLimit) return finishGuitar()
        val pending = loaded.filter { versionsMatch(it) && it.chart.md5 !in separate }
            .take(guitarLimit - guitarChecked)
        if (pending.isEmpty()) {
            if (!hasMore) return finishGuitar()
            loadNextPage()
            continue
        }
        coroutineScope {
            pending.forEach { item ->
                launch {
                    val result = guitarChecks.withPermit { encore.hasSeparateGuitar(item.chart) }
                    separate[item.chart.md5] = result
                    guitarChecked++
                    publish()
                }
            }
        }
    }
}
```

(`publish()` recalcula `results` e `guitarSearch`; `finishGuitar()` marca `running = false`; `loadNextPage()` chama `encore.search(lastQuery, page + 1)`, junta em `loaded` sem repetir md5 e atualiza `hasMore`; as exceções do Encore param o preenchimento com o erro de sempre e mantêm `loaded`. `searchMoreWithGuitar()` soma `GUITAR_LIMIT` a `guitarLimit` e reinicia `fill()`. Fora do filtro de guitarra, a marca continua sendo conferida para os visíveis, como hoje, mas registrando em `separate`. Constantes: `GUITAR_TARGET = 10`, `GUITAR_LIMIT = 100`, `MAX_AUTO_PAGES = 5`.)

- [ ] **Step 1: testes que falham**

O `FakeTransport` do teste ganha páginas: `pages: List<String>` (corpo JSON por página, `page` lido do JSON enviado) e `stems: Set<String>` (md5 cujo começo de pacote traz `guitar.ogg` + `song.ogg`; os demais trazem só `song.opus`). Helper `page(vararg charts: Triple<md5, name, charter>, found: Int)` monta o JSON no formato do fixture (com `diff_guitar: 2`, `icon: ""`, `notesData.noteCounts` com guitar expert).

```kotlin
@Test
fun originalsHideVersionsAndVersionsShowOnlyThem() = runTest {
    transport.pages = listOf(page(c("a", "Enter Sandman"), c("b", "Enter Sandman (Metallica cover)"), found = 2))
    val vm = viewModel(SearchRoute(query = "sandman"))
    advanceUntilIdle()
    assertEquals(listOf("a"), vm.uiState.value.results.map { it.chart.md5 })

    vm.showVersions(true)

    assertEquals(listOf("b"), vm.uiState.value.results.map { it.chart.md5 })
}

@Test
fun aPageEmptyAfterTheFiltersFetchesTheNextOne() = runTest {
    transport.pages = listOf(
        page(c("a", "X (cover)"), found = 50),
        page(c("b", "X"), found = 50),
    )
    val vm = viewModel(SearchRoute(query = "x"))
    advanceUntilIdle()

    assertEquals(listOf("b"), vm.uiState.value.results.map { it.chart.md5 })
}

@Test
fun theGuitarFilterKeepsFetchingUntilItFindsTenOrChecksAHundred() = runTest {
    // 4 páginas de 25: só 1 mapa com guitarra separada em cada.
    transport.pages = (0 until 4).map { p ->
        page(*Array(25) { i -> c("m$p-$i", "Música $p $i") }, found = 200)
    }
    transport.stems = setOf("m0-3", "m1-7", "m2-1", "m3-9")
    val vm = viewModel(SearchRoute(query = "x"))
    advanceUntilIdle()

    vm.onlySeparateGuitar(true)
    advanceUntilIdle()

    assertEquals(setOf("m0-3", "m1-7", "m2-1", "m3-9"), vm.uiState.value.results.map { it.chart.md5 }.toSet())
    assertEquals(GuitarSearch(checked = 100, limit = 100, running = false), vm.uiState.value.guitarSearch)
    assertEquals(4, transport.searches) // páginas 1 a 4
}

@Test
fun searchMoreChecksAnotherHundred() = runTest {
    transport.pages = (0 until 8).map { p -> page(*Array(25) { i -> c("m$p-$i", "M $p $i") }, found = 200) }
    val vm = viewModel(SearchRoute(query = "x"))
    advanceUntilIdle()
    vm.onlySeparateGuitar(true)
    advanceUntilIdle()

    vm.searchMoreWithGuitar()
    advanceUntilIdle()

    assertEquals(200, vm.uiState.value.guitarSearch?.checked)
}

@Test
fun theMarkIsRememberedAndNotCheckedAgain() = runTest {
    transport.pages = listOf(page(c("a", "A"), c("b", "B"), found = 2))
    transport.stems = setOf("a")
    val vm = viewModel(SearchRoute(query = "x"))
    advanceUntilIdle()
    val peeks = transport.peeks

    vm.onlySeparateGuitar(true)
    advanceUntilIdle()
    vm.showVersions(true)
    vm.showVersions(false)
    advanceUntilIdle()

    assertEquals(listOf("a"), vm.uiState.value.results.map { it.chart.md5 })
    assertEquals(peeks, transport.peeks) // os dois já tinham sido conferidos pela marca
}

@Test
fun aNewSearchStopsTheOldOne() = runTest {
    transport.pages = (0 until 4).map { p -> page(*Array(25) { i -> c("m$p-$i", "M $p $i") }, found = 200) }
    transport.peekGate = CompletableDeferred() // segura as conferências
    val vm = viewModel(SearchRoute(query = "x"))
    advanceUntilIdle()
    vm.onlySeparateGuitar(true)
    runCurrent()

    vm.onQueryChange("y")
    transport.pages = listOf(page(c("z", "Z"), found = 1))
    vm.search()
    checkNotNull(transport.peekGate).complete(Unit)
    advanceUntilIdle()

    assertEquals(true, vm.uiState.value.results.all { it.chart.md5 == "z" })
}

@Test
fun encoreGoingDownMidSearchKeepsWhatWasFound() = runTest {
    transport.pages = listOf(page(*Array(25) { i -> c("m$i", "M $i") }, found = 100))
    transport.stems = setOf("m2")
    transport.failFromPage = 2
    val vm = viewModel(SearchRoute(query = "x"))
    advanceUntilIdle()

    vm.onlySeparateGuitar(true)
    advanceUntilIdle()

    assertEquals(listOf("m2"), vm.uiState.value.results.map { it.chart.md5 })
    assertEquals(SearchError.UNAVAILABLE, vm.uiState.value.error)
    assertEquals(false, vm.uiState.value.guitarSearch?.running)
}
```

(`c(md5, name)` = `Triple(md5, name, "charter")`; `transport.searches` conta os POSTs; `transport.peeks` conta os GET com Range; `peekGate` faz o GET esperar; `failFromPage` faz o POST da página N lançar `UnknownHostException`. Os testes existentes do arquivo continuam valendo: o de "Carregar mais" e o da marca.)

- [ ] **Step 2:** `./scripts/gw :feature:search:testDebugUnitTest --console=plain` → FAIL (compilação: `showVersions`, `onlySeparateGuitar`, `GuitarSearch`).

- [ ] **Step 3: implementar** conforme o bloco de Interfaces e o `fill()` acima: `search()` limpa `loaded`, `page`, `guitarChecked`, `guitarLimit = GUITAR_LIMIT` (mas **não** `separate`), cancela `filling`, carrega a página 1 e chama `fill()`; `loadMore()` (Carregar mais) carrega a próxima página e chama `fill()`; `showVersions`/`onlySeparateGuitar` atualizam o estado, chamam `publish()` e reiniciam `fill()` sem zerar a contagem. Se o `SearchViewModel` passar de 11 funções, mover o filtro de visibilidade (`visible`, `versionsMatch`) para funções de topo no arquivo.

- [ ] **Step 4:** `./scripts/gw :feature:search:testDebugUnitTest detekt --console=plain` → PASS.
- [ ] **Step 5:** commit `feat: filtros Originais/Versões e só com guitarra separada, com busca automática`.

---

### Task 3: Controles e mensagens na tela de pesquisa

**Files:**
- Modify: `feature/search/src/main/kotlin/com/rodrigomachado/riff/feature/search/SearchScreen.kt`
- Modify: `feature/search/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Test: `SearchContentTest.kt`, `SearchScreenshotTest.kt` (screenshot `Search_results` regravado e `Search_guitar_filter` novo)

**Interfaces:**
- Consumes: `SearchUiState.versions`, `onlySeparateGuitar`, `guitarSearch` e as funções do Task 2.
- Produces: `SearchContent(..., onShowVersions: (Boolean) -> Unit, onOnlySeparateGuitar: (Boolean) -> Unit, onSearchMoreWithGuitar: () -> Unit)`.

Strings novas:

| chave | pt | en |
|---|---|---|
| search_originals | Originais | Originals |
| search_versions | Versões | Versions |
| search_only_separate_guitar | Só com guitarra separada | Separate guitar only |
| search_guitar_progress | Procurando mapas com guitarra separada… %1$d de %2$d | Looking for separate-guitar charts… %1$d of %2$d |
| search_guitar_none | Nenhum mapa com guitarra separada nos primeiros %1$d resultados. | No separate-guitar chart in the first %1$d results. |
| search_guitar_more | Procurar mais | Search more |

Layout: abaixo da linha do campo, uma `Row` com dois `TextButton` lado a lado para Originais|Versões (o escolhido em `RiffColors.Paper` sublinhado/negrito, o outro em `PaperDim`) e, à direita ou na linha de baixo, um `Switch` com o rótulo "Só com guitarra separada" (as cores do `Switch` das Configurações). Todos com `withMenuSound(MenuSound.NAVIGATE, …)`. Com `guitarSearch?.running == true`, uma linha de texto com `search_guitar_progress`; com `running == false`, `onlySeparateGuitar` ligado e lista vazia, `search_guitar_none` + `TextButton(search_guitar_more)`.

- [ ] **Step 1: testes que falham**

```kotlin
@Test
fun theFiltersSoundLikeNavigatingAndReportTheChoice() {
    show(SearchUiState(query = "x"))

    composeRule.onNodeWithText("Versões").performClick()
    composeRule.onNodeWithText("Só com guitarra separada").performClick()

    assertEquals(listOf("versions:true", "guitar:true"), events)
    assertEquals(listOf(MenuSound.NAVIGATE, MenuSound.NAVIGATE), sounds)
}

@Test
fun theGuitarSearchShowsItsProgressAndOffersMoreWhenNothingWasFound() {
    show(SearchUiState(query = "x", onlySeparateGuitar = true, guitarSearch = GuitarSearch(37, 100, running = true)))
    composeRule.onNodeWithText("Procurando mapas com guitarra separada… 37 de 100").assertExists()
}

@Test
fun nothingFoundWithTheGuitarFilterOffersToSearchMore() {
    show(SearchUiState(query = "x", onlySeparateGuitar = true, guitarSearch = GuitarSearch(100, 100, running = false)))

    composeRule.onNodeWithText("Nenhum mapa com guitarra separada nos primeiros 100 resultados.").assertExists()
    composeRule.onNodeWithText("Procurar mais").performClick()
    assertEquals(listOf("more-guitar"), events)
}
```

(O `show` do teste passa `onShowVersions = { events += "versions:$it" }`, `onOnlySeparateGuitar = { events += "guitar:$it" }`, `onSearchMoreWithGuitar = { events += "more-guitar" }`. O interruptor é clicado pelo rótulo: o `Row` do rótulo e do `Switch` é `toggleable`.)

- [ ] **Step 2:** `./scripts/gw :feature:search:testDebugUnitTest --tests '*SearchContentTest' --console=plain` → FAIL.
- [ ] **Step 3:** implementar o layout acima; `SearchScreen` liga `viewModel::showVersions`, `viewModel::onlySeparateGuitar`, `viewModel::searchMoreWithGuitar`. Se `SearchContent` passar de 60 linhas, extrair `FilterRow(state, …)` e `GuitarSearchStatus(state, …)`.
- [ ] **Step 4:** `./scripts/gw :feature:search:testDebugUnitTest :feature:search:recordRoborazziDebug detekt --console=plain` → PASS; conferir à mão `Search_results.png` (só a linha de filtros a mais) e `Search_guitar_filter.png` (filtro ligado, procurando).
- [ ] **Step 5:** commit `feat: controles de filtro na pesquisa e progresso da busca por guitarra separada`.

---

### Task 4: Tirar o som de acerto

**Files:**
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/Players.kt` (`SfxPlayer`: sai `hit`, `setVolumes(hit, miss)` vira `setMissVolume(miss: Float)`)
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/SoundPoolSfxPlayer.kt` (sem os sons de acerto)
- Delete: `core/audio/src/main/res/raw/sfx_hit_1.wav` … `sfx_hit_5.wav`, `scripts/gerar_sfx.py`
- Modify: `core/audio/src/test/.../SfxAssetsTest.kt` (sem os testes de acerto; um teste de que eles não existem mais)
- Modify: `feature/gameplay/.../GameSession.kt` (sem `hitSounds`), `GameplayViewModel.kt` (`setMissVolume(settings.effectiveMissVolume)`; sem `hitSounds =`)
- Modify: `feature/gameplay/src/test/.../Fakes.kt`, `GameSessionTest.kt`, `GameplayViewModelTest.kt`
- Modify: `core/model/.../Settings.kt` (sem `hitVolume`/`effectiveHitVolume`), `core/model/src/test/.../SettingsTest.kt`
- Modify: `core/datastore/.../RiffPreferences.kt` (sem `HIT_VOLUME`; ao gravar, `prefs.remove(HIT_VOLUME_LEGACY)`), `RiffPreferencesTest.kt`
- Modify: `feature/settings/...` (tela, ViewModel, strings, Fakes e testes: sem o controle e a prévia de acerto)

**Interfaces:**
- Produces: `interface SfxPlayer { fun miss(); fun setMissVolume(volume: Float); fun release() }`; `Settings` sem `hitVolume`.

- [ ] **Step 1: testes que falham**

`GameplayViewModelTest` — o caso que hoje espera `sfx.hits == 1` num mapa da biblioteca sem guitarra separada passa a esperar a música tocando sem som de acerto:

```kotlin
@Test
fun aChartWithoutSeparateGuitarPlaysWholeWithOnlyTheMissSound() = runTest {
    loader.result = { playable() } // ChartAudio.Single: sem guitarra separada
    val vm = viewModel()
    val session = (vm.uiState.value as GameplayUiState.Ready).session
    time.now = 1_000
    session.tick()

    session.press(0) // acerto: sem som
    time.now = 3_000
    session.tick() // a nota de 2 s passou: erro

    assertEquals(1, sfx.misses)
    assertEquals(0.6f, sfx.missVolume) // volume de erro × geral
}
```

`RiffPreferencesTest`:

```kotlin
@Test
fun anOldHitVolumeStoredOnTheDeviceIsIgnored() = runTest {
    val store = InMemoryPreferencesStore()
    store.updateData { it.toMutablePreferences().apply { set(floatPreferencesKey("hit_volume"), 0.2f) } }

    assertEquals(Settings.DEFAULT, RiffPreferences(store).settings.first())
}
```

`SfxAssetsTest`: `fun theHitSoundsAreGone() = (1..5).forEach { assertFalse(file("sfx_hit_$it.wav").exists()) }`.

`SettingsContentTest`: `composeRule.onNodeWithText("Som de acerto").assertDoesNotExist()`.

- [ ] **Step 2:** `./scripts/gw :core:audio:testDebugUnitTest :feature:gameplay:testDebugUnitTest :core:datastore:test :feature:settings:testDebugUnitTest --continue --console=plain` → FAIL (compilação pela API nova nos testes; o de assets falha porque os arquivos existem).

- [ ] **Step 3: implementar**

- `GameSession.press`: o `when` vira `guitar?.setGuitarAudible(true)` (sem o ramo de `sfx.hit`); o parâmetro `hitSounds` sai do construtor e de todos os chamadores.
- `SoundPoolSfxPlayer`: sem `hitSounds`, `HIT_SOUNDS`, `hitVolume`; `setMissVolume(volume)` guarda `missVolume`.
- `Settings`: sem `hitVolume` (construtor, `require`, `effectiveHitVolume`, `DEFAULT`); KDoc atualizada.
- `RiffPreferences`: sem `HIT_VOLUME` em `toSettings`/`updateSettings`; o `LEGACY_SFX_VOLUME` continua valendo só para `missVolume`; `updateSettings` faz `prefs.remove(floatPreferencesKey("hit_volume"))`.
- Configurações: sai o `SettingSlider` de acerto, `onHitVolume`, `onPreviewHit`, `setHitVolume`, `previewHit`, a string `settings_hit_volume`; `preview` usa `setMissVolume(settings.effectiveMissVolume)`.
- Apagar `sfx_hit_*.wav` e `scripts/gerar_sfx.py`.
- Fakes: `FakeSfxPlayer` sem `hit`/`hitLanes`/`hitVolume`, com `override fun setMissVolume(volume: Float) { missVolume = volume }`.
- Testes que verificavam acerto (`GameSessionTest` linha ~128 `sfx.hits`, `GameplayViewModelTest` ~110/309/375/389 `hitVolume`/`hits`, `SettingsViewModelTest` ~55–92 `previewHit`/`hitVolume`) passam a verificar só o erro ou saem (quando testavam só o acerto).

- [ ] **Step 4:** `./scripts/gw detekt -PdetektAutoCorrect -q; ./scripts/gw testDebugUnitTest verifyRoborazziDebug detekt --continue --console=plain` → os testes passam; o screenshot das Configurações acusa só o controle a menos (conferir e regravar com `:feature:settings:recordRoborazziDebug`).
- [ ] **Step 5:** commit `feat: sem guitarra separada a música toca inteira, só com som de erro; som de acerto sai do app`.

---

### Task 5: README, pendências e verificação

- [ ] **Step 1: README**
  - Em "Pesquisar e baixar mapas": "**Originais | Versões** escolhe entre as gravações originais e covers, remixes, ao vivo e medleys (pelo título). **Só com guitarra separada** procura nas páginas seguintes sozinho até achar 10 mapas ou conferir 100 resultados; **Procurar mais** confere outros 100."
  - Em "Biblioteca, para jogar" (topo): "Se a pasta traz a guitarra separada, ela some quando você erra e volta quando acerta. Sem ela, a música toca inteira e só o erro faz som."
  - Em "Configurações": tirar "do acerto"; ficam volume geral e do erro.
  - Em "Créditos": "Som de acerto e metrônomo da calibração: sintetizados" vira "Metrônomo da calibração: sintetizado pelo próprio projeto."; tirar `scripts/gerar_sfx.py` da tabela de módulos.
- [ ] **Step 2: pendências** — tirar o ponto da marca "Guitarra separada" que não voltava numa segunda busca e o da página só com rips (resolvidos).
- [ ] **Step 3: suíte completa** + `scripts/conferir_r8_spotify.sh`; instalar o release.
- [ ] **Step 4: no aparelho** — pesquisar "metallica" (Originais, Versões), ligar "Só com guitarra separada" (progresso; algum resultado ou "Nenhum…"); jogar um mapa sem guitarra separada (sem som de acerto, com som de erro) e o Teen Spirit (guitarra corta no erro).
- [ ] **Step 5:** commit `docs: README dos filtros e do jogo sem guitarra separada`.
