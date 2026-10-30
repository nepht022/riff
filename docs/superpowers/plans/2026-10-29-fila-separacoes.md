# Plano: fila de separações da guitarra por IA

Spec: `docs/superpowers/specs/2026-10-29-fila-separacoes-design.md`. TDD em cada tarefa (teste vermelho pelo motivo certo → código mínimo → verde), suíte completa no fim.

## Tarefa 1 — Fila no banco
- `SeparationQueueEntity(chartId PK, FK charts ON DELETE CASCADE, title, addedAt)` e `SeparationQueueDao` (`observe()` ordenado, `first()`, `insertIgnore(list)`, `delete(chartId)`, `clear()`).
- Banco 11 → 12 (migração automática); `MigrationTest` cobre 11 → 12.
- Testes do DAO: ordem por `addedAt`/`chartId`, sem duplicar, cascade ao apagar o mapa.

## Tarefa 2 — Repositório da fila (core:data)
- `SeparationQueue` (interface em `core:data`): `items: Flow<List<QueuedSeparation>>`, `add(list)`, `next()`, `remove(chartId)`, `clear()`; implementação Room com o relógio injetável.
- `QueuedSeparation(chartId, title)` em `core:model`.

## Tarefa 3 — Controle da atual (core:separation)
- `SeparationQueueControl` (singleton): o laço registra o `Job` da separação atual; `cancelCurrent()` cancela só ele.

## Tarefa 4 — Laço do Worker
- `SeparationQueueLoop(queue, runner, control)`: enquanto houver `next()`, roda `runner.run` dentro de um `Job` filho; ao terminar (sucesso, falha ou cancelamento da atual), `remove(chartId)` e segue. Cancelar o trabalho inteiro (CancellationException do Worker) **não** tira a música da fila.
- `SeparationWorker` usa o laço; notificação de concluída/falha por música; a de progresso ganha "mais n na fila".
- Testes do laço com runner falso: ordem, falha não para, cancelar a atual segue, interrupção mantém a música, fila vazia termina.

## Tarefa 5 — Scheduler
- `SeparationScheduler`: `enqueue(list)`, `cancelCurrent()`, `remove(chartId)`, `cancelAll()`, `queue: StateFlow<List<QueuedSeparation>>` (sem a atual), `waitingForCharger: StateFlow<Boolean>`; sai `start`/`cancel`.
- `enqueueUniqueWork(APPEND_OR_REPLACE)` com `setRequiresCharging` pela opção; mudar a opção com a fila parada refaz com `REPLACE`.
- `reconcile`: trabalho vivo volta a aparecer (como hoje); fila não vazia sem trabalho vivo (processo morto antes de pedir) pede de novo.
- Testes no WorkManager de teste (como `WorkSeparationSchedulerTest`).

## Tarefa 6 — Opção "Separar só carregando"
- `Settings.separateOnlyCharging` (falso), `RiffPreferences`, `SettingsViewModel`, interruptor na seção da IA (pt e en).

## Tarefa 7 — Biblioteca
- Pergunta: ocupado = separando **ou** fila não vazia → texto "Já tem uma separando: esta entra na fila." e botão "Pôr na fila"; música na fila não é perguntada.
- Seleção: "Separar (n)" conta as elegíveis (sem `separateGuitar`, fora de `aiSeparated`, fora da fila e da atual); põe na ordem da lista, sai da seleção, pede a permissão de notificação.
- Excluir o mapa que separa: `cancelCurrent()`.

## Tarefa 8 — Ícone e painel (core:ui)
- `SeparationControls` ganha `queue`, `waitingForCharger`, `cancelCurrent`, `remove`, `cancelAll`.
- Ícone aparece com fila não vazia; número quando o total passa de 1.
- Painel: atual (progresso, Cancelar), "Esperando o carregador"/"Começando…", "Na fila (n)" com ✕, "Cancelar todas". Testes de tela + screenshots novos.

## Tarefa 9 — README, Baseline Profile e pendências
- README (guitarra por IA: fila e opção), pendências (sai "Fila de separações"), Baseline Profile se mudar a abertura.
