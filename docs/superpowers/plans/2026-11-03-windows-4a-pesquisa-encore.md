# Riff no Windows — Etapa 4a: pesquisa no Encore no PC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No PC, a Biblioteca abre a pesquisa no Encore como no celular: pesquisar, filtrar (Originais/Versões, só com guitarra separada), baixar o pacote completo (`.sng`) para "Baixados pelo Riff" na pasta da biblioteca do PC e jogar. O celular fica igual.

**Architecture:** `:feature:search` vira multiplataforma como a Biblioteca: `SearchViewModel` `open class` em `commonMain` (a consulta inicial chega pelo construtor; no celular um `HiltSearchViewModel` lê o `SavedStateHandle`), tela comum, o seletor de pasta pelo mesmo `rememberFolderPicker` da Biblioteca (mover para `:core:ui` ou repetir o `expect` no módulo — decisão do implementador, registrada), o `Log` do Android por uma função de plataforma. No PC, o `DesktopGraph` monta `EncoreClient(UrlConnectionTransport())`, o `ChartDownloader` comum e o acesso a pastas do PC (2a).

**Tech Stack:** Compose Multiplatform 1.12.2, `:core:encore` (já comum), `:core:data` (download e pastas, já comuns).

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§3 "pesquisar e baixar no Encore"; §6 etapa 4). Especificação da pesquisa: `docs/superpowers/specs/2026-10-12-pesquisa-encore-design.md`.

## Divisão da etapa 4 (escolha minha; o usuário pediu para decidir)

- **4a (este plano):** pesquisa no Encore.
- **4b:** Reportar e Licenças no PC.
- **4c:** guitarra separada por IA no PC.
- **4d:** ranking com três tabelas (celular, PC com palheta, PC sem palheta) e o mesmo nome em vários aparelhos — muda também o celular e o Apps Script; **republicar o script é ação do usuário** (parar e pedir).
- **4e:** instalador do Windows — publicar no GitHub Releases **só quando o usuário pedir**.

## Global Constraints

- O celular fica igual: testes e screenshots da pesquisa (`Search_*.png`) sem regravar.
- API do Encore como hoje (não documentada; `api.enchor.us/search`, `files.enchor.us/<md5>.sng`); testes nunca acessam a rede (transporte falso).
- No PC, "Baixados pelo Riff" fica dentro da pasta da biblioteca escolhida na 2a; sem pasta escolhida, a pesquisa pede a pasta como no celular (o seletor do PC).
- Navegação sem mouse (3c): a tela da pesquisa entra com `menuKeys` como as outras; o campo de busca não prende o foco (↑/↓ saem, como o filtro da Biblioteca); foco visível nos resultados e botões.
- Lições: `composeResources` com `%1$d`/`%1$s` e sem `\'`/`\"`; testes do PC teclam/clicam na thread da janela; screenshots do PC com 1%; detekt autoCorrect bagunça (corrigir à mão); `git grep`; Gradle por `./scripts/gw`, saídas longas em arquivo e limitadas (`| tail -c 200000`); memória curta.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **Sem internet ou Encore fora do ar no PC:** a pesquisa avisa como no celular, sem travar a janela (rede fora da thread da UI). Teste na Task 2.
2. **Download interrompido** (fechar a pesquisa no meio, rede caindo): nenhum `.sng` pela metade fica na biblioteca. Coberto pelo downloader comum; conferir no PC (arquivo temporário e troca no fim) na Task 2.
3. **Nome de arquivo inválido no Windows** (título com `: ? * " < > |` ou nome reservado como `CON`): o download grava com um nome válido. Teste na Task 2.
4. **Mapa baixado aparece na Biblioteca do PC sem reimportar à mão** e abre a partida. Teste na Task 2.
5. **Busca digitada só com o teclado** (Enter pesquisa; ↓ sai do campo para os resultados; Esc volta). Teste na Task 2.

---

### Task 1: `:feature:search` multiplataforma (sem mudança no celular)

