# Riff no Windows — Etapa 4b: Reportar e Licenças no PC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No PC, o envelope da tela inicial abre o Reportar (o mesmo formulário e o mesmo Apps Script do celular), e as Configurações mostram "Licenças e privacidade" com a privacidade do PC e as licenças do que vai no app do PC. O celular fica igual.

**Architecture:** `:feature:report` e `:feature:licenses` viram multiplataforma como a pesquisa (4a): ViewModels `open class` em `commonMain` com um `Hilt…ViewModel` fino no `androidMain`; telas e strings em `commonMain`/`composeResources`; o que é do sistema (informações do aparelho, relógio, lista de bibliotecas, textos das licenças) por interface/`expect`. No PC, o `DesktopGraph` monta o envio (`AppsScriptReportSender` com o mesmo endereço do build), as informações do PC e a fonte das licenças.

**Tech Stack:** Compose Multiplatform 1.12.2, AboutLibraries 15.2.0 (plugin genérico para a lista do PC), `HttpURLConnection` (já usado no celular).

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§3: Reportar e Licenças no Windows; §6 etapa 4; "Endereços do ranking e do Reportar como hoje — fora do git, embutidos no build").

## Decisões (minhas; o usuário pediu para decidir)

1. **Endereço do Reportar:** sai do `BuildConfig` do Android para uma constante gerada em `commonMain` (`ReportConfig.REPORT_URL`, lida do `local.properties` → `report.url`), como o `RankingConfig` do `:core:data`. Celular e PC usam o mesmo script.
2. **Versão do Riff:** passa a morar num lugar só (`gradle.properties` → `riff.versionName=0.5.0`), lida pelo `:app` (versionName) e pelo gerador acima (`ReportConfig.APP_VERSION`, só para o PC; o celular continua lendo do `PackageManager`).
3. **Informações do aparelho no PC:** `DeviceInfo` ganha `system` no lugar de `android` (texto pronto: "Android 14" no celular, "Windows 11 10.0 (amd64)" no PC) e `model` fica opcional (no PC, `null`: o nome do computador é dado pessoal e não ajuda). O JSON enviado mantém o formato do celular (`aparelho.android` com o mesmo valor de hoje, sem o "Android") e, no PC, manda `aparelho.sistema` no lugar de `aparelho.android` e não manda `modelo`. **O script do Reportar não está no repositório:** se ele lista os campos pelo nome, o email do PC pode vir sem o sistema — o teste do usuário (Task 3) confere.
4. **Licenças no PC:** a lista de bibliotecas vem do plugin genérico do AboutLibraries no `:desktop` (`aboutlibraries.json` nos recursos), lida por `Libs.Builder().withJson(...)`. A lista manual do PC traz fontes, sons, Encore e o LWJGL/GLFW (avisos BSD-3 e zlib); Demucs e ONNX Runtime entram na 4c, junto da IA. Os textos (`assets/licencas`) vão para `composeResources/files/licencas` (comuns).
5. **Privacidade no PC:** texto próprio (seus mapas, recordes e configurações ficam no PC; Reportar e Encore iguais; sem o ranking, que chega na 4d — a 4d atualiza o texto).
6. **Teclado:** campos de uma linha saem com ↑/↓ (`upDownLeave`, como o filtro da Biblioteca); no texto do relato (várias linhas), ↑/↓ andam no texto e **Tab / Shift+Tab** saem; a lista de categorias e os itens das Licenças com `focusHighlight` e Enter.
7. **Espera entre relatos:** o relógio do `ReportCooldown` passa a ser `TimeSource.Monotonic` (comum), injetável nos testes.

## Global Constraints

