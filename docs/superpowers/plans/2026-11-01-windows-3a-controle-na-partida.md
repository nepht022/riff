# Riff no Windows — Etapa 3a: o controle na partida — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No PC, um controle USB (o genérico do usuário, XInput ou DirectInput) toca a partida com os botões de fábrica: as cinco cores, a palheta no direcional e a pausa no Start, com o mesmo julgamento e o mesmo atraso medido do teclado. Antes de tudo, um verificador mostra se o controle do usuário é reconhecido.

**Architecture:** O controle é lido pelo GLFW (LWJGL 3.3.6), que entende XInput e DirectInput e traz a base de mapeamentos de controles do SDL (`glfwJoystickIsGamepad`/`glfwGetGamepadState`); um controle fora da base cai no modo cru (botões e direcional pelo índice). Uma thread própria do PC inicializa o GLFW e lê o controle a cada ~1 ms, emitindo só mudanças (`PadEvent`, com o instante em `System.nanoTime()`). A partida (comum) recebe esses eventos por um `CompositionLocal` de `:core:ui` (nulo no celular) e chama a mesma `GameSession` do teclado (`fretDown`/`fretUp`/`strum`), descontando o atraso de cada evento.

**Tech Stack:** LWJGL 3.3.6 (`lwjgl`, `lwjgl-glfw`, nativos do sistema do build), Compose Multiplatform 1.12.2.

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§4 controle, §8 risco "Controle genérico no Windows", §6 etapa 3).

## Divisão da etapa 3 (escolha minha; o usuário pediu para decidir)

- **3a (este plano):** verificador do controle e o controle na partida, com botões de fábrica.
- **3b:** aba de controles nas Configurações (com/sem palheta; cada ação: apertar a tecla ou o botão desejado; voltar ao padrão) e o modo "Tocar sem palheta" (a cor apertada na hora basta, o julgamento do toque).
- **3c:** navegação de todas as telas sem mouse (foco visível; setas/direcional movem, Enter/✕ confirma, Esc/○ volta).

## Global Constraints

- O celular fica igual (nenhuma dependência nova no Android; o `CompositionLocal` do controle é nulo lá). Screenshots do celular sem regravar.
- Botões de fábrica (escolha minha, pelas cores dos símbolos do PlayStation, que o controle genérico imita): verde **△** (Y do layout do GLFW), vermelho **○** (B), amarelo **□** (X), azul **✕** (A), laranja **R1** (right bumper); palheta **direcional ↑/↓** e também o **analógico esquerdo** para cima/baixo (limiar 0,5, com histerese até 0,3); pausa **Start**. Configurar é da 3b.
- Controle fora da base do SDL (modo cru), pela ordem crua do DualShock 3 (base escolhida pelo usuário: "use como base um controle de ps3"): botão 12 △ → verde, 13 ○ → vermelho, 15 □ → amarelo, 14 ✕ → azul, 11 R1 → laranja, 3 Start → pausa, 4 ↑ e 6 ↓ → palheta; o chapéu 0 ↑/↓ (clones que expõem o direcional como chapéu) também palheta. Com o controle na base do SDL (o caso comum), vale o layout mapeado acima.
- Atraso do evento: `(System.nanoTime() - evento.atNanos) / 1_000_000`, limitado a 0..200 ms (o mesmo limite do teclado).
- Janela sem foco: os eventos do controle são ignorados (o GLFW lê o controle mesmo com a janela em segundo plano; a partida já pausa ao perder o foco).
- Lições: textos do `composeResources` com `%1$d` e sem `\'`/`\"`; testes do PC que navegam teclam na thread da janela; `git grep`; Gradle por `./scripts/gw`; memória curta (não deixar Gradle/`:desktop:run` em segundo plano).
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **Alt+Tab com o controle na mão:** com a janela sem foco, apertar Start ou as cores não continua a partida nem marca erro. Teste na Task 3.
2. **Controle desconectado no meio da música:** a leitura não trava nem derruba o jogo; as cores seguradas são soltas (sem nota longa presa) e a partida pausa. Teste na Task 2 (fonte falsa que some) e na Task 3.
3. **Controle conectado depois de abrir o jogo** (ou trocado): é encontrado sem reiniciar. Teste na Task 2.
4. **Analógico parado perto do meio (deriva) ou tremendo no limiar:** não gera palhetadas soltas. Teste na Task 2 (histerese).
5. **PC sem controle ou sem o GLFW carregando** (nativo ausente, `glfwInit` falso): o jogo abre e o teclado funciona; o verificador diz o que houve. Teste na Task 2.

---

### Task 1: Verificador do controle (risco primeiro)

