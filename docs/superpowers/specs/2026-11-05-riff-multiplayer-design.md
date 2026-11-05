# Riff — multiplayer por código de sala (projeto 3)

Terceiro de três projetos, nesta ordem (decisão do usuário, 2026-10-30): 1. tirar o Spotify (feito, 0.5.0) → 2. Riff no Windows (feito, etapas 1 a 4e) → **3. multiplayer por código de sala**. O usuário pediu, em 2026-11-05, para pular as perguntas e deixar as decisões comigo ("pode cm"); o que vem dele está marcado **(usuário)**, o resto é decisão minha.

## Intenção

Jogar o Riff com amigos ao mesmo tempo, cada um no seu aparelho (celular ou PC), na mesma música, vendo quem está na frente durante a partida. É uma disputa curta entre conhecidos: alguém cria a sala, manda o código, os outros entram, todo mundo toca junto e no fim aparece quem ganhou.

## O que já foi decidido pelo usuário

- Multiplayer **por código de sala**, **até 4** jogadores, **placar ao vivo** — minimizado na partida, com **troca de posições** visível. **(usuário)**
- **Firebase gratuito** como servidor. **(usuário)**
- **Partida aleatória** (entrar com desconhecidos) fica **para depois**. **(usuário)**
- O botão **Sala** da tela inicial (hoje desligado, "em breve") é a entrada. **(usuário, projeto 1)**
- Celular e PC jogam juntos (o porte para Windows existe para isto). **(usuário, 2026-10-30)**

## Objetivos

1. Na tela inicial, **Sala** abre: "Criar sala" e "Entrar com código".
2. Quem cria vira o **dono**: recebe um código de 4 letras, escolhe a música na Biblioteca e começa quando quiser.
3. Quem entra digita o código (no PC também pelo teclado/controle), vê a sala (jogadores, música escolhida) e escolhe a **própria dificuldade**.
4. A partida começa **ao mesmo tempo** em todos os aparelhos (contagem regressiva 3-2-1), com diferença de até ~150 ms entre eles.
5. Durante a partida, um **placar ao vivo minimizado** mostra as posições (nome curto e pontos) e anima a troca quando alguém passa outro.
6. No fim, o **Resultado da sala** mostra a classificação final; o recorde pessoal e o ranking global continuam valendo como numa partida sozinho (na tabela do aparelho: celular, PC com palheta ou sem palheta).
7. Depois do Resultado, todos voltam à sala; o dono escolhe a próxima música.
8. Funciona em celular e PC, misturados, em qualquer rede com internet.

## Não-objetivos

- Partida aleatória/matchmaking, lista de salas públicas, amigos, convites por link (depois).
- Chat, voz, emojis, espectadores.
- Modo banda/cooperativo (instrumentos diferentes), batalha com poderes, star power.
- Jogar sem internet ou só na rede local.
- Contas (o jogador é anônimo; o nome é o do ranking).

## Anti-objetivos (falhou mesmo se "funcionar")

- A rede atrapalhar a partida: nenhum quadro pode esperar a rede; o jogo nunca trava ou engasga por causa do placar ao vivo.
- Perder recordes ou dados locais por causa do multiplayer.
- Expor o banco do Firebase: qualquer pessoa conseguir apagar salas dos outros, escrever lixo à vontade ou estourar a cota gratuita.
- Um jogador sem a música travar a sala inteira.
- Segredos no git (chave e URL do Firebase vão no `local.properties`, como os URLs do ranking e do Reportar).

## Restrições

- Mesmo código comum (KMP) do celular e do PC; telas novas em `commonMain`, com `Hilt…ViewModel` fino no Android e o `DesktopGraph` no PC, como as outras features.
- Firebase no **plano gratuito (Spark)**: 100 conexões simultâneas, 1 GB guardado, 10 GB/mês baixados. O desenho tem de caber com folga para um grupo de amigos.
- Sem SDK do Firebase no app: não há SDK oficial para JVM desktop (só o Admin, que exige chave de serviço). O app usa a **API REST** do Realtime Database (leitura, escrita e o **streaming por SSE**, `Accept: text/event-stream`) e a **API REST do Identity Toolkit** para o login anônimo — os mesmos `HttpURLConnection` já usados no ranking, no celular e no PC.
- Criar o projeto do Firebase, ativar o login anônimo e publicar as regras do banco são **ações do usuário** (como republicar o Apps Script). O repositório leva as regras (`firebase/database.rules.json`) e o passo a passo.
- Testes nunca falam com o Firebase de verdade (servidor falso, como o script falso do ranking).
- Nada é publicado (GitHub, Releases, Play) sem o usuário pedir.

