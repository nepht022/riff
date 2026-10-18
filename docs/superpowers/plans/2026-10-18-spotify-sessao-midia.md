# Spotify pela sessão de mídia — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O Riff passa a acompanhar e controlar o Spotify pela sessão de mídia do Android (MediaSession + "Acesso às notificações"). O SDK, o login e a API web do Spotify saem do app.

**Architecture:** A fronteira `SpotifyRemote` (`:core:spotify`) fica. A implementação `AppRemoteSpotify` é trocada por `MediaSessionSpotify`, que lê um `SpotifySession` (interface própria sobre `MediaController`, com um fake nos testes). A fila passa a vir da mesma sessão (`SessionSpotifyQueue`). O resto do app (Tocando agora, partida, fila, Configurações) muda só onde dependia de conexão, autorização ou duração da fila.

**Tech Stack:** Kotlin, Compose, Hilt, Coroutines/Flow, `android.media.session.*`, `NotificationListenerService`, JUnit4 + Robolectric + Roborazzi, detekt.

**Spec:** `docs/superpowers/specs/2026-10-18-spotify-sessao-midia-design.md`

## Global Constraints

- **Só o pacote `com.spotify.music`:** o filtro fica numa constante só, `SPOTIFY_PACKAGE`.
- **Faixa de música** = `MEDIA_ID` que começa com `spotify:track:`. O resto (episódios, anúncios, nulo) não é faixa.
- **`trackUri`** continua sendo a chave de `track_matches`, dos ajustes e de `queue_downloads`. Nenhuma migração apaga dados de casamento.
- **Nada de `playFromUri`**: ele pode trocar a playlist e quebrar o modo contínuo e a fila.
- **"Jogar de novo":** `skipToPrevious()` no máximo 2 vezes. Se a faixa não virar a atual em cerca de 3 s, aparece "Volte a música no Spotify para jogar de novo".
- **Anúncio** (`ADVERTISEMENT = 1`): o modo contínuo espera; no meio da partida, ela pausa sozinha e continua quando a nossa faixa voltar a tocar.
- **O `NotificationListenerService` não lê notificações**: existe só para liberar `getActiveSessions`.
- **Textos novos:** em pt e en, no tom dos atuais. "Se preferir, jogue no modo Biblioteca" é pedido do dono.
- **Sincronia:** com a correção fixa, a posição da sessão fica dentro de ±50 ms da posição do SDK (medida da Task 3).
- **Fluxo do repositório:** TDD (RED→GREEN), suíte completa (`./scripts/gw --stop; ./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --continue --console=plain`) e os limites do detekt (LongMethod 60, TooManyFunctions 11, MaxLineLength 120). Módulo novo de feature precisa de `src/test/resources/robolectric.properties` com `sdk=35`.

## Review Focus

1. **Anúncio no meio da partida:** a partida pausa e continua sozinha, sem contar como fim de música (`completed`). Teste: `SpotifySongPlayerTest.anAdMidSongPausesAndResumesWithoutEnding` (Task 4).
2. **Permissão desligada com o app aberto:** o Tocando agora volta ao aviso, e a partida congela a pista. Teste: `MediaSessionSpotifyTest.losingThePermissionFailsTheConnection` (Task 2) e `SpotifySongPlayerTest.losingTheSessionFreezesTheTrack` (Task 4).
3. **O jogador troca de música no próprio Spotify durante a partida:** continua sendo "a música acabou" (`completed`), como hoje. Teste: `SpotifySongPlayerTest.anotherTrackAfterOursEndsTheMatch` (Task 4).
4. **Fila vazia ou sem item ativo** (rádio, aleatório, conta grátis): nada é baixado e nada quebra. Teste: `SessionSpotifyQueueTest.withoutAnActiveItemThereIsNoNext` (Task 5).
5. **Anúncio entre músicas no modo contínuo:** o pedido de começar a próxima não pode ser descartado durante o anúncio (`playback == null`). Teste: `NowPlayingViewModelTest.anAdDoesNotCancelTheNextSong` (Task 6).

---

### Task 1: Ler a sessão do Spotify (modelo puro)

