# Riff no Windows — Etapa 2c-2: a partida no PC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No PC, escolher uma música na Biblioteca abre a partida: a pista no meio da janela, tocando com o teclado (cores A S J K L, palheta ↑ ↓, pausa Enter/Esc), com a música, os sons de erro e o corte da guitarra; no fim, o Resultado com o recorde. O celular fica igual.

**Architecture:** `:feature:gameplay` e `:feature:results` viram multiplataforma como a Biblioteca: tela e ViewModel em `commonMain` (os argumentos da rota chegam pelo construtor; no celular uma subclasse `@HiltViewModel` lê o `SavedStateHandle`), o que é do Android (toque, `BackHandler`, tela acesa, escala de animação do sistema, decodificar a capa) por função de plataforma. A `GameSession` ganha a entrada de teclado com palheta (motor da 2c-1). A tela da partida escolhe o arranjo pelo formato: em pé (celular) como hoje; deitada (PC) com a **pista central**, placar e combo à esquerda, multiplicador à direita e o progresso embaixo (escolha do usuário em 2026-11-01).

**Tech Stack:** Compose Multiplatform 1.12.2, eventos de tecla do Compose (`onKeyEvent`/`onPreviewKeyEvent`).

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§4 "Como se joga no PC", §5 "Telas", §6 etapa 2).

## Global Constraints

- O celular fica igual: os testes e screenshots da partida (`Gameplay_*.png`) e do resultado (`Results_*.png`) passam sem regravar; o toque segue pelo `onPress`/`onRelease`.
- Teclas de fábrica (padrão do Clone Hero, wiki oficial): verde **A**, vermelho **S**, amarelo **J**, azul **K**, laranja **L**; palheta **↑** e **↓**; pausa **Enter** (Clone Hero) e **Esc** (spec). Configurar teclas e "Tocar sem palheta" é da etapa 3; aqui a palheta fica ligada.
- A repetição automática do teclado (tecla segurada) não gera palhetadas nem apertos repetidos.
- Ranking no PC é da etapa 4: o Resultado no PC não mostra o cartão do ranking (repositório "desligado", `isConfigured = false`).
- Lições: textos do `composeResources` com `%1$d` (não `%d`) e sem `\'`; testes do PC que navegam clicam/teclam na thread da janela (`clickOnWindowThread`); `.preferences_pb`.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **Tecla segurada (repetição automática do Windows):** segurar ↓ não vira uma rajada de palhetadas; segurar A não "reaperta". Teste na Task 2.
2. **Janela perde o foco no meio da música** (Alt+Tab): a partida pausa (como o celular pausa ao sair), e as cores seguradas são soltas (sem nota longa presa). Teste na Task 4.
3. **Música sem áudio, áudio corrompido ou mapa ilegível no PC:** a partida avisa como no celular, sem travar. Coberto pelos testes do ViewModel (comum) e pela 2b; conferir na Task 5.
4. **Janela pequena ou muito larga:** a pista central cabe (altura toda, largura proporcional) e o HUD não sobrepõe a pista. Screenshot do PC em duas proporções na Task 4.
5. **Voltar da partida e "Jogar de novo"** no PC: a pilha da navegação fica certa (Biblioteca → Partida → Resultado → Biblioteca), sem tela duplicada. Teste na Task 5.

---

### Task 1: `:feature:gameplay` multiplataforma (sem mudança no celular)

**Files:**
- Modify: `feature/gameplay/build.gradle.kts` → `riff.kmp.feature` + Roborazzi; `commonMain` com `:core:game`, `:core:audio`.
- Move para `commonMain`: `GameSession`, `HudState`, `SongProgressBar`, `StageRenderer`, `GameplayRoute`, `GameplayViewModel` (vira `open class GameplayViewModel(chartId: String, difficulty: Difficulty, …)`), `GameplayScreen` (sem o padrão `hiltViewModel()`), strings → `composeResources` (conferir `%d` e `\'`).
- Fica em `androidMain`: `GameplayModule` (Hilt; `TimeSource` com `SystemClock`), `HiltGameplayViewModel` (`@HiltViewModel`, lê `chartId`/`difficulty` do `SavedStateHandle`), `ChartArt` (decodificação com `BitmapFactory`) atrás de `expect`/`actual` (PC: `org.jetbrains.skia.Image.makeFromEncoded(...).toComposeImageBitmap()` com o mesmo limite de lado de 1280).
- Funções de plataforma (expect/actual) para o que a tela usa do Android: relógio de toque (`uptimeMillis`), "animações reduzidas" (escala de animação do sistema; PC: falso), Voltar do sistema (`BackHandler`; PC: nada — a pausa é pela tecla), tela acesa (`keepScreenOn`; PC: nada), entrada por toque (o `pointerInput` de hoje fica no Android).
- Modify: `app/.../RiffNavHost.kt` (passa `hiltViewModel<HiltGameplayViewModel>()`).