## Como funciona

### Identidade

- No primeiro uso da Sala, o app faz o **login anônimo** (`accounts:signUp` do Identity Toolkit) e guarda o `refreshToken` nas preferências; o `idToken` (1 h) é renovado pelo `securetoken.googleapis.com` quando vence. O `uid` é a identidade do jogador nas salas.
- O **nome** é o do ranking (`PlayerName`). Sem nome, a Sala pede o nome com o mesmo diálogo do ranking antes de criar ou entrar.

### Dados no Realtime Database

```
salas/{CODIGO}
  dono: uid
  criadaEm: timestamp do servidor
  estado: "sala" | "contagem" | "partida" | "fim"
  musica: { chartId, titulo, artista, md5Encore?, duracaoMs }   (ausente até o dono escolher)
  rodada: n                       (sobe a cada partida nova)
  inicio: timestamp do servidor   (quando a música começa nesta rodada)
  jogadores/{uid}: { nome, aparelho: "celular"|"pc", dificuldade, pronto, temMusica, entrouEm }
  placar/{rodada}/{uid}: { pontos, combo, acertos, notas, terminou, saiu }
```

- **Código:** 4 letras de um alfabeto sem ambíguas (sem I, L, O, 0, 1): 24⁴ ≈ 330 mil códigos. Criar tenta um código aleatório com escrita condicional (só se `salas/{CODIGO}` não existe); colidiu, tenta outro.
- **Validade:** sala sem atividade por 2 h é considerada morta; o app que cria uma sala apaga até 5 salas mortas que encontrar (limpeza oportunista, sem servidor próprio). As regras deixam qualquer autenticado apagar só salas com `criadaEm` mais velha que 2 h e sem `estado: "partida"`.

### Regras do banco (`firebase/database.rules.json`)

- Leitura de `salas/{CODIGO}` só para autenticados (o código é o segredo, como numa sala de jogo comum).
- `dono`, `musica`, `estado`, `rodada`, `inicio`: só o dono escreve.
- `jogadores/{uid}` e `placar/*/{uid}`: só o próprio `uid` escreve; no máximo 4 jogadores (a regra conta os filhos ao entrar).
- Validação de tipos e tamanhos (nome até 20 caracteres, os mesmos limites do ranking; números inteiros não negativos).
- Nada fora de `salas/` é legível ou gravável.

### Fluxo

1. **Sala → Criar:** login anônimo (se preciso) → cria a sala → tela da sala com o código grande, "Escolher música" e a lista de jogadores.
2. **Sala → Entrar:** digita o código → entra (`jogadores/{uid}`) → mesma tela, sem os controles do dono. Sala cheia, inexistente ou em partida: mensagem clara.
3. **Escolher música (dono):** abre a Biblioteca em modo de escolha; a música escolhida vai para `musica`. Mapas do Encore levam o `md5Encore` (tabela `encore_charts`).
4. **Cada jogador** confere se tem o `chartId`:
   - tem: `temMusica: true`;
   - não tem, mas veio do Encore: o app oferece **baixar do Encore** (o download que já existe, pelo md5) e marca quando terminar;
   - não tem e não é do Encore: "Você não tem esta música" — o jogador fica na sala sem jogar esta rodada (não trava os outros).
