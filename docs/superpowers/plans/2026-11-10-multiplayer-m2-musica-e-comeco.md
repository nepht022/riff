# Multiplayer M2 — música e começo juntos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** na sala, o dono escolhe a música; cada jogador confere se tem (ou baixa do Encore), escolhe a dificuldade e marca Pronto; o dono começa e todos tocam a mesma música começando juntos (contagem 3-2-1), sem pausa.

**Architecture:** o `RoomRepository` (`:core:room`) ganha a música, a rodada, o começo e o estado de cada jogador; a sala (`:feature:room`) ganha a música, a dificuldade, o Pronto e o Começar; a partida (`:feature:gameplay`) ganha um modo de sala por uma interface pequena (`MatchStart`) que o `:feature:room` implementa — a partida não depende do Firebase. O relógio comum vem do `vistoEm` (hora do servidor) lido de volta.

**Tech Stack:** o mesmo da M1 (`docs/superpowers/plans/2026-11-05-multiplayer-m1-sala.md`): Kotlin/Compose Multiplatform, Firebase REST + SSE, regras testadas no emulador.

**Spec:** `docs/superpowers/specs/2026-11-05-riff-multiplayer-design.md` (Fluxo 3–8, Telas: sala e contagem). Leia também `docs/pendencias.md`, seção "Multiplayer M1".

## Global Constraints

- Até **4** jogadores; **com 1 jogador só o Começar fica desligado; com 2, 3 ou 4 começa** (decisão de 2026-11-10, pedido do usuário).
- Começar habilitado quando **pelo menos 2** jogadores vivos estão **prontos com a música**, e todos os vivos com a música estão prontos — ou **30 s** depois do primeiro Pronto com pelo menos 2 prontos ("Começar com quem está pronto").
- Começo: `inicio` = hora do servidor + **3 s** depois que todos os que vão jogar carregaram a música (ou **15 s** depois do Começar, com quem carregou); cada aparelho começa no seu relógio local equivalente.
- Diferença de começo entre aparelhos: até ~**150 ms** (meta da especificação).
- Na partida de sala **não há pausa**: Esc/Start, perda de foco (PC) e interrupções (celular) não pausam; Sair da partida marca `saiu` e volta à sala.
- Quem não tem a música: veio do Encore → "Baixar do Encore"; não veio → "Você não tem esta música" e fica de fora da rodada sem travar os outros.
- Mudança de regras = o usuário republica `firebase/database.rules.json` no console (ação dele).
- Testes nunca falam com o Firebase/Encore reais; nada de `local.properties` impresso/commitado; sem push.
- Padrões das features KMP (ViewModels `open` em `commonMain`, `Hilt…ViewModel` finos, `DesktopGraph` no PC, strings em `composeResources`), detekt (120 colunas), comentários em português.

## Decisões

1. **Banco** (acréscimos ao formato da M1):
   - `salas/{C}/musica: {chartId, titulo, artista, dificuldades: "EASY,MEDIUM,…", duracaoMs?, encore?: {md5, video: bool, charter?}}` — só o dono escreve; sem `musica` = nenhuma escolhida.
   - `salas/{C}/rodada: n` (inteiro ≥ 0), `inicio: ms` (hora do servidor, ou ausente), `estado: "sala"|"contagem"|"partida"` — só o dono.
   - `jogadores/{uid}`: `dificuldade` (`EASY|MEDIUM|HARD|EXPERT`), `pronto` (bool), `musica` (`"tem"|"baixando"|"sem"`), `carregou` (inteiro: a rodada em que a música dele terminou de carregar), `saiu` (inteiro: a rodada que ele abandonou).
   - Trocar a música zera nada no servidor: cada app recalcula `musica`/`pronto` ao ver um `chartId` novo (o próprio jogador grava).