**Files:**
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/session/SessionSnapshot.kt`
- Test: `core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/session/SessionSnapshotTest.kt`

**Interfaces:**
- Produces:
  - `data class SessionSnapshot(val mediaId: String?, val title: String?, val artist: String?, val durationMs: Long?, val state: Int, val positionMs: Long, val updatedAtElapsedMs: Long, val speed: Float, val isAd: Boolean)`, onde `state` são as constantes de `android.media.session.PlaybackState`;
  - `fun SessionSnapshot.toPlaybackState(correctionMs: Long): PlaybackState?`;
  - `data class SessionQueueItem(val queueId: Long, val mediaId: String?, val title: String?, val artist: String?)`;
  - `const val SPOTIFY_PACKAGE = "com.spotify.music"`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun aPlayingTrackBecomesPlaybackWithTheSessionClock() {
    val s = snap(mediaId = "spotify:track:1", state = STATE_PLAYING, positionMs = 10_000, updatedAtElapsedMs = 500)
    assertEquals(PlaybackState("spotify:track:1", "T", "A", 200_000, 10_000 - 340, false, 500), s.toPlaybackState(-340))
}
@Test fun pausedBufferingAndStoppedAreNotPlaying() {
    listOf(STATE_PAUSED, STATE_BUFFERING, STATE_STOPPED).forEach { assertTrue(snap(state = it).toPlaybackState(0)!!.isPaused) }
}
@Test fun aPausedPositionIsNotCorrected() {
    assertEquals(10_000, snap(state = STATE_PAUSED, positionMs = 10_000).toPlaybackState(-340)!!.positionMs)
}
@Test fun notATrackIsNoPlayback() {
    assertNull(snap(mediaId = "spotify:episode:9").toPlaybackState(0))
    assertNull(snap(mediaId = null).toPlaybackState(0))
    assertNull(snap(isAd = true).toPlaybackState(0))
}
@Test fun negativePositionsAreClampedToZero() {
    assertEquals(0, snap(state = STATE_PLAYING, positionMs = 100).toPlaybackState(-340)!!.positionMs)
}
```
`snap(...)` é um auxiliar do teste com padrões `mediaId = "spotify:track:1"`, título "T", artista "A", `durationMs = 200_000`, `STATE_PLAYING` e `speed = 1f`. A correção vale só tocando, porque a medida mostrou o erro na extrapolação depois de tocar. O valor real vem da Task 3; aqui ele é um parâmetro.

- [ ] **Step 2: Run** `./scripts/gw :core:spotify:testDebugUnitTest --tests '*SessionSnapshotTest*'`. Expected: FAIL (unresolved reference).
- [ ] **Step 3: Implement** `toPlaybackState` em `SessionSnapshot.kt`, com `durationMs ?: 0`, título e artista `orEmpty()` e `capturedAtElapsedMs = updatedAtElapsedMs`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Sessão de mídia: estado do Spotify em PlaybackState"`

### Task 2: `MediaSessionSpotify` — conexão, estado e comandos

**Files:**
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/session/SpotifySession.kt` (interface + `AndroidSpotifySession` sobre `MediaSessionManager`/`MediaController`)
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/session/RiffNotificationListener.kt` (serviço vazio)
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/session/MediaSessionSpotify.kt`
- Modify: `core/spotify/src/main/AndroidManifest.xml` (declara o serviço com `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`, `exported="false"` e o intent-filter `android.service.notification.NotificationListenerService`)
- Modify: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyRemote.kt` (acrescenta `val adPlaying: StateFlow<Boolean>`)
- Modify: `core/model/.../SpotifyConnection.kt` (acrescenta `SpotifyError.NO_PERMISSION`; os outros valores saem na Task 6)
- Test: `core/spotify/src/test/kotlin/com/rodrigomachado/riff/core/spotify/session/MediaSessionSpotifyTest.kt`, com `FakeSpotifySession`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - ```kotlin
    interface SpotifySession {
        val hasPermission: Boolean
        val isInstalled: Boolean
        val snapshot: StateFlow<SessionSnapshot?>
        val queue: StateFlow<List<SessionQueueItem>>
        val activeQueueId: StateFlow<Long?>
        fun recheck()
        fun play(); fun pause(); fun seekTo(ms: Long); fun skipToNext(); fun skipToPrevious()
    }
    ```
    `recheck()` relê a permissão e as sessões.
  - `class MediaSessionSpotify @Inject constructor(session: SpotifySession, @SessionCorrection correctionMs: Long, clock: () -> Long) : SpotifyRemote`.
  - `const val SESSION_CORRECTION_MS = -340L` (provisório; a Task 3 fixa o valor).

