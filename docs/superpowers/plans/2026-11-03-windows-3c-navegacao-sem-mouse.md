# Riff no Windows — Etapa 3c: navegação sem mouse — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No PC, todas as telas do jogo funcionam só com o teclado ou só com o controle: o elemento com o foco é visível; setas (ou direcional) movem o foco; Enter (ou ✕) confirma; Esc (ou ○) volta. Dá para ir da tela inicial a uma partida, pausar, sair, abrir as Configurações e calibrar sem tocar no mouse. O celular fica igual.

**Architecture:** O foco é o do Compose (`FocusManager`). Três peças no PC: (1) um destaque de foco comum (`Modifier.focusHighlight()` em `:core:ui`, desenhado só no PC) aplicado aos componentes que se clicam; (2) na raiz do app do PC, as setas que nenhum componente usou movem o foco (`moveFocus`), a primeira seta numa tela sem foco foca o primeiro item, e Esc volta (`popBackStack`), exceto onde a tela usa o Esc (partida, diálogos, calibração medindo); (3) o controle vira teclado nos menus: direcional → setas, ✕ → Enter, ○ → Esc, entregues como teclas à janela, desligado durante a partida tocando e enquanto um diálogo de escolha de botão está aberto.

**Tech Stack:** Compose Multiplatform 1.12.2 (foco, `onPreviewKeyEvent`/`onKeyEvent`), AWT (eventos de tecla sintéticos na janela).

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§5 "Foco visível em todas as telas; setas/direcional movem, Enter/X confirma, Esc/○ volta").

## Decisões (minhas; o usuário pediu para decidir sem perguntas)

- **Destaque do foco:** borda de 2 dp na cor `RiffColors.Paper` com cantos do próprio componente, mais um leve clarão de fundo; só no PC (no celular, `focusHighlight()` não desenha nada — o celular fica igual, inclusive com teclado físico).
- **Controle nos menus:** botões fixos, independentes das escolhas da partida: direcional ↑↓←→ (`m:11..14`; no cru, pela tabela do DualShock 3 e o chapéu), ✕ (`m:0`) confirma, ○ (`m:1`) volta; Start nos menus = Enter. Segurar o direcional não repete (uma tecla por aperto).
- **Pausa:** na pausa, setas/direcional movem entre os botões da pausa (Continuar, Sair, ajuste de sincronia); Enter/✕ aciona o botão com foco; sem foco, Enter continua (como hoje). Esc/○/Start continuam.
- **Tela "Não dá para jogar" (PC):** Esc/○ volta (pendência da 2c-2).
- **Escolha de botão na aba Controles:** ○ do controle cancela o diálogo de escolha de **tecla**; no de **botão**, o ○ é escolhível, e cancela-se com Esc (teclado) — registrar.

## Global Constraints

- O celular fica igual: screenshots do celular sem regravar; nenhuma mudança de foco/teclas no Android (o que é do PC fica atrás de `expect`/`actual` ou no `:desktop`).
- A partida tocando não muda: as teclas e o controle da partida (2c-2/3a/3b) seguem; a emulação de teclas pelo controle fica desligada com a partida tocando.
- Lições: testes do PC teclam na thread da janela (`clickOnWindowThread`/`runOnUiThread`); screenshots do PC com 1% de tolerância; `composeResources` com `%1$s`, sem `\'`; detekt autoCorrect bagunça (corrigir à mão); `git grep`; Gradle por `./scripts/gw`; memória curta.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **Foco "perdido" depois de uma ação** (o item focado some — música apagada, diálogo fechado, aba trocada): a próxima seta foca algo visível na tela, sem precisar do mouse. Teste na Task 2.
2. **Lista longa (Biblioteca):** descer com a seta rola a lista e mantém o item focado visível. Teste na Task 2.
3. **Esc em telas com Esc próprio** (partida, diálogo aberto, calibração medindo, escolha de tecla): faz só o que a tela faz, sem voltar duas vezes. Teste na Task 2/3.
4. **Controle nos menus logo depois de sair da partida** (botão ainda segurado ao trocar de tela; ✕ que acertou nota não vira "confirmar"): nada é acionado por engano. Teste na Task 4.
5. **Controles de valor (sliders de volume/velocidade):** ←/→ mudam o valor do slider focado em vez de mover o foco; ↑/↓ saem dele. Teste na Task 2.

---

### Task 1: Foco visível

