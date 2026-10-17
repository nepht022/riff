# Mapas da fila do Spotify — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** com a opção ligada, o Riff mantém baixados e casados os mapas das duas próximas músicas da fila do Spotify e apaga os que passaram sem ser jogados.

**Architecture:** `:core:spotify` ganha a autorização da API Web (PKCE, token guardado criptografado) e a leitura da fila. `:core:data` ganha o coordenador da fila (`QueuePrefetcher`, singleton): escolhe o mapa (`QueueChartPicker`), baixa com o `ChartDownloader` existente, registra em `queue_downloads` (Room v7) e apaga pelas regras (`QueueCleaner`), respeitando partida em andamento (`MatchActivity`), rede e espaço. As Configurações ganham a aba Spotify com quatro opções.

**Tech Stack:** Kotlin, coroutines/Flow, Hilt, Room (AutoMigration), DataStore, `HttpURLConnection` (como o `:core:encore`), `com.spotify.android:auth:5.0.0`, Android Keystore, Compose, Robolectric/Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-17-fila-spotify-design.md`

## Global Constraints

- Janela: as **2** próximas faixas de música da fila depois da atual; episódios ignorados; a atual não entra.
- Leitura da fila: a cada troca de música e a cada **60 s** com a mesma música; só com o Riff conectado ao Spotify; o recurso nunca chama `connect()`.
- Escolha: `TrackMatcher.score ≥ TrackMatcher.AUTOMATIC_THRESHOLD` (0,92), com guitarra; originais (`VersionTitles.isVersion == false`) antes; desempate: nota, guitarra separada (no máximo **3** olhadas `EncoreClient.hasSeparateGuitar`), mais dificuldades de guitarra. Nada acima do limiar → não baixa e a faixa fica "tentada" até o Riff fechar.
- Música só com mapa sugerido: `queueReplaceSuggested = true` → entra na busca; `false` → pulada.
- Downloads em sequência; nenhum começa com partida em andamento; no máximo **3** mapas da fila não jogados e apagáveis ao mesmo tempo (o limite não vale com "apagar" desligado).
- Rede: sem "dados móveis", só rede sem cobrança (`NET_CAPABILITY_NOT_METERED`). Espaço livre mínimo: **200 MB**. Sem pasta com escrita: não baixa.
- Preferências e padrões: `queue_prefetch` falso, `queue_delete_unplayed` verdadeiro, `queue_use_mobile_data` falso, `queue_replace_suggested` verdadeiro.
- Linha em `queue_downloads` só é gravada se "apagar" estiver ligada no momento do download.
- Escopo da API Web: `user-read-playback-state`; fila em `GET https://api.spotify.com/v1/me/player/queue`; token em `https://accounts.spotify.com/api/token`; refresh token criptografado com Android Keystore, nunca no log; 401 renova uma vez; 429 respeita `Retry-After`; o Riff nunca abre a autorização sozinho.
- Textos (pt / en) da aba **Spotify**:
  - "Baixar mapas da fila" — "Enquanto você ouve, o Riff baixa do Encore os mapas das duas próximas músicas da fila do Spotify." / "Download queue maps" — "While you listen, Riff downloads from Encore the maps of the next two songs in your Spotify queue."
  - "Apagar os que eu não joguei" — "Quando a música passa sem você jogar, o mapa baixado sozinho é apagado do celular." / "Delete the ones I didn't play" — "When the song ends without you playing it, the map downloaded on its own is deleted from the phone."
  - "Usar dados móveis" — "Desligado, só baixa no Wi-Fi. Cada mapa tem dezenas de MB." / "Use mobile data" — "Off, downloads only on Wi-Fi. Each map is tens of MB."
  - "Música com mapa sugerido": "Baixar um mais parecido" / "Manter o que já tenho" — "Song with a suggested map": "Download a closer one" / "Keep the one I have".
  - Recusa: "Sem a autorização do Spotify, o Riff não consegue ver a fila." / "Without Spotify's permission, Riff can't see the queue."
  - Perdida: "O Spotify pediu para autorizar de novo. Ligue a opção para autorizar." / "Spotify asked to authorize again. Turn the option on to authorize."
