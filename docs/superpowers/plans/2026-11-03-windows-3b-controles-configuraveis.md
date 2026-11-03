# Riff no Windows — Etapa 3b: controles configuráveis e "Tocar sem palheta" — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No PC, as Configurações ganham a aba **Controles**: ligar/desligar "Tocar com palheta" e, para cada ação (cores 1–5, palheta ↑, palheta ↓, pausa), apertar a tecla do teclado ou o botão do controle que quiser, com "Voltar ao padrão". A partida usa o que foi escolhido; sem palheta, apertar a cor na hora basta (o julgamento do toque do celular). O celular fica igual.

**Architecture:** As escolhas viram um campo `controls: Controls` em `Settings` (`:core:model`), gravado nas preferências como os outros campos. O teclado (`KeyboardInput`) e o controle (`PadMapper`) deixam de ter mapa fixo e recebem as escolhas; o modo sem palheta é da `GameSession` (cor apertada julga como o toque). A aba é comum (`:feature:settings`) e só entra na lista de abas do PC. Para "apertar o botão que quer", o controle passa a contar também o botão cru apertado (`Gamepad.presses`).

**Tech Stack:** Compose Multiplatform 1.12.2, DataStore Preferences, LWJGL/GLFW (3a).

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§4 "Sem palheta" e "Configurar", §6 etapa 3). Plano anterior: `docs/superpowers/plans/2026-11-01-windows-3a-controle-na-partida.md`.

## Decisões (minhas; o usuário pediu para decidir sem perguntas — sem maquete prévia)

- **Ações:** `FRET_1..FRET_5`, `STRUM_UP`, `STRUM_DOWN`, `PAUSE` (palheta ↑ e ↓ separadas, como no Clone Hero). Uma tecla e um botão por ação.
- **Fixos (não configuráveis):** Esc sempre pausa/continua no teclado; o analógico esquerdo sempre palheta no controle (além dos botões escolhidos).
- **Conflito:** escolher uma tecla/botão já usado por outra ação **troca** (a outra fica com a antiga). Esc não pode ser escolhida (cancela a escolha).
- **Aba:** "Controles" (en "Controls"), entre "Jogo" e "Visual da pista"; só no PC. Linhas: interruptor "Tocar com palheta" (dica: "Desligado, apertar a cor na hora basta"); título "Teclado" e 8 linhas (nome da ação → nome da tecla); título "Controle" e 8 linhas (→ nome do botão, ou "Sem controle ligado" no título quando `connected` é falso, linhas ainda editáveis); botão "Voltar ao padrão". Escolher: um diálogo "Aperte a tecla para Verde" / "Aperte o botão para Verde" com "Esc cancela".
- **Sem palheta:** as teclas/botões de palheta não fazem nada; a cor apertada chama o mesmo julgamento do toque (`engine.onPress`), e soltar encerra a nota longa. Ranking separado por modo é da etapa 4 (anotar).

## Global Constraints

