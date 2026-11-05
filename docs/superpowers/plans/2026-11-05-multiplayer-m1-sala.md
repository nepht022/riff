# Multiplayer M1 — Firebase e a sala — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** no celular e no PC, **Sala** na tela inicial cria uma sala com código de 4 letras ou entra numa pelo código, e a tela da sala mostra ao vivo os jogadores (até 4); sair passa o dono adiante.

**Architecture:** módulo KMP novo `:core:room` com o cliente REST do Firebase (login anônimo do Identity Toolkit, Realtime Database por REST e streaming SSE) sobre `HttpURLConnection`, e um `RoomRepository` com a lógica da sala; módulo KMP novo `:feature:room` com as telas (escolha, entrar com código, sala) e ViewModels `open` no `commonMain`, `Hilt…ViewModel` finos no Android e o `DesktopGraph` no PC. As regras do banco ficam em `firebase/database.rules.json`, testadas no emulador do Firebase.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2, Hilt (Android), Room/DataStore existentes, `org.json` (já usado no ranking), Firebase Realtime Database REST + Identity Toolkit REST, `firebase-tools` (emulador, só nos testes das regras).

**Spec:** `docs/superpowers/specs/2026-11-05-riff-multiplayer-design.md` (seções Identidade, Dados, Regras, Fluxo 1–2 e 11, Desconexões da sala, Telas: escolha, entrar, sala).

## Global Constraints

- Firebase **plano gratuito (Spark)**; sem SDK do Firebase no app; "API REST do Realtime Database (leitura, escrita e o streaming por SSE, `Accept: text/event-stream`) e a API REST do Identity Toolkit para o login anônimo".
- Configuração no `local.properties` (fora do git): `firebase.apiKey` e `firebase.dbUrl` → constantes geradas `RoomConfig.API_KEY` e `RoomConfig.DB_URL` (como o `RankingConfig`). Vazias = Sala desligada ("em breve", como hoje). Nunca imprimir nem commitar o `local.properties`.
- Testes nunca falam com o Firebase de verdade.
- Código: 4 letras do alfabeto `ABCDEFGHJKMNPQRSTUVWXYZ` (23 letras: sem `I`, `L`, `O`; números fora) = 23⁴ = 279.841 códigos.
- Até **4** jogadores; nome até **20** caracteres (o do ranking).
- Sala morta: sem atividade por **2 h**; quem cria apaga até **5** salas mortas.
- Jogador que some na sala: **60 s** sem escrever (batimento a cada 20 s).
- Mesmos padrões das features KMP existentes (`riff.kmp.feature`/`riff.kmp.library`, strings em `composeResources`, testes de Android em `androidHostTest`, PC em `desktopTest`); textos em português; detekt (linhas até 120).
- Criar o projeto do Firebase, ligar o login anônimo e publicar as regras são **ações do usuário**.

## Decisões

1. **Formato do banco** (o da especificação): `salas/{CODIGO}` com `dono`, `criadaEm`, `atividadeEm`, `estado` (`"sala"` em M1), `jogadores/{uid}: {nome, aparelho, entrouEm, vistoEm}`. `atividadeEm`/`vistoEm` com `{".sv":"timestamp"}`.
2. **Criar sem colisão:** `PUT salas/{CODIGO}` com `X-Firebase-ETag: true` e `if-match: null_etag` (só grava se não existe); `412` → outro código, até 10 tentativas.
3. **Entrar:** a regra recusa o 5º jogador; o app lê a sala antes para dar a mensagem certa (`Sala não encontrada`, `Sala cheia`).
4. **Dono sai:** o app do dono, ao sair, grava como `dono` o jogador com o menor `entrouEm` (se houver) e se remove; sem ninguém, apaga a sala. Dono que some (60 s sem `vistoEm`): o jogador mais antigo vivo assume (as regras deixam qualquer jogador trocar o `dono` só quando o `vistoEm` do dono atual tem mais de 60 s).
5. **Streaming:** um `GET salas/{CODIGO}.json?auth=…` com `Accept: text/event-stream`; eventos `put`/`patch` aplicados numa árvore JSON local; `keep-alive` ignorado; `cancel`/`auth_revoked` → renovar o token e reabrir; queda → reabrir com espera 1, 2, 4… até 30 s.
6. **Token:** `refreshToken` e `uid` nas preferências do DataStore (`room_refresh_token`, `room_uid`); `idToken` só na memória, renovado 5 min antes de vencer.

## Review Focus