**Files:**
- Create `core/ui/src/commonMain/kotlin/com/rodrigomachado/riff/core/ui/component/FocusHighlight.kt`: `fun Modifier.focusHighlight(shape: Shape = RoundedCornerShape(…)): Modifier` (comum; desenha quando focado) e `expect val showsFocusHighlight: Boolean` (PC `true`, Android `false` — com falso, o modificador é `this`).
- Modify (aplicar): `PosterButton`, `TrackCard`, `DifficultyDialog` (botões), `feature/settings` (`ChoiceRow`, `SwitchRow`, linhas de slider, abas, linhas da aba Controles, botões dos diálogos de escolha), `feature/home` (botões da tela inicial), `feature/results` (botões), `feature/gameplay` (botões da pausa e da tela indisponível), `feature/calibration` (botões), `feature/library` (botões, cartões, diálogos), `TextButton`s de "Voltar".
- Test: `core/ui/src/desktopTest/.../FocusHighlightScreenshotTest.kt` (um `PosterButton` e um `TrackCard` focados e não focados; gravar e olhar); celular `verifyRoborazziAndroidHostTest` sem regravar.

- [ ] **Step 1: Teste (RED)** — screenshot do PC com o botão focado (`requestFocus`) igual ao não focado → não há destaque (falha ao comparar com a referência gravada depois de implementar: grave a referência só depois de olhar a imagem certa).
- [ ] **Step 2: Implementar e aplicar.** **Step 3:** gravar (`recordRoborazziDesktop` dos módulos tocados) e olhar; `verifyRoborazziAndroidHostTest` sem regravar.
- [ ] **Step 4: Commit** — `feat: o foco visível no PC`

### Task 2: Teclado nos menus do PC

**Files:**
- Create `core/ui/src/commonMain/kotlin/com/rodrigomachado/riff/core/ui/input/MenuKeys.kt`: `fun Modifier.menuKeys(onBack: (() -> Unit)?): Modifier` — no `onKeyEvent` (depois dos filhos): ↑↓←→ → `focusManager.moveFocus(Up/Down/Left/Right)`; sem nada focado, a primeira seta faz `moveFocus(FocusDirection.Enter)` (ou foca o primeiro item focável) ; Esc → `onBack` (nulo: nada). Só no PC (`expect val menuKeysEnabled`; Android `false` → o modificador é `this`).
- Modify `desktop/.../RiffDesktopApp.kt`: cada destino (menos a partida) envolve a tela com `menuKeys(onBack = { navController.popBackStack() })` (a tela inicial com `onBack = null`); diálogos abertos consomem o Esc antes (conferir: `AlertDialog` fecha no Esc pelo `onDismissRequest`).
- Modify `feature/calibration`: durante a medição o Esc não volta (consumido pela área de medição: hoje devolve `false` — passa a cancelar a medição e consumir).
- Test: `desktop/src/test/kotlin/com/rodrigomachado/riff/desktop/DesktopKeyboardNavigationTest.kt`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun theFirstArrowFocusesTheFirstButtonAndEnterOpensIt()   // tela inicial: ↓ → "Sozinho" focado; Enter → Biblioteca
@Test fun escapeGoesBack()                                       // Biblioteca → Esc → tela inicial; na tela inicial Esc não faz nada
@Test fun arrowsWalkTheSettingsTabsAndRows()                     // Configurações: → muda de aba focada; ↓ chega a "Sons do menu"; Enter alterna
@Test fun leftAndRightChangeAFocusedSlider()                     // aba Sons: foco no volume; → aumenta o valor gravado; ↓ sai do slider
@Test fun downScrollsTheLibraryList()                            // Biblioteca com 30 mapas falsos: ↓ 25 vezes → o 25º está focado e visível
@Test fun escapeClosesADialogWithoutGoingBack()                  // idioma: diálogo aberto, Esc → fecha; continua nas Configurações
@Test fun afterTheFocusedItemDisappearsTheNextArrowFindsAnother() // fechar um diálogo (foco some) → ↓ foca um item visível
@Test fun escapeWhileMeasuringStopsTheCalibrationOnly()          // calibração medindo: Esc → instruções; segundo Esc → Configurações
```

- [ ] **Step 2–4:** FAIL → implementar → `./scripts/gw :desktop:test :core:ui:desktopTest :feature:calibration:desktopTest :feature:settings:desktopTest detekt` PASS.
- [ ] **Step 5: Commit** — `feat: o teclado nos menus do PC`

### Task 3: A pausa e a tela indisponível sem mouse

**Files:**
- Modify `feature/gameplay/src/commonMain/.../GameKeyboard.kt` e a pausa (`PauseOverlay`): com a pausa aberta, ↑↓←→ movem o foco entre os botões da pausa (a primeira seta foca "Continuar"); Enter aciona o focado; sem foco, Enter continua (como hoje). Fora da pausa, nada muda.
- Modify a tela "Não dá para jogar": `GameBackHandler` do PC (hoje vazio) passa a voltar com Esc (`expect`/`actual` de `:feature:gameplay`; Android continua o `BackHandler`).
- Test: `feature/gameplay/src/desktopTest/.../DesktopPauseMenuKeysTest.kt`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun arrowsMoveBetweenThePauseButtons()      // pausa aberta: ↓ foca Continuar; ↓ foca o próximo (ajuste ou Sair)
@Test fun enterOnExitLeavesTheMatch()             // foco em Sair, Enter → onExit
@Test fun enterWithNothingFocusedResumes()        // como hoje
@Test fun escapeOnTheUnavailableScreenGoesBack()
@Test fun arrowsStillStrumWhilePlaying()          // fora da pausa: ↓ palheta (nada muda)
```