- [ ] **Step 1: Write the failing tests** (`MediaSessionSpotifyTest`):

```kotlin
@Test fun withoutPermissionTheConnectionSaysSo() { session.hasPermission = false; remote.connect()
    assertEquals(SpotifyConnection.Failed(SpotifyError.NO_PERMISSION), remote.connection.value) }
@Test fun withoutSpotifyInstalledTheConnectionSaysSo() { session.isInstalled = false; remote.connect()
    assertEquals(SpotifyConnection.Failed(SpotifyError.NOT_INSTALLED), remote.connection.value) }
@Test fun withPermissionItIsConnectedEvenWithNothingPlaying() { remote.connect()
    assertEquals(SpotifyConnection.Connected, remote.connection.value); assertNull(remote.playback.value) }
@Test fun theSessionStateIsThePlayback() { remote.connect(); session.snapshot.value = playing("spotify:track:1", 5_000, at = 100)
    assertEquals("spotify:track:1", remote.playback.value!!.trackUri) }
@Test fun anAdIsFlaggedAndIsNoPlayback() { remote.connect(); session.snapshot.value = playing("spotify:track:1").copy(isAd = true)
    assertTrue(remote.adPlaying.value); assertNull(remote.playback.value) }
@Test fun losingThePermissionFailsTheConnection() { remote.connect(); session.hasPermission = false; remote.connect()
    assertEquals(SpotifyConnection.Failed(SpotifyError.NO_PERMISSION), remote.connection.value); assertNull(remote.playback.value) }
@Test fun playingTheCurrentTrackRestartsIt() { remote.connect(); session.snapshot.value = playing("spotify:track:1")
    remote.play("spotify:track:1"); assertEquals(listOf("seekTo:0", "play"), session.commands) }
@Test fun playingThePreviousTrackGoesBackWithPrevious() { remote.connect(); session.snapshot.value = playing("spotify:track:2")
    remote.play("spotify:track:1"); assertEquals(listOf("previous"), session.commands)
    session.snapshot.value = playing("spotify:track:2", 0); /* ainda não voltou: o ⏮ do começo só reinicia */
    advance(); assertEquals(listOf("previous", "previous"), session.commands) }
@Test fun playingATrackThatIsNotThereGivesUpAfterTwoPrevious() { /* faixa nunca vira a pedida */
    assertEquals(2, session.commands.count { it == "previous" }) }
@Test fun commandsGoToTheSession() { /* pause, resume=play, seekTo, skipNext, skipPrevious */ }
```
O primeiro ⏮ no começo da faixa já vai para a anterior. Mas quando a faixa atual já passou dos primeiros segundos, o primeiro ⏮ só a reinicia, e por isso existe o segundo. Os testes usam o relógio de teste para os cerca de 3 s.

- [ ] **Step 2: Run** `./scripts/gw :core:spotify:testDebugUnitTest --tests '*MediaSessionSpotifyTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** `MediaSessionSpotify`:
  - `connect()` e `refresh()` chamam `session.recheck()` e recalculam `connection`;
  - `playback` = `snapshot.toPlaybackState(correctionMs)`;
  - `adPlaying` = `snapshot?.isAd == true`;
  - `play(uri)`: se a faixa é a atual, `seekTo(0)` e `play()`; senão, `skipToPrevious()`, espera até 1,5 s pela faixa pedida e, se não vier, um segundo `skipToPrevious()`, até cerca de 3 s no total. A falha não lança erro: a Task 4 percebe pelo prazo de começo.

  Depois, implemente `AndroidSpotifySession`:
  - `MediaSessionManager.getActiveSessions(ComponentName(context, RiffNotificationListener::class.java))` filtrado por `SPOTIFY_PACKAGE`;
  - `addOnActiveSessionsChangedListener` e `MediaController.Callback` (`onMetadataChanged`, `onPlaybackStateChanged`, `onQueueChanged`, `onSessionDestroyed`) atualizam os flows;
  - `hasPermission` = `NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)`;
  - `isInstalled` pelo `PackageManager` (`<queries><package android:name="com.spotify.music"/></queries>` no manifesto);
  - `SecurityException` conta como sem permissão;
  - o `SessionSnapshot` sai de `MediaMetadata` (`METADATA_KEY_MEDIA_ID`, `TITLE`, `ARTIST`, `DURATION`, `"android.media.metadata.ADVERTISEMENT"` como long ≠ 0) e de `PlaybackState` (`state`, `position`, `lastPositionUpdateTime`, `playbackSpeed`);
  - a fila sai de `controller.queue` e de `playbackState.activeQueueItemId`.

  `AndroidSpotifySession` não tem teste de unidade (depende do sistema). Ele é coberto pela Task 3, no aparelho.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Sessão de mídia: MediaSessionSpotify e a leitura do Android"`

