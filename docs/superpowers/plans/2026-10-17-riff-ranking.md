# Ranking online Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nome de jogador único por celular, envio automático das melhores partidas para um ranking online (Apps Script + planilha) e telas de Ranking por mapa e dificuldade.

**Architecture:** O motor passa a medir "Tempo sem errar". Um Apps Script novo guarda jogadores e o melhor de cada número por jogador+mapa+dificuldade. Em `:core:data/ranking`, um cliente HTTP, o perfil do jogador (DataStore) e uma fila local (Room) formam o `RankingRepository`, chamado ao salvar o placar. Um módulo novo `:feature:ranking` tem o pedido do nome e as telas de lista e placar.

**Tech Stack:** Kotlin, Compose, Hilt, Room (v9), DataStore, HttpURLConnection, Google Apps Script (testado com Node + mocks), Robolectric/Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-17-ranking-design.md`

## Global Constraints

- Branch `feat/ranking`, base `main` `ac0d08a`. Suíte: `./scripts/gw --stop; ./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --console=plain` + `bash scripts/conferir_r8_spotify.sh`.
- Números do ranking (chave do protocolo → rótulo): `pontos` Pontos · `precisao` Precisão · `combo` Maior combo · `perfeitos` Perfeitos · `semErrar` Tempo sem errar. Todos "maior é melhor"; o servidor guarda o máximo de cada um.
- Nome: 3–20 caracteres de `[\p{L}\p{N} _-]`, sem espaços nas pontas, único sem diferenciar maiúsculas.
- `ranking.url` em `local.properties` (fora do git) → `BuildConfig.RANKING_URL` em `:core:data`. Sem URL: o ranking fica desligado (nada de pedido do nome, Resultado sem cartão, botão Ranking mostra "Ranking não configurado").
- Chave do celular: UUID aleatório, guardado no DataStore (entra no backup); o script guarda só o SHA-256 dela.
- Limite no script: 60 envios por hora por chave; corpo até 4 000 caracteres.
- Placar: 100 primeiros + a linha do jogador se estiver fora deles.
- Textos PT/EN: "Ranking"/"Ranking"; pedido: título "Seu nome no ranking"/"Your ranking name", campo "Nome"/"Name", "Salvar"/"Save", "Agora não"/"Not now", "Esse nome já é de outra pessoa"/"That name belongs to someone else", "Use de 3 a 20 letras, números, espaço, _ ou -"/"Use 3 to 20 letters, numbers, space, _ or -", "Sem internet: tente de novo depois"/"No internet: try again later"; Resultado: "Ranking: %1$dº de %2$d"/"Ranking: #%1$d of %2$d", "Enviando para o ranking…"/"Sending to the ranking…", "Sem internet: vai para o ranking quando voltar"/"No internet: it goes to the ranking when you're back online", "Escolha um nome para entrar no ranking"/"Choose a name to join the ranking", "Ver ranking"/"See ranking", "Tempo sem errar"/"Time without missing"; Configurações (Geral): "Nome no ranking"/"Ranking name".

## Review Focus

1. Duas pessoas reservam o mesmo nome quase juntas (ou "Rodrigo" e "rodrigo"): só uma fica com ele → teste `twoRegistrationsOfTheSameNameOnlyOneWins` (Task 2).
2. Partida termina sem internet e o app é fechado: o envio sobrevive (fila no Room) e sai na próxima abertura → teste `aQueuedSubmissionSurvivesAndIsSentLater` (Task 3).
3. A chave de um celular tenta enviar/renomear em nome de outro (chave errada): recusado → teste `aWrongKeyCannotRenameOrSubmit` (Task 2).
4. Histórico antigo sem "Tempo sem errar" enviado no primeiro nome: o placar ordenado por `semErrar` não mostra essas linhas como 0 → teste `boardBySemErrarSkipsPlayersWithoutIt` (Task 2).
5. Resposta do script que não é JSON (página de login do Google, cota estourada): vira "sem conexão" e o envio fica na fila, sem travar → teste `aNonJsonAnswerKeepsTheSubmissionQueued` (Task 3).

---

### Task 1: "Tempo sem errar" no motor, no placar local e no Resultado

**Files:**
- Modify: `core/game/src/main/kotlin/com/rodrigomachado/riff/core/game/GameEngine.kt`
- Modify: `core/model/.../ScoreRecord.kt` (`cleanStreakMs: Long? = null`)
- Modify: `core/database/.../Entities.kt` (`ScoreEntity.cleanStreakMs: Long?`), `RiffDatabase.kt` (v9, `AutoMigration(8, 9)`), schema `9.json`, score DAO/mapper
- Modify: `feature/gameplay/.../GameplayViewModel.kt` (grava `cleanStreakMs = engine.longestCleanMs`)
- Modify: `feature/results/.../ResultsScreen.kt` + strings (linha "Tempo sem errar" m:ss)
- Test: `GameEngineTest`, `MigrationTest.version8BecomesVersion9`, `GameplayViewModelTest`, `ResultsContentTest`, screenshot `Results_new_best`

**Interfaces:**
- Produces: `GameEngine.longestCleanMs: Long` (maior trecho sem erro, do 1º ao último acerto do trecho, no tempo do mapa); `ScoreRecord.cleanStreakMs: Long?` (null = partida antiga).

- [ ] **Step 1: Testes que falham.**
  - `GameEngineTest.cleanTimeRunsFromTheFirstToTheLastHitOfTheLongestStreak`: notas em 1 000, 2 000, 3 000, 4 000, 5 000 (mesma trilha); acerta 1 000 e 2 000, deixa 3 000 passar (erro), acerta 4 000 e 5 000 → `longestCleanMs == 1_000`.
  - `GameEngineTest.aTapWithoutANoteBreaksTheCleanTime`: notas 1 000, 2 000, 3 000; acerta 1 000; toque em trilha vazia em 1 500; acerta 2 000 e 3 000 → `1_000`.
  - `GameEngineTest.aSingleHitIsZeroCleanTime`: uma nota acertada → `0`.
  - `MigrationTest.version8BecomesVersion9`: linha em `scores` na v8 → na v9 `cleanStreakMs` é NULL.
  - `GameplayViewModelTest.theScoreKeepsTheCleanTime`: `onSessionFinished` → `scores.saved.single().cleanStreakMs == session.engine.longestCleanMs`.
  - `ResultsContentTest.theCleanTimeIsShown`: `cleanStreakMs = 38_000` → texto "0:38"; `null` → linha ausente.
- [ ] **Step 2:** `./scripts/gw :core:game:test :core:database:testDebugUnitTest :feature:gameplay:testDebugUnitTest :feature:results:testDebugUnitTest` → falha de compilação/asserção.
- [ ] **Step 3: Implementar.** No motor: `streakStartMs` (tempo da nota do 1º acerto do trecho) zera junto com `combo = 0` (nos dois lugares); a cada acerto `longestCleanMs = max(longestCleanMs, note.timeMs - streakStartMs)`.
- [ ] **Step 4:** mesmo comando + `:feature:results:recordRoborazziDebug detekt` → PASS.
- [ ] **Step 5: Commit** `feat: Tempo sem errar (motor, placar e Resultado)`.

### Task 2: Apps Script do ranking

**Files:**
- Create: `scripts/ranking_apps_script.gs`, `scripts/ranking_appsscript.json`, `scripts/ranking_apps_script.test.js` (Node, sem dependências; mocks de `SpreadsheetApp`, `LockService`, `CacheService`, `ContentService`, `Utilities.computeDigest`)

**Interfaces (protocolo, POST JSON → JSON):**
- `{acao:"reservar", nome, chave}` → `{ok:true, nome}` | `{ok:false, motivo:"nome_em_uso"|"nome_invalido"}`. Reservar de novo com a mesma chave e outro nome = renomear (libera o antigo).
- `{acao:"enviar", chave, mapa, titulo, artista, dificuldade, pontos, precisao, combo, perfeitos, semErrar|null}` → `{ok:true, posicao, total}` (posição em pontos naquele mapa+dificuldade) | `{ok:false, motivo:"sem_nome"|"limite"}`.
- `{acao:"musicas", chave, busca}` → `{ok:true, musicas:[{mapa, titulo, artista, jogadores, suaPosicao|null}]}` (suaPosicao: a melhor posição em pontos entre as dificuldades).
- `{acao:"placar", chave, mapa, dificuldade, ordem, sentido:"desc"|"asc", busca}` → `{ok:true, dificuldades:[...], total, linhas:[{posicao, nome, valor, voce}]}`.
- Qualquer erro → `{ok:false, motivo:"erro interno"}` (como o script do Reportar).

- [ ] **Step 1: Testes (Node) que falham** em `ranking_apps_script.test.js`, rodando o `.gs` num contexto com mocks (planilha em memória):
  - `reservaENomeEmUso`: chave A reserva "Rodrigo" ok; chave B reserva "rodrigo" → `nome_em_uso`; nome "ab" → `nome_invalido`.
  - `twoRegistrationsOfTheSameNameOnlyOneWins`: duas reservas de "Ana" (chaves diferentes) dentro da trava → uma ok, outra `nome_em_uso`.
  - `renomearLiberaOAntigoEMantemPontuacoes`: A reserva "Rodrigo", envia; A reserva "Digo"; B reserva "Rodrigo" ok; placar mostra "Digo" com a pontuação de A.
  - `aWrongKeyCannotRenameOrSubmit`: chave sem jogador → `enviar` dá `sem_nome`.
  - `guardaOMelhorDeCadaNumero`: A envia {pontos 100, combo 50}, depois {pontos 90, combo 70} → placar pontos mostra 100, placar combo mostra 70.
  - `placarOrdenaBuscaEMarcaVoce`: 3 jogadores; `sentido:"asc"` inverte; `busca:"ana"` filtra; a linha da chave pedinte tem `voce:true`.
  - `boardBySemErrarSkipsPlayersWithoutIt`: jogador com `semErrar:null` não aparece no placar por `semErrar`.
  - `limitePorHora`: 61º envio na mesma hora → `limite`.
  - `chaveGuardadaSoComoHash`: a aba Jogadores não contém a chave em texto.
- [ ] **Step 2:** `node scripts/ranking_apps_script.test.js` → FAIL (arquivo .gs não existe).
- [ ] **Step 3: Implementar** o `.gs` (abas criadas na primeira chamada; `LockService.getScriptLock()` em reservar/enviar; hash com `Utilities.computeDigest(SHA_256)`; cabeçalho com o passo a passo de publicação, como o do Reportar, com `ranking.url`). Manifesto só com `spreadsheets` e `script.scriptapp`.
- [ ] **Step 4:** `node scripts/ranking_apps_script.test.js` → todos `ok`.
- [ ] **Step 5: Commit** `feat: Apps Script do ranking (nomes, melhores por número, placar)`.

### Task 3: Cliente, perfil do jogador e fila em `:core:data`

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/ranking/RankingApi.kt` (protocolo da Task 2 sobre `JsonPost`), `PlayerStore.kt` (+ chaves em `:core:datastore`), `RankingRepository.kt`, `RankingModule.kt`
- Create: `core/database/.../RankingPendingEntity.kt` + DAO; `RiffDatabase` v10 (`AutoMigration(9, 10)`, schema `10.json`)
- Modify: `core/data/build.gradle.kts` (`buildFeatures.buildConfig = true`, `RANKING_URL` de `local.properties`)
- Modify: `app/.../RiffApplication.kt` (na abertura: `ranking.flush()` no `appScope`)
- Test: `RankingRepositoryTest`, `RankingApiTest`, `PlayerStoreTest`, `MigrationTest.version9BecomesVersion10` (tabela `ranking_pending` criada)

