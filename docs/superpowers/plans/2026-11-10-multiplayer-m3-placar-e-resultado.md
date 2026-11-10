# Multiplayer M3 — placar ao vivo e Resultado da sala — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** durante a partida de sala, um placar ao vivo minimizado mostra a posição de cada um e anima a troca; no fim, o Resultado da sala mostra a classificação de todos; o começo junto fica mais preciso.

**Architecture:** cada aparelho grava o próprio placar em `salas/{C}/placar/{rodada}/{uid}` a cada 1 s e no fim; o streaming da sala (já aberto pela presença) entrega o dos outros. A partida (`:feature:gameplay`) recebe o placar dos outros e publica o próprio por uma interface pequena (`LiveBoard`), implementada no `:feature:room` — a partida continua sem depender do Firebase. O Resultado da sala é uma tela nova do `:feature:room`, aberta depois do Resultado de sempre.

**Tech Stack:** o mesmo da M1/M2 (`docs/superpowers/plans/2026-11-10-multiplayer-m2-musica-e-comeco.md`).

**Spec:** `docs/superpowers/specs/2026-11-05-riff-multiplayer-design.md` (Fluxo 9–11, Desconexões, Telas: placar ao vivo minimizado e Resultado da sala). Leia também `docs/pendencias.md`, seções "Multiplayer M2" e "Multiplayer M1".

## Global Constraints

- Placar ao vivo: cada aparelho grava a cada **1 s** e no fim (`terminou`); até **4** linhas "posição · nome · pontos", a própria destacada, as linhas deslizando quando as posições trocam; não cobre a pista (celular: em cima; PC: na coluna do placar, à esquerda).
- Nenhum quadro espera a rede: gravar e ler o placar nunca roda na thread da tela, e a partida não engasga por causa dele.
- Fim da rodada: quando todos os da rodada terminaram ou saíram, ou **10 s** depois do fim da música.
- Quem saiu fica em último ("saiu"); jogador sem escrever o placar por **15 s** na partida aparece "sem conexão" (o próprio placar é reenviado quando a conexão volta).
- Recorde pessoal e ranking global continuam como numa partida sozinho.
- Começo junto: o teste do usuário deu "menos de 1 s"; meta **~150 ms**.
- Mudança de regras = o usuário republica `firebase/database.rules.json` (ação dele).
- Testes nunca falam com o Firebase real; nada de `local.properties`; sem push. Padrões das features KMP; detekt (120 colunas); comentários em português; strings pt/en.

## Decisões

1. **Banco:** `salas/{C}/placar/{rodada}/{uid}: {pontos, combo, maiorCombo, acertos, notas, precisao (0–10000), terminou: bool}` — só o próprio `uid` escreve, `rodada` = a atual (ou a anterior, para o envio final atrasado), inteiros ≥ 0 com teto (pontos ≤ 10⁹). Ao começar uma rodada nova, o dono apaga `placar/{rodada − 2}` (guarda a anterior para o Resultado de quem atrasou).
2. **Interface na partida:**
   ```kotlin
   interface LiveBoard {
       /** O placar dos outros desta rodada, ao vivo. */
       val others: Flow<List<LiveScore>>
       /** O meu, chamado a cada 1 s pela partida (e no fim com finished = true); não bloqueia. */
       fun publish(mine: LiveScore)
   }
   data class LiveScore(val uid: String, val name: String, val score: Long, val combo: Int, val maxCombo: Int,
       val accuracy: Float, val finished: Boolean, val left: Boolean, val stale: Boolean)
   ```
   O `MatchStartFactory` da M2 passa a entregar também o `LiveBoard` (ex.: `RoomMatch(start: MatchStart, board: LiveBoard)`), ou uma fábrica irmã — o implementador escolhe, mantendo o `:feature:gameplay` sem `:core:room`.
