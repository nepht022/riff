# Spotify contínuo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No modo Spotify, a fila baixa durante a partida e, quando o Spotify passa de música, a partida da próxima começa sozinha (ou abre pausada pedindo para confirmar a sugestão).

**Architecture:** A fila deixa de esperar a partida e passa a expor as músicas "em preparo" (`QueuePreparing`). O fim da partida no Spotify, com a opção "Ir direto para a próxima", salva o placar e reaproveita o pedido de começo automático do Tocando agora (`NowPlayingAutoPlay`), agora com prazo variável, espera pelo preparo e uma ação nova `Confirm`, que abre a partida pausada com um painel de confirmação.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Navigation type-safe, coroutines/Flow, Robolectric + Compose UI test + Roborazzi, detekt.

**Spec:** `docs/superpowers/specs/2026-10-17-spotify-continuo-design.md`

## Global Constraints

- Branch `feat/spotify-continuo` (em cima de `feat/ajustes-tocando-biblioteca`). Modo biblioteca não muda.
- Opção "Ao acabar a música": padrão **Ir direto para a próxima** (`Settings.spotifyNextSongDirect = true`, chave `spotify_next_song_direct`); a outra é **Mostrar o resultado** (comportamento de hoje). Sempre ativa, independente de "Baixar mapas da fila".
- Prazo do pedido vindo do fim da partida: o que falta da faixa no Spotify (`durationMs - positionMs` do último estado) + **10 000 ms**; troca na pausa continua com `NowPlayingAutoPlay.WAIT_MS` (6 000 ms).
- Sugestão: só abre o painel se o mapa sugerido tiver a dificuldade; senão fica no Tocando agora. Sem mapa: fica no Tocando agora.
- Painel: título "Mapa sugerido", texto "Este mapa parece ser desta música: %1$s — %2$s", botões **Confirmar mapa e jogar** · **Escolher outro** · **Voltar ao menu** (EN: "Suggested map", "This map seems to be for this song: %1$s — %2$s", "Confirm map and play", "Choose another", "Back to menu").
- Tocando agora, música em preparo: "Baixando o mapa desta música…" / "Downloading the map for this song…".
- Configurações: "Ao acabar a música" / "When the song ends"; "Ir direto para a próxima" / "Go straight to the next one"; "Mostrar o resultado" / "Show the result".
- Fila: um download por vez, Wi-Fi (ou dados móveis), espaço e pasta continuam; o mapa da partida aberta nunca é apagado (`QueueCleaner` não muda).
- Suíte: `./scripts/gw --stop; ./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --console=plain` + `bash scripts/conferir_r8_spotify.sh`.

## Review Focus

1. A música está na fila de downloads atrás de outra (ainda não começou a baixar) quando o Spotify troca: o Tocando agora deve esperar por ela também → teste `aQueuedSongIsPreparingUntilItsTurnEnds` (Task 1).
2. O download termina e a decisão acontece antes de o `nowPlaying` recalcular o casamento: a decisão precisa ver o mapa novo → teste `afterTheDownloadTheDecisionSeesTheNewMap` (Task 3).
3. Sair do Tocando agora enquanto ele espera o preparo: nada pode começar depois → teste `leavingWhileWaitingForTheDownloadStartsNothing` (Task 3).
4. Confirmar depois de o Spotify ter passado de música (ou tocar duas vezes): não pode salvar casamento nem tocar faixa errada → teste `confirmAfterTheSongMovedOnDoesNothing` (Task 4).
5. Fim da partida sem estado do Spotify (desconectado, `playback == null`): o prazo cai para `WAIT_MS` em vez de travar ou esperar para sempre → teste `withoutPlaybackTheNextSongWaitIsTheShortOne` (Task 5).

---

### Task 1: Fila baixa durante a partida e expõe o que está em preparo

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/queue/QueuePreparing.kt`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/queue/QueuePrefetcher.kt` (tirar a espera de partida e `downloadUnlessAMatchOpens`; tirar o parâmetro `matchActivity`)
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/queue/QueueDownloads.kt` (binding no `QueueModule`)
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/queue/QueuePrefetcherTest.kt`