- O celular fica igual: `SettingsContent` por padrão **não** mostra a aba Controles; os novos campos de `Settings` têm padrão e o Android não os usa; testes e screenshots do celular sem regravar.
- Padrões = os de hoje: teclado A S J K L, ↑, ↓, Enter; controle `m:3` (Y/△), `m:1` (B/○), `m:2` (X/□), `m:0` (A/✕), `m:5` (R1), `m:11` (direcional ↑), `m:13` (direcional ↓), `m:7` (Start) — índices do layout de gamepad do GLFW.
- Um botão `m:` vale num controle mapeado pelo layout do GLFW e, num controle cru, pela ordem crua do DualShock 3 (tabela fixa `m:` → `r:`: 3→12, 1→13, 2→15, 0→14, 4→10, 5→11, 6→0, 7→3, 9→1, 10→2, 11→4, 12→5, 13→6, 14→7, 8→16); no cru, `m:11`/`m:13` também aceitam o chapéu 0 ↑/↓ (como hoje). Um botão `r:` vale contra os botões crus em qualquer controle, e `h:` contra o chapéu 0. Por isso a leitura (`PadSnapshot`) passa a trazer os botões crus e o chapéu sempre, e os do layout quando mapeado. Na escolha, um controle mapeado informa `m:`; um cru, `r:`/`h:`.
- Gravação: teclas pelo código da tecla do Compose (`Key.keyCode`, `Long`); botões como texto `m:<índice>` (layout mapeado), `r:<índice>` (cru), `h:up`/`h:down` (chapéu). Valor ilegível → o padrão daquela ação.
- Lições: `composeResources` com `%1$s`/`%1$d` e sem `\'`/`\"`; testes do PC teclam na thread da janela; `git grep`; Gradle por `./scripts/gw`; detekt autoCorrect bagunça (corrigir à mão); memória curta.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **Escolher uma tecla que já é de outra ação:** troca, nenhuma ação fica sem tecla e nenhuma tecla vale por duas. Teste na Task 1.
2. **Preferência gravada estragada ou de versão futura** (texto ilegível, código desconhecido): o jogo abre com o padrão daquela ação. Teste na Task 1.
3. **Trocar de modo (com/sem palheta) e voltar à partida:** a partida seguinte usa o modo novo, e uma partida aberta não mistura os dois. Teste na Task 2 e na Task 5.
4. **Diálogo de escolha aberto e a janela perde o foco, ou o controle desconecta:** nada é gravado por engano; Esc cancela. Teste na Task 4.
5. **Tecla escolhida usada também pela navegação do app** (Enter, Tab, setas): na partida vale a escolha; Esc continua pausando. Teste na Task 3.

---

### Task 1: As escolhas nas preferências

**Files:**
- Create `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/Controls.kt`: `enum class ControlAction { FRET_1, FRET_2, FRET_3, FRET_4, FRET_5, STRUM_UP, STRUM_DOWN, PAUSE }`; `data class Controls(val strum: Boolean = true, val keys: Map<ControlAction, Long> = DEFAULT_KEYS, val pad: Map<ControlAction, String> = DEFAULT_PAD)` com `fun withKey(action, keyCode): Controls` e `fun withPad(action, button): Controls` (trocam em conflito) e `companion` com os padrões (códigos de tecla AWT: A=65, S=83, J=74, K=75, L=76, ↑=38, ↓=40, Enter=10 — os do `Key.keyCode` do Compose desktop; conferir com `Key.A.keyCode` num teste do PC).
- Modify `Settings.kt`: `val controls: Controls = Controls()`.
- Modify `core/datastore/.../RiffPreferences.kt`: grava/lê `controls_strum` (bool), `controls_keys` e `controls_pad` (texto `AÇÃO=valor;…`); ilegível → padrão por ação.
- Test: `core/model/src/test/.../ControlsTest.kt`; casos novos em `core/datastore/src/androidHostTest/.../RiffPreferencesTest.kt`.

**Interfaces:**
- Produces: `ControlAction`, `Controls(strum, keys, pad)`, `Controls.withKey/withPad`, `Controls.DEFAULT_KEYS/DEFAULT_PAD`, `Settings.controls`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun theDefaultsAreTodaysKeys()                 // keys[FRET_1] == 65 (A) … PAUSE == 10; pad[FRET_1] == "m:3"
@Test fun choosingAKeyInUseSwapsTheTwoActions()      // withKey(FRET_1, 83 /*S*/): FRET_1=S, FRET_2=A
@Test fun choosingAButtonInUseSwapsTheTwoActions()
@Test fun controlsSurviveSavingAndReading()          // RiffPreferences: strum=false, FRET_1=Q, STRUM_UP="r:7" → lidos iguais
@Test fun anUnreadableValueFallsBackToItsDefault()   // controls_keys = "FRET_1=xx;FOO=3;FRET_2=81" → FRET_1 padrão, FRET_2=81, o resto padrão
```

- [ ] **Step 2: Rodar** → FAIL. **Step 3: Implementar.** **Step 4: Rodar** `./scripts/gw :core:model:test :core:datastore:testAndroidHostTest :core:datastore:desktopTest detekt` → PASS.
- [ ] **Step 5: Commit** — `feat: as escolhas de teclas e botões nas preferências`

### Task 2: "Tocar sem palheta" na partida

**Files:**
- Modify `feature/gameplay/src/commonMain/.../GameSession.kt`: parâmetro `strum: Boolean = true` no construtor; sem palheta, `fretDown` julga como o toque (o mesmo caminho de `press`: `engine.onPress(lane, t)`, chama, som/guitarra, erro) e `fretUp` solta como o toque; `strum()` não faz nada.
- Modify `GameplayViewModel` (cria a sessão com `settings.controls.strum`).
- Test: casos novos em `feature/gameplay/src/androidHostTest/.../GameSessionStrumTest.kt`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun withoutStrumThePressAloneHits()            // strum=false: fretDown(0) na nota → PERFECT, combo 1
@Test fun withoutStrumTheStrumKeyDoesNothing()       // strum=false: strum() sem nota → nenhum erro, sfx.misses == 0
@Test fun withoutStrumReleasingEndsTheLongNote()
@Test fun aWrongFretWithoutStrumIsAMissLikeTouch()   // mesma regra do toque (pressPointer) para cor sem nota
```