**Interfaces:**
- `data class Player(val name: String?, val key: String?)`; `PlayerStore { val player: Flow<Player>; suspend fun save(name: String, key: String) }`.
- "Agora não" só na memória: `RankingRepository.askLater()` e `val askedLater: StateFlow<Boolean>` (zera a cada abertura do app, como o spec pede).
- `enum class RankingMetric(val wire: String) { POINTS("pontos"), ACCURACY("precisao"), COMBO("combo"), PERFECT("perfeitos"), CLEAN("semErrar") }`.
- `sealed interface NameResult { Ok; Taken; Invalid; Offline }`.
- `sealed interface SubmitStatus { data class Placed(val position: Int, val total: Int); Sending; Queued; NoName; NotBest }`.
- `RankingRepository`:
  - `val isConfigured: Boolean`
  - `suspend fun register(name: String): NameResult` (1º nome: gera a chave, salva e envia uma vez os melhores do histórico — `ScoreRepository` agrupado por mapa+dificuldade; renomear: mesma chave)
  - `fun submitIfBest(record: ScoreRecord, title: String, artist: String): StateFlow<SubmitStatus>` (compara com o histórico deste celular **antes** desta partida; melhor em algum número → fila no Room → tenta enviar; roda no escopo do app, não no da tela)
  - `suspend fun flush()` (envia a fila; falha de rede deixa na fila)
  - `suspend fun songs(query: String): Result<List<RankedSong>>`, `suspend fun board(chartId: String, difficulty: Difficulty?, metric: RankingMetric, descending: Boolean, query: String): Result<Board>`
