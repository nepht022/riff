# Riff no Windows — Etapa 2d: calibração e Configurações no PC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No PC, a tela inicial abre as Configurações (Geral, Sons, Jogo, Visual da pista; idioma, volumes, velocidade, canhoto, sons do menu, visual) e, delas, a Calibração, medida apertando uma tecla junto com o metrônomo; o atraso salvo vale na partida seguinte. O celular fica igual.

**Architecture:** `:feature:settings` e `:feature:calibration` viram multiplataforma como a Biblioteca e a partida (ViewModel `open class` em `commonMain`, subclasse `@HiltViewModel` fina em `androidMain`, o que é do Android por `expect`/`actual`). O atraso da tecla e a perda de foco da janela saem de `:feature:gameplay` para `:core:ui`, usados pela partida e pela calibração. O idioma no PC fica num arquivo da pasta de dados e o app refaz as telas sem perder a pilha.

**Tech Stack:** Compose Multiplatform 1.12.2, composeResources, JetBrains navigation-compose, `DesktopGraph` (DI manual).

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§3 objetivos "calibração, Configurações"; §5 telas; §6 etapa 2).

## Global Constraints

- O celular fica igual: os testes e screenshots das Configurações e da Calibração passam sem regravar; no celular a calibração continua pelo toque (`uptimeMillis` do evento).
- Fora desta etapa: aba de controles, teclas configuráveis e "Tocar sem palheta" (etapa 3, com maquete antes); navegação de todas as telas só por teclado/controle (etapa 3); ranking, Licenças, Reportar e a guitarra por IA no PC (etapa 4). No PC as Configurações escondem a aba "Guitarra IA", a linha do nome no ranking (`NoRanking.isConfigured = false`) e a linha das Licenças.
- Lições: textos do `composeResources` com `%1$d` (não `%d`) e sem `\'`; testes do PC que navegam clicam/teclam na thread da janela (`clickOnWindowThread`); screenshots do PC com 1% de tolerância; `git grep`, não `grep -r`.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **Tecla segurada na calibração (repetição automática do Windows):** segurar uma tecla conta um toque só. Teste na Task 2.
2. **Alt+Tab no meio da medição:** a medição para e volta às instruções (como o `ON_STOP` do celular), sem metrônomo tocando escondido. Teste na Task 2.
3. **PC sem saída de áudio** (nenhum dispositivo, linha que não abre): a calibração mostra "Não deu para tocar o metrônomo", sem travar. Coberto pelo `CalibrationViewModelTest` (comum); conferir que a falha do `JavaSoundSink` vira exceção dentro de `play()` na Task 2.
4. **Trocar o idioma no PC:** a tela continua nas Configurações já no idioma novo, Voltar leva à tela inicial (pilha intacta), e o idioma vale ao abrir o app de novo. Teste na Task 3.
5. **O atraso salvo vale na partida do PC:** depois de Salvar, `settings.audioOffsetMs` é o oposto do medido e a próxima partida o usa. Teste na Task 4.

---

### Task 1: `:feature:settings` multiplataforma (sem mudança no celular)

**Files:**
- Modify: `feature/settings/build.gradle.kts` → `riff.kmp.feature` (+ `riff.kmp.hilt`) + Roborazzi; `commonMain` com `:core:audio`.
- Move para `commonMain`: `SettingsViewModel` (vira `open class SettingsViewModel(repository, separations, languages, ranking, sfxPlayers)`), `SettingsScreen`/`SettingsContent`, `SettingsTabs`, `TrackLookSection`, `AiSeparationSection`, `SettingsRoute`; strings → `composeResources` (conferir `%d` e `\'`; plurais).
- Create `androidMain`: `HiltSettingsViewModel` (`@HiltViewModel`, `@Inject constructor` com os mesmos parâmetros).
- Create `SettingsPlatform.kt` (`commonMain` + actuals):
  - `@Composable expect fun currentAppLanguage(): AppLanguage` — Android: `LocalConfiguration.current.locales[0]` (como hoje); PC: `Locale.getDefault()`.
  - `@Composable expect fun formatFileSize(bytes: Long): String` — Android: `Formatter.formatShortFileSize`; PC: uma casa decimal em KB/MB/GB.
  - `@Composable expect fun rememberLanguageRefresh(): () -> Unit` — Android: o `activity?.recreate()` antes do Android 13 (o código de hoje); PC: nada (o app refaz as telas, Task 3).