- [ ] **Step 2–4:** FAIL → implementar → `./scripts/gw :feature:gameplay:testAndroidHostTest :feature:gameplay:desktopTest` PASS.
- [ ] **Step 5: Commit** — `feat: tocar sem palheta`

### Task 3: Teclado e controle com as escolhas

**Files:**
- Modify `KeyboardInput.kt`: recebe `keys: Map<ControlAction, Long>` (sem `DesktopKeys` fixo; `DesktopKeys` some ou vira só o Esc); Esc sempre pausa; `STRUM_UP`/`STRUM_DOWN` → `onStrum`.
- Modify `GameKeyboard.kt`/`GameplayScreen.kt`: as teclas vêm de `state.settings.controls.keys`.
- Modify `desktop/.../pad/PadMapper.kt`: recebe `pad: Map<ControlAction, String>`; traduz `m:`/`r:`/`h:` contra o snapshot (mapeado/cru); o analógico continua fixo; `PadAction` ganha nada novo (STRUM_UP/DOWN → `PadAction.STRUM`).
- Modify `GlfwGamepad`: `fun setBindings(pad: Map<ControlAction, String>)` (lido pela thread `riff-pad` a cada leitura; `@Volatile`); `override val presses: Flow<String>` — cada botão cru/mapeado/chapéu que desce, como texto (`m:3`, `r:12`, `h:down`), para a escolha na aba.
- Modify `core/ui/.../input/Gamepad.kt`: `val presses: Flow<String>` na interface.
- Modify `RiffDesktopApp`: `graph.gamepad.setBindings(settings.controls.pad)` quando as preferências mudam.
- Test: `KeyboardInputTest.kt` e `PadMapperTest.kt` (casos novos), `GlfwGamepadTest.kt`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun aChosenKeyPlaysItsFret()                   // keys FRET_1=Q: Q → onFretDown(0); A não faz nada
@Test fun escapeAlwaysPauses()                       // keys PAUSE=P: Esc → onPause; P → onPause
@Test fun enterChosenAsAFretIsAFret()                // FRET_3=Enter: Enter → onFretDown(2), não pausa
@Test fun aChosenPadButtonPlaysItsFret()             // pad FRET_1="m:0" (A/✕): A → FRET_1
@Test fun aChosenRawButtonStrums()                   // pad STRUM_DOWN="r:7": botão cru 7 → STRUM
@Test fun theStickStillStrumsWithAnyChoice()
@Test fun pressingAButtonReportsItForTheChoice()     // GlfwGamepad: botão cru 9 desce → presses emite "r:9"
```

- [ ] **Step 2–4:** FAIL → implementar → `./scripts/gw :feature:gameplay:desktopTest :feature:gameplay:testAndroidHostTest :desktop:test :core:ui:desktopTest detekt` PASS.
- [ ] **Step 5: Commit** — `feat: o teclado e o controle usam as escolhas`

### Task 4: A aba Controles

**Files:**
- Modify `feature/settings/src/commonMain/.../SettingsTabs.kt`: `SettingsTab.CONTROLS` (entre `GAME` e `TRACK_LOOK`); `SettingsContent` padrão `tabs = SettingsTab.entries - SettingsTab.CONTROLS` (o celular não vê).
- Create `feature/settings/src/commonMain/.../ControlsTab.kt`: `@Composable internal fun ControlsTab(controls: Controls, padConnected: Boolean, onStrum: (Boolean) -> Unit, onKey: (ControlAction, Long) -> Unit, onPad: (ControlAction, String) -> Unit, onDefaults: () -> Unit)`; diálogo de escolha: teclado pelo `onPreviewKeyEvent` do diálogo (Esc cancela; a primeira descida de outra tecla escolhe), controle pela coleta de `LocalGamepad.current?.presses` enquanto o diálogo está aberto (ignorado sem foco da janela). Nomes: tecla por `expect fun keyName(keyCode: Long): String` (PC: `java.awt.event.KeyEvent.getKeyText`; Android: o código), botões: `m:` pelos símbolos do PS3 (✕ ○ □ △ L1 R1 Select Start PS L3 R3 Direcional ↑ → ↓ ←), `r:N` → "Botão N", `h:up/down` → "Direcional ↑/↓".
- Modify `SettingsViewModel`: `setStrum`, `setKey`, `setPad`, `resetControls` (via `save { … controls.withKey(...) }`).
- Strings pt/en no `composeResources`.
- Test: `feature/settings/src/desktopTest/.../DesktopControlsTabTest.kt`, `SettingsViewModelTest` (casos novos), screenshot `Settings_wide_controls` em `DesktopSettingsScreenshotTest`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun theControlsTabShowsEachActionWithItsKeyAndButton()  // "Verde" → "A" e "△"
@Test fun pressingAKeyInTheDialogChoosesIt()                  // clicar a linha do Verde (teclado), apertar Q → onKey(FRET_1, Q)
@Test fun escapeCancelsTheChoice()                            // Esc → nada escolhido, diálogo fecha
@Test fun pressingAPadButtonChoosesIt()                       // Gamepad falso: presses emite "r:9" → onPad(FRET_1, "r:9")
@Test fun losingTheWindowFocusChoosesNothing()                // foco falso: presses e teclas ignorados
@Test fun theStrumSwitchTurnsStrumOff()
@Test fun defaultsRestoreEverything()                         // ViewModel: depois de mudar, resetControls → Controls()
```