- Validação local do nome igual à do script (para avisar sem rede).

- [ ] **Step 1: Testes que falham** (fake `JsonPost`, Room em memória, `ScoreRepository` fake):
  - `registerSavesNameAndKeyAndUploadsHistoryOnce`: histórico com 2 mapas → 1 `reservar` + 2 `enviar`; segunda chamada (renomear) não reenvia histórico.
  - `aTakenNameIsNotSaved`: resposta `nome_em_uso` → `NameResult.Taken`, `PlayerStore` sem nome.
  - `onlyABestIsSubmitted`: histórico com 100 pontos/50 combo; partida 90/60 → envia (combo melhor); partida 80/40 → `NotBest`, nada enviado.
  - `withoutANameItIsQueuedAndSentAfterRegistering`: sem nome → `NoName`, fica na fila; `register` envia a fila.
  - `aQueuedSubmissionSurvivesAndIsSentLater`: sem rede → `Queued`; novo repositório sobre o mesmo banco → `flush()` envia e esvazia.
  - `aNonJsonAnswerKeepsTheSubmissionQueued`: corpo HTML com HTTP 200 → `Queued`.
  - `withoutUrlNothingIsCalled`: `isConfigured == false`, nenhum POST.
  - `RankingApiTest`: o JSON de `enviar` tem `semErrar: null` para partida antiga; `placar` com `sentido:"asc"`.