- Fluxo do projeto: TDD, detekt, suíte completa `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --console=plain` + `scripts/conferir_r8_spotify.sh`.

## Review Focus

1. Mapa errado: resultado do Encore com título parecido de outra música (ou cover com o mesmo nome) não pode ser baixado — testes de limiar e de originais no Task 3.
2. Apagar o que não devia: mapa jogado, baixado na mão ou baixado com "apagar" desligada nunca é apagado — testes no Task 7.
3. Partida a 120 fps: nenhum download começa durante a partida e os pendentes esperam o fim — teste no Task 6.
4. Token: o refresh token nunca vai para o log nem para preferências em texto puro, e 401 após renovar desliga a opção sem abrir tela — testes no Task 4.
5. Dados móveis: com a opção desligada, nada baixa em rede com cobrança, nem a pesquisa no Encore — teste no Task 6.

---

### Task 1: Preferências da fila

**Files:**
- Modify: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/Settings.kt`
- Modify: `core/datastore/src/main/kotlin/com/rodrigomachado/riff/core/datastore/RiffPreferences.kt`
- Test: `core/model/.../SettingsTest.kt`, `core/datastore/.../RiffPreferencesTest.kt`

**Interfaces:**
- Produces: `Settings.queuePrefetch: Boolean = false`, `queueDeleteUnplayed: Boolean = true`, `queueUseMobileData: Boolean = false`, `queueReplaceSuggested: Boolean = true`; chaves `queue_prefetch`, `queue_delete_unplayed`, `queue_use_mobile_data`, `queue_replace_suggested`.

- [ ] **Step 1: Testes que falham** — `SettingsTest.queueOptionsStartWithPrefetchOffAndDeletingOn` (padrões acima); `RiffPreferencesTest.queueOptionsAreSaved` (grava os quatro com o valor oposto ao padrão e relê).
- [ ] **Step 2:** `./scripts/gw :core:model:test :core:datastore:testDebugUnitTest --console=plain` → FAIL (referência não resolvida).
- [ ] **Step 3:** campos em `Settings` e leitura/gravação em `RiffPreferences` no padrão das chaves existentes.
- [ ] **Step 4:** mesmo comando → PASS.
- [ ] **Step 5:** commit `feat: preferências da fila do Spotify`.

### Task 2: Tabela `queue_downloads`

**Files:**
- Modify: `core/database/.../Entities.kt`, `RiffDatabase.kt` (version 7, `AutoMigration(from = 6, to = 7)`), `DatabaseModule.kt`
- Create: `core/database/.../QueueDownloadDao.kt`; `core/database/schemas/.../7.json` (gerado)
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/queue/QueueDownloads.kt`
- Test: `core/database/.../MigrationTest` (se existir o padrão) e `core/data/.../queue/RoomQueueDownloadsTest.kt`

**Interfaces:**
- Produces: `QueueDownloadEntity(chartId: String PK, trackUri: String, downloadedAt: Long, played: Boolean)`; `interface QueueDownloads { suspend fun add(chartId: String, trackUri: String, at: Long); suspend fun markPlayed(chartId: String); suspend fun unplayed(): List<QueueDownload>; suspend fun remove(chartIds: Collection<String>); suspend fun forTrack(trackUri: String): QueueDownload? }`; `data class QueueDownload(val chartId: String, val trackUri: String, val downloadedAt: Long, val played: Boolean)`; `RoomQueueDownloads` ligado por `@Binds` num módulo próprio do arquivo (o `DataBindsModule` está no limite do detekt).
- Linha some junto com o mapa: `ForeignKey` para `charts` com `onDelete = CASCADE` (como `ai_separations`, se for o padrão; senão o `QueueCleaner` remove explicitamente).