1. Sem internet ao abrir a Sala/criar/entrar: mensagem "Sem conexão" e tentar de novo, sem travar a tela → teste `createWithoutInternetShowsNoConnection` (Task 5).
2. Dois aparelhos criando com o mesmo código ao mesmo tempo: só um fica com ele → teste `createRetriesWhenTheCodeIsTaken` (Task 3).
3. O 5º jogador tentando entrar (inclusive dois entrando juntos com 3 na sala): recusado pelas regras → `fifthPlayerIsRefused` (Task 1) e `joinFullRoomSaysFull` (Task 3).
4. Token vencido no meio da sala (1 h): o streaming reabre sozinho → `streamReopensWithFreshTokenOnAuthRevoked` (Task 2).
5. Código digitado em minúsculas, com espaço ou com `O`/`0`/`I`/`1`: normalizado ou recusado na caixa → `codeInputNormalizes` (Task 4).

---

### Task 1: regras do banco e os testes no emulador

**Files:**
- Create: `firebase/database.rules.json`, `firebase/firebase.json` (só o emulador do database e do auth), `firebase/package.json` (dev: `firebase-tools`, `@firebase/rules-unit-testing`, `firebase`), `firebase/rules.test.mjs`, `firebase/.gitignore` (`node_modules/`, logs do emulador)
- Create: `docs/firebase.md` (passo a passo do usuário: criar projeto Spark, Realtime Database, ligar Anonymous, publicar as regras pelo console colando o arquivo, copiar `apiKey` e URL do banco para o `local.properties`)

**Interfaces:**
- Produces: as regras que as Tasks 2–5 obedecem (formato da Decisão 1).
- Regras (o essencial):
  - raiz: `.read`/`.write` false.
  - `salas/$codigo`: `.read: auth != null`; `$codigo` casa `^[A-HJKMNP-Z]{4}$`.
  - criar (`!data.exists()`): `newData.child('dono').val() == auth.uid`, `estado == 'sala'`, `criadaEm`/`atividadeEm` == `now`.
  - apagar (`!newData.exists()`): o dono, ou qualquer autenticado se `data.child('atividadeEm').val() < now - 7200000`.
  - `dono`: escreve o dono, ou um jogador da sala se `now - data.parent().child('jogadores').child(data.val()).child('vistoEm').val() > 60000`, e o novo dono é um jogador da sala.
  - `estado`, `criadaEm`: só o dono; `atividadeEm`: qualquer jogador (== `now`).
  - `jogadores/$uid`: só `auth.uid == $uid`; entrar só se `data.parent().numChildren() < 4` (ou já está lá); `nome` string 1–20; `aparelho` `celular|pc`; `entrouEm`/`vistoEm` == `now`; nada além desses campos (`$other: .validate false`).
  - o dono pode remover qualquer jogador (sair passa o dono e se remove).

- [ ] **Step 1: Write the failing tests** (`rules.test.mjs`, `node:test` + `@firebase/rules-unit-testing`):
  `anonymousCannotReadOutsideRooms`, `unauthenticatedCannotReadARoom`, `creatorBecomesOwner`, `cannotCreateWithSomeoneElseAsOwner`, `codeMustBeFourAllowedLetters` (`ABCD` ok; `ABCO`, `abcd`, `ABC` recusados), `playerWritesOnlyItself`, `fifthPlayerIsRefused`, `nameLongerThan20IsRefused`, `onlyOwnerChangesState`, `ownerCanBePassedOnlyWhenGoneFor60s`, `staleRoomCanBeDeletedByAnyone` (com `atividadeEm` de 3 h atrás), `freshRoomCanBeDeletedOnlyByOwner`, `extraFieldsAreRefused`.
- [ ] **Step 2:** `cd firebase && npm install && npx firebase emulators:exec --only database,auth "node --test rules.test.mjs"` → FAIL (regras ausentes). O emulador precisa de Java: no WSL não há; rode pelo Windows (`cmd.exe /c` com `JAVA_HOME` do Temurin 21 em `C:\Users\Rodrigo Machado\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2`) e registre no README do `firebase/` o comando que funcionou.
- [ ] **Step 3:** escrever `database.rules.json`.
- [ ] **Step 4:** os 13 testes passam.
- [ ] **Step 5:** `docs/firebase.md`; commit `feat: regras do banco da sala (Firebase) com testes no emulador`.

### Task 2: `:core:room` — configuração, login anônimo, REST e streaming