2. **Rodada:** Começar grava `{estado: "contagem", rodada: n+1}` (sem `inicio`). Cada app em `contagem` que está pronto com a música abre a partida da rodada `n+1`; carregou → `carregou: n+1`. O dono, quando todos os prontos da rodada carregaram (ou 15 s), grava `{estado: "partida", inicio: agoraServidor + 3000}`. A regra aceita `inicio` entre `now` e `now + 20000`.
3. **Relógio:** `RoomRepository.measureClock(code)` faz 3 vezes: `PATCH vistoEm = ServerTime.NOW`, `GET jogadores/{uid}/vistoEm`, `offset = vistoEm − (t0 + t1) / 2`; fica com a de menor `t1 − t0`. `serverNow() = clock() + offset`. Medido ao abrir a sala e de novo ao abrir a partida.
4. **Fim da rodada na M2:** quem termina vê o Resultado de sempre (recorde e ranking como sozinho) e "Voltar" leva à sala; o dono, ao sair da partida (fim ou Sair), grava `estado: "sala"` e apaga `inicio`. O Resultado da sala é da M3.
5. **Modo de sala da partida:** `GameplayRoute(chartId, difficulty, roomCode: String? = null, round: Int = 0)`. O `GameplayViewModel` recebe `matchStart: MatchStart?`:
   ```kotlin
   interface MatchStart {
       /** A música carregou e a partida está montada, parada. */
       suspend fun loaded()
       /** Espera o começo combinado; devolve o instante no relógio deste aparelho (ms, mesmo relógio do `clock`). */
       suspend fun awaitStart(): Long
       /** O jogador saiu no meio da rodada. */
       suspend fun left()
   }
   ```
   Com `matchStart`, o `load()` não chama `session.start()`: chama `loaded()`, espera `awaitStart()`, mostra a contagem (`GameplayUiState.Ready.countdownEndsAt: Long?`) e começa a sessão no instante devolvido; `pause()` não faz nada; as interrupções e a perda de foco não pausam.
6. **Escolher a música:** `LibraryRoute` ganha o modo de escolha (`LibraryRoute(pickForRoom: String? = null)` ou parâmetro equivalente): o toque na música abre o diálogo de dificuldade de sempre; escolher grava a `musica` da sala e a dificuldade do dono e volta para a sala.
7. **Encore:** o `chartId` do outro aparelho é o SHA-1 das notas — se o jogador não tem o `chartId`, mas a música tem `encore`, "Baixar do Encore" usa o `ChartDownloader` com um `EncoreChart` montado da sala (`md5`, `video`, `titulo`, `artista`, `charter`, `duracaoMs`, `dificuldades`); depois de baixado, confere de novo pelo `chartId` (o mesmo pacote dá o mesmo SHA-1); se ainda não bater, "sem".

## Review Focus

1. Um jogador nunca carrega (aparelho lento, música corrompida): os outros começam em até 15 s → `ownerStartsWithWhoLoadedAfter15s` (Task 2).
2. O dono sai ou some durante a contagem: o novo dono (M1) assume e começa → `newOwnerFinishesTheCountdown` (Task 2).
3. O relógio do aparelho adiantado/atrasado em minutos: o começo ainda acontece junto (o offset compensa) → `startIsInLocalClockWithTheMeasuredOffset` (Task 2).
4. Trocar de música com jogadores já prontos: todos voltam a "não pronto" e conferem de novo → `changingTheSongClearsReady` (Task 3).
5. Apertar Esc/Voltar, perder o foco ou receber ligação na partida de sala: não pausa → `roomMatchNeverPauses` (Task 4).

---

### Task 1: regras da M2

**Files:** Modify `firebase/database.rules.json`, `firebase/rules.test.mjs`; Modify `docs/firebase.md` (aviso: republicar).

**Interfaces:** Produces: as regras para o formato da Decisão 1 e as transições da Decisão 2.

- [ ] **Step 1: Write the failing tests:** `ownerChoosesTheSong`, `guestCannotChooseTheSong`, `songFieldsAreValidated` (`chartId` 40 hex; `titulo`/`artista` até 200; `dificuldades` só as 4 palavras separadas por vírgula; `encore.md5` 32 hex), `ownerStartsARoundByOne` (`rodada` só sobe de 1 em 1, com `estado: "contagem"`), `inicioMustBeSoon` (`now ≤ inicio ≤ now + 20000`; fora disso recusado), `onlyOwnerSetsInicio`, `playerSetsItsDifficultyReadyAndSong`, `loadedMustBeTheCurrentRound` (`carregou` = `rodada` atual), `leftMustBeTheCurrentRound`, `extraPlayerFieldsStillRefused`, e as escritas exatas do `RoomRepository` (Task 2) como nos testes "repository…" da M1.
- [ ] **Step 2:** `scripts/testar_regras_sala.sh` → os novos FAIL, os da M1 passam.
- [ ] **Step 3:** regras. **Step 4:** todos passam.
- [ ] **Step 5:** commit `feat: regras da sala para a música e o começo`.

### Task 2: `RoomRepository` — música, rodada, relógio e começo

**Files:** Modify `core/room/src/commonMain/kotlin/com/rodrigomachado/riff/core/room/RoomRepository.kt`, `RoomFormat.kt`, `RoomModels.kt`; Create `RoomClock.kt`, `RoomRound.kt`; Modify `core/room/testing/.../FakeFirebase.kt` (relógio do servidor diferente do aparelho, para os testes do offset); Test `RoomRepositoryTest`, `RoomClockTest`, `RoomRoundTest`.