3. **Ordem:** pontos (desc); empate → maior combo; "saiu" sempre por último; a própria linha destacada mesmo fora do top.
4. **Resultado da sala:** depois do Resultado de sempre da partida de sala, "Ver a sala" abre `RoomResultsRoute(code, round)`: classificação final (posição, nome, aparelho, pontos, precisão, maior combo; "saiu"; "sem conexão" para quem não mandou o final), atualizando ao vivo enquanto alguém ainda termina; "Voltar à sala". O Resultado de sempre continua com recorde e ranking.
5. **Sincronia:** (a) medir o relógio com **5** idas e voltas e usar a mediana das 3 mais rápidas; (b) o instante do começo passa a considerar a latência de saída do áudio do aparelho quando a plataforma informar (Android: `AudioTrack`/`AudioManager` `PROPERTY_OUTPUT_FRAMES_PER_BUFFER` + a latência do `AudioTrack.getTimestamp`, se o player já tiver; PC: o tamanho do buffer do Java Sound), somada à calibração; (c) registrar no log de depuração (sem dados pessoais) o offset e a ida e volta medidos, para o teste do usuário. Se (b) não tiver fonte confiável numa plataforma, fica só a calibração e isso vai para as pendências.

## Review Focus

1. Um jogador sem internet na partida: o jogo dele segue sem engasgar; os outros veem "sem conexão" na linha dele → `staleScoreShowsNoConnection` (Task 2) e `offlinePublishNeverBlocksTheFrame` (Task 3).
2. Dois com os mesmos pontos: ordem estável (maior combo, depois quem entrou primeiro) e sem a linha piscar → `tiesAreStable` (Task 2).
3. Alguém que sai no meio: vai para o fim com "saiu" e não segura o fim da rodada → `leftGoesLastAndDoesNotHoldTheEnd` (Task 2).
4. O envio final que chega depois do fim da rodada (conexão ruim): o Resultado da sala atualiza quando chega → `lateFinalScoreUpdatesTheRoomResults` (Task 4).
5. Partida sozinho: nada de placar ao vivo e nenhuma escrita → `soloMatchHasNoLiveBoard` (Task 3).

---

### Task 1: regras do placar

**Files:** Modify `firebase/database.rules.json`, `firebase/rules.test.mjs`, `firebase/README.md`, `docs/firebase.md`.

- [ ] **Step 1: Write the failing tests:** `playerWritesItsScoreForTheRound`, `cannotWriteSomeoneElsesScore`, `scoreMustBeTheCurrentOrPreviousRound`, `scoreFieldsAreValidated` (inteiros ≥ 0, `precisao` ≤ 10000, `pontos` ≤ 10⁹, `terminou` bool, campos extras recusados), `ownerDeletesOldRounds`, `guestCannotDeleteScores`, e as escritas exatas do repositório (Task 2). Todos os testes da M1/M2 continuam passando.
- [ ] **Step 2:** `scripts/testar_regras_sala.sh` → os novos FAIL. **Step 3:** regras. **Step 4:** todos passam.
- [ ] **Step 5:** commit `feat: regras do placar ao vivo`.

### Task 2: `:core:room` — placar da rodada

**Files:** Modify `core/room/.../RoomRepository.kt`, `RoomModels.kt`, `RoundFormat.kt`; Create `core/room/.../RoundBoard.kt`; Modify `FakeFirebase`; Test `RoundBoardTest`, `RoomRepositoryBoardTest`.

**Interfaces:**
- Produces: `data class BoardLine(val uid: String, val name: String, val device: RoomDevice, val score: Long, val combo: Int, val maxCombo: Int, val accuracy: Float, val finished: Boolean, val left: Boolean, val stale: Boolean)`; `Room.board: List<BoardLine>` (da rodada atual, na ordem da Decisão 3; `stale` = sem escrita há 15 s pela hora do servidor estimada, ou nunca escreveu passados 15 s do `inicio`); `Room.roundOver(nowServer): Boolean` (todos os da rodada `finished` ou `left`, ou `inicio + duracaoMs + 10 s`); `RoomRepository.publishScore(code: String, round: Int, score: RoundScore)` (não lança; sem internet guarda o último e reenvia no próximo), `data class RoundScore(...)`; o dono apaga `placar/{rodada − 2}` no `startRound`.
- [ ] **Step 1: Write the failing tests:** `boardIsOrderedByScoreThenCombo`, `tiesAreStable`, `leftGoesLastAndDoesNotHoldTheEnd`, `staleScoreShowsNoConnection`, `roundIsOverWhenAllFinishedOrLeft`, `roundIsOverTenSecondsAfterTheSong`, `publishWritesMyScore`, `publishOfflineKeepsTheLastAndResends`, `startRoundDeletesTheRoundBeforeLast`.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** passam; detekt; kover.
- [ ] **Step 5:** commit `feat: placar da rodada na sala`.