**Interfaces:**
- Produces: `interface QueuePreparing { val tracks: StateFlow<Set<String>>; fun start(trackUri: String); fun done(trackUri: String) }`, `@Singleton class MemoryQueuePreparing @Inject constructor() : QueuePreparing`. `QueuePrefetcher` recebe `preparing: QueuePreparing` (depois de `misses`) e não recebe mais `matchActivity`.

- [ ] **Step 1: Testes que falham** — em `QueuePrefetcherTest` (o `start()` passa a construir com `preparing = MemoryQueuePreparing()` e sem `matchActivity`):
  - `aDownloadHappensDuringAMatch`: `matchActivity.enter("x")`; fila `a`; `nowPlaying("now")` → `downloader.downloaded == listOf("md5-a" to "a")`.
  - `aDownloadIsNotStoppedWhenAMatchOpens`: `downloader.gate` segurando; `nowPlaying("now")`; `matchActivity.enter("x")`; `gate.complete(Unit)`; `runCurrent()` → `downloader.cancelled == 0` e `downloaded == listOf("md5-a" to "a")`.
  - `aQueuedSongIsPreparingUntilItsTurnEnds`: fila `a`,`b`, `gate` segurando o primeiro → `preparing.tracks.value == setOf("a", "b")`; `gate.complete(Unit)` + `runCurrent()` → `preparing.tracks.value == emptySet()`.
  - `aSongThatCannotDownloadStopsPreparing`: `conditions.unmetered = false`; fila `a` → depois de `nowPlaying("now")`, `preparing.tracks.value == emptySet()`.
  - Apagar `nothingStartsDuringAMatchAndPendingOnesWaitForTheEnd`, `aDownloadInProgressStopsWhenAMatchStartsAndResumesAfter`, `afterAMatchTheConditionsAreCheckedAgain` (o comportamento saiu do spec).
- [ ] **Step 2:** `./scripts/gw :core:data:testDebugUnitTest --tests '*QueuePrefetcherTest*'` → falha de compilação (`MemoryQueuePreparing`).
- [ ] **Step 3: Implementar.** `start(uri)` quando a faixa entra no canal (no `read`, junto do `waiting.add`); `done(uri)` num `finally` em volta do `prepare`. `prepare` sem `matchActivity.inMatch.first { !it }` e baixando direto (`download(track, target)`), sem o laço de refazer.
- [ ] **Step 4:** mesmo comando → PASS; depois `./scripts/gw :core:data:testDebugUnitTest detekt`.
- [ ] **Step 5: Commit** `feat: fila baixa durante a partida e expõe as músicas em preparo`.

### Task 2: Opção "Ao acabar a música"