### Task 3: Medir a sessão contra o SDK (aparelho) e fixar a correção

O `AppRemoteSpotify` ainda está ligado e autorizado com o SHA-1 novo. Esta tarefa não muda o comportamento do app: os registros são temporários.

**Files:**
- Modify (temporário, não vai para o commit): `RiffApplication.kt`, para iniciar um coletor que registra, a cada mudança de qualquer um dos dois, `println("RiffSync sdk=<uri> <posição extrapolada agora> sessao=<uri> <posição extrapolada agora, sem correção>")`. São usados o `AppRemoteSpotify` injetado direto e o `MediaSessionSpotify` com `correctionMs = 0`.
- Modify (fica): `MediaSessionSpotify.kt`, com `SESSION_CORRECTION_MS` = o valor medido.

- [ ] **Step 1:** Instalar o debug com os dois coletores. O dono liga "Acesso às notificações" para o Riff.
- [ ] **Step 2:** Pelo `adb`, com uma música Premium, executar tocar do começo (`play` do SDK), pausar, retomar, avançar (`seekTo` 60 s) e próxima. Coletar `adb logcat | grep RiffSync` por 2 minutos.
- [ ] **Step 3:** Calcular a diferença `sessao − sdk` em cada caso, na mesma música e no mesmo instante. Expected: média estável (esperado algo perto de −340 ms tocando) e desvio de até ±50 ms em cada caso. Se um caso destoar mais que isso, registrar no ledger como `Ruling:` e tratar na Task 4 (por exemplo, uma janela "não confiar logo depois do seek").
- [ ] **Step 4:** Gravar o valor médio em `SESSION_CORRECTION_MS`, desfazer os coletores (`git checkout RiffApplication.kt`) e rodar `./scripts/gw :core:spotify:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Sessão de mídia: correção medida contra o SDK (<valor> ms)"`, com a tabela de medidas na mensagem.

### Task 4: A partida sobre a sessão (`SpotifySongPlayer`)

**Files:**
- Modify: `core/spotify/.../SpotifySongPlayer.kt`
- Modify: `core/audio/.../Players.kt` (acrescenta `val trackRefused: Boolean get() = false` ao `SongPlayer`)
- Modify: `feature/gameplay/.../GameplayViewModel.kt` e `GameplayScreen.kt` (mostra o aviso da faixa recusada na pausa)
- Modify: `feature/gameplay/src/main/res/values{,-en}/strings.xml`
- Modify: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/SpotifyModule.kt` (liga `SpotifyRemote` a `MediaSessionSpotify`, provê `@SessionCorrection` e o relógio `SystemClock::elapsedRealtime`)
- Test: `core/spotify/src/test/.../SpotifySongPlayerTest.kt`, `feature/gameplay/src/test/.../GameplaySpotifyFlowTest.kt`

**Interfaces:**
- Consumes: Task 2 (`SpotifyRemote.adPlaying`).
- Produces: `SongPlayer.trackRefused: Boolean`; string `gameplay_spotify_go_back` = "Volte a música no Spotify para jogar de novo" / "Go back to the song on Spotify to play again".

- [ ] **Step 1: Write the failing tests** (`SpotifySongPlayerTest`; o `FakeSpotifyRemote` ganha `adPlaying`):

```kotlin
@Test fun anAdMidSongPausesAndResumesWithoutEnding() // tocando a nossa → ad (playback null, adPlaying true): posição congela, isCompleted false → a nossa volta tocando: a posição volta a andar sem novo play()
@Test fun losingTheSessionFreezesTheTrack()           // playback null sem anúncio: congela; a nossa volta pausada → continua pausada até play()
@Test fun anotherTrackAfterOursEndsTheMatch()         // como hoje: outra faixa depois de ver a nossa → isCompleted
@Test fun aTrackThatNeverStartsIsRefused()            // play() e a faixa nunca vira a pedida em START_TIMEOUT_MS → trackRefused == true e isPlaying == false
@Test fun resumingAfterTheSessionCameBackSeeksTheSameTrack() // depois de playback null, play() com a mesma faixa → seekTo(posição) + resume(), sem play(uri)
```
Em `GameplaySpotifyFlowTest`: `aRefusedTrackShowsGoBackInThePause`. Com o player fake, `trackRefused = true` → o estado da pausa expõe a mensagem `gameplay_spotify_go_back`.

- [ ] **Step 2: Run** `./scripts/gw :core:spotify:testDebugUnitTest :feature:gameplay:testDebugUnitTest`. Expected: os testes novos FAIL.
- [ ] **Step 3: Implement**:
  - o caminho de reconexão vira "sessão sumiu e voltou": `resumeAfterReconnecting` passa a ser `seekTo` + `resume` quando a faixa é a mesma, e espera caso contrário;
  - `null` com `adPlaying` = pausa de fora, e a volta da nossa faixa tocando é aceita como retomada;
  - o prazo de começo já existente marca `trackRefused`;
  - o `prepare` deixa de esperar `Connected` por 15 s: com `Failed(NO_PERMISSION | NOT_INSTALLED)`, ele lança `IOException`, como hoje.

  Revalidar com a medida da Task 3 se `isEarly`/`PAUSE_CONFIRM_MS` e `START_POSITION_SLACK_MS` ainda valem. O que não valer sai, com `Ruling:` no ledger.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Partida sobre a sessão de mídia: anúncio, sessão perdida e Jogar de novo"`

