# Riff 0.5.0 sem o Spotify: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** tirar o modo Spotify inteiro do app e abrir numa tela inicial nova (Sozinho · Sala desligada · Ranking), versão 0.5.0.

**Architecture:** um módulo novo `:feature:home` vira a rota inicial do `NavHost`; depois o Spotify sai de fora para dentro: as rotas e os fluxos (partida, resultado, Biblioteca, pesquisa), depois os módulos (`:feature:nowplaying`, `:core:spotify`) e o que só eles usavam em `:core:data`, `:core:audio`, `:core:model`, `:core:chart` e Configurações; por fim os dados (banco v13 e preferências), os textos e o Baseline Profile.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose (rotas tipadas), Hilt, Room (migração automática com `@DeleteTable`), DataStore, Robolectric + Roborazzi, Macrobenchmark.

**Spec:** `docs/superpowers/specs/2026-10-30-sem-spotify-design.md`

## Global Constraints

- Mesmo pacote `com.rodrigomachado.riff`; `versionCode 5`, `versionName "0.5.0"`.
- Tudo fica menos o Spotify: Biblioteca, pesquisa no Encore, guitarra por IA (com a fila de separações), ranking, visual da pista, calibração, Reportar, Licenças.
- Nada do Spotify no app instalado: telas, `NotificationListenerService`, permissão `BIND_NOTIFICATION_LISTENER_SERVICE`, textos, licenças, privacidade, categoria do Reportar.
- Dados que ficam: `charts`, `chart_difficulties`, `scores`, `chart_offsets`, `encore_charts`, `ai_separations`, `separation_queue`, `ranking_pending`, preferências que não são do Spotify, nome e chave do ranking. Saem: tabelas `track_matches` e `queue_downloads`; chaves `queue_*` e `spotify_next_song_direct`.
- Tudo desenhado em código (sem imagens prontas); textos em pt e en; TDD; suíte completa verde antes do merge.

## Review Focus

1. **Atualizar por cima da 0.4.0 com dados reais:** recordes, separações, ajustes por mapa e o nome do ranking continuam; o app abre na tela inicial. → Task 4 (`MigrationTest` 12 → 13 com dados nas tabelas que ficam) e aceite no celular.
2. **Preferências gravadas pela 0.4.0** com as chaves do Spotify: a leitura não quebra e a próxima gravação as apaga. → Task 4.
3. **Voltar:** da Biblioteca e do Ranking volta à tela inicial; na tela inicial, o Voltar do sistema fecha o app (não abre nada). → Task 1.
4. **Notificação da separação** com o app fechado: abre a Biblioteca, e o Voltar dela leva à tela inicial. → Task 1.
5. **Relato salvo pela 0.4.0 com a categoria "SPOTIFY"** (estado salvo do Reportar): restaurado sem a categoria, sem quebrar. → Task 5.

---

### Task 1: Tela inicial

**Files:**
- Create: `feature/home/build.gradle.kts` (plugin `riff.android.feature` + roborazzi), `feature/home/src/main/kotlin/com/rodrigomachado/riff/feature/home/HomeRoute.kt`, `HomeScreen.kt`, `HomeIcons.kt` (os desenhos: nota, bandeira, envelope, engrenagem), `feature/home/src/main/res/values/strings.xml` e `values-en/strings.xml`
- Modify: `settings.gradle.kts` (`include(":feature:home")`), `app/build.gradle.kts`, `app/src/main/kotlin/com/rodrigomachado/riff/RiffNavHost.kt`
- Test: `feature/home/src/test/kotlin/com/rodrigomachado/riff/feature/home/HomeContentTest.kt`, `HomeScreenshotTest.kt`

**Interfaces:**
- Produces: `@Serializable data object HomeRoute`; `@Composable fun HomeScreen(onSolo: () -> Unit, onRanking: () -> Unit, onSettings: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier)`; `object HomeTestTags { SOLO = "home_solo"; ROOM = "home_room"; RANKING = "home_ranking" }` (as etiquetas viram resource-id para o Macrobenchmark da Task 6).

Layout (maquete opção 1): ícones de Reportar (esquerda) e Configurações (direita) no topo, com `SeparationIndicator()` no meio da mesma linha (só aparece com separação ou fila); logo "Riff" (o letreiro do `PosterTitle`); dois quadros altos lado a lado — Sozinho (`TourRed`, ícone de nota) e Sala (escuro, ícone de bandeira, "em breve", desligado); no pé, o cartaz "★ Ranking" (`PosterButton`). Sons: Sozinho e Ranking `NAVIGATE`; ícones `NAVIGATE`.