**Files:**
- Create: `core/room/build.gradle.kts` (`riff.kmp.library`, `riff.kmp.hilt`, `kover`; `generateRoomConfig` como o `generateRankingConfig` do `core/data/build.gradle.kts`, lendo `firebase.apiKey` e `firebase.dbUrl`), `settings.gradle.kts` (`include(":core:room")`)
- Create em `core/room/src/commonMain/kotlin/com/rodrigomachado/riff/core/room/`: `RoomHttp.kt`, `AnonymousAuth.kt`, `FirebaseDb.kt`, `JsonTree.kt`, `SseParser.kt`
- Modify: `core/datastore` (as duas chaves da Decisão 6, como `PlayerStore` faz com `player_key`)
- Test: `core/room/src/commonTest/...` (ou `androidHostTest`/`desktopTest` conforme o padrão do `:core:data`): `SseParserTest`, `JsonTreeTest`, `AnonymousAuthTest`, `FirebaseDbTest`

**Interfaces:**
- Produces:
  - `interface RoomHttp { suspend fun request(method: String, url: String, body: String?, headers: Map<String, String>): HttpReply; fun stream(url: String): Flow<String> /* linhas */ }`, `data class HttpReply(val code: Int, val body: String, val headers: Map<String, String>)`; `UrlConnectionRoomHttp` (timeouts 15 s; o `stream` com `readTimeout` 60 s, já que o Firebase manda `keep-alive` a cada ~30 s).
  - `class AnonymousAuth(http: RoomHttp, store: RoomIdentityStore, apiKey: String, clock: () -> Long) { suspend fun token(): String; suspend fun uid(): String; suspend fun refreshNow() }` — `accounts:signUp?key=` com `{"returnSecureToken":true}`; renovação em `https://securetoken.googleapis.com/v1/token?key=` (`grant_type=refresh_token`).
  - `interface RoomIdentityStore { suspend fun read(): RoomIdentity?; suspend fun write(identity: RoomIdentity) }`, `data class RoomIdentity(val uid: String, val refreshToken: String)`.
  - `class FirebaseDb(http: RoomHttp, auth: AnonymousAuth, dbUrl: String) { suspend fun get(path: String): Any?; suspend fun put(path: String, value: Any?, ifMatch: String? = null): PutResult; suspend fun patch(path: String, values: Map<String, Any?>); suspend fun delete(path: String); suspend fun etag(path: String): Pair<String, Any?>; fun observe(path: String): Flow<Any?> }` — valores como `JSONObject`/`JSONArray`/primitivos; `sealed interface PutResult { data object Ok; data object Conflict /* 412 */ }`; `observe` emite a árvore inteira depois de cada `put`/`patch`, reabre como na Decisão 5.
  - `object ServerTime { val NOW: Map<String, String> = mapOf(".sv" to "timestamp") }`.
  - Erros: `class NoConnection : Exception()` (IOException/timeout), `class RoomHttpError(val code: Int) : Exception()`.

- [ ] **Step 1: Write the failing tests:**
  - `SseParserTest`: `putEventWithDataIsParsed` (`event: put` + `data: {"path":"/","data":{...}}` + linha vazia → `SseEvent("put", "/", json)`), `keepAliveIsIgnored`, `multiLineDataIsJoined`, `cancelAndAuthRevokedAreSurfaced`.
  - `JsonTreeTest`: `putAtRootReplaces`, `putAtChildPathSetsDeep` (`/jogadores/u1`), `putNullDeletes`, `patchMergesChildren`.
  - `AnonymousAuthTest` (HTTP falso): `firstUseSignsUpAndStoresIdentity`, `secondUseRefreshesWithStoredToken` (sem novo signUp), `tokenIsReusedUntilFiveMinutesBeforeExpiry`, `noInternetThrowsNoConnection`.
  - `FirebaseDbTest`: `putWithNullEtagReturnsConflictOn412`, `urlsCarryAuthAndJsonSuffix` (`…/salas/ABCD.json?auth=<token>`), `observeAppliesPutsAndPatches`, `streamReopensWithFreshTokenOnAuthRevoked`, `streamReopensAfterDrop` (com espera controlada por um relógio falso).
- [ ] **Step 2:** `./scripts/gw :core:room:allTests 2>&1 | tail -c 3000` (ou as tasks de teste do padrão do módulo) → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** testes passam; `detekt` limpo.
- [ ] **Step 5:** commit `feat: cliente do Firebase para a sala (login anônimo, REST e streaming)`.

### Task 3: `RoomRepository` — criar, entrar, sair e a sala ao vivo

**Files:**
- Create: `core/room/src/commonMain/kotlin/com/rodrigomachado/riff/core/room/RoomRepository.kt`, `RoomModels.kt`, `RoomCodes.kt`
- Create: um Firebase falso em memória para os testes (`FakeFirebase` em `testFixtures` ou na pasta de teste comum, implementando `RoomHttp` com a semântica de `PUT`/`PATCH`/`DELETE`/ETag/`stream` e o `{".sv":"timestamp"}` por um relógio falso; **sem** regras — as regras são da Task 1)
- Test: `RoomRepositoryTest`, `RoomCodesTest`