**Files:**
- Modify: `gradle/libs.versions.toml` (`lwjgl = "3.3.6"`; `lwjgl-core = org.lwjgl:lwjgl`, `lwjgl-glfw = org.lwjgl:lwjgl-glfw`), `desktop/build.gradle.kts` (as duas, mais os nativos com o classificador do sistema do build — `natives-windows`/`natives-linux`, como o `nativeClassifier` do `core/audio/build.gradle.kts`; tarefa `runPadCheck` como a `runAudioCheck`).
- Create: `desktop/src/main/kotlin/com/rodrigomachado/riff/desktop/pad/PadCheck.kt` — `fun main()`: inicializa o GLFW (falhou: imprime o motivo e sai), lista os controles (`glfwJoystickPresent` 0..15: nome, GUID, `glfwJoystickIsGamepad`, nome do mapeamento) e, por 20 s, imprime cada mudança de botão do layout de gamepad (quando há mapeamento) e dos botões/eixos/chapéus crus, e ao fim o intervalo real entre leituras (média e pior caso).

- [ ] **Step 1:** Implementar; `./scripts/gw :desktop:compileKotlin detekt` → BUILD SUCCESSFUL. (Ferramenta de medição, como o `AudioCheck`: sem teste automático.)
- [ ] **Step 2: No Windows (usuário)** — (adiado para o teste da Task 3, a pedido do usuário: base PS3) `./scripts/gw :desktop:runPadCheck` com o controle ligado: apertar △ ○ □ ✕ R1, Start, direcional ↑/↓ e analógico esquerdo. Anotar no ledger: nome, se tem mapeamento (gamepad) ou é cru, a ordem dos botões crus e o intervalo de leitura. Se o intervalo médio passar de 4 ms, Ruling na Task 2 (ex.: `timeBeginPeriod` pelo JNA não é dependência; usar espera ativa curta).
- [ ] **Step 3: Commit** — `feat: verificador do controle no PC`

### Task 2: Leitura do controle (thread própria, só mudanças)

**Files:**
- Create `:core:ui` `commonMain` `com/rodrigomachado/riff/core/ui/input/Gamepad.kt`:
  - `enum class PadAction { FRET_1, FRET_2, FRET_3, FRET_4, FRET_5, STRUM, PAUSE }` (+ `val fretLane: Int?` 0..4 para as cores)
  - `data class PadEvent(val action: PadAction, val down: Boolean, val atNanos: Long)`
  - `interface Gamepad { val events: Flow<PadEvent>; val connected: StateFlow<Boolean> }`
  - `val LocalGamepad = staticCompositionLocalOf<Gamepad?> { null }`
- Create `desktop/src/main/kotlin/com/rodrigomachado/riff/desktop/pad/`:
  - `PadSnapshot` (estado lido: botões do layout de gamepad ou crus, eixo Y esquerdo, chapéu) e `interface PadSource { fun init(): Boolean; fun poll(): PadSnapshot? /* null = nenhum controle */ }`; `GlfwPadSource` (procura o primeiro joystick presente a cada leitura sem controle — encontra um conectado depois; gamepad pelo `glfwGetGamepadState`, senão cru).
  - `PadMapper` — puro: `fun changes(previous: PadSnapshot?, current: PadSnapshot?, atNanos: Long): List<PadEvent>` com os botões de fábrica (Global Constraints), histerese do analógico (liga em |y| ≥ 0,5, desliga em < 0,3) e a palheta do direcional e do analógico somadas (uma palhetada por borda de descida de qualquer um). Controle sumiu (`current == null`) → `down = false` para tudo o que estava apertado.
  - `GlfwGamepad(source: PadSource = GlfwPadSource(), clock: () -> Long = System::nanoTime) : Gamepad, AutoCloseable` — thread daemon `riff-pad` que chama `init()` (falso: `connected` fica falso para sempre e a thread acaba), lê a cada ~1 ms e emite os eventos num `MutableSharedFlow(extraBufferCapacity = 64)`; `close()` encerra a thread.
- Modify: `DesktopGraph` (`val gamepad = GlfwGamepad()`; fechado no `close()`), `RiffDesktopApp` (`CompositionLocalProvider(LocalGamepad provides graph.gamepad)`).
- Test: `desktop/src/test/kotlin/com/rodrigomachado/riff/desktop/pad/PadMapperTest.kt`, `GlfwGamepadTest.kt` (com `PadSource` falso).

**Interfaces:**
- Produces: `PadAction`, `PadEvent`, `Gamepad`, `LocalGamepad` (`com.rodrigomachado.riff.core.ui.input`); `GlfwGamepad`.

- [ ] **Step 1: Testes (RED)**