Textos pt / en: "Sozinho" / "Solo"; "Sala" / "Room"; "em breve" / "coming soon"; "Ranking" / "Ranking"; descrições "Reportar problema" / "Report a problem", "Configurações" / "Settings"; Sala lida como "Sala, em breve" / "Room, coming soon".

- [ ] **Step 1: Testes de tela que falham** — `HomeContentTest`:
  - `soloOpensTheLibraryAndRankingOpensTheRanking`: clicar na etiqueta SOLO e na RANKING registra `["solo", "ranking"]`.
  - `theCornerIconsOpenReportAndSettings`: `onNodeWithContentDescription("Reportar problema")` e `("Configurações")` registram `["report", "settings"]`.
  - `theRoomIsComingSoonAndDoesNothing`: o nó ROOM tem descrição "Sala, em breve", `assertIsNotEnabled()`, e clicar não registra nada.
- [ ] **Step 2: Rodar** `./scripts/gw :feature:home:testDebugUnitTest` → falha (módulo/compilação).
- [ ] **Step 3: Implementar** o módulo e `HomeScreen`; `HomeScreenshotTest` grava `Home.png` (360 dp, pt-BR) e `Home_queue.png` (com `LocalSeparation` de mentira mostrando uma separação: a palheta no meio do topo).
- [ ] **Step 4: NavHost** — `startDestination = HomeRoute`; `composable<HomeRoute>` liga Sozinho → `LibraryRoute()`, Ranking → `RankingRoute`, ícones → `SettingsRoute` / `ReportRoute`. O Tocando agora sai da navegação (o código dele sai na Task 3). A Biblioteca aberta pela notificação continua por `navigate(LibraryRoute())`, por cima da tela inicial. O `PlayerNameGate` continua por cima de tudo.
- [ ] **Step 5: Rodar** `./scripts/gw :feature:home:testDebugUnitTest :feature:home:recordRoborazziDebug :app:assembleDebug` e olhar `Home.png` e `Home_queue.png` (a palheta centralizada, nada cortado).
- [ ] **Step 6: Commit** — "Tela inicial: Sozinho, Sala (em breve) e Ranking; o app abre nela".

### Task 2: Partida, resultado, Biblioteca e pesquisa só no modo Biblioteca

**Files (modificar):**
- `feature/gameplay/.../GameplayRoute.kt` (sai `spotifyTrackUri`, `trackOffsetMs`, `confirmFirst`), `GameplayViewModel.kt` (sai o modo Spotify: `spotifyPlayers`, `SpotifySongOffset`, `NextSong`, confirmação de sugestão, `onPlaybackChanged` do Spotify, `onChangeSong`), `GameplayScreen.kt` (saem `PlaybackControls`, `ConfirmOverlay`, os avisos `PlayerNotice` do Spotify), `SpotifySongOffset.kt` (apagar), `PauseNotice.kt`
- `feature/results/.../ResultsRoute.kt` (saem os parâmetros do Spotify)
- `feature/library/.../LibraryRoute.kt` (data object sem `matchTrackUri`/`matchTrackTitle`), `LibraryViewModel.kt` (sai `chooseChartFor`, `matchSaved`, `TrackMatchRepository`), `LibraryScreen.kt` (saem `match`, `onMatched`, o modo "escolher mapa para a música")
- `feature/search/.../SearchRoute.kt` (só `query`), `SearchViewModel.kt` / `SearchScreen.kt` (sai o casamento ao baixar e a ordem pela música do Spotify)
- `app/.../RiffNavHost.kt` (rotas sem os parâmetros; sai `onNextSong`, `onChooseOther`, `onMatched`)
- Testes: os do modo Spotify desses módulos saem (`GameplaySpotifyFlowTest`, `NextSongEffectTest`, `GameConfirmPanelTest`, os de `SpotifySongOffset`/`InitialSongOffsetTest`, os de casar mapa na Biblioteca e na pesquisa); os do modo Biblioteca ficam e passam.

**Interfaces:**
- Produces: `GameplayRoute(chartId: String, difficulty: String)`; `ResultsRoute(scoreId: Long)`; `@Serializable data object LibraryRoute`; `SearchRoute(query: String = "")`; `GameplayUiState.Ready` sem `spotifyTrackUri`/`awaitingConfirmation`; `GameplayUiState` sem `NextSong`.