- [ ] **Step 2–4:** FAIL → implementar → gravar `recordRoborazziDesktop` (olhar a imagem) → `./scripts/gw :feature:settings:desktopTest :feature:settings:testAndroidHostTest :feature:settings:verifyRoborazziAndroidHostTest :feature:settings:verifyRoborazziDesktop detekt` PASS (celular sem regravar).
- [ ] **Step 5: Commit** — `feat: a aba Controles nas Configurações do PC`

### Task 5: Ligar no app do PC

**Files:**
- Modify `RiffDesktopApp.kt`: tabs do PC = `SettingsTab.entries - SettingsTab.AI` (inclui CONTROLS).
- Test: `desktop/src/test/.../DesktopControlsFlowTest.kt`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun aKeyChosenInTheSettingsPlaysTheMatch()   // Configurações → Controles → Verde = Q; abrir uma partida (mapa de fixture da 2c-2): Q + ↓ na nota → acerto
@Test fun withoutStrumTheMatchJudgesThePress()      // desligar "Tocar com palheta"; partida: A na nota → acerto sem ↓
```

- [ ] **Step 2–4:** FAIL → implementar → `./scripts/gw :desktop:test detekt` PASS.
- [ ] **Step 5: No Windows (usuário)** — escolher teclas, jogar com e sem palheta; com o controle à mão, escolher botões (pendente da 3a).
- [ ] **Step 6: Commit** — `feat: no PC, os controles escolhidos valem na partida`

### Task 6: Fechamento

- [ ] **Step 1:** Suíte completa → BUILD SUCCESSFUL.
- [ ] **Step 2:** README (aba Controles, sem palheta) e `docs/pendencias.md` (ranking por modo na etapa 4). Commit.
