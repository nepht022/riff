# Regras do banco da sala (Firebase)

`database.rules.json` são as regras do Realtime Database do multiplayer; elas são publicadas à mão no console
(passo a passo em `docs/firebase.md`). Esta pasta guarda as regras e os testes delas no **emulador** do Firebase,
com o projeto `demo-riff` (projetos `demo-*` só existem no emulador: os testes nunca tocam num Firebase de verdade).

## Rodar os testes

Do WSL, na raiz do repositório:

```bash
scripts/testar_regras_sala.sh
```

O emulador do Firebase precisa de Java 11+ e o WSL não tem; o script roda tudo pelo Windows: o Node do Windows
(`C:\Program Files\nodejs`) e o Temurin 21 que o Gradle já baixou
(`C:\Users\Rodrigo Machado\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2`; outro JDK do Windows pela variável
`RIFF_JDK`, com o caminho no formato do WSL). Ele põe o `bin` do JDK no começo do `PATH`, que o WSL traduz para o
processo do Windows, instala os pacotes na primeira vez (`npm ci`) e roda `npm test`, que é:

```
firebase emulators:exec --project demo-riff --only database,auth "node --test rules.test.mjs"
```

No Windows (cmd ou PowerShell), com um Java 11+ no `PATH`: `cd firebase`, `npm ci` e `npm test`.

Os testes (`rules.test.mjs`, `node:test` com `@firebase/rules-unit-testing`) carregam as regras no emulador, gravam o
estado inicial sem regras e conferem cada escrita e leitura como um usuário anônimo (`authenticatedContext(uid)`).

## Formato da sala e por que as regras são assim

```
salas/{CODIGO}                          4 letras de ABCDEFGHJKMNPQRSTUVWXYZ
  dono: uid
  estado: "sala" | "contagem" | "partida" | "fim"
  criadaEm, atividadeEm: tempo do servidor ({".sv": "timestamp"})
  vagas/{v1..v4}: uid
  jogadores/{uid}: { nome (1 a 20), aparelho ("celular" | "pc"), vaga ("v1".."v4"), entrouEm, vistoEm,
                    dificuldade?, pronto?, musica?, carregou?, saiu? }          (os opcionais são da M2)
  musica?: { chartId (40 hex minúsculos), titulo (até 200), artista (até 200),
             dificuldades ("EASY,MEDIUM,HARD,EXPERT", só essas palavras), duracaoMs?,
             encore?: { md5 (32 hex), video (bool), charter? (até 200) } }       (M2; ausente = nenhuma escolhida)
  rodada?: n                            (M2; ausente = 0)
  inicio?: hora do servidor             (M2; só na "partida")
```

- **Vagas.** As regras do Realtime Database não contam filhos (não existe `numChildren()` nas regras; o emulador
  recusa a regra). O limite de 4 jogadores vem de 4 vagas: entrar é uma escrita só (`PATCH salas/{CODIGO}`) com
  `jogadores/{uid}` (com `vaga: "vN"`) e `vagas/vN: uid`. Um jogador sem a própria vaga é recusado e uma vaga guarda um
  uid só, então nunca há mais de 4. A vaga pode ser ocupada se está livre, se já é sua, ou se foi abandonada (o uid
  dela não aponta mais para ela: saiu sem liberar ou foi removido pelo dono). Liberar uma vaga só vale para uma vaga
  abandonada, por exemplo na mesma escrita em que o jogador sai (`jogadores/{uid}: null` e `vagas/vN: null`). Dois
  entrando juntos na mesma vaga: o banco aplica um de cada vez, o segundo é recusado (o app relê a sala e tenta outra
  vaga; sem vaga, "Sala cheia").
- **Criar:** só se a sala não existe; quem cria é o `dono`, `estado` é `"sala"`, `criadaEm` e `atividadeEm` são o
  tempo do servidor, e o dono está em `jogadores` ocupando uma vaga (as vagas garantem que é o único).