- [ ] **Step 1: Testes que falham** — `addedMapsAreUnplayedUntilMarked`, `removedMapsDisappear`, `aMapIsFoundByItsTrack`; teste de migração 6→7 seguindo o existente.
- [ ] **Step 2:** `./scripts/gw :core:database:testDebugUnitTest :core:data:testDebugUnitTest --console=plain` → FAIL.
- [ ] **Step 3:** entidade, DAO, migração, repositório.
- [ ] **Step 4:** PASS; `git status` mostra `7.json` novo.
- [ ] **Step 5:** commit `feat: tabela dos mapas baixados pela fila`.

### Task 3: Escolha do melhor mapa (`QueueChartPicker`)

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/queue/QueueChartPicker.kt`
- Test: `core/data/.../queue/QueueChartPickerTest.kt`

**Interfaces:**
- Consumes: `EncoreClient.search(query, page = 1)`, `EncoreClient.hasSeparateGuitar(chart)`, `TrackMatcher.score`, `VersionTitles.isVersion`.
- Produces: `class QueueChartPicker @Inject constructor(encore: EncoreClient) { suspend fun pick(track: TrackQuery): EncoreChart? }`. Consulta: `"${track.artist} ${track.title}"`. Um `EncoreClient` falso nos testes (o transporte `HttpTransport` já é injetável).

- [ ] **Step 1: Testes que falham**
  - `onlyChartsAboveTheAutomaticThresholdAreKept` — único resultado com nota 0,9 → `null`.
  - `originalsComeBeforeVersions` — "Song (Live)" nota 0,97 e "Song" nota 0,93 → escolhe "Song".
  - `aVersionIsAcceptedOnlyWithoutAnOriginal`.
  - `chartsWithoutGuitarAreIgnored`.
  - `tieBreakPrefersSeparateGuitarThenMoreDifficulties` — mesmas notas; o com guitarra separada vence; sem diferença nisso, o com mais dificuldades.
  - `atMostThreeChartsArePeeked` — 5 candidatos empatados → `hasSeparateGuitar` chamado 3 vezes.
  - `aCoverWithTheSameTitleByAnotherArtistIsNotTaken` (Review Focus 1).
- [ ] **Step 2:** `./scripts/gw :core:data:testDebugUnitTest --tests '*QueueChartPicker*' --console=plain` → FAIL.
- [ ] **Step 3:** implementar; a ordenação é (original desc, nota desc), olhando guitarra separada só nos 3 primeiros empatados na nota.
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** commit `feat: escolha do melhor mapa do Encore para a fila`.

### Task 4: Autorização da API Web do Spotify

**Files:**
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/web/SpotifyWebAuth.kt`, `TokenStore.kt`, `Pkce.kt`
- Modify: `core/spotify/.../SpotifyModule.kt`, `core/spotify/build.gradle.kts` (se precisar de `androidx.security`/Keystore util)
- Test: `core/spotify/src/test/.../web/SpotifyWebAuthTest.kt`, `PkceTest.kt`, `TokenStoreTest.kt`

**Interfaces:**
- Produces:
  - `interface SpotifyWebAuth { val state: StateFlow<WebAuthState>; suspend fun authorize(): Boolean; suspend fun accessToken(): String?; suspend fun onUnauthorized(): String? }` — `authorize()` abre a tela (só chamado pelas Configurações); `accessToken()` renova com o refresh token quando faltar menos de 60 s; `onUnauthorized()` renova uma vez e devolve o token novo ou `null`.
  - `enum class WebAuthState { NOT_AUTHORIZED, AUTHORIZED, LOST }` — `LOST` quando a renovação é recusada (400 `invalid_grant` ou 401): apaga o refresh token.
  - `object Pkce { fun verifier(random: SecureRandom = SecureRandom()): String; fun challenge(verifier: String): String }` (RFC 7636, S256, base64url sem `=`).
  - `interface TokenStore { fun read(): String?; fun write(refreshToken: String?) }`; implementação com AES/GCM e chave do Android Keystore, gravada nas `SharedPreferences` `riff_spotify_web`.