- O celular fica igual: testes e screenshots de `:feature:report` e `:feature:licenses` (`Report*.png`, `Licenses*.png`) sem regravar; o JSON do celular idêntico ao de hoje (teste de igualdade).
- Nunca enviar relato de verdade em teste: transporte falso (`JsonPost`); sem `report.url`, o PC mostra "O envio de relatos não está configurado" como o celular.
- `local.properties` fica fora do git (já está); nenhum endereço no código.
- Navegação sem mouse (3c): `menuKeys` nas duas telas, foco visível, Esc volta (no Reportar, Esc com a lista de categorias aberta só fecha a lista).
- Lições: `composeResources` com `%1$d`/`%1$s` e sem `\'`/`\"`; testes do PC teclam/clicam na thread da janela; screenshots do PC com 1%; detekt autoCorrect bagunça (corrigir à mão); `git grep`; Gradle por `./scripts/gw`, saídas longas em arquivo e limitadas (`| tail -c 300000`); memória curta.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **O celular não muda:** mesmo JSON, mesmas telas, mesma versão no relato; o `aboutlibraries` do `:app` continua gerando a lista do celular.
2. **Privacidade (LGPD):** no PC não vai nome do computador, usuário do Windows nem caminhos de pasta; a pessoa vê na tela exatamente o que vai junto e pode desligar. O texto de privacidade do PC não promete o que o PC não faz.
3. **Envio no PC:** sem internet ou com o script fora do ar, avisa e mantém o texto; a janela não trava (rede fora da thread da UI); o redirecionamento do Apps Script (302 → `script.googleusercontent.com`) é seguido no PC (o `HttpURLConnection` do Java não segue redirecionamento POST→GET entre hosts sozinho do mesmo jeito que o Android — conferir e testar com servidor local).
4. **Licenças completas no PC:** tudo que vai no app do PC aparece (bibliotecas do Gradle + LWJGL/GLFW nativos + fontes + sons), sem as que só existem no celular.
5. **Teclado:** relato escrito e enviado só com o teclado; Esc fecha a lista de categorias antes de sair da tela.

---

### Task 1: `:feature:report` e `:feature:licenses` multiplataforma (sem mudança no celular)

**Files:**
- `gradle.properties` (`riff.versionName`), `app/build.gradle.kts` (lê de lá).
- `feature/report/build.gradle.kts` → `riff.kmp.feature` + Roborazzi; gerador `ReportConfig` (`REPORT_URL`, `APP_VERSION`) em `commonMain`; `org.json` como na pesquisa (ou JSON escrito à mão com teste de igualdade — decisão do implementador, registrada).
- Move para `commonMain`: `BugReport` (com `DeviceInfo(appVersion, system, model: String?, language)`), `CategoryField`, `ReportScreen` (`ReportContent`), `ReportSender` (`AppsScriptReportSender(url, transport)` + `UrlConnectionJsonPost`, o `Log` por função de plataforma), `ReportViewModel` (`open class`, estado salvo por um `SavedStateHandle` comum do lifecycle-viewmodel ou por interface — decisão registrada), `ReportCooldown(now)`; strings → `composeResources` (`@StringRes` → `StringResource`).
- `androidMain`: `AndroidDeviceInfo`, `HiltReportViewModel`, `ReportModule`.
- `feature/licenses/build.gradle.kts` → KMP; `LicensesViewModel` `open class` + `HiltLicensesViewModel`; `LicensesScreen` comum; textos → `composeResources/files/licencas`; `AndroidLicenseSource` lê os textos pelo `Res.readBytes`; a privacidade do celular igual.
- `app/.../RiffNavHost.kt`: `hiltViewModel<Hilt…>()`.
- Tests: os de hoje para `androidHostTest`; o teste do JSON do celular **igual byte a byte** ao de hoje.