**Files:**
- Modify: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/Settings.kt`, `core/datastore/src/main/kotlin/com/rodrigomachado/riff/core/datastore/RiffPreferences.kt`
- Modify: `feature/settings/src/main/kotlin/com/rodrigomachado/riff/feature/settings/SpotifyTab.kt`, `SettingsViewModel.kt`, `res/values{,-en}/strings.xml`
- Test: `SettingsTest.kt`, `RiffPreferencesTest.kt`, `SettingsViewModelTest.kt`, `SettingsContentTest.kt`, screenshot `Settings_spotify.png`

**Interfaces:**
- Produces: `Settings.spotifyNextSongDirect: Boolean = true`; `QueueAction.NextSongDirect(val direct: Boolean)`.

- [ ] **Step 1: Testes que falham:** `SettingsTest` padrão `true`; `RiffPreferencesTest.queueOptionsAreSaved` grava/lê `spotifyNextSongDirect = false`; `SettingsViewModelTest.theOtherQueueOptionsAreSaved` com `vm.onQueue(QueueAction.NextSongDirect(false))`; `SettingsContentTest`:
  - `theSongEndChoiceIsAlwaysOn`: `show()` (prefetch desligado), aba Spotify → nó selecionável "Ir direto para a próxima" `assertIsEnabled()` e `assertIsSelected()`; clique em "Mostrar o resultado" → `events == listOf(QueueAction.NextSongDirect(false).toString())`.
- [ ] **Step 2:** `./scripts/gw :core:model:test :core:datastore:testDebugUnitTest :feature:settings:testDebugUnitTest` → falha de compilação.
- [ ] **Step 3: Implementar.** Chave `spotify_next_song_direct`. No `SpotifyTab`, a escolha vem **primeiro**, com `QueueChoice(..., enabled = true)`; depois o resto como hoje.
- [ ] **Step 4:** mesmo comando + `:feature:settings:recordRoborazziDebug detekt` → PASS; olhar `Settings_spotify.png`.
- [ ] **Step 5: Commit** `feat: opção Ao acabar a música (ir direto para a próxima ou mostrar o resultado)`.

### Task 3: Tocando agora espera o preparo e sabe pedir confirmação

**Files:**
- Modify: `feature/nowplaying/src/main/kotlin/com/rodrigomachado/riff/feature/nowplaying/NowPlayingAutoPlay.kt`, `NowPlayingViewModel.kt`, `NowPlayingScreen.kt`, `NowPlayingMessages.kt`, `res/values{,-en}/strings.xml`
- Test: `NowPlayingViewModelTest.kt`, `NowPlayingContentTest.kt`, `NowPlayingScreenshotTest.kt` (novo `NowPlaying_preparing`)

**Interfaces:**
- Consumes: `QueuePreparing` (Task 1).
- Produces:
  - `NowPlayingAutoPlay.request(saved, difficulty, fromTrackUri, waitMs: Long = WAIT_MS)` (chave nova `autoPlayWaitMs`).
  - `NowPlayingAutoPlay.Action.Confirm(val request: PlayRequest) : Action` (sugestão com a dificuldade).
  - `PlayRequest(..., val confirmFirst: Boolean = false)`; `Action.Confirm` sempre com `confirmFirst = true` e `trackOffsetMs = 0`.
  - `NowPlayingUiState.Playing(nowPlaying, queueMiss, preparingMap: Boolean = false)`.
  - `NowPlayingViewModel(repository, matches, misses, preparing: QueuePreparing)`.

- [ ] **Step 1: Testes que falham** (`NowPlayingViewModelTest`, com `preparing = MemoryQueuePreparing()`; `saved` é o `SavedStateHandle` do teste):
  - `aSuggestionWithTheDifficultyAsksToConfirmInTheMatch`: pedido EXPERT de `"spotify:track:velha"`; a faixa troca para `x` com `Suggested(chart(), 0.8f)` (chart com EXPERT) → `autoPlay.value == Action.Confirm(PlayRequest(chart().id, EXPERT, "spotify:track:x", 0, confirmFirst = true))`.
  - `aSuggestionWithoutTheDifficultyStaysHere`: mesmo, com dificuldade que o chart não tem → `autoPlay.value == null`.
  - `theWaitComesFromTheRequest`: `request(saved, EXPERT, from, waitMs = 20_000)`; `advanceTimeBy(15_000)`; troca de faixa com `Matched` → `Action.Start`.
  - `afterTheDownloadTheDecisionSeesTheNewMap`: `preparing.start("spotify:track:x")`; pedido; troca para `x` com `NoMatch` → `autoPlay.value == null`; `matches.statuses["spotify:track:x"] = Matched(chart(), 0)` **sem** emitir `nowPlaying` de novo; `preparing.done("spotify:track:x")` → `Action.Start`. (O fake de `TrackMatchRepository` do módulo ganha `statuses` se não tiver.)
  - `leavingWhileWaitingForTheDownloadStartsNothing`: como acima, mas `viewModel.cancelAutoPlay()` antes do `done` → continua `null`.
  - `whatIsPreparingIsShown`: `preparing.start("spotify:track:x")`, faixa `x` `NoMatch` → `uiState.value == Playing(now, null, preparingMap = true)`.
  - `NowPlayingContentTest.preparingSaysTheMapIsDownloading`: `Playing(playing(NoMatch), preparingMap = true)` → texto "Baixando o mapa desta música…" existe.
- [ ] **Step 2:** `./scripts/gw :feature:nowplaying:testDebugUnitTest` → falha de compilação.
- [ ] **Step 3: Implementar.**
  - `decide(nowPlaying, difficulty)`: `Matched` como hoje; `Suggested` com a dificuldade → `Confirm`; o resto `null`.
  - `watchAutoPlay`: prazo de `saved[KEY_WAIT]` (padrão `WAIT_MS`); depois da troca, se a faixa estiver em `preparing.tracks`, espera `preparing.tracks.first { uri !in it }` (sem prazo; `cancelAutoPlay` cancela). Decide com o casamento **lido na hora**: `NowPlaying(playback, matches.matchFor(playback))`.
  - Tela: `Start` e `Confirm` vão para `onPlay(request)` (o `LaunchedEffect` atual trata as duas).
  - `Playing.preparingMap` = faixa em `preparing` e `match == NoMatch`; a tela mostra o texto de preparo no lugar do `queueMiss` (mesma `testTag` `QUEUE_MISS`).
- [ ] **Step 4:** `./scripts/gw :feature:nowplaying:testDebugUnitTest :feature:nowplaying:recordRoborazziDebug detekt` → PASS.
- [ ] **Step 5: Commit** `feat: Tocando agora espera o mapa em preparo e pede confirmação da sugestão na partida`.

### Task 4: Partida aberta pedindo confirmação

**Files:**
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayRoute.kt`, `GameplayViewModel.kt`, `GameplayScreen.kt`, `res/values{,-en}/strings.xml`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/NowPlayingRepository.kt` (+ implementação) e os fakes de `SpotifyControls`/`NowPlayingRepository` nos testes
- Test: `GameplayViewModelTest.kt`, `GameplayScreenTest.kt` (ou o arquivo de testes de tela existente), screenshot novo `Gameplay_confirm_suggestion`

**Interfaces:**
- Consumes: `PlayRequest.confirmFirst` (Task 3).
- Produces:
  - `GameplayRoute(..., val confirmFirst: Boolean = false)`.
  - `SpotifyControls.playback: StateFlow<PlaybackState?>` (a faixa no Spotify agora; a implementação repassa `remote.playback`).
  - `GameplayUiState.Ready.awaitingConfirmation: Boolean = false`.
  - `GameplayUiState.NextSong(val difficulty: Difficulty, val fromTrackUri: String, val waitMs: Long) : GameplayUiState`.
  - `GameplayViewModel.confirmAndPlay()`.
  - `GameplayScreen(onFinished, onExit, ..., onNextSong: (Difficulty, String, Long) -> Unit, onChooseOther: (trackUri: String, title: String) -> Unit)` — `onSongChanged` vira `onNextSong` (a troca na pausa passa `NowPlayingAutoPlay.WAIT_MS`; o valor fica em `GameplayViewModel` como `CHANGE_WAIT_MS = 6_000L` para o gameplay não depender do nowplaying).

- [ ] **Step 1: Testes que falham** (`GameplayViewModelTest`, rota com `spotifyTrackUri = "spotify:track:x"`, `confirmFirst = true`):
  - `aSuggestionToConfirmOpensPausedWithoutPlaying`: `Ready.awaitingConfirmation == true`, `isPaused == true`, o player do Spotify não recebeu `play`.
  - `confirmingSavesTheMatchAndStarts`: `confirmAndPlay()` → `trackMatches` salvou `("spotify:track:x", chartId, 1f, isManual = true)`; `awaitingConfirmation == false`, `isPaused == false`, player recebeu `play`.
  - `confirmAfterTheSongMovedOnDoesNothing`: `controls.playback.value = playbackOf("spotify:track:y")` → `uiState.value == NextSong(EXPERT, "spotify:track:x", 6_000)`; `confirmAndPlay()` → nada salvo, sem `play`.
  - `confirmTwiceSavesOnce`: dois `confirmAndPlay()` → um `save`.
  - Tela: `theConfirmPanelOffersConfirmChooseAndBack`: `GameStage` com `Ready(awaitingConfirmation = true, isPaused = true)` → textos "Mapa sugerido", "Este mapa parece ser desta música: Título — Banda", e os três botões; cliques chamam os callbacks; o menu de pausa normal ("Continuar") não aparece.
- [ ] **Step 2:** `./scripts/gw :feature:gameplay:testDebugUnitTest` → falha de compilação.
- [ ] **Step 3: Implementar.**
  - `load()`: com `confirmFirst`, não chama `session.start()` e devolve `Ready(isPaused = true, awaitingConfirmation = true)`.
  - Enquanto `awaitingConfirmation`, observa `spotify.playback`: faixa não nula e diferente de `spotifyTrackUri` → `release()` e `NextSong(difficulty, spotifyTrackUri, CHANGE_WAIT_MS)`.
  - `confirmAndPlay()`: só com `Ready.awaitingConfirmation` (marca já como falso antes de salvar, para o segundo toque não repetir); `trackMatches.save(uri, chartId, 1f, isManual = true)`; `session.start()`; `isPaused = false`.
  - Tela: com `awaitingConfirmation`, um `ConfirmOverlay` no lugar do `PauseOverlay` (mesmo fundo, `PosterTitle`, texto, `PosterButton` "Confirmar mapa e jogar", `TextButton`s "Escolher outro" e "Voltar ao menu"). `NextSong` → `LaunchedEffect` chama `onNextSong`.
- [ ] **Step 4:** `./scripts/gw :feature:gameplay:testDebugUnitTest :feature:gameplay:recordRoborazziDebug :core:data:testDebugUnitTest detekt` → PASS.
- [ ] **Step 5: Commit** `feat: partida de sugestão abre pausada pedindo para confirmar o mapa`.

### Task 5: Fim da partida segue para a próxima

**Files:**
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModel.kt`, `GameplayScreen.kt`
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/RiffNavHost.kt` (ligar `onNextSong`, `onChooseOther`, `confirmFirst`)
- Modify: `README.md` (seção "Mapas da fila do Spotify" e modo Spotify)
- Test: `GameplayViewModelTest.kt`

**Interfaces:**
- Consumes: `Settings.spotifyNextSongDirect` (Task 2), `NowPlayingAutoPlay.request(..., waitMs)` (Task 3), `GameplayUiState.NextSong`, `SpotifyControls.playback` (Task 4).

- [ ] **Step 1: Testes que falham** (`GameplayViewModelTest`, Spotify):
  - `withNextSongDirectTheEndSavesAndGoesOn`: `spotifyNextSongDirect = true`; `controls.playback.value` com `durationMs = 200_000`, `positionMs = 170_000`; `onSessionFinished()` → placar salvo (`scores.saved.size == 1`) e `uiState.value == NextSong(EXPERT, "spotify:track:x", 40_000)`.
  - `withShowResultTheEndGoesToTheResult`: `false` → `Finished(id)`.
  - `withoutPlaybackTheNextSongWaitIsTheShortOne`: `playback.value = null` → `NextSong(..., 6_000)`.
  - `theLibraryAlwaysGoesToTheResult`: rota sem `spotifyTrackUri`, opção ligada → `Finished(id)`.
- [ ] **Step 2:** `./scripts/gw :feature:gameplay:testDebugUnitTest` → FAIL.
- [ ] **Step 3: Implementar** em `onSessionFinished`: depois de `scores.save`, com Spotify e opção ligada → `NextSong(difficulty, uri, wait)`, `wait = playback?.let { (it.durationMs - it.positionMs).coerceAtLeast(0) + 10_000 } ?: CHANGE_WAIT_MS` (só se a faixa do estado for a da partida; senão `CHANGE_WAIT_MS`). `RiffNavHost`: `onNextSong` = o atual `onSongChanged` passando `waitMs`; `onChooseOther` = `popBackStack()` e `navigate(LibraryRoute(matchTrackUri, title))`; `onPlay` do Tocando agora passa `confirmFirst` para a `GameplayRoute`.
- [ ] **Step 4:** suíte completa (Global Constraints) → BUILD SUCCESSFUL + `ok` do R8.
- [ ] **Step 5: Commit** `feat: fim da partida no Spotify segue para a próxima música`.

### Task 6: Medição no celular e entrega

- [ ] Instalar o release; no S25, uma partida do Spotify com a fila baixando (registro `RiffQueue` mostrando "pronto" durante a partida) e outra sem download; `scripts/desempenho.sh medir` + `scripts/quadros_travados.py` nas duas. Critério do spec: com download ≤ sem download (0–3 atrasados em ~2.500). Se pior: `Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND)` no download da fila, medir de novo, ledger.
- [ ] Reinstalar o release (o `desempenho.sh` instala o debug).