- Antes do Step 3: conferir no AAR `com.spotify.android:auth:5.0.0` (`javap` nas classes `AuthorizationRequest$Builder`) se há suporte a PKCE (code challenge). Com suporte, `AuthorizationResponse.Type.CODE` + o challenge; sem suporte, Custom Tabs para `https://accounts.spotify.com/authorize?response_type=code&code_challenge_method=S256&...` com o redirect `riff://callback`. Registrar no ledger qual caminho.

- [ ] **Step 1: Testes que falham**
  - `PkceTest.challengeMatchesTheRfcExample` (verifier `dBjftJeZ4CVP-mJ92K1N4bPpN8OJMRF2dX0cgUpQ` / exemplo do RFC 7636 → `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`).
  - `SpotifyWebAuthTest.anExpiredTokenIsRenewedWithTheRefreshToken` (transporte falso devolve `access_token`, `expires_in`, `refresh_token` novo; o novo é gravado).
  - `aRefusedRenewalLosesTheAuthorizationAndOpensNothing` — 400 `invalid_grant` → `state == LOST`, `TokenStore.read() == null`, nenhuma Activity aberta (Review Focus 4).
  - `theRefreshTokenNeverGoesToTheLog` — com `ShadowLog`, nenhuma linha contém o token.
  - `TokenStoreTest.theTokenIsNotStoredAsPlainText` — o arquivo de preferências não contém o token em claro; `read()` devolve o mesmo valor.
- [ ] **Step 2:** `./scripts/gw :core:spotify:testDebugUnitTest --console=plain` → FAIL.
- [ ] **Step 3:** implementar; transporte HTTP injetável (`fun interface FormPost { suspend fun post(url: String, form: Map<String, String>): Pair<Int, String> }`); a Activity da autorização vem de `ForegroundActivity.current`, como em `AppRemoteSpotify.authorize`.
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** commit `feat: autorização da API Web do Spotify com PKCE`.

### Task 5: Leitura da fila (`SpotifyQueueApi`)

**Files:**
- Create: `core/spotify/src/main/kotlin/com/rodrigomachado/riff/core/spotify/web/SpotifyQueueApi.kt`
- Test: `core/spotify/src/test/.../web/SpotifyQueueApiTest.kt`

**Interfaces:**
- Consumes: `SpotifyWebAuth.accessToken()`, `onUnauthorized()`.
- Produces: `class SpotifyQueueApi { suspend fun next(count: Int = 2): QueueRead }`; `sealed interface QueueRead { data class Tracks(val tracks: List<QueuedTrack>) : QueueRead; data class RetryAfter(val seconds: Long) : QueueRead; data object Unavailable : QueueRead }`; `data class QueuedTrack(val uri: String, val title: String, val artist: String, val durationMs: Long)` (artistas juntos por ", ", como o App Remote mostra).

- [ ] **Step 1: Testes que falham** (JSON de exemplo da documentação da API, com um episódio no meio)
  - `theNextTwoMusicTracksAreRead` — episódios e faixas locais (`is_local`) pulados.
  - `a401RenewsOnceAndRetries`; `a401AfterRenewingIsUnavailable`.
  - `a429ReturnsRetryAfter` (cabeçalho `Retry-After: 30` → 30).
  - `noTokenIsUnavailableWithoutCallingTheApi`.
- [ ] **Step 2:** `./scripts/gw :core:spotify:testDebugUnitTest --tests '*SpotifyQueueApi*' --console=plain` → FAIL.
- [ ] **Step 3:** implementar (GET com `Authorization: Bearer`).
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** commit `feat: leitura das próximas músicas da fila do Spotify`.