- [ ] **Step 1:** Apagar os testes do modo Spotify listados acima e ajustar os que só passavam os parâmetros do Spotify; rodar `:feature:gameplay:testDebugUnitTest :feature:results:testDebugUnitTest :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest` → falham na compilação quando apontam para APIs que vão sair (é o vermelho desta tarefa: a remoção é guiada pela compilação).
- [ ] **Step 2:** Remover o código do Spotify dos arquivos acima até compilar; o modo Biblioteca não muda (pausa, retrocesso de 0,5 s, áudio ocupado, sincronia por mapa em `chart_offsets`, separação).
- [ ] **Step 3: Rodar** os quatro módulos + `:app:compileDebugKotlin` + `verifyRoborazziDebug` desses módulos → verde (screenshots do modo Spotify saem com os testes deles).
- [ ] **Step 4: Commit** — "Partida, resultado, Biblioteca e pesquisa só no modo Biblioteca".

### Task 3: Fora os módulos e o código do Spotify

**Files:**
- Delete: `feature/nowplaying/` inteiro, `core/spotify/` inteiro, `core/data/.../queue/` inteiro, `core/data/.../NowPlayingRepository.kt`, `core/data/.../TrackMatchRepository.kt`, os players e fontes do Spotify em `core/audio` (`SpotifySongPlayer` e afins em `Players.kt`/`SongSources.kt`), os modelos `core/model/.../NowPlaying.kt`, `PlaybackState.kt`, `SpotifyConnection.kt`, `TrackMatch.kt`, `core/chart/.../match/` se nada mais usar (`git grep TrackMatcher` vazio fora de testes)
- Modify: `settings.gradle.kts`, `app/build.gradle.kts`, `core/data/build.gradle.kts`, `feature/settings/build.gradle.kts` (sem `:core:spotify`/`:feature:nowplaying`); `app/src/main/AndroidManifest.xml` (sem o serviço e a permissão do listener); `RiffApplication.kt` (sem `queuePrefetcher`); `MainActivity.kt` (sem o que só servia ao Spotify); `core/data/.../DataModule.kt` (bindings); `feature/settings/` (sai `SpotifyTab.kt`, a aba `SettingsTab.SPOTIFY`, `QueueUiState`/`QueueAction`, `canWriteToLibrary` se só a fila usava); `core/model/.../Settings.kt` (saem `queuePrefetch`, `queueDeleteUnplayed`, `queueUseMobileData`, `queueReplaceSuggested`, `queueClosestDuration`, `spotifyNextSongDirect`); `core/datastore/.../RiffPreferences.kt` (sem esses campos — a limpeza das chaves é a Task 4)
- Testes: os desses arquivos saem junto; os das Configurações perdem os casos da aba Spotify.

**Interfaces:**
- Consumes: as rotas da Task 2.
- Produces: `SettingsTab` sem `SPOTIFY`; `Settings` sem os seis campos acima.

- [ ] **Step 1: Teste que falha** — em `app`, um teste de manifesto (Robolectric, `PackageManager`) `noNotificationListenerIsDeclared`: nenhum serviço do app com a permissão `BIND_NOTIFICATION_LISTENER_SERVICE`. Rodar `:app:testDebugUnitTest` → falha.
- [ ] **Step 2:** Apagar os módulos e arquivos, ajustar o Gradle e o manifesto, remover os campos de `Settings` e a aba; corrigir o que deixar de compilar.
- [ ] **Step 3: Rodar** `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug --continue` → verde; `git grep -i spotify -- '*/src/main/*'` só pode sobrar nos textos que a Task 5 troca e nas limpezas da Task 4.
- [ ] **Step 4: Commit** — "Fora o Tocando agora, a sessão do Spotify e a fila de mapas".

### Task 4: Dados da atualização (banco 13 e preferências)

**Files:**
- Modify: `core/database/.../RiffDatabase.kt` (versão 13; `AutoMigration(from = 12, to = 13, spec = DropSpotifyTables::class)`), `Entities.kt` (saem `TrackMatchEntity`, `QueueDownloadEntity`), DAOs `TrackMatchDao.kt`/`QueueDownloadDao.kt` (apagar), `DatabaseModule.kt`; `core/datastore/.../RiffPreferences.kt`
- Create: `core/database/schemas/.../13.json` (gerado)
- Test: `core/database/.../MigrationTest.kt`, `core/datastore/.../RiffPreferencesTest.kt`

**Interfaces:**
- Produces: `@DeleteTable(tableName = "track_matches") @DeleteTable(tableName = "queue_downloads") class DropSpotifyTables : AutoMigrationSpec`.

- [ ] **Step 1: Testes que falham**
  - `MigrationTest.version12BecomesVersion13WithoutTheSpotifyTables`: cria a v12 com uma linha em `charts`, `scores`, `chart_offsets`, `ai_separations`, `separation_queue`, `ranking_pending`, `track_matches` e `queue_downloads`; migra para 13; as seis primeiras continuam com a linha; `track_matches` e `queue_downloads` não existem (`SELECT name FROM sqlite_master WHERE name IN (...)` vazio).
  - `RiffPreferencesTest.theSpotifyKeysAreForgotten`: um store com `queue_prefetch = true` e `spotify_next_song_direct = false` mais `left_handed = true`; ler as configurações não quebra e mantém `leftHanded = true`; depois de `updateSettings { it }`, as duas chaves do Spotify não existem mais no store.
