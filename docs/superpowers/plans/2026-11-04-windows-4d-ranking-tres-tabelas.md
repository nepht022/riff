# Riff no Windows — Etapa 4d: ranking com três tabelas e o mesmo nome em vários aparelhos — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O ranking online chega ao PC e passa a ter **três tabelas** — **Celular**, **PC com palheta** e **PC sem palheta** — no mesmo Apps Script e na mesma planilha. O placar de cada mapa mostra uma tabela por vez, com a troca na tela Ranking. Um jogador pode usar **o mesmo nome em vários aparelhos** (celular e PC, ou dois PCs) por um código de vínculo. Muda também o celular e o script; **republicar o script é ação do usuário**.

**Architecture:** O script ganha a coluna `tabela` nas pontuações e o conceito de **jogador** (um id; vários aparelhos apontam para ele). O app grava, em cada partida, a tabela em que ela conta (celular; no PC, conforme "Tocar com palheta" naquela partida) e manda a tabela junto. `:feature:ranking` vira multiplataforma (como a 4a/4b) e entra no app do PC. O vínculo: no aparelho que já tem o nome, "Usar este nome em outro aparelho" mostra um código; no outro, "Já tenho um nome" + o código.

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§2: ranking no mesmo Apps Script e planilha com três tabelas separadas, uma por vez com a troca na tela Ranking; o mesmo nome em vários aparelhos; dados não passam entre celular e PC).

## Decisões (minhas; o usuário pediu para decidir)

1. **Tabelas no protocolo:** `tabela` = `celular` | `pc_palheta` | `pc_sem_palheta`. Envio sem `tabela` (o app 0.5.0 já instalado) = `celular`. Linhas antigas da planilha (sem a coluna) = `celular`. A posição devolvida no envio é dentro da tabela da partida.
2. **O app antigo não vê o PC:** `placar` e `musicas` sem o pedido novo (`tabelas: true`) devolvem só a tabela `celular`, como hoje; com ele, todas as linhas com a `tabela` de cada uma (o app troca de tabela sem voltar ao script).
3. **Jogador e aparelhos:** a aba Jogadores ganha a coluna `jogador` (o id do jogador; nas linhas de hoje, o próprio hash do aparelho — as pontuações de hoje já estão nesse id, sem migrar nada). As pontuações passam a ser do jogador, não do aparelho: dois aparelhos com o mesmo nome somam o melhor de cada número na mesma linha (por tabela, mapa e dificuldade). Trocar o nome num aparelho troca em todos. `removerJogador` apaga o jogador inteiro e marca todos os aparelhos dele como removidos.
4. **Código de vínculo:** `codigo` (aparelho com nome) → 6 caracteres sem ambíguos (`ABCDEFGHJKMNPQRSTUVWXYZ23456789`), vale 10 minutos, uso único, um ativo por jogador (pedir de novo troca); `vincular` (outro aparelho) com o código → o aparelho passa a ser do jogador. Limites: 5 códigos e 5 tentativas de vínculo por aparelho por hora. Se o aparelho que vincula já tinha um nome próprio, esse nome sai (fica livre) e as pontuações dele saem da planilha; o app reenvia o histórico local com o novo jogador (o reenvio que já existe ao pôr o nome).
5. **Partidas guardam a tabela:** `ScoreEntity`/`RankingPendingEntity` ganham `rankingTable` (banco v14, migração com `DEFAULT 'celular'`, teste de migração). No PC, partida nova grava `pc_palheta` ou `pc_sem_palheta` conforme a partida; partidas do PC de antes da 4d (marcadas `celular` pela migração) contam como `pc_sem_palheta` (o padrão de fábrica do PC) — a regra fica num lugar só (`RankingTable.forThisDevice`). O "é o melhor?" compara só dentro da mesma tabela. O recorde local do Resultado continua um só por mapa e dificuldade.
6. **Tela Ranking:** abre na tabela do aparelho (celular → Celular; PC → a do modo atual "Tocar com palheta"); três abas/chips "Celular · PC com palheta · PC sem palheta"; a lista de músicas mostra as músicas com alguém em qualquer tabela.
7. **Privacidade:** o texto do celular e o do PC passam a dizer que o ranking guarda nome, pontuações e um código aleatório de cada aparelho, e que o vínculo junta aparelhos sob o mesmo nome.
8. **Compatibilidade do app novo com o script antigo** (antes da republicação): `codigo`/`vincular` recusados como `pedido` → a tela diz "O ranking precisa ser atualizado" (sem travar); a troca de tabela mostra só o que vier (tudo como `celular`).