### Task 6: Coordenador da fila (`QueuePrefetcher`) e partida em andamento

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/queue/QueuePrefetcher.kt`, `MatchActivity.kt`, `DownloadConditions.kt`
- Modify: `feature/gameplay/.../GameplayViewModel.kt` (avisa início e fim da partida)
- Test: `core/data/.../queue/QueuePrefetcherTest.kt`, `feature/gameplay/.../GameplayViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 1–5; `SpotifyRemote.playback`/`connection`; `TrackMatchRepository.matchFor(PlaybackState)`; `ChartDownloader.download(chart, matchTrackUri, onProgress)`; `SettingsRepository.settings`.
- Produces:
  - `interface MatchActivity { val inMatch: StateFlow<Boolean>; fun started(chartId: String); fun ended() }` (singleton `DefaultMatchActivity`); `started` também chama `QueueDownloads.markPlayed(chartId)`.
  - `interface DownloadConditions { fun canDownload(useMobileData: Boolean): Boolean }` (rede sem cobrança ou dados móveis permitidos; ≥ 200 MB livres na pasta do app; pasta da biblioteca com escrita).
  - `class QueuePrefetcher @Inject constructor(...) { fun start(scope: CoroutineScope) }` — singleton; idempotente.
- `GameplayViewModel`: `matchActivity.started(chartId)` quando a partida fica pronta; `ended()` em `release()`.

- [ ] **Step 1: Testes que falham** (relógio virtual, fakes de todas as dependências)
  - `withTheOptionOffNothingIsRead`.
  - `onSongChangeTheNextTwoGetTheirMaps` — fila `[a, b, c]` sem mapas → baixa a e b, nessa ordem, um por vez.
  - `songsWithAMapAreSkipped`; `aSuggestedMapFollowsTheOption` (replace true baixa; false pula).
  - `aTriedSongIsNotSearchedAgain`; `aFailedDownloadCountsAsTried` (o `ChartDownloader` falha → não tenta de novo nesta abertura).
  - `theQueueIsReadAgainAfter60Seconds`.
  - `nothingStartsDuringAMatchAndPendingOnesWaitForTheEnd` (Review Focus 3).
  - `withoutMobileDataNothingIsSearchedOrDownloadedOnMeteredNetwork` (Review Focus 5).
  - `atMostThreeDeletableQueueMapsExist`; `theCapDoesNotApplyWithDeletingOff`.
  - `aRowIsWrittenOnlyWhenDeletingIsOn`.
  - `retryAfterIsRespected`; `aLostAuthorizationTurnsTheOptionOff` (grava `queuePrefetch = false`).
  - `GameplayViewModelTest.aStartedMatchMarksItsMapAsPlayed`.
- [ ] **Step 2:** `./scripts/gw :core:data:testDebugUnitTest --tests '*QueuePrefetcher*' :feature:gameplay:testDebugUnitTest --console=plain` → FAIL.
- [ ] **Step 3:** implementar; leitura disparada por `playback.distinctUntilChangedBy { trackUri }` combinada com um tique de 60 s; downloads numa fila serial (`Channel`/`Mutex`).
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** commit `feat: coordenador que baixa os mapas das próximas músicas da fila`.