- [ ] **Step 2: Rodar** `:core:database:testDebugUnitTest :core:datastore:testDebugUnitTest` → falham.
- [ ] **Step 3: Implementar** a migração e, em `updateSettings`, `prefs.remove(...)` das chaves `queue_prefetch`, `queue_delete_unplayed`, `queue_use_mobile_data`, `queue_replace_suggested`, `queue_closest_duration`, `spotify_next_song_direct` (como já se faz com as chaves antigas).
- [ ] **Step 4: Rodar** de novo → verde (o `MigrationTest` pode falhar na primeira compilação do esquema novo, instável conhecido: rodar outra vez).
- [ ] **Step 5: Commit** — "Banco 13 sem as tabelas do Spotify; preferências do Spotify esquecidas".

### Task 5: Textos sem o Spotify

**Files:**
- Modify: `feature/report/.../BugReport.kt` (sai `ReportCategory.SPOTIFY` e a string), `feature/report/.../ReportViewModel.kt` só se a restauração não tolerar nome desconhecido; `feature/licenses/.../AndroidLicenseSource.kt` e `assets/licencas/` (sai o Spotify); texto de privacidade (`licenses_privacy_text` pt/en) sem a sessão do Spotify e o "Acesso às notificações"; `README.md` (sai a seção do Spotify, a instalação fala só da Biblioteca, Configurações sem a aba Spotify, versão 0.5.0 e tela inicial; o histórico das versões cita que a 0.4.0 tinha o Spotify)
- Test: `ReportViewModelTest`, `LicensesTest` / `AndroidLicenseSourceTest`

- [ ] **Step 1: Testes que falham**
  - `ReportViewModelTest.aSavedSpotifyCategoryIsDropped`: `SavedStateHandle(mapOf("kind" to "PROBLEM", "category" to "SPOTIFY"))` → `category == null`, sem exceção.
  - `ReportViewModelTest.noCategoryMentionsSpotify`: nenhum `ReportCategory.entries` tem "Spotify" no `label`.
  - `AndroidLicenseSourceTest.nothingFromSpotify`: nenhuma entrada de conteúdo ou biblioteca com "Spotify" no nome.
- [ ] **Step 2: Rodar** → falham.
- [ ] **Step 3: Implementar** e reescrever os textos (pt e en). `git grep -i spotify -- '*/src/main/*'` vazio, exceto a limpeza antiga de abertura se ficar (decisão do construtor, spec §10).
- [ ] **Step 4: Rodar** `:feature:report:testDebugUnitTest :feature:licenses:testDebugUnitTest verifyRoborazziDebug` → verde (regravar só os screenshots com texto mudado, olhando cada um).
- [ ] **Step 5: Commit** — "Textos sem o Spotify: Reportar, Licenças, privacidade e README".

### Task 6: Versão 0.5.0, Baseline Profile e desempenho

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 5`, `versionName = "0.5.0"`); `baselineprofile/.../Journeys.kt` (`OPEN_LIBRARY = "home_solo"`, sem passar pelo Tocando agora); `app/src/release/generated/baselineProfiles/` (refeitos); `README.md` (seção Desempenho com as medidas novas e a data)

- [ ] **Step 1:** Ajustar `Journeys.kt`; com o celular no adb e a Biblioteca carregada (a música do argumento `riff.song`), rodar `scripts/desempenho.sh tudo`.
- [ ] **Step 2:** Conferir os perfis novos (sem classes de `nowplaying`/`spotify`: `grep -c -i "nowplaying\|spotify"` nos dois arquivos dá 0) e as medidas (abertura com e sem perfil, fps, quadros atrasados).
- [ ] **Step 3:** Atualizar o README (Desempenho, com a data) e a versão.
- [ ] **Step 4: Rodar a suíte completa** `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --continue` → `BUILD SUCCESSFUL` (instáveis conhecidos: rodar de novo sozinhos).
- [ ] **Step 5: Commit** — "Versão 0.5.0; Baseline Profile e desempenho a partir da tela inicial".

### Depois das tarefas

- Revisão independente da branch inteira; críticos e importantes corrigidos com teste; menores em `docs/pendencias.md` (e saem as pendências que eram só do Spotify).
- Instalar o release por cima da 0.4.0 no celular (`adb install -r`) e o aceite do usuário (spec §9).
- Menu de merge.
