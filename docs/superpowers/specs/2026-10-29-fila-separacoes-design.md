# Riff: fila de separações da guitarra por IA

Data: 2026-10-29 · Status: decisões do usuário tomadas em conversa (2026-10-29); detalhes técnicos decididos por mim
Complementa: `2026-10-13-separacao-guitarra-ia-design.md`

## 1. Intenção

Hoje a separação da guitarra é uma por vez: com uma em andamento, a pergunta "Separar a guitarra com IA?" só diz "Já tem uma separação em andamento". O objetivo é escolher várias músicas e deixar o celular separar todas em sequência (por exemplo, carregando à noite), com o ícone mostrando quantas faltam.

## 2. Decisões do usuário

- **Pergunta + seleção:** com uma separação em andamento, a pergunta troca "Separar" por **"Pôr na fila"**; e a seleção da Biblioteca (a mesma do Excluir) ganha **"Separar (n)"** para pôr várias de uma vez.
- **"Separar só carregando"** nas Configurações, desligado de fábrica. Ligado, a fila espera o carregador.
- **Painel com a lista:** a atual com o progresso e "Cancelar"; as da fila embaixo, cada uma com ✕; "Cancelar todas". O ícone mostra quantas faltam.

## 3. Objetivos e não objetivos

**Objetivos**
- Pôr várias músicas na fila e elas serem separadas uma depois da outra, sem o usuário voltar ao app.
- A fila sobreviver ao Android fechar o app e ao celular reiniciar (como a separação de hoje).
- Ver e mexer na fila pelo ícone da palheta.

**Não objetivos**
- Separar duas ao mesmo tempo (memória e calor).
- Reordenar a fila (a ordem é a de chegada).
- Escolher a ordem por prioridade, ou separar músicas da fila do Spotify sozinho.

**Anti-objetivos**
- Uma falha no meio parar a fila inteira.
- Separar de novo uma música que já tem guitarra separada (do mapa ou da IA).
- Perder uma separação quase pronta por causa de outra que entrou na fila.

## 4. O que o usuário vê

### 4.1 Pergunta "Separar a guitarra com IA?"
- Sem separação em andamento: como hoje ("Sem separar" / "Separar").
- Com separação em andamento (ou fila não vazia): o texto diz **"Já tem uma separando: esta entra na fila."** e os botões são "Sem separar" / **"Pôr na fila"**. "Sem separar" continua não guardando a recusa nesse caso (a pergunta volta da próxima vez).
- A música já na fila não é perguntada de novo (como a que está separando hoje).

### 4.2 Seleção da Biblioteca
- Ao lado de "Excluir (n)", **"Separar (n)"**, onde n conta só as selecionadas que dá para separar: sem guitarra separada do mapa, sem separação da IA, fora da fila. n = 0 desliga o botão.
- Tocar põe essas músicas na fila (na ordem da lista), sai do modo de seleção e pede a permissão de notificação como a pergunta faz hoje.
- Uma música recusada antes ("Sem separar") entra normalmente: escolher na seleção é um pedido explícito.

### 4.3 Ícone e painel
- O ícone aparece com separação em andamento **ou** fila não vazia, com um número quando há mais de uma no total (a atual + a fila).
- Painel:
  - **Separando:** título, porcentagem e minutos restantes (ou "Pausado para esfriar"), botão **Cancelar** (cancela só esta; a fila segue).
  - **Esperando:** sem nenhuma separando e com fila: "Esperando o carregador" (opção ligada e celular fora da tomada) ou "Começando…".
  - **Na fila (n):** uma linha por música com ✕ para tirar.
  - **Cancelar todas** (com fila): cancela a atual e esvazia a fila.
- Falha: a notificação avisa como hoje e a fila segue. **Sem espaço** no celular, as próximas falhariam do mesmo jeito: a fila é esvaziada (decidido na implementação). Se a **última** da fila falhar, o ícone fica com o tom de erro e o painel mostra a falha, como hoje.

### 4.4 Notificação
- Durante a separação: "42%" vira **"42% · mais 2 na fila"** quando há fila.

### 4.5 Configurações
- Na mesma seção da guitarra por IA: **"Separar só carregando"** (interruptor), com a explicação "A fila espera o celular estar na tomada.".
- Mudar a opção com a fila parada esperando refaz o pedido na hora; com a fila já separando, vale na próxima vez que a fila começar (o trabalho que roda esvazia a fila inteira). Com a opção ligada, tirar da tomada interrompe a atual (o WorkManager para o trabalho) e ela recomeça do início quando voltar à tomada.

## 5. Como funciona (técnico)