- **Apagar a sala:** o dono, ou qualquer autenticado se `atividadeEm` tem mais de 2 h.
- **`dono`:** o dono passa para outro jogador quando quiser; um jogador da sala assume quando o `vistoEm` do dono tem
  mais de 60 s ou o dono não está mais em `jogadores`. O novo dono tem de ser um jogador da sala.
- **`estado`, `criadaEm`:** só o dono (`criadaEm` sempre com o tempo do servidor).
- **`atividadeEm`:** qualquer jogador da sala (inclusive quem entra na mesma escrita), sempre com o tempo do servidor.
- **`jogadores/{uid}`:** só o próprio uid escreve, numa sala que existe; o dono pode remover qualquer jogador.
  `entrouEm`/`vistoEm` com o tempo do servidor (ou o mesmo valor de antes, para entrar de novo mantendo o
  `entrouEm`). Campos fora da lista são recusados, na sala e no jogador.
- **`musica` (M2):** só o dono e só com `estado: "sala"`; o dono também pode apagá-la. Campos fora da lista
  recusados.
- **`rodada` (M2):** só o dono, só sobe de 1 em 1 (a primeira é 1) e só na mesma escrita em que o `estado` é
  `"contagem"`; nunca é apagada. E o contrário: entrar em `"contagem"` vindo de outro estado exige subir a rodada na
  mesma escrita (o `.validate` do `estado` vê o `newData` da sala inteira, então o PATCH
  `{estado: "contagem", rodada: n+1}` é conferido junto). Repetir `"contagem"` sem subir vale (o app tentando de novo).
- **`inicio` (M2):** só o dono, hora do servidor entre `now` e `now + 20000`, e só com `estado: "partida"`;
  `"partida"` só vem depois da `"contagem"` e sempre com o `inicio`; `"sala"` e `"contagem"` exigem o `inicio`
  apagado na mesma escrita (`inicio: null`).
- **Jogador (M2):** `dificuldade` (`EASY|MEDIUM|HARD|EXPERT`), `pronto` (bool), `musica` (`tem|baixando|sem`),
  `carregou` e `saiu` iguais à `rodada` da sala depois da escrita. Só o próprio jogador grava (nem o dono).
- **Leitura:** `salas/{CODIGO}` para qualquer autenticado; nada mais, nem a lista de salas (o código é o segredo).

### Limitações

- **Sem listar salas mortas.** Uma leitura de `salas` só das mortas precisaria de uma regra como
  `query.orderByChild == 'atividadeEm' && query.endAt <= now - 7200000 && query.limitToFirst <= 5`, mas o emulador
  recusa qualquer `<`, `<=` ou conta sobre `query.endAt`/`query.startAt` (só a igualdade funciona), então ela não pode
  ser testada; e uma listagem sem esse limite mostraria códigos de salas vivas. A limpeza fica no momento de criar:
  se o código sorteado já existe (`412`), o app lê a sala e, se ela está morta, apaga e cria de novo com o mesmo código.
- **Quantidade de salas.** As regras não limitam quantas salas um uid cria (não há contador por usuário sem servidor);
  o login anônimo e o plano Spark (1 GB, 100 conexões) seguram o abuso.
- **Dono novo e começo na mesma escrita.** As escritas do dono (`estado`, `rodada`, `inicio`, `musica`) conferem o
  dono de antes da escrita: quem assume o dono (`{dono: uid}`) grava a rodada ou o começo numa escrita seguinte.
- **`carregou`/`saiu` não conferem o estado:** valem em qualquer estado, desde que sejam a rodada atual.
- **`inicio` confere a hora do servidor na escrita,** não a do relógio de quem lê; o app converte pelo offset medido.
- **Vaga abandonada.** Quem sai sem liberar a vaga (o app sempre libera) deixa a vaga abandonada; ela continua
  contando como ocupada na leitura até alguém ocupá-la, então o app trata uma vaga cujo uid não está em `jogadores`
  (ou aponta para outra vaga) como livre.