- [ ] **Step 2:** `./scripts/gw :core:data:testDebugUnitTest :core:database:testDebugUnitTest` → falha de compilação.
- [ ] **Step 3: Implementar** (cliente reaproveita o padrão do `AppsScriptReportSender`: `{"ok":true}` obrigatório; o `JsonPost` de produção vai para `:core:data` e o `:feature:report` passa a usá-lo de lá).
- [ ] **Step 4:** mesmo comando + `detekt` → PASS.
- [ ] **Step 5: Commit** `feat: cliente do ranking, perfil do jogador e fila de envios`.

### Task 4: Pedido do nome e troca nas Configurações

**Files:**
- Create: módulo `:feature:ranking` (`settings.gradle.kts`, `build.gradle.kts` como `:feature:report`), `PlayerNameDialog.kt`, `PlayerNameViewModel.kt`
- Modify: `app/.../RiffNavHost.kt` (o pedido aparece por cima do início — Tocando agora ou Biblioteca — quando `isConfigured && name == null && !askedLater`)
- Modify: `feature/settings/...` aba Geral: linha "Nome no ranking" com o nome atual; tocar abre o mesmo diálogo (sem "Agora não")
- Test: `PlayerNameDialogTest`, `PlayerNameViewModelTest`, `SettingsContentTest`, screenshot `Ranking_name_prompt`

**Interfaces:**
- Consumes: `RankingRepository.register`, `PlayerStore`.
- Produces: `@Composable fun PlayerNameDialog(state: PlayerNameUiState, onName: (String) -> Unit, onSave: () -> Unit, onLater: (() -> Unit)?)`; `PlayerNameUiState(name, saving, error: NameResult?)`.

- [ ] **Step 1: Testes que falham:** `savingAFreeNameCloses`; `aTakenNameShowsTheMessageAndKeepsTheDialog` ("Esse nome já é de outra pessoa"); `anInvalidNameIsCaughtBeforeSending` (nenhuma chamada ao repositório); `notNowHidesItForThisSession` (`askLater()` chamado; o pedido some; um repositório novo — nova abertura — volta a pedir); `settingsShowsTheNameAndRenames`.
- [ ] **Step 2:** `./scripts/gw :feature:ranking:testDebugUnitTest :feature:settings:testDebugUnitTest` → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4:** mesmo comando + `recordRoborazziDebug detekt :app:assembleDebug` → PASS.
- [ ] **Step 5: Commit** `feat: nome de jogador do ranking (primeira abertura e Configurações)`.

### Task 5: Envio ao terminar a partida e o cartão do Resultado