**Interfaces:**
- Produces: `GameplayScreen(viewModel: GameplayViewModel, onFinished: (Long) -> Unit, onExit: () -> Unit, …)` em `commonMain`; `GameplayViewModel(chartId, difficulty, loader, settings, scores, offsets, songPlayers, stemPlayers, sfxPlayers, timeSource, interruptions, chartArt, ranking)`.

- [ ] **Step 1: Linha de base** — `:feature:gameplay:testDebugUnitTest` (82 testes) e as 4 screenshots.
- [ ] **Step 2: Teste de idioma (base)** — no teste de tela da partida, o aviso de pausa em inglês (`Locale.setDefault` + qualifiers `en`), molde do `LibraryContentTest`.
- [ ] **Step 3: Converter.**
- [ ] **Step 4: Rodar** — `:feature:gameplay:testAndroidHostTest --rerun :feature:gameplay:verifyRoborazziAndroidHostTest :feature:gameplay:compileKotlinDesktop :app:testDebugUnitTest :app:assembleDebug` → os mesmos 82+ testes, screenshots sem regravar.
- [ ] **Step 5: Commit** — `refactor: a partida multiplataforma`

### Task 2: Teclado com palheta na `GameSession`

**Files:**
- Modify: `feature/gameplay/src/commonMain/.../GameSession.kt`
- Create: `feature/gameplay/src/commonMain/.../KeyboardInput.kt` — `class KeyboardInput(session)` que recebe eventos de tecla (`Key`, `KeyEventType`) e chama a sessão; ignora `KeyDown` de uma tecla já segura (repetição automática).
- Test: `feature/gameplay/src/desktopTest/.../KeyboardInputTest.kt` e casos novos em `GameSessionStrumTest.kt` (comum ao PC; o `GameSessionTest` de hoje continua no Android).

**Interfaces:**
- Consumes: `GameEngine.onStrum(held, t)`, `onFretsChanged(held, t)`, `onRelease(lane, t)` (2c-1).
- Produces:
  - `GameSession.fretDown(lane: Int, lateByMs: Long)`, `fretUp(lane: Int, lateByMs: Long)`, `strum(lateByMs: Long)` — mantêm o conjunto de cores seguradas, chamam o motor, tocam o som de erro numa palhetada errada, ligam/desligam a guitarra separada como o toque faz, e soltam a nota longa da cor solta (`onRelease`).
  - `isPressed(lane)` reflete as cores seguradas (os botões da pista acendem).
  - `object DesktopKeys { val FRETS = listOf(Key.A, Key.S, Key.J, Key.K, Key.L); val STRUM = setOf(Key.DirectionUp, Key.DirectionDown); val PAUSE = setOf(Key.Enter, Key.Escape) }`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun aStrumWithTheFretHeldHitsAndLightsTheButton()     // fretDown(0); strum na nota → acerto; isPressed(0) até fretUp(0)