5. Cada um escolhe a **dificuldade** entre as que o mapa tem e marca **Pronto** (a música carregada na memória, pronta para tocar).
6. **Começar (dono):** habilitado quando todos com a música estão prontos (ou depois de 30 s, com quem estiver pronto). O dono grava `estado: "contagem"`, `rodada + 1` e `inicio = agora do servidor + 5 s`.
7. **Relógio comum:** ao entrar na sala, o app mede a diferença entre o seu relógio e o do servidor (grava `{".sv": "timestamp"}` num nó próprio, lê de volta e desconta metade do tempo de ida e volta; repete 3 vezes e fica com a de menor ida e volta). Cada aparelho começa a música no seu relógio local equivalente a `inicio`, mais a calibração dele (o atraso de áudio já usado na partida).
8. **Partida:** a mesma tela de sempre, com o placar ao vivo minimizado; **sem pausa** (Esc/Start e a perda de foco não pausam; uma ligação no celular deixa a música seguir). Sair no meio marca `saiu` e o jogador fica em último.
9. **Placar ao vivo:** cada aparelho grava o próprio placar a cada **1 s** e no fim (`terminou`); lê o dos outros pelo streaming. Com 4 jogadores, ~4 escritas/s na sala — folga grande para o plano gratuito.
10. **Fim:** quando todos terminaram ou saíram (ou 10 s depois do fim da música), cada aparelho mostra o **Resultado da sala** e o dono grava `estado: "fim"`. O recorde pessoal e o ranking global são gravados como numa partida sozinho.
11. **Voltar à sala:** "Próxima música" (dono) ou "Sair da sala". Se o dono sai, o jogador que entrou primeiro vira dono.

### Desconexões

- Sem internet na sala: aviso e nova tentativa automática; na partida, o jogo segue e o placar ao vivo mostra "sem conexão" no lugar dos outros; o próprio placar é enviado quando a conexão volta.
- Jogador que some (sem escrever por 15 s na partida, ou 60 s na sala) aparece apagado e não segura o Começar.

### Telas

- **Sala (escolha):** dois botões grandes, "Criar sala" e "Entrar com código", no estilo pôster da tela inicial.
- **Entrar:** 4 caixas de letra; no PC, teclado e controle (letra a letra com o direcional).
- **Sala:** código grande (com copiar no celular), música escolhida com a capa, lista de até 4 jogadores (nome, aparelho, dificuldade, pronto/sem música/baixando), dificuldade própria, Pronto, e para o dono "Escolher música" e "Começar".
- **Contagem:** a pista já montada, com 3-2-1 grande por cima.
- **Placar ao vivo minimizado:** um bloco pequeno num canto que não cobre a pista (no celular em cima; no PC na coluna do placar, à esquerda), com até 4 linhas "posição · nome · pontos", a própria linha destacada, e as linhas deslizando quando as posições trocam.
- **Resultado da sala:** pódio/classificação com pontos, precisão e maior combo de cada um, e o próprio resultado de sempre (recorde, ranking) abaixo.

### Etapas (cada uma com plano próprio, testável sozinha)

- **M1 — Firebase e a sala:** login anônimo, cliente REST + streaming, regras, criar/entrar/sair, lista de jogadores ao vivo, tela de entrada e da sala (celular e PC). Termina com o usuário criando o projeto Firebase e publicando as regras.
- **M2 — Música e começo juntos:** escolher a música, conferir quem tem, baixar do Encore, dificuldade, Pronto, relógio comum, contagem e partida sem pausa.
- **M3 — Placar ao vivo e Resultado da sala:** placar ao vivo minimizado com troca de posições, fim da rodada, Resultado da sala, próxima música, troca de dono, desconexões.

## Deixado para quem constrói

- A estrutura de módulos (sugestão: `:core:room` com o cliente do Firebase e o repositório; `:feature:room` com as telas), nomes de classes e o formato exato dos eventos SSE tratados.
- O desenho visual dentro da identidade "Pôster de turnê", seguindo as telas existentes.
- Os tempos exatos de nova tentativa e de "sumiu", dentro dos valores acima.
- Como o modo "escolher música" da Biblioteca é passado (parâmetro de rota ou tela própria reaproveitando a lista).

## Testes de aceitação (do usuário)

- Um celular e o PC na mesma sala: criar, entrar pelo código, escolher música, os dois começam juntos (de ouvido, a música soa junta), o placar ao vivo troca de posição, Resultado da sala igual nos dois, próxima música.
- Um jogador sem a música do Encore baixa e joga; sem a música local, fica de fora sem travar.
- Tirar a internet de um durante a partida: o jogo dele segue; o outro vê "sem conexão".