**Interfaces:**
- Produces:
  - `data class RoomSong(val chartId: String, val title: String, val artist: String, val difficulties: Set<Difficulty>, val durationMs: Long?, val encore: EncoreRef?)`, `data class EncoreRef(val md5: String, val video: Boolean, val charter: String?)`.
  - `enum class SongStatus { HAS, DOWNLOADING, MISSING }`; `enum class RoomState { LOBBY, COUNTDOWN, PLAYING }`.
  - `RoomPlayer` ganha `difficulty: Difficulty?`, `ready: Boolean`, `song: SongStatus?`, `loadedRound: Int?`, `leftRound: Int?`; `Room` ganha `song: RoomSong?`, `round: Int`, `state: RoomState`, `startAt: Long?` (hora do servidor), `firstReadyAt: Long?` (calculado: o menor instante em que alguém ficou pronto nesta música — guardado localmente pelo observador), `fun canStart(nowServer: Long): Boolean` (regras dos Global Constraints) e `fun playersInRound(): List<RoomPlayer>` (vivos, prontos e com a música na rodada atual).
  - `RoomRepository`: `suspend fun chooseSong(code: String, song: RoomSong, myDifficulty: Difficulty)`, `suspend fun setMe(code: String, difficulty: Difficulty?, ready: Boolean, song: SongStatus)`, `suspend fun startRound(code: String): Boolean` (só o dono; false se `canStart` é falso), `suspend fun markLoaded(code: String, round: Int)`, `suspend fun markLeft(code: String, round: Int)`, `suspend fun scheduleStartIfDue(code: String, round: Int)` (dono: todos os da rodada carregaram ou 15 s desde o Começar → `inicio = serverNow + 3000`, `estado: "partida"`), `suspend fun backToLobby(code: String)` (dono), `suspend fun measureClock(code: String): RoomClock`.
  - `class RoomClock(val offsetMs: Long, private val clock: () -> Long) { fun serverNow(): Long; fun toLocal(serverMs: Long): Long }`.
- [ ] **Step 1: Write the failing tests:** `chooseSongWritesTheSongAndMyDifficulty`, `setMeWritesMyState`, `canStartNeedsTwoReadyWithTheSong` (1 pronto → false; 2 → true; 3 vivos com a música e só 2 prontos → false até 30 s do primeiro Pronto, depois true), `missingSongPlayerDoesNotBlock` (um "sem" não conta), `goneDoesNotBlock`, `startRoundRaisesTheRoundAndCountsDown`, `guestCannotStartRound`, `ownerStartsWhenAllLoaded` (`inicio = serverNow + 3000`), `ownerStartsWithWhoLoadedAfter15s`, `newOwnerFinishesTheCountdown`, `measureClockUsesTheFastestRoundTrip` (servidor 5 min adiantado; 3 idas e voltas com 300, 80, 200 ms → offset da de 80 ms ±40), `startIsInLocalClockWithTheMeasuredOffset`, `backToLobbyClearsStart`, `leftIsWrittenForTheRound`.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** passam; detekt; kover.
- [ ] **Step 5:** commit `feat: música, rodada e começo junto na sala`.

### Task 3: a sala com música, dificuldade, Pronto e Começar (celular)

**Files:** Modify `feature/room/.../RoomLobbyScreen.kt`, `RoomLobbyViewModel.kt`, strings; Modify `feature/library` (modo de escolha, Decisão 6) e `app/.../RiffNavHost.kt`; Create `feature/room/.../RoomSongCheck.kt` (tenho? baixar do Encore); Test `RoomLobbyViewModelTest`, `RoomScreensTest`, `RoomScreenshotTest`, teste da Biblioteca no modo de escolha.