- Modify `SettingsContent`: `tabs: List<SettingsTab> = SettingsTab.entries` (a barra e o `when` só mostram essas) e `onLicenses: (() -> Unit)?` (null esconde a linha). `SettingsScreen(onBack, onCalibrate, viewModel: SettingsViewModel, modifier, onRankingName, onLicenses: (() -> Unit)? = {}, tabs = SettingsTab.entries)`.
- Modify: `app/.../RiffNavHost.kt` (passa `hiltViewModel<HiltSettingsViewModel>()`).
- Tests: os de hoje vão para `androidHostTest`; `Fakes.kt` para `commonTest`.

**Interfaces:**
- Produces: `SettingsScreen(...)` e `SettingsViewModel(...)` em `commonMain`; `SettingsTab` (enum de hoje); `SettingsContent(tabs, onLicenses: (() -> Unit)?)`.

- [ ] **Step 1: Linha de base** — `:feature:settings:testDebugUnitTest` (anotar o número de testes) e as screenshots de hoje.
- [ ] **Step 2: Testes novos (RED onde couber)**

```kotlin
@Test fun inEnglishTheTabsReadInEnglish()             // Locale.setDefault(en) + qualifiers "en": a aba "Sounds" existe (base: passa antes e depois)
@Test fun withoutTheAiTabItIsNotShown()                // SettingsContent(tabs = entries - AI): a aba da IA não existe
@Test fun withoutLicensesTheRowIsNotShown()            // SettingsContent(onLicenses = null): a linha das Licenças não existe
```

- [ ] **Step 3: Converter.**
- [ ] **Step 4: Rodar** — `:feature:settings:testAndroidHostTest --rerun :feature:settings:verifyRoborazziAndroidHostTest :feature:settings:compileKotlinDesktop :app:testDebugUnitTest :app:assembleDebug detekt` → os mesmos testes + os novos, screenshots sem regravar.
- [ ] **Step 5: Commit** — `refactor: as Configurações multiplataforma`

### Task 2: Calibração multiplataforma, com teclado no PC

**Files:**
- Move de `:feature:gameplay` para `:core:ui` (`core/ui/src/*/kotlin/com/rodrigomachado/riff/core/ui/input/KeyTiming.kt`): `expect fun KeyEvent.lateByMs(): Long` (o `keyLateByMs` de hoje, com o mesmo limite de 0 a 200 ms no PC) e `@Composable expect fun OnWindowFocusLost(onLost: () -> Unit)`. A partida passa a usá-los (sem mudar comportamento).
- Modify `feature/calibration/build.gradle.kts` → `riff.kmp.feature` (+ `riff.kmp.hilt`) + Roborazzi; `commonMain` com `:core:audio`.
- Move para `commonMain`: `CalibrationSession`, `ClickTrack`, `ClickTimeline`, `ClickTrackPlayer`/`AudioClickTrackPlayer` (o `SystemClock.uptimeMillis()` vira `calibrationClockMs()`), `CalibrationViewModel` (`open class CalibrationViewModel(settings, player)`), `CalibrationScreen`/`CalibrationContent`, `CalibrationRoute`; strings → `composeResources`.
- `androidMain`: `CalibrationModule` (Hilt, como hoje), `HiltCalibrationViewModel`.
- Create `CalibrationPlatform.kt` (`commonMain` + actuals):
  - `expect fun calibrationClockMs(): Long` — Android: `SystemClock.uptimeMillis()`; PC: `System.nanoTime() / 1_000_000`.
  - `expect val tapsByTouch: Boolean` — Android `true` (a `TapArea` com `pointerInput`, como hoje); PC `false`: a área de medição pega o foco ao entrar em `Measuring` e cada tecla apertada (menos Esc; repetição ignorada) chama `onTap(calibrationClockMs() - event.lateByMs())`.