- [ ] **Step 1: Linha de base** — `:feature:report:testDebugUnitTest :feature:licenses:testDebugUnitTest` (anotar os números) e as screenshots.
- [ ] **Step 2: Converter.** **Step 3: Rodar** `:feature:report:testAndroidHostTest :feature:licenses:testAndroidHostTest --rerun :feature:report:verifyRoborazziAndroidHostTest :feature:licenses:verifyRoborazziAndroidHostTest :feature:report:compileKotlinDesktop :feature:licenses:compileKotlinDesktop :app:testDebugUnitTest :app:assembleDebug detekt` → os mesmos testes, screenshots sem regravar.
- [ ] **Step 4: Commit** — `refactor: o Reportar e as Licenças multiplataforma`

### Task 2: Reportar e Licenças no app do PC

**Files:**
- `feature/report/src/desktopMain/.../DesktopDeviceInfo.kt`: `system` = `os.name os.version (os.arch)`, `model = null`, `language` = idioma do jogo, `appVersion` = `ReportConfig.APP_VERSION`.
- `feature/licenses/src/desktopMain/.../DesktopLicenseSource.kt` + `desktop/build.gradle.kts` (plugin genérico do AboutLibraries; `aboutlibraries.json` nos recursos do `:desktop`, passado à fonte); lista manual do PC; privacidade do PC (`licenses_privacy_text` por plataforma).
- `desktop/.../DesktopGraph.kt`: `reportViewModel()` (`AppsScriptReportSender(ReportConfig.REPORT_URL, UrlConnectionJsonPost)` — transporte injetável), `licensesViewModel()`; o `ReportCooldown` um só por app.
- `desktop/.../RiffDesktopApp.kt`: rotas `ReportRoute` (envelope da tela inicial) e `LicensesRoute` (Configurações → Licenças); `menuKeys`. `DesktopSettingsFlowTest` deixa de exigir que as Licenças não existam.
- Teclado: `upDownLeave` nos campos de uma linha; Tab/Shift+Tab no texto do relato; `focusHighlight` nas categorias e nos itens das Licenças.
- Tests: `desktop/src/test/.../DesktopReportFlowTest.kt`, `DesktopLicensesFlowTest.kt`; `feature/report/src/desktopTest/.../UrlConnectionJsonPostDesktopTest.kt` (servidor HTTP local com 302 para outro caminho/porta → o corpo final chega e `{"ok":true}` é aceito).

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun theEnvelopeOpensTheReportAndSendsIt()       // inicial → envelope; Problema, categoria, texto; Enviar → transporte falso recebe o JSON com "sistema" e sem "modelo"; tela "Obrigado"
@Test fun withoutAnAddressTheReportSaysItIsNotConfigured()
@Test fun withoutTheInternetTheTextStays()            // transporte lança IOException → "Não deu para enviar"; o texto continua no campo
@Test fun theDeviceLineShowsWhatGoes()                // "Riff 0.5.0 · Windows …" visível; desligar → JSON sem "aparelho"
@Test fun keyboardOnlyReport()                        // só teclado: ↓ do tipo à categoria, Enter abre, ↓/Enter escolhe, Esc fecha só a lista; texto; Tab sai; Enter em Enviar
@Test fun theSettingsOpenTheLicenses()                // Configurações → Licenças e privacidade; privacidade do PC (sem "celular"); "LWJGL" e "Oswald" na lista; Enter abre o texto
@Test fun redirectIsFollowedOnThePc()                 // servidor local 302 → resposta {"ok":true} aceita
```

- [ ] **Step 2–4:** FAIL → implementar → `./scripts/gw :desktop:test :feature:report:desktopTest :feature:licenses:desktopTest detekt` PASS.
- [ ] **Step 5: Commit** — `feat: no PC, o Reportar e as Licenças`

### Task 3: Fechamento

- [ ] **Step 1: No Windows (usuário)** — enviar um relato de teste do PC (com o `report.url` no `local.properties`) e conferir o email (o sistema aparece?); abrir Licenças e privacidade.
- [ ] **Step 2:** Suíte completa → BUILD SUCCESSFUL.
- [ ] **Step 3:** README ("Riff no PC": Reportar e Licenças) e `docs/pendencias.md`. Commit.