**Interfaces:**
- Consumes: `FirebaseDb`, `AnonymousAuth`, `ServerTime` (Task 2).
- Produces:
  - `data class Room(val code: String, val owner: String, val players: List<RoomPlayer>, val me: String)` com `val iAmOwner: Boolean`; `data class RoomPlayer(val uid: String, val name: String, val device: RoomDevice, val joinedAt: Long, val seenAt: Long, val gone: Boolean)`; `enum class RoomDevice { PHONE, PC }` (`"celular"`, `"pc"`).
  - `sealed interface JoinResult { data class Joined(val code: String); data object NotFound; data object Full; data object NoConnection }`; `sealed interface CreateResult { data class Created(val code: String); data object NoConnection }`.
  - `class RoomRepository(db: FirebaseDb, auth: AnonymousAuth, device: RoomDevice, clock: () -> Long, random: Random = Random.Default) { suspend fun create(name: String): CreateResult; suspend fun join(code: String, name: String): JoinResult; suspend fun leave(code: String); fun observe(code: String): Flow<Room?> /* null = sala acabou */; suspend fun heartbeat(code: String) }`.
  - `object RoomCodes { const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ"; fun random(random: Random): String; fun normalize(input: String): String? /* maiúsculas, sem espaços; null se não for 4 letras do alfabeto */ }`.
  - `gone` = `now - seenAt > 60_000`; `observe` recalcula `gone` a cada 5 s mesmo sem evento.

- [ ] **Step 1: Write the failing tests:**
  - `RoomCodesTest`: `randomUsesOnlyTheAlphabet` (10.000 códigos, 4 letras, sem `I L O`), `normalizeUppercasesAndTrims` (`" abcd "` → `"ABCD"`), `normalizeRefusesAmbiguousAndWrongLength` (`"ABCO"`, `"AB1D"`, `"ABC"`, `"ABCDE"` → null).
  - `RoomRepositoryTest`: `createWritesOwnerAndFirstPlayer`, `createRetriesWhenTheCodeIsTaken` (o falso já tem `salas/AAAA`; o `Random` falso sorteia `AAAA` e depois `BBBB` → `Created("BBBB")`), `createCleansUpToFiveStaleRooms` (7 salas com `atividadeEm` de 3 h → 5 apagadas, a fresca fica), `joinAddsMe`, `joinUnknownCodeSaysNotFound`, `joinFullRoomSaysFull`, `joinTwiceKeepsOneEntry`, `leaveAsOwnerPassesToOldestPlayer`, `leaveAsLastPlayerDeletesTheRoom`, `goneOwnerIsReplacedByOldestLivePlayer`, `observeEmitsPlayersInJoinOrder`, `observeEmitsNullWhenRoomIsDeleted`, `playerSilentFor60sIsGone`, `noInternetOnCreateSaysNoConnection`.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** passam; detekt; kover do módulo no mesmo nível dos outros `:core:*`.
- [ ] **Step 5:** commit `feat: sala do multiplayer (criar, entrar, sair, ao vivo)`.

### Task 4: `:feature:room` — telas e ViewModels; Sala ligada na tela inicial do celular

**Files:**
- Create: `feature/room/build.gradle.kts` (`riff.kmp.feature`), `settings.gradle.kts`
- Create em `feature/room/src/commonMain/kotlin/com/rodrigomachado/riff/feature/room/`: `RoomRoutes.kt` (`RoomEntryRoute`, `RoomJoinRoute`, `RoomLobbyRoute(code: String)`), `RoomEntryScreen.kt`, `RoomJoinScreen.kt`, `RoomLobbyScreen.kt`, `RoomEntryViewModel.kt`, `RoomJoinViewModel.kt`, `RoomLobbyViewModel.kt`; strings em `composeResources/values/strings.xml`
- Create em `androidMain`: `HiltRoom…ViewModel`s e o módulo Hilt do `:core:room` (em `core/room/src/androidMain`)
- Modify: `feature/home` (Sala ligada quando `RoomConfig.API_KEY` não é vazio; continua "em breve" sem configuração), `app/src/main/kotlin/com/rodrigomachado/riff/RiffNavHost.kt` (rotas novas), `app/build.gradle.kts`
- Test: `feature/room/src/androidHostTest/...`: `RoomJoinViewModelTest`, `RoomLobbyViewModelTest`, `RoomScreensTest` (Roborazzi: escolha, entrar vazio/com erro, sala como dono com 1 e com 4 jogadores, sala como convidado, jogador "sumiu"); `feature/home` `HomeContentTest` atualizado