@Test fun anOverstrumPlaysTheMissSoundAndCutsTheGuitar()     // strum sem nota → sfx.miss e guitarra cortada (com faixas separadas)
@Test fun releasingTheFretEndsItsLongNote()                  // nota longa acertada; fretUp → para de pontuar
@Test fun aHopoIsHitByThePressAlone()                         // depois de um acerto, fretDown na cor do HOPO acerta sem strum
@Test fun holdingAKeyDoesNotRepeat()                          // KeyboardInput: dois KeyDown seguidos de ↓ → um strum; dois de A → um fretDown
@Test fun upAndDownBothStrum()                                // ↑ e ↓ palhetam
@Test fun enterAndEscapePause()                               // Enter e Esc chamam a pausa
```

- [ ] **Step 2: Rodar** → FAIL. **Step 3: Implementar.** **Step 4: Rodar** → PASS (`:feature:gameplay:desktopTest :feature:gameplay:testAndroidHostTest :core:game:test`).
- [ ] **Step 5: Commit** — `feat: teclado com palheta na partida`

### Task 3: `:feature:results` multiplataforma

**Files:**
- Modify: `feature/results/build.gradle.kts` → `riff.kmp.feature` + Roborazzi.
- Move: `ResultsScreen`, `RankingCard`, `ResultsRoute`, `ResultsViewModel` (`open class ResultsViewModel(scoreId: Long, …)`) → `commonMain`; strings → `composeResources`; `HiltResultsViewModel` em `androidMain`; o locale do placar como no `Hud` (2c-1).
- Modify: `app/.../RiffNavHost.kt`.

- [ ] **Step 1: Linha de base** (19 testes, 3 screenshots) e teste em inglês do texto de recorde (base).
- [ ] **Step 2: Converter.** **Step 3: Rodar** → mesmos testes, screenshots sem regravar, `:feature:results:compileKotlinDesktop`.
- [ ] **Step 4: Commit** — `refactor: o resultado multiplataforma`

### Task 4: A tela da partida deitada (pista central) e o foco da janela

**Files:**
- Modify: `feature/gameplay/src/commonMain/.../GameplayScreen.kt` — arranjo escolhido pelo formato da área: altura ≥ largura → o de hoje (sem mudança); largura > altura → pista central com a proporção da pista do celular (largura = altura × 9/16, centrada), placar e combo à esquerda, multiplicador à direita, barra de progresso embaixo na largura da pista. A entrada de teclado (`KeyboardInput`) entra pela plataforma (no PC, um `Modifier.onKeyEvent` com foco na tela da partida).
- Modify: pausa ao perder o foco da janela (PC: `LocalWindowInfo.isWindowFocused` falso → pausa e solta as cores seguradas).
- Test: `feature/gameplay/src/desktopTest/.../DesktopGameplayScreenshotTest.kt` (16:9 e 21:9, com notas, chama e HUD; gravado e conferido a olho); `DesktopGameplayFocusTest.kt`.

- [ ] **Step 1: Testes (RED)** — screenshots do PC ainda sem arranjo deitado (o de em pé esticado) e:

```kotlin
@Test fun losingTheWindowFocusPausesAndReleasesTheFrets() // cor segurada; foco perdido → isPaused e isPressed(lane) falso
```

- [ ] **Step 2: Implementar.** **Step 3:** gravar (`recordRoborazziDesktop`) e olhar: pista central na altura toda, HUD nas laterais sem sobrepor a pista, nas duas proporções. **Step 4:** celular: `verifyRoborazziAndroidHostTest` sem regravar.
- [ ] **Step 5: Commit** — `feat: a partida no PC com a pista central`

### Task 5: Ligar a partida e o resultado no app do PC

**Files:**
- Modify: `desktop/.../DesktopGraph.kt` — `FolderChartTrackLoader`, `RoomScoreRepository`, `RoomChartOffsetRepository`, `DesktopPlayers` (2b), `ClipSfxPlayer`, `TimeSource { System.nanoTime() / 1_000_000 }`, `DesktopPlayers.interruptions`, `ChartArtImages` do PC, um `RankingRepository` desligado (`isConfigured = false`, nada enviado) até a etapa 4; `gameplayViewModel(chartId, difficulty)`, `resultsViewModel(scoreId)`.
- Modify: `RiffDesktopApp.kt` — rotas `GameplayRoute` e `ResultsRoute` como no celular (`popUpTo` iguais), Biblioteca → Partida.
- Test: `desktop/src/test/.../DesktopGameplayFlowTest.kt` — com um mapa curto de fixture (2 notas e um áudio de 2 s de tom), a partida abre, teclas acertam, o fim leva ao Resultado com o recorde gravado; "Jogar de novo" volta à partida sem empilhar; Voltar do Resultado volta à Biblioteca.

- [ ] **Step 1: Teste (RED).** **Step 2: Implementar.** **Step 3: Rodar** → PASS.
- [ ] **Step 4: No Windows** — `./scripts/gw :desktop:run`: o usuário joga "Smells Like Teen Spirit" (faixas separadas) e um `.sng` com o teclado; conferir sincronia (se precisar de ajuste geral, a calibração do PC é da 2d: anotar), corte da guitarra, sons de erro, resultado e recorde.
- [ ] **Step 5: Commit** — `feat: no PC, a Biblioteca abre a partida e o resultado`

### Task 6: Fechamento

- [ ] **Step 1:** Suíte completa → BUILD SUCCESSFUL (Linux pelo CI do GitHub se a memória da máquina estiver curta; registrar).
- [ ] **Step 2:** README ("Riff no PC": como jogar, teclas) e `docs/pendencias.md`. Commit.