- Textos do PC: `calibration_instructions_keys` = "Aperte qualquer tecla junto com cada batida." e `calibration_measuring_keys` = "Aperte junto com cada batida" (en: "Press any key on each beat." / "Press on each beat"), escolhidos por `tapsByTouch`.
- PC: `OnWindowFocusLost { viewModel.cancel() }` na `CalibrationScreen` (no celular, o `ON_STOP` de hoje).
- Modify: `app/.../RiffNavHost.kt` (`hiltViewModel<HiltCalibrationViewModel>()`).
- Tests: os de hoje para `androidHostTest`; novos em `feature/calibration/src/desktopTest/.../DesktopCalibrationKeysTest.kt` e `core/ui/src/desktopTest/.../KeyTimingTest.kt`.

**Interfaces:**
- Consumes: `AudioSinkFactory` (`:core:audio`), `SettingsRepository`.
- Produces: `CalibrationScreen(onDone, viewModel: CalibrationViewModel, modifier, onSaved = onDone)`; `CalibrationViewModel(settings: SettingsRepository, player: ClickTrackPlayer)`; `AudioClickTrackPlayer(sinks: AudioSinkFactory)`; `KeyEvent.lateByMs()`, `OnWindowFocusLost` em `com.rodrigomachado.riff.core.ui.input`.

- [ ] **Step 1: Testes (RED)**

```kotlin
// DesktopCalibrationKeysTest (CalibrationContent em Measuring, onTap gravando)
@Test fun anyKeyCountsAsATap()                    // keyDown(Key.Spacebar) e keyDown(Key.A) → 2 toques
@Test fun holdingAKeyCountsOnce()                  // dois KeyDown seguidos de A sem KeyUp → 1 toque
@Test fun escapeIsNotATap()                        // Key.Escape → 0 toques
@Test fun losingTheWindowFocusStopsTheMeasuring()  // CalibrationScreen com ViewModel e player falso que suspende; foco perdido → Instructions
// KeyTimingTest
@Test fun aKeyFromTheFutureIsNotLate()             // AWT `when` depois de agora → 0
@Test fun aVeryLateKeyIsCappedAt200Ms()            // `when` 5 s atrás → 200
@Test fun withoutAnAwtEventTheKeyIsOnTime()        // KeyEvent sem evento nativo → 0
```

- [ ] **Step 2: Rodar** → FAIL. **Step 3: Converter e implementar.**
- [ ] **Step 4: Rodar** — `:feature:calibration:testAndroidHostTest --rerun :feature:calibration:desktopTest :feature:calibration:verifyRoborazziAndroidHostTest :core:ui:desktopTest :feature:gameplay:testAndroidHostTest :feature:gameplay:desktopTest :app:assembleDebug detekt` → PASS, screenshots do celular sem regravar.
- [ ] **Step 5: Commit** — `feat: a calibração multiplataforma, com teclado no PC`

### Task 3: Idioma no PC

**Files:**
- Create: `desktop/src/main/kotlin/com/rodrigomachado/riff/desktop/DesktopAppLanguageStore.kt` — `class DesktopAppLanguageStore(file: File) : AppLanguageStore` com `val language: StateFlow<AppLanguage>`; lê o arquivo `language.txt` (a tag) na criação (ausente ou ilegível → Português, o padrão de hoje); `set` grava o arquivo, aplica `Locale.setDefault` e atualiza o `StateFlow`.
- Modify: `DesktopGraph` (`languages = DesktopAppLanguageStore(File(dataDir, "language.txt"))`), `Main.kt` (aplica o idioma salvo em vez do `DesktopLocale.applyDefault()`; remover o `DesktopLocale`), `RiffDesktopApp.kt`: o `NavHostController` fica fora; as telas são refeitas quando `language` muda (ex.: `key(language) { DesktopScreens(graph, navController) }`), mantendo a pilha.
- Test: `desktop/src/test/.../DesktopAppLanguageStoreTest.kt` (a troca na tela, com a pilha intacta, é testada na Task 4, que liga as Configurações).