### Task 5: A fila pela sessão

**Files:**
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/session/SessionSpotifyQueue.kt`
- Modify: `core/spotify/.../web/SpotifyQueueApi.kt`: `QueuedTrack`, `QueueRead` e `SpotifyQueue` vão para `core/spotify/.../SpotifyQueue.kt`, com `QueuedTrack.durationMs: Long?`. `QueueRead.RetryAfter` sai.
- Modify: `core/data/.../queue/QueuePrefetcher.kt`
- Modify: `core/data/.../TrackMatchRepository.kt` (acrescenta `suspend fun matchFor(trackUri: String, query: TrackQuery): MatchStatus`; o `matchFor(PlaybackState)` delega para ele)
- Test: `core/spotify/src/test/.../session/SessionSpotifyQueueTest.kt`, `core/data/src/test/.../queue/QueuePrefetcherTest.kt`

**Interfaces:**
- Consumes: Task 2 (`SpotifySession.queue`, `activeQueueId`).
- Produces:
  - `class SessionSpotifyQueue @Inject constructor(session: SpotifySession) : SpotifyQueue`, em que `next(count)` = os `count` itens depois do ativo que são faixas (`spotify:track:`);
  - `val changes: Flow<Unit>`, que emite a cada mudança da fila.

- [ ] **Step 1: Write the failing tests**:

```kotlin
// SessionSpotifyQueueTest
@Test fun theNextAreTheItemsAfterTheActiveOne()   // ativo = 16; itens 16,17,18,19 → next(2) = 17,18 com durationMs == null
@Test fun withoutAnActiveItemThereIsNoNext()     // activeQueueId null → Tracks(emptyList())
@Test fun episodesAreSkipped()                   // item spotify:episode no meio → pulado
// QueuePrefetcherTest
@Test fun aMapForATrackWithoutDurationIsNotSavedAsAMatch()  // fila sem duração → download sem matchTrackUri; nenhum matches.save; queue_downloads registra (com "apagar não jogados")
@Test fun theQueueIsReadAgainWhenItChanges()                // changes emite → nova leitura sem esperar READ_INTERVAL_MS
@Test fun aTrackWithDurationStillMatchesAsToday()           // duração presente → comportamento atual (matchTrackUri = uri)
```
- [ ] **Step 2: Run** `./scripts/gw :core:spotify:testDebugUnitTest :core:data:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement**:
  - `QueuePrefetcher.download` passa `track.uri` ao downloader só se `track.durationMs != null && suggestion == null`;
  - `statusOf` usa `matches.matchFor(track.uri, TrackQuery(title, artist, durationMs))`;
  - `follow()` lê a fila na troca de faixa e a cada `changes` (no lugar do `delay` fixo), mantendo uma leitura de segurança a cada `READ_INTERVAL_MS`;
  - o ramo `Unavailable` deixa de desligar `queuePrefetch` (não existe mais autorização);
  - `SpotifyQueue` liga a `SessionSpotifyQueue` no `SpotifyModule`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Fila do Spotify pela sessão de mídia"`