### Task 7: Apagar os não jogados (`QueueCleaner`)

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/queue/QueueCleaner.kt`
- Modify: `QueuePrefetcher.kt` (chama o cleaner a cada troca de música e a cada leitura da fila)
- Test: `core/data/.../queue/QueueCleanerTest.kt`

**Interfaces:**
- Consumes: `QueueDownloads`, `ChartDeleter.delete(chartIds)`, `Settings.queueDeleteUnplayed`.
- Produces: `class QueueCleaner { suspend fun afterSongChange(previousTrackUri: String?, currentTrackUri: String?, window: List<String>); suspend fun atStartup(window: List<String>, currentTrackUri: String?) }`.

- [ ] **Step 1: Testes que falham** (Review Focus 2)
  - `aSongThatPassedWithoutPlayingLosesItsMap`.
  - `aPlayedMapIsNeverDeleted`.
  - `aMapThatLeftTheWindowWithoutPlayingIsDeleted` (nem atual nem na janela).
  - `theCurrentSongsMapIsKept`.
  - `withDeletingOffNothingIsDeleted`.
  - `manualDownloadsAreNeverDeleted` (sem linha em `queue_downloads`).
  - `leftoversAreDeletedAtStartup`; `turningPrefetchOffDeletesLeftoversAtTheNextStart`.
- [ ] **Step 2:** `./scripts/gw :core:data:testDebugUnitTest --tests '*QueueCleaner*' --console=plain` → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** commit `feat: apagar os mapas da fila que passaram sem ser jogados`.

### Task 8: Aba Spotify nas Configurações

**Files:**
- Modify: `feature/settings/.../SettingsTabs.kt` (aba `SPOTIFY` depois de `AI`), `SettingsScreen.kt`, `SettingsViewModel.kt`, strings pt/en
- Create: `feature/settings/.../SpotifyTab.kt`
- Test: `SettingsContentTest.kt`, `SettingsViewModelTest.kt`, `SettingsScreenshotTest.kt` (`Settings_spotify.png`)

**Interfaces:**
- Consumes: `SpotifyWebAuth.authorize()`, `state`; Task 1.
- Produces: `SettingsViewModel.setQueuePrefetch(on: Boolean)` (ligar sem autorização chama `authorize()`; recusa → continua desligada e `queueMessage = REFUSED`), `setQueueDeleteUnplayed`, `setQueueUseMobileData`, `setQueueReplaceSuggested`; `queueMessage: StateFlow<QueueMessage?>` com `REFUSED` e `LOST` (este quando `state == LOST`).

- [ ] **Step 1: Testes que falham**
  - Content: `theSpotifyTabShowsTheFourOptionsWithTheirHints`; `theOtherOptionsAreDisabledWithPrefetchOff`; `theSuggestedChoiceOffersBothOptions`; `aRefusedOrLostAuthorizationIsExplained`; `withoutAWritableFolderTheOptionSaysSo` (o mesmo aviso de pasta sem escrita da pesquisa, sob a opção).
  - VM: `turningPrefetchOnAsksForAuthorization`; `aRefusedAuthorizationKeepsItOff`; `alreadyAuthorizedTurnsOnWithoutAsking`.
- [ ] **Step 2:** `./scripts/gw :feature:settings:testDebugUnitTest --console=plain` → FAIL.
- [ ] **Step 3:** implementar; a escolha "Música com mapa sugerido" usa a mesma lista com rádio das outras escolhas das Configurações.
- [ ] **Step 4:** PASS; `recordRoborazziDebug` gera `Settings_spotify.png`; conferir a imagem.
- [ ] **Step 5:** commit `feat: aba Spotify com as opções da fila`.

### Task 9: Ligar no app, README e aparelho

**Files:**
- Modify: `app/.../RiffApplication.kt` (`queuePrefetcher.start(applicationScope)`), `README.md`, `docs/pendencias.md`

- [ ] **Step 1:** iniciar o coordenador uma vez no `RiffApplication`; ele mesmo espera a conexão e a opção.
- [ ] **Step 2:** README: seção "Mapas da fila do Spotify" (o que faz, as quatro opções, Wi-Fi, a autorização extra, que só funciona com o Riff aberto) e a nota de privacidade (o app lê a fila do Spotify).
- [ ] **Step 3:** suíte completa + `scripts/conferir_r8_spotify.sh` (o R8 não pode quebrar as classes de autorização do Spotify).
- [ ] **Step 4:** no celular: ligar a opção e autorizar; fila com duas músicas sem mapa; ver os mapas chegarem antes da troca (logcat tag `RiffQueue`); passar uma sem jogar e ver o mapa sumir; jogar outra e ver que fica.
- [ ] **Step 5:** commit `docs: README dos mapas da fila do Spotify`.