### 5.1 A fila no banco
- Tabela nova `separation_queue(chartId PK → charts.id ON DELETE CASCADE, title, position, attempts)`, banco versão 12 (migração automática, coberta pelo `MigrationTest`).
- O mapa sair da biblioteca (Excluir, Reimportar sem ele) tira a música da fila pelo cascade.
- Ordem: `position` (o fim da fila + a ordem dada). `attempts` conta as vezes que a música começou: passando de 3 (o trabalho sempre morre nela), a fila desiste dela; uma música já separada (processo morto entre guardar e tirar da fila) é pulada. Uma falha fora da separação (o banco) faz o trabalho tentar de novo com a espera do WorkManager (revisão final).

### 5.2 Um trabalho que esvazia a fila
- O `SeparationWorker` passa a ser um laço: pega a primeira da fila, separa (o `GuitarSeparationRunner` de hoje), tira da fila e segue até a fila acabar. A música só sai da fila depois de terminar (com sucesso ou falha), então um trabalho interrompido (Android fechou o app, saiu da tomada, limite de tempo do WorkManager) recomeça pela mesma música.
- `SeparationScheduler.enqueue(itens)` só grava na fila. O scheduler acompanha a fila e os trabalhos: fila não vazia sem trabalho vivo → pede um (`KEEP`); com um vivo, o laço pega as novas sozinho. A música que entra no instante em que o laço termina é vista quando o trabalho acaba (sem trabalho vivo → pede de novo). Isso também cobre o processo morto entre gravar a fila e pedir o trabalho (decidido na implementação, no lugar de pedir a cada `enqueue`).
- "Separar só carregando" vira `Constraints.setRequiresCharging(true)` no pedido. Mudar a opção com a fila esperando (nada rodando) refaz o pedido com `REPLACE`.
- Sem conseguir virar primeiro plano (app em segundo plano, Android 12+), a separação segue sem a notificação de progresso, como hoje; o limite de 10 minutos de um trabalho comum é o motivo do recomeço acima ser importante.

### 5.3 Cancelar
- **Cancelar (a atual):** um controle em memória (`SeparationQueueControl`, singleton no mesmo processo do trabalho) cancela só a separação em andamento; o laço tira a música da fila e segue.
- **✕ numa da fila:** apaga a linha.
- **Cancelar todas:** esvazia a fila e cancela o trabalho (`cancelUniqueWork`).
- Excluir o mapa que está separando cancela a atual (hoje cancela o trabalho); a fila segue.

### 5.4 Estado para as telas
- `SeparationStatus` continua dizendo o que acontece com a atual (`Idle`, `Running`, `Failed`).
- `SeparationScheduler.queue: StateFlow<List<QueuedSeparation>>` (título e id, sem a atual) e `waitingForCharger: StateFlow<Boolean>` para o painel e o número do ícone.
- `SeparationControls` (a interface que o app dá às telas) ganha a fila, `cancelCurrent`, `remove(chartId)` e `cancelAll`.
- Falha no meio da fila: o runner marca `Failed`, a notificação avisa e o laço começa a próxima (que troca o estado para `Running`). Falha na última fica como `Failed` até o usuário ver, como hoje.

## 6. Riscos

- **Tempo em segundo plano:** sem primeiro plano, o Android pode parar o trabalho; o recomeço pela mesma música perde no máximo uma separação parcial (até ~3 min de trabalho).
- **Tirar da tomada no fim de uma separação:** recomeça do zero. Aceito (a opção é para deixar à noite).
- **Calor:** a pausa para esfriar de hoje vale para cada música; uma fila longa pode demorar bem mais que 3 min por música.

## 7. Testes

- Repositório da fila (Room, Robolectric): ordem, sem duplicar, cascade ao apagar o mapa.
- `MigrationTest`: 11 → 12.
- Laço do trabalho: separa em ordem, tira da fila ao terminar, falha não para a fila, cancelar a atual segue a fila, fila vazia termina.
- Scheduler: `APPEND_OR_REPLACE`, restrição de carregador pela opção, `REPLACE` ao mudar a opção com a fila parada.
- Biblioteca: "Pôr na fila" com uma em andamento; "Separar (n)" conta só as elegíveis e põe na ordem; música na fila não é perguntada.
- Painel e ícone: número, lista com ✕, Cancelar, Cancelar todas, "Esperando o carregador" (testes de tela e screenshot).
- Configurações: o interruptor grava a opção.
- Aceite no celular: pôr 3 músicas pela seleção, tirar uma, cancelar a atual, "Separar só carregando" com o celular fora e dentro da tomada.