**Files:**
- Modify: `feature/gameplay/.../GameplayViewModel.kt` (depois de `scores.save`, `ranking.submitIfBest(record, title, artist)` — vale também quando o Resultado é pulado)
- Modify: `feature/results/.../ResultsViewModel.kt`, `ResultsScreen.kt` (cartão de status + "Ver ranking"; `ResultsRoute` ganha nada: o status vem do repositório pelo `scoreId`)
- Modify: `app/.../RiffNavHost.kt` (`onSeeRanking(chartId, difficulty)`)
- Test: `GameplayViewModelTest`, `ResultsViewModelTest`, `ResultsContentTest`, screenshots `Results_ranking_placed`, `Results_ranking_no_name`

**Interfaces:**
- Consumes: `RankingRepository.submitIfBest` (guarda o último status por `scoreId`: `fun statusFor(scoreId: Long): StateFlow<SubmitStatus?>`), `GameplayUiState.Ready.title/artist`.

- [ ] **Step 1: Testes que falham:** `theFinishedMatchGoesToTheRankingEvenWhenTheResultIsSkipped` (Spotify + `spotifyNextSongDirect`: `NextSong` e o fake do repositório recebeu a partida); `theResultShowsThePosition` ("Ranking: 3º de 27" + "Ver ranking" chama `onSeeRanking("a", HARD)`); `withoutANameTheResultInvitesToChooseOne` (toque abre o pedido do nome); `queuedSaysItGoesWhenOnline`; `notBestShowsNothing`.
- [ ] **Step 2:** `./scripts/gw :feature:gameplay:testDebugUnitTest :feature:results:testDebugUnitTest` → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4:** mesmo comando + `:feature:results:recordRoborazziDebug detekt :app:assembleDebug` → PASS.
- [ ] **Step 5: Commit** `feat: partida vai sozinha para o ranking e o Resultado mostra a posição`.

### Task 6: Telas de Ranking

**Files:**
- Create: `feature/ranking/.../RankingRoute.kt` (`RankingRoute`, `RankingBoardRoute(chartId: String, difficulty: String? = null)`), `RankingSongsScreen.kt`, `RankingBoardScreen.kt`, `RankingViewModel.kt`, `RankingBoardViewModel.kt`
- Modify: `RiffNavHost.kt`; `NowPlayingScreen.kt` (botão "Ranking" na linha do topo, que passa a rolar na horizontal se não couber); `LibraryScreen.kt` (botão "Ranking" na linha Voltar/Pesquisar)
- Test: `RankingSongsContentTest`, `RankingBoardContentTest`, `RankingBoardViewModelTest`, screenshots `Ranking_songs`, `Ranking_board` (como as maquetes D e E)

**Interfaces:**
- Consumes: `RankingRepository.songs/board`, `RankingMetric`.

- [ ] **Step 1: Testes que falham:** `songsListShowsPlayersAndYourPlace` ("27 jogadores · você 3º"); `searchingSongsAsksTheServerWithTheText`; `boardMarksYouAndSortsBothWays` (linha "Rodrigo (você)"; tocar no sentido pede `descending=false`); `metricChipsChangeTheOrder` (Combo pede `RankingMetric.COMBO`); `boardStartsOnTheDifficultyFromTheRoute`; `offlineShowsTryAgain`; `notConfiguredExplains`.
- [ ] **Step 2:** `./scripts/gw :feature:ranking:testDebugUnitTest` → FAIL.
- [ ] **Step 3: Implementar** (busca com espera de 300 ms entre teclas antes de pedir ao servidor).
- [ ] **Step 4:** `./scripts/gw :feature:ranking:testDebugUnitTest :feature:ranking:recordRoborazziDebug :feature:nowplaying:testDebugUnitTest :feature:library:testDebugUnitTest detekt :app:assembleDebug` → PASS; olhar as capturas contra as maquetes.
- [ ] **Step 5: Commit** `feat: telas de Ranking (músicas e placar)`.

### Task 7: README, suíte e teste no celular

- [ ] README: seção "Ranking" (como funciona) e o passo a passo de publicar o script (`ranking.url`). Suíte completa + R8.
- [ ] Com o usuário: publicar o script (ele cola o código e implanta; eu passo o passo a passo), `ranking.url` no `local.properties`, instalar o release e conferir no celular: pedido do nome, partida enviada, placar com destaque.