```kotlin
// PadMapperTest
@Test fun triangleIsTheGreenFret()                 // Y apertado → PadEvent(FRET_1, down = true)
@Test fun r1IsTheOrangeFret()
@Test fun dpadDownStrumsOncePerPress()              // ↓ apertado em duas leituras seguidas → um STRUM down
@Test fun theStickStrumsWithHysteresis()            // y: 0.0 → 0.6 (strum) → 0.4 (nada) → 0.6 (nada) → 0.2 → 0.6 (strum)
@Test fun aStickDriftingNearTheMiddleDoesNotStrum() // y oscilando entre 0.2 e 0.45 → nenhum evento
@Test fun startPauses()
@Test fun rawPadUsesTheRawButtonOrder()             // snapshot cru: botão 3 → FRET_1 (verde), botão 0 → FRET_4 (azul), chapéu ↑ → STRUM
@Test fun aDisconnectedPadReleasesWhatWasHeld()     // △ e R1 apertados, depois null → FRET_1 e FRET_5 down = false
// GlfwGamepadTest
@Test fun aPadPluggedInLaterIsFound()               // fonte: null, null, depois △ → evento FRET_1; connected vira true
@Test fun withoutGlfwTheGameStillOpens()            // init() = false → connected false, nenhum evento, close() não trava
@Test fun closingStopsTheReadingThread()
```

- [ ] **Step 2: Rodar** → FAIL. **Step 3: Implementar.** **Step 4: Rodar** `./scripts/gw :desktop:test :core:ui:desktopTest detekt` → PASS.
- [ ] **Step 5: Commit** — `feat: leitura do controle no PC`

### Task 3: O controle na partida

**Files:**
- Create `feature/gameplay/src/commonMain/.../GamePad.kt`: `@Composable internal fun HandleGamepad(session: GameSession, isPaused: Boolean, onPause: () -> Unit, onResume: () -> Unit)` — coleta `LocalGamepad.current?.events` (nulo: nada), ignora eventos com a janela sem foco (`LocalWindowInfo.current.isWindowFocused`), e chama: cor down/up → `session.fretDown/fretUp(lane, lateBy)`; STRUM down → `session.strum(lateBy)`; PAUSE down → pausa ou, pausado, continua (com o som de confirmar, como o Enter). `lateBy = ((nanoTime agora - atNanos) / 1_000_000).coerceIn(0, 200)` (`expect fun nowNanos(): Long` em `:core:ui` `input/` — Android `System.nanoTime()` também; uma linha).
- Modify `GameplayScreen.kt` (chama `HandleGamepad`), e na perda de foco da janela também `session.releaseAllFrets()` já cobre o controle (as cores do controle passam pela mesma sessão).
- Modify `GlfwGamepad`/`HandleGamepad`: controle desconectado (`connected` vira falso) durante a partida → pausa (as cores já chegam soltas pelo `PadMapper`).
- Test: `feature/gameplay/src/desktopTest/.../DesktopGameplayPadTest.kt` (um `Gamepad` falso com `MutableSharedFlow`, a janela de foco controlado do `DesktopStage.kt`).

**Interfaces:**
- Consumes: `LocalGamepad`, `PadEvent` (Task 2); `GameSession.fretDown/fretUp/strum` (2c-2).

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun holdingTriangleAndStrummingHitsTheGreenNote()   // FRET_1 down + STRUM down na hora da nota → PERFECT, combo 1
@Test fun anEventThatWaitedIsJudgedAtItsOwnTime()         // atNanos 30 ms antes de "agora": julgado 30 ms antes
@Test fun startPausesAndStartAgainResumes()
@Test fun withTheWindowUnfocusedThePadIsIgnored()          // foco falso: STRUM e PAUSE não fazem nada
@Test fun unpluggingThePadPausesTheMatch()                 // connected → false: onPause chamado
```

- [ ] **Step 2: Rodar** → FAIL. **Step 3: Implementar.** **Step 4: Rodar** `./scripts/gw :feature:gameplay:desktopTest :feature:gameplay:testAndroidHostTest :feature:gameplay:verifyRoborazziAndroidHostTest :desktop:test detekt` → PASS.
- [ ] **Step 5: No Windows (usuário)** — `./scripts/gw :desktop:run`: escolher a música com o mouse, jogar com o controle (cores, palheta no direcional e no analógico, Start pausa e continua), Alt+Tab, desconectar o cabo no meio.
- [ ] **Step 6: Commit** — `feat: o controle na partida do PC`

### Task 4: Fechamento

- [ ] **Step 1:** Suíte completa → BUILD SUCCESSFUL (Linux pelo CI; os testes não inicializam o GLFW — fontes falsas).
- [ ] **Step 2:** README ("Jogar no PC": botões de fábrica do controle, `runPadCheck`) e `docs/pendencias.md`. Commit.