### Task 6: Tocando agora — permissão, Spotify parado e anúncio

**Files:**
- Modify: `core/model/.../SpotifyConnection.kt`: `SpotifyError` fica `{ NO_PERMISSION, NOT_INSTALLED }`.
- Modify: `core/data/.../NowPlayingRepository.kt` (acrescenta `val adPlaying: StateFlow<Boolean>`, vindo do remote)
- Modify: `feature/nowplaying/.../NowPlayingViewModel.kt`, `NowPlayingScreen.kt`, `NowPlayingMessages.kt` e as strings pt/en
- Test: `feature/nowplaying/src/test/.../NowPlayingViewModelTest.kt`, `NowPlayingContentTest.kt` e os screenshots

**Interfaces:**
- Consumes: Task 2.
- Produces:
  - `NowPlayingUiState.Ad`;
  - `NowPlayingUiState.Unavailable(NO_PERMISSION)`, que mostra Permitir + Biblioteca;
  - `NothingPlaying` com o botão "Abrir o Spotify";
  - os callbacks de tela `onAllow: () -> Unit` (abre `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`) e `onOpenSpotify: () -> Unit` (`packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)`).

- [ ] **Step 1: Write the failing tests**:

```kotlin
@Test fun withoutPermissionItAsksAndOffersTheLibrary()  // texto "Para jogar com a música do Spotify, o Riff precisa ver o que está tocando"; botões "Permitir" e "Se preferir, jogue no modo Biblioteca"
@Test fun nothingPlayingOffersToOpenSpotify()           // "Abra o Spotify e dê play numa música" + "Abrir o Spotify"
@Test fun anAdSaysTheMatchWaits()                       // "Anúncio no Spotify: a partida começa quando ele acabar"
@Test fun returningToTheAppRechecksThePermission()      // onReturn() com Unavailable(NO_PERMISSION) → repository.connect()
@Test fun anAdDoesNotCancelTheNextSong()                // pedido de autoplay pendente; nowPlaying vira null com adPlaying → o pedido continua; a próxima faixa chega → começa
```
- [ ] **Step 2: Run** `./scripts/gw :feature:nowplaying:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement** os estados e textos acima.
  - `onReturn()` passa a reconectar em `NO_PERMISSION` e `NOT_INSTALLED`;
  - o filtro de autoplay ignora `null` enquanto `adPlaying`;
  - tirar os ramos e strings de `NOT_CONFIGURED`, `NOT_LOGGED_IN`, `NOT_AUTHORIZED`, `OFFLINE`, `DISCONNECTED` e `UNKNOWN`, e ajustar quem usava esses valores (`Fakes` dos testes, `GameplayViewModel`).
- [ ] **Step 4: Run** `./scripts/gw :feature:nowplaying:testDebugUnitTest :feature:nowplaying:recordRoborazziDebug`, depois `verifyRoborazziDebug`. Expected: PASS. Conferir os PNGs novos à vista.
- [ ] **Step 5: Commit** `git commit -m "Tocando agora: permissão, Spotify parado e anúncio"`

### Task 7: Configurações → Spotify sem autorização

**Files:**
- Modify: `feature/settings/.../SpotifyTab.kt`, `SettingsViewModel.kt` e as strings
- Test: `feature/settings/src/test/.../SettingsViewModelTest.kt`, `SettingsContentTest.kt` e os screenshots

- [ ] **Step 1: Write the failing tests**:
  - `turningOnQueueMapsOnlySavesTheOption`: `onQueue(Prefetch(true))` liga `queuePrefetch` sem abrir login;
  - `theSpotifyTabHasNoAuthorizationMessage`: nenhum texto de "autorizar" ou "autorização perdida".
- [ ] **Step 2: Run** `./scripts/gw :feature:settings:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement**: tirar `QueueMessage`, o uso de `SpotifyWebAuth`/`WebAuthState` e os textos de autorização. Os outros interruptores ficam iguais.
- [ ] **Step 4: Run** the tests, then `recordRoborazziDebug` and `verifyRoborazziDebug` do módulo. Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Configurações: fila do Spotify sem autorização"`