**Interfaces:**
- Produces: `DesktopGraph.languages: DesktopAppLanguageStore`.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun withoutAFileTheLanguageIsPortuguese()
@Test fun theChosenLanguageIsKeptForTheNextOpening()   // set(ENGLISH) → nova loja no mesmo arquivo lê ENGLISH
@Test fun aBrokenFileFallsBackToPortuguese()           // conteúdo "xx-??" → PORTUGUESE
@Test fun choosingTheLanguageChangesTheDefaultLocale()  // set(ENGLISH) → Locale.getDefault().language == "en"
```

- [ ] **Step 2: Rodar** → FAIL. **Step 3: Implementar.** **Step 4: Rodar** `:desktop:test` → PASS.
- [ ] **Step 5: Commit** — `feat: o idioma do jogo no PC`

### Task 4: Configurações e Calibração no app do PC

**Files:**
- Modify: `DesktopGraph` — `settingsViewModel()` = `SettingsViewModel(settings, NoAiSeparations, languages, NoRanking, sfx)`; `calibrationViewModel()` = `CalibrationViewModel(settings, AudioClickTrackPlayer(audioOut))`.
- Modify: `RiffDesktopApp.kt` — `onSettings` da tela inicial abre `SettingsRoute`; `SettingsScreen(tabs = SettingsTab.entries - SettingsTab.AI, onLicenses = null, onRankingName = {})`; `onCalibrate` abre `CalibrationRoute`; Salvar volta às Configurações em silêncio (como o `silenceNextReturn` do celular, se houver equivalente no PC; senão, anotar).
- Modify (comum): na janela larga, a coluna das Configurações e da Calibração com largura máxima de 640 dp, centrada (`fillMaxWidth().widthIn(max = 640.dp)`); no celular (largura < 640 dp) nada muda.
- Test: `desktop/src/test/.../DesktopSettingsFlowTest.kt`; screenshots do PC `feature/settings/src/desktopTest/.../DesktopSettingsScreenshotTest.kt` (abas Geral e Jogo, 960×540) e `feature/calibration/src/desktopTest/.../DesktopCalibrationScreenshotTest.kt` (Instruções e Resultado), gravadas e conferidas a olho.

**Interfaces:**
- Consumes: Tasks 1–3.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun homeOpensTheSettingsWithoutTheAiTab()          // tela inicial → Configurações: abas Geral, Sons, Jogo, Visual; sem "Guitarra IA", sem Licenças
@Test fun aChangedSettingIsKept()                         // canhoto ligado → settings.leftHanded == true
@Test fun savingTheCalibrationAppliesTheOffset()          // Calibrar com ClickTrackPlayer falso (cliques fixos) e teclas nos cliques + 40 ms → Salvar → settings.audioOffsetMs == -40 e volta às Configurações
@Test fun switchingToEnglishKeepsTheSettingsOpen()        // Geral → Idioma → English: título "Settings"; Voltar → tela inicial
```

- [ ] **Step 2: Rodar** → FAIL. **Step 3: Implementar.** **Step 4:** gravar (`recordRoborazziDesktop`) e olhar as screenshots; celular `verifyRoborazziAndroidHostTest` sem regravar.
- [ ] **Step 5: No Windows** — `./scripts/gw :desktop:run`: o usuário abre as Configurações, troca volumes/velocidade/visual, troca o idioma e volta, calibra com o teclado e joga uma música para conferir a sincronia.
- [ ] **Step 6: Commit** — `feat: no PC, as Configurações e a Calibração`

### Task 5: Fechamento

- [ ] **Step 1:** Suíte completa → BUILD SUCCESSFUL (Linux pelo CI do GitHub se a memória estiver curta; registrar).
- [ ] **Step 2:** README ("Riff no PC": Configurações e calibração) e `docs/pendencias.md` (tirar o item do `keyLateByMs` sem teste, coberto na Task 2). Commit.