## Global Constraints

- **Republicar o script é ação do usuário:** ao fim da Task 1, PARAR e pedir (Implantar → Gerenciar implantações → editar → Nova versão, mesmo URL). Nunca mexer na planilha de verdade nem chamar o script de produção em testes.
- Testes do script em Node (`node scripts/ranking_apps_script.test.js`), com a planilha falsa que imita as conversões do Google; os testes de hoje continuam passando (app antigo).
- Nenhum endereço no código (`ranking.url` no `local.properties`, como hoje; o `RankingConfig` passa a servir também ao PC).
- Celular muda só no que o plano diz (tabela, troca de tabela, vínculo, privacidade); screenshots do ranking que mudarem são regravadas e conferidas uma a uma; as outras ficam.
- Lições: `composeResources` com `%1$d`/`%1$s` e sem `\'`/`\"`; testes do PC teclam/clicam na thread da janela; screenshots do PC com 1%; detekt sem autoCorrect; `git grep`; Gradle por `./scripts/gw`, saídas limitadas (`| tail -c 300000`); memória curta.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue` e `node scripts/*.test.js`.

## Review Focus

1. **App antigo e planilha de hoje:** nada quebra nem some depois de republicar (linhas sem `tabela`/`jogador`; o 0.5.0 sem `tabelas: true` só vê o celular).
2. **Vínculo seguro:** código curto mas com limite e validade; não dá para tomar o nome de alguém sem o código; o código não fica na planilha (só no cache, com validade); o hash da chave continua o único dado do aparelho.
3. **Tabela certa:** a partida do PC conta na tabela do modo em que foi jogada (não no modo de agora); "é o melhor?" por tabela; o histórico reenviado leva a tabela de cada partida.
4. **Migração v14** do banco no celular e no PC sem perder dados (teste de migração como os de hoje).
5. **PC:** ranking inteiro só com o teclado; sem `ranking.url` o ranking fica desligado como no celular.

---

### Task 1: O script (três tabelas, jogador, vínculo) — e PARAR para republicar

**Files:** `scripts/ranking_apps_script.gs`, `scripts/ranking_apps_script.test.js`, `README.md` (seção do ranking: as abas e colunas novas, o vínculo, `removerJogador`).

- [ ] **Step 1: Testes (RED)** em Node: envio sem `tabela` vai para `celular`; `pc_palheta` separado do `celular` no mesmo mapa; posição dentro da tabela; `placar` sem `tabelas` só celular, com `tabelas` todas com a coluna; linhas antigas (sem colunas novas) como `celular` e jogador = hash; `codigo` + `vincular` junta dois aparelhos (pontuações no mesmo jogador, melhor de cada número); código errado/vencido/usado recusado; limites por hora; vincular com nome próprio libera o nome e apaga as pontuações dele; trocar o nome troca nos dois aparelhos; `removerJogador` apaga o jogador e marca os dois aparelhos; os testes de hoje passando.
- [ ] **Step 2–4:** implementar → `node scripts/ranking_apps_script.test.js` PASS.
- [ ] **Step 5: Commit** — `feat: ranking com três tabelas e o mesmo nome em vários aparelhos (script)`.
- [ ] **Step 6: PARAR — o usuário republica o script** (as Tasks 2–4 podem seguir enquanto isso; o teste real da Task 5 espera a republicação).

### Task 2: `:feature:ranking` multiplataforma (sem mudança no celular) e o `RankingConfig` comum

**Files:** `feature/ranking/build.gradle.kts` → KMP; telas, ViewModels (`open class` + `Hilt…`), strings → `composeResources`; `core/data/build.gradle.kts`: `RankingConfig` em `commonMain` (o PC lê o mesmo `ranking.url`); `app/.../RiffNavHost.kt`.

- [ ] Linha de base → converter → `:feature:ranking:testAndroidHostTest :feature:ranking:verifyRoborazziAndroidHostTest :feature:ranking:compileKotlinDesktop :app:assembleDebug detekt` com os mesmos testes e screenshots sem regravar. Commit `refactor: o ranking multiplataforma`.

### Task 3: Tabela e vínculo no app (celular e comum)

**Files:**
- `core/model`: `RankingTable` (`PHONE`, `PC_STRUM`, `PC_NO_STRUM`, com `wire`); `ScoreRecord.rankingTable`.
- `core/database`: v14 (`ScoreEntity.rankingTable`, `RankingPendingEntity.rankingTable`, migração 13→14, `MigrationTest`).
- `core/data/ranking`: `Submission.table`; `RankingApi` (`tabela` no envio, `tabelas: true` no placar/músicas, `BoardEntry.table`, `linkCode()`, `link(code)`); `RankingRepository` (`improves` por tabela, histórico com a tabela de cada partida, `linkCode`, `link` com reenvio do histórico); `RankingTable.forThisDevice` (decisão 5).
- `core/data/ScoreRepository` + quem grava a partida (`GameSession`/ViewModel do jogo): a tabela da partida.
- `feature/ranking`: troca de tabela na tela do placar (decisão 6), "Usar este nome em outro aparelho" (mostra o código e a validade) e "Já tenho um nome em outro aparelho" (digita o código) na tela do nome; mensagens de erro (código errado/vencido, limite, ranking desatualizado).
- Privacidade do celular (decisão 7).
- Tests: `RankingApiTest`/`RankingRepositoryTest` (tabela no envio, melhor por tabela, histórico, vínculo, script antigo), `MigrationTest` 13→14, `RankingScreensTest` (troca de tabela, código, vínculo), screenshots do ranking regravadas e conferidas.

- [ ] TDD → `./scripts/gw :core:data:testAndroidHostTest :core:data:desktopTest :core:database:testAndroidHostTest :feature:ranking:testAndroidHostTest :feature:gameplay:testAndroidHostTest :app:testDebugUnitTest detekt` PASS → commit `feat: três tabelas no ranking e o mesmo nome em vários aparelhos`.

### Task 4: O ranking no app do PC

**Files:** `desktop/.../DesktopGraph.kt` (`AppsScriptRankingRepository` com o transporte do PC, injetável; `NoRanking` sai se ninguém usar); `RiffDesktopApp.kt` (★ Ranking na tela inicial, rotas do ranking e do nome, "Nome no ranking" nas Configurações, o envio no Resultado); a partida do PC grava `pc_palheta`/`pc_sem_palheta`; privacidade do PC (com o ranking); `menuKeys`/`focusHighlight` nas telas do ranking.
- Tests (`desktop/src/test`): `DesktopRankingFlowTest` com transporte falso: pôr o nome; jogar com e sem palheta → envios com a tabela certa; tela Ranking abre na tabela do modo atual e troca; o placar só com o teclado; vincular por código; sem `ranking.url` → ranking desligado.

- [ ] TDD → `./scripts/gw :desktop:test :feature:ranking:desktopTest detekt` PASS → commit `feat: no PC, o ranking`.

### Task 5: Fechamento

- [ ] **Step 1 (usuário, depois de republicar o script):** no celular (Galaxy S25, APK de debug), conferir que o ranking de hoje continua lá e mostra "Celular"; no PC, pôr o nome pelo código do celular, jogar com e sem palheta e ver as três tabelas.
- [ ] **Step 2:** Suíte completa → BUILD SUCCESSFUL.
- [ ] **Step 3:** README e `docs/pendencias.md`. Commit.