### Task 8: Tirar o SDK, o login e a API web; documentos

**Files:**
- Delete:
  - `AppRemoteSpotify.kt`, `Reconnector.kt`, `ReconnectPolicy.kt`, `ForegroundActivity.kt`, `SpotifyConfig.kt`, `SpotifyMappers.kt` e o pacote `web/` inteiro, com os testes correspondentes;
  - `third_party/maven/com/spotify/`;
  - `scripts/conferir_r8_spotify.sh`.
- Modify:
  - `core/spotify/build.gradle.kts`: sem `buildConfig`, `SPOTIFY_*`, `spotify.app.remote`, `spotify.auth` e `gson`, se não houver outro uso;
  - `gradle/libs.versions.toml`;
  - `core/spotify/src/main/AndroidManifest.xml`: sem `RedirectUriReceiverActivity`;
  - `app/proguard-rules.pro`: sem as regras `com.spotify.*`;
  - `app/src/main/res/xml/data_extraction_rules.xml` e `backup_rules.xml`: sem `riff_spotify_web.xml`;
  - `RiffApplication.kt`: sem `ForegroundActivity`, e apaga uma vez o arquivo antigo com `deleteSharedPreferences("riff_spotify_web")`;
  - `feature/licenses/.../AndroidLicenseSource.kt`: sem o item "Spotify App Remote SDK" e sem o `IN_CONTENT` dele;
  - os textos de privacidade pt/en em `feature/licenses`: a música tocando e a fila são lidas "pelo controle de mídia do Android";
  - `README.md`: "Instalar e configurar → Spotify" vira "Spotify: permitir o acesso a notificações"; em "Enviar o APK", sem User Management e sem Premium; "Mapas da fila do Spotify"; Créditos; a lista de módulos (`:core:spotify` = "Spotify pela sessão de mídia do Android"); e o comando de conferência do R8 sai;
  - `third_party/README.md`: sem a seção do SDK.
- Test: `feature/licenses/src/test/.../AndroidLicenseSourceTest.kt` e `LicensesTest.kt`, para o texto novo da privacidade.

- [ ] **Step 1: Write the failing test**: `theSpotifySdkIsNoLongerListed` (nenhum item com "Spotify App Remote" em `content()`) e a asserção do texto "pelo controle de mídia do Android" em `LicensesTest`.
- [ ] **Step 2: Run** `./scripts/gw :feature:licenses:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 3: Implement** as remoções e os textos.
- [ ] **Step 4: Run** a suíte completa. Expected: `BUILD SUCCESSFUL`. Os flaky conhecidos (`lintAnalyzeDebugUnitTest` FIR, `ResamplingPcmSourceTest`, `SettingsContentTest` com timeout ocioso, `MigrationTest` FileNotFound) são rodados de novo sozinhos e registrados. Depois, conferir `unzip -l app/build/outputs/apk/release/app-release.apk | grep -ci spotify` = 0 em classes do SDK (`com/spotify/android`), e que o build passa sem `spotify.clientId` no `local.properties`.
- [ ] **Step 5: Commit** `git commit -m "Sai o SDK do Spotify: o Riff segue pela sessão de mídia"`

### Task 9: Aceite no aparelho

- [ ] **Step 1:** Instalar o release por cima do atual, sem desinstalar. Expected: os mapas escolhidos continuam casados e com os ajustes.
- [ ] **Step 2:** Com o dono, jogar 3 partidas Premium: uma do começo, uma com pausa e retomada, uma pulando na pausa. Expected: nenhum salto de pista e sincronia percebida igual à de antes.
- [ ] **Step 3:** ⏮ ⏯ ⏭ no Tocando agora; modo contínuo com 2 músicas; a fila baixando as 2 próximas durante a partida; "Jogar de novo" com "Mostrar o resultado". Expected: tudo funciona.
- [ ] **Step 4:** Desligar a permissão com o app aberto, e de novo durante a partida. Expected: o aviso aparece e a pista congela.
- [ ] **Step 5:** Registrar o resultado no ledger. Em seguida, a revisão final e o menu de merge.