- [ ] **Step 2–4:** FAIL → implementar → `./scripts/gw :feature:gameplay:desktopTest :feature:gameplay:testAndroidHostTest :feature:gameplay:verifyRoborazziAndroidHostTest detekt` PASS.
- [ ] **Step 5: Commit** — `feat: a pausa e a tela indisponível sem mouse`

### Task 4: O controle nos menus

**Files:**
- Create `desktop/src/main/kotlin/com/rodrigomachado/riff/desktop/pad/PadMenuKeys.kt`: `class PadMenuKeys(send: (awtKeyCode: Int) -> Unit)` com `fun onPress(button: String)` — `m:11/12/13/14` → ↑ → ↓ ← (`VK_UP`, `VK_RIGHT`, `VK_DOWN`, `VK_LEFT`), `m:0` → `VK_ENTER`, `m:1` → `VK_ESCAPE`, `m:7` (Start) → `VK_ENTER`; outros nada. (No controle cru, `presses` já dá esses nomes pela tabela do DualShock 3 — 3b.)
- Modify `RiffDesktopApp`/`Main.kt`: coleta `graph.gamepad.presses` e envia cada tecla à janela (`window.dispatchEvent(KeyEvent(KEY_PRESSED…))` seguido de `KEY_RELEASED`, na thread do AWT) — só com a janela focada, **desligado** quando: (a) a rota atual é a partida e ela não está pausada (estado exposto por um `CompositionLocal` de `:core:ui`, ex.: `LocalPadMenuSuspended`, que a partida e o diálogo de escolha de botão ligam enquanto ativos); (b) um diálogo de escolha de botão está aberto. Ao sair da partida, os botões já segurados não disparam (só descidas depois da troca de tela).
- Modify `feature/settings/.../ControlsTab.kt` (o diálogo de botão liga a suspensão) e `feature/gameplay` (a partida tocando liga; pausada, desliga).
- Test: `desktop/src/test/kotlin/com/rodrigomachado/riff/desktop/pad/PadMenuKeysTest.kt` e `desktop/src/test/.../DesktopPadNavigationTest.kt` (controle falso: direcional ↓ + ✕ na tela inicial abre a Biblioteca; ○ volta; na partida tocando, ✕ não confirma nada; no diálogo de escolha de botão, ✕ é escolhido e não reabre o diálogo).

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun dpadAndCrossMapToArrowsAndEnter()
@Test fun circleIsEscape()
@Test fun otherButtonsSendNothing()
@Test fun aPadAloneOpensTheLibraryAndComesBack()         // fluxo: ↓ (m:13) e ✕ (m:0); ○ (m:1)
@Test fun whilePlayingThePadDoesNotPressMenuKeys()        // partida tocando: ✕ não chega como Enter (não pausa, não aciona)
@Test fun choosingAButtonDoesNotAlsoConfirm()             // aba Controles: diálogo de botão aberto, ✕ → escolhido; o diálogo não reabre
```

- [ ] **Step 2–4:** FAIL → implementar → `./scripts/gw :desktop:test :feature:settings:desktopTest :feature:gameplay:desktopTest detekt` PASS.
- [ ] **Step 5: No Windows (usuário)** — só teclado: da tela inicial a uma partida e de volta, pausa, Configurações (abas, sliders, idioma, Controles), Calibração, sem mouse. Controle: pendente (junto do teste da 3a).
- [ ] **Step 6: Commit** — `feat: o controle nos menus do PC`

### Task 5: Fechamento

- [ ] **Step 1:** Suíte completa → BUILD SUCCESSFUL.
- [ ] **Step 2:** README ("Riff no PC": sem mouse) e `docs/pendencias.md`. Commit.