**Interfaces:**
- Consumes: Task 2.
- Produces: `RoomLobbyViewModel` com `fun chooseDifficulty(d: Difficulty)`, `fun toggleReady()`, `fun download()`, `fun start()`, e `val navigateToMatch: StateFlow<MatchTicket?>` (`data class MatchTicket(val chartId: String, val difficulty: Difficulty, val round: Int)`), emitido quando o estado vira `COUNTDOWN` com a rodada nova e eu estou pronto com a música; `fun interface ChartLookup { suspend fun has(chartId: String): Boolean }` (o celular usa o repositório de mapas; o PC também); download pelo `ChartDownloader`.
- Textos: "Escolher música", "Trocar música", "Nenhuma música escolhida", "Dificuldade", "Pronto", "Não estou pronto", "Começar", "Começar com quem está pronto", "Precisa de pelo menos 2 prontos", "Baixar do Encore", "Baixando…", "Você não tem esta música.", "Sem a música", "Pronto"/"Escolhendo" nos cartões, "Começando…".
- [ ] **Step 1: Write the failing tests:** `ownerPicksASongFromTheLibrary` (a Biblioteca no modo de escolha devolve chartId+dificuldade e a sala mostra a música), `guestSeesTheSongAndChoosesADifficultyOfTheSong`, `changingTheSongClearsReady`, `missingEncoreSongOffersDownloadAndThenHas`, `missingLocalSongSaysYouDoNotHaveIt`, `startIsOffWithOneReady`, `startIsOnWithTwoReady`, `startWithWhoIsReadyAfter30s`, `countdownOpensTheMatchForReadyPlayersOnly`, screenshots (sala com música e 3 jogadores em estados diferentes; sem música; baixando).
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** passam; screenshots conferidas; detekt; lint.
- [ ] **Step 5:** commit `feat: música, dificuldade e Pronto na sala`.

### Task 4: a partida de sala (contagem, começo junto, sem pausa)

**Files:** Modify `feature/gameplay/.../GameplayViewModel.kt`, `GameplayRoute.kt`, a tela da partida (contagem 3-2-1 por cima da pista); Create `feature/gameplay/.../MatchStart.kt`; Create `feature/room/.../RoomMatchStart.kt` (implementa `MatchStart` com o `RoomRepository` e o `RoomClock`; o dono chama `scheduleStartIfDue` a cada 1 s enquanto espera; `left()` → `markLeft`); Modify `app/.../RiffNavHost.kt` (o `Hilt…ViewModel` da partida monta o `RoomMatchStart` quando a rota tem `roomCode`; ao sair/terminar, volta à sala; o dono chama `backToLobby`); Test `GameplayViewModelTest` (modo de sala) e `RoomMatchStartTest`.

**Interfaces:**
- Consumes: `MatchStart` (Decisão 5), Task 2.
- Produces: `GameplayUiState.Ready.countdownEndsAt: Long?` e `inRoom: Boolean` (a tela esconde o botão de pausa e não pausa).
- [ ] **Step 1: Write the failing tests:** `roomMatchWaitsForTheAgreedStart` (relógio falso: a sessão não começa antes do instante e começa nele, ±1 quadro), `roomMatchReportsLoadedOnce`, `roomMatchNeverPauses` (`pause()`, interrupção e foco: a sessão segue), `leavingTheRoomMatchMarksLeft`, `soloMatchIsUnchanged` (sem `matchStart`, tudo como antes), `RoomMatchStartTest`: `awaitStartReturnsLocalTimeOfInicio`, `ownerSchedulesWhenAllLoaded`.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** passam (inclusive todos os testes da partida de antes); detekt.
- [ ] **Step 5:** commit `feat: a partida de sala começa junto e não pausa`.

### Task 5: no PC

**Files:** Modify `desktop/.../DesktopGraph.kt`, `RiffDesktopApp.kt` (Biblioteca no modo de escolha, partida de sala, `ChartLookup`, download); a perda de foco não pausa a partida de sala; Test `desktop/src/test/.../DesktopRoomMatchFlowTest.kt` (o app como dono e outro jogador pelo `FakeFirebase.repository(...)`).

- [ ] **Step 1: Write the failing tests:** `ownerPicksSongReadiesAndStartsWithAGuest` (o convidado falso fica pronto e carrega; a partida do PC começa no `inicio`), `focusLossDoesNotPauseTheRoomMatch`, `escInTheRoomMatchAsksToLeaveAndMarksLeft`, `onlyKeyboardPicksDifficultyReadyAndStart`.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** `:desktop:test detekt` passa.
- [ ] **Step 5:** commit `feat: música e começo junto no PC`.

### Task 6: documentação e teste do usuário

- [ ] **Step 1:** `README.md` (Sala: M2), `docs/pendencias.md` ("Multiplayer M2"); commit `docs: sala com música e começo junto (M2)`.
- [ ] **Step 2: ação do usuário:** republicar `firebase/database.rules.json` no console.
- [ ] **Step 3: teste do usuário:** PC e celular na sala; o PC escolhe uma música do Encore que o celular não tem → o celular baixa; os dois escolhem dificuldades diferentes e ficam prontos; Começar; contagem e começo juntos (de ouvido); Esc no PC não pausa; no fim, Resultado e volta à sala; com um jogador só, o Começar fica desligado.