**Interfaces:**
- Consumes: `RoomRepository`, `Room`, `RoomCodes`, `JoinResult`, `CreateResult` (Task 3); `PlayerName`/diálogo do nome do `:feature:ranking` (sem nome → pede antes de criar/entrar).
- Produces: `open class RoomLobbyViewModel(code: String, repository: RoomRepository)` com `uiState: StateFlow<RoomLobbyUiState>` (`Loading`, `Ready(room)`, `Ended` — a sala acabou, `NoConnection`), `fun leave()`; batimento a cada 20 s enquanto a tela está aberta; `open class RoomJoinViewModel` com `fun onCode(input: String)` (guarda só o normalizado possível: maiúsculas, sem espaços, máx. 4) e `fun join()` → navegação para a sala ou erro (`Sala não encontrada`, `Sala cheia`, `Sem conexão`); `open class RoomEntryViewModel` com `fun create()`.
- Textos: "Criar sala", "Entrar com código", "Código da sala", "Entrar", "Sala não encontrada.", "Sala cheia (4 jogadores).", "Sem conexão. Tente de novo.", "Sair da sala", "Dono", "Celular", "PC", "Saiu da sala?"/"sumiu" para o jogador `gone`, "Aguardando jogadores…"; na sala, a música e o Começar aparecem só na M2 (aqui um texto "A música é escolhida na próxima versão" **não** entra: a tela mostra só o código, os jogadores e Sair).

- [ ] **Step 1: Write the failing tests:** `codeInputNormalizes` (`"ab c"` → `"ABC"`; `"abcde"` → `"ABCD"`; `"o"` recusado), `joinShowsNotFound`, `joinShowsFull`, `createWithoutInternetShowsNoConnection`, `lobbyShowsPlayersLive` (o falso adiciona um jogador → aparece), `lobbyEndsWhenTheRoomIsDeleted`, `leaveNavigatesBack`, `withoutConfigTheRoomTileStaysComingSoon`, `withConfigTheRoomTileOpensTheRoom`, e as screenshots.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** implementar (identidade visual das telas existentes).
- [ ] **Step 4:** passam; `recordRoborazziAndroidHostTest` do módulo e conferir as imagens; detekt, lint.
- [ ] **Step 5:** commit `feat: telas da sala no celular`.

### Task 5: a sala no PC

**Files:**
- Modify: `desktop/src/main/kotlin/com/rodrigomachado/riff/desktop/DesktopGraph.kt` (`RoomRepository` com `UrlConnectionRoomHttp`, `device = RoomDevice.PC`, o `RoomIdentityStore` no DataStore do PC), `RiffDesktopApp.kt` (rotas), `desktop/build.gradle.kts`
- Test: `desktop/src/test/kotlin/com/rodrigomachado/riff/desktop/DesktopRoomFlowTest.kt` (com o `FakeFirebase` da Task 3), `DesktopRoomScreenshotTest` se o padrão do PC tiver

**Interfaces:**
- Consumes: tudo das Tasks 2–4.
- Comportamento: teclado e controle em todas as telas da sala (setas/direcional entre os botões; na caixa do código, digitar letras; Backspace apaga; Enter entra; Esc volta); a mesma lógica de "em breve" sem configuração.

- [ ] **Step 1: Write the failing tests:** `roomTileWithoutConfigIsComingSoon`, `createShowsTheCodeAndMe`, `joinByKeyboardTypesTheCodeAndEnters`, `anotherPlayerJoiningAppearsLive`, `escLeavesTheRoom`, `onlyKeyboardReachesEveryButton`.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** passam; `:desktop:test detekt`.
- [ ] **Step 5:** commit `feat: a sala no PC`.

### Task 6: documentação, verificação e teste do usuário

**Files:**
- Modify: `README.md` (seção "Sala (multiplayer)": módulos, `local.properties`, `docs/firebase.md`, testes das regras), `docs/pendencias.md` (seção "Multiplayer M1")

- [ ] **Step 1:** docs; commit `docs: sala do multiplayer (M1)`.
- [ ] **Step 2: ação do usuário:** seguir `docs/firebase.md` (criar o projeto, ligar Anonymous, publicar as regras, mandar a `apiKey` e o URL — eu coloco no `local.properties` sem imprimir).
- [ ] **Step 3: teste do usuário:** criar a sala no PC, entrar pelo celular (APK de release por cima) com o código, ver os dois na lista, sair do PC (o celular vira dono), sair do celular (a sala some).