**Files:**
- Modify `feature/search/build.gradle.kts` → `riff.kmp.feature` + Roborazzi; `commonMain` com `:core:encore`, `:core:chart`; `androidMain` com `activity-compose`.
- Move para `commonMain`: `SearchViewModel` (`open class SearchViewModel(initialQuery: String, encore, downloader, downloads, folderAccess, folders)`), `SearchScreen`, `SearchFilters`, `SearchRoute`; strings → `composeResources`.
- `androidMain`: `HiltSearchViewModel` (lê `query` do `SavedStateHandle`); o `Log` vira função de plataforma.
- Seletor de pasta: reusar o `rememberFolderPicker` da Biblioteca (Ruling sobre onde fica).
- Modify `app/.../RiffNavHost.kt` (`hiltViewModel<HiltSearchViewModel>()`).
- Tests: os de hoje para `androidHostTest`; Fakes para `commonTest`; teste de inglês (base) se faltar.

- [ ] **Step 1: Linha de base** — `:feature:search:testDebugUnitTest` (anotar o número) e as screenshots.
- [ ] **Step 2: Converter.** **Step 3: Rodar** `:feature:search:testAndroidHostTest --rerun :feature:search:verifyRoborazziAndroidHostTest :feature:search:compileKotlinDesktop :app:testDebugUnitTest :app:assembleDebug detekt` → os mesmos testes, screenshots sem regravar.
- [ ] **Step 4: Commit** — `refactor: a pesquisa multiplataforma`

### Task 2: A pesquisa no app do PC

**Files:**
- Modify `desktop/.../DesktopGraph.kt`: `searchViewModel(query)` com `EncoreClient(transport)` (transporte injetável, padrão `UrlConnectionTransport()`), o `ChartDownloader` comum com as pastas do PC, `EncoreDownloads`, `LibraryFolderStore` do PC.
- Modify `desktop/.../RiffDesktopApp.kt`: rota `SearchRoute` (Biblioteca → Pesquisa; "Jogar" de um mapa baixado abre a partida como no celular; Voltar); `menuKeys`.
- Modify (se preciso) o nome de arquivo do download para o Windows (caracteres e nomes reservados) no código comum de `:core:data`, com teste.
- Test: `desktop/src/test/kotlin/com/rodrigomachado/riff/desktop/DesktopSearchFlowTest.kt` com um `HttpTransport` falso (respostas de busca em JSON e um `.sng` de fixture — há fixtures `.sng` nos testes de `:core:chart`/da 2a).

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun searchingAndDownloadingPutsTheChartInTheLibrary()   // Biblioteca → Pesquisar; digitar "teste" + Enter; Baixar o 1º → "Baixados pelo Riff/<arquivo>.sng" existe e o mapa aparece na Biblioteca
@Test fun aDownloadedChartPlays()                             // depois de baixar, Jogar → partida abre (palco)
@Test fun withoutTheInternetTheSearchSaysSo()                 // transporte lança IOException → a mensagem de "sem conexão" do celular aparece; a janela segue respondendo
@Test fun aTitleWithCharactersWindowsForbidsStillDownloads()  // título "AC/DC: Back In Black?" → arquivo com nome válido no Windows (sem : ? / etc.), e nome reservado "CON" vira outro
@Test fun keyboardOnlySearch()                                // só teclado: foco no campo, digitar, Enter pesquisa; ↓ chega ao 1º resultado; Esc volta à Biblioteca
```

- [ ] **Step 2–4:** FAIL → implementar → `./scripts/gw :desktop:test :core:data:desktopTest :feature:search:desktopTest detekt` PASS.
- [ ] **Step 5: No Windows (usuário)** — pesquisar uma música de verdade, baixar (com e sem guitarra separada) e jogar.
- [ ] **Step 6: Commit** — `feat: no PC, a pesquisa no Encore`

### Task 3: Fechamento

- [ ] **Step 1:** Suíte completa → BUILD SUCCESSFUL.
- [ ] **Step 2:** README ("Riff no PC": pesquisa) e `docs/pendencias.md`. Commit.