### Task 3: o placar ao vivo na partida (celular e PC)

**Files:** Create `feature/gameplay/.../LiveBoard.kt`, `feature/gameplay/.../LiveBoardHud.kt`; Modify `GameplayViewModel.kt` (publica a cada 1 s fora da thread da tela e no fim; recebe `others`), `GameplayScreen.kt`/`GameplayHud.kt` (o bloco minimizado no lugar da Decisão/Global Constraints, linhas animadas com `animateItem`/offset), `feature/room/.../RoomLiveBoard.kt` (implementa `LiveBoard` com o `RoomRepository` e a sala observada pela presença), `app/.../RoomMatchModule.kt`, `desktop/.../DesktopGraph.kt`; Test `GameplayLiveBoardTest`, `LiveBoardHudTest` (+ screenshots celular e PC), `RoomLiveBoardTest`.

- [ ] **Step 1: Write the failing tests:** `publishesEverySecondAndOnFinish` (relógio falso), `offlinePublishNeverBlocksTheFrame` (`publish` que demora não atrasa o quadro), `othersAppearOnTheHud`, `myLineIsHighlighted`, `overtakingAnimatesTheSwap` (a ordem muda e as linhas trocam de lugar), `staleLineSaysNoConnection`, `soloMatchHasNoLiveBoard`, screenshots (4 jogadores no celular em pé e no PC; com "saiu" e "sem conexão").
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** passam (inclusive todos os da partida de antes); detekt; lint.
- [ ] **Step 5:** commit `feat: placar ao vivo na partida de sala`.

### Task 4: Resultado da sala

**Files:** Create `feature/room/.../RoomResultsRoute.kt`, `RoomResultsScreen.kt`, `RoomResultsViewModel.kt`; Modify `feature/results` (na partida de sala, "Ver a sala" no lugar do "Voltar à sala", ou além dele — "Ver a sala" abre o Resultado da sala, que tem "Voltar à sala"), `app/.../RiffNavHost.kt`, `desktop/.../RiffDesktopApp.kt`; Test `RoomResultsViewModelTest`, `RoomResultsScreenTest` (+ screenshots), `DesktopRoomResultsFlowTest`.

- [ ] **Step 1: Write the failing tests:** `roomResultsShowTheFinalOrder`, `lateFinalScoreUpdatesTheRoomResults`, `leftAndNoConnectionAreShown`, `backToTheRoomFromRoomResults`, `pcKeyboardReachesBackToTheRoom`, screenshots (pódio com 4; com 2).
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** passam; detekt; lint.
- [ ] **Step 5:** commit `feat: Resultado da sala`.

### Task 5: começo mais junto

**Files:** Modify `core/room/.../RoomRepository.kt` (`measureClock` com 5 medidas e a mediana das 3 mais rápidas), `feature/room/.../RoomMatchStart.kt`, `feature/gameplay` (instante do começo descontando a latência de saída quando a plataforma informar — Decisão 5b), `core/audio` (expor a latência de saída onde houver: `expect`/`actual` ou propriedade do `SongPlayer`), log de depuração do offset; Test `RoomClockTest`, `RoomMatchStartTest`, testes do player.

- [ ] **Step 1: Write the failing tests:** `clockUsesTheMedianOfTheThreeFastestOfFive`, `outputLatencyStartsTheSongEarlier` (latência informada de 120 ms → a sessão começa 120 ms antes do instante combinado, no relógio falso), `noLatencyInfoKeepsTheCalibrationOnly`.
- [ ] **Step 2:** FAIL. **Step 3:** implementar. **Step 4:** passam; detekt.
- [ ] **Step 5:** commit `feat: começo da sala mais junto`.

### Task 6: documentação e teste do usuário

- [ ] **Step 1:** `README.md` (Sala: M3), `docs/pendencias.md` ("Multiplayer M3"); commit `docs: placar ao vivo e Resultado da sala (M3)`.
- [ ] **Step 2: ação do usuário:** republicar `firebase/database.rules.json`.
- [ ] **Step 3: teste do usuário:** PC e celular na sala; durante a música, o placar ao vivo mostra os dois e troca de posição; sem internet num deles por alguns segundos → "sem conexão" no outro e o jogo segue; no fim, "Ver a sala" mostra a classificação igual nos dois; o começo junto, de ouvido, comparado com a M2.
