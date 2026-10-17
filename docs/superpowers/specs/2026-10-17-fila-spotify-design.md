# Riff: mapas da fila do Spotify baixados antes da hora

Data: 2026-10-17 · Status: aprovado em conversa, aguardando revisão da especificação escrita (dúvidas do revisor respondidas em 2026-10-17)
Complementa: `2026-10-12-pesquisa-encore-design.md` (pesquisa e download do Encore) e os controles do Spotify com começo automático (mergeados em 2026-10-17)

## 1. Intenção

No modo Spotify, o usuário ouve música e, quando quer, joga a que está tocando. Hoje a música só tem mapa se ele já estiver na biblioteca; senão é preciso pesquisar no Encore e baixar na hora. O objetivo é o modo Spotify "andar junto" com o Spotify: enquanto uma música toca, o Riff já prepara os mapas das **duas próximas da fila**. Assim, ao passar de música, o mapa já está na biblioteca e casado com a faixa, e a partida pode começar direto (inclusive pelo começo automático da pausa).

Sucesso, nas palavras do usuário: "quando ele passasse a música, já estivesse pronto".

## 2. Decisões do usuário

- **Opção nas Configurações** para ligar o recurso ("aceitar sugestões de mapa para o Spotify").
- **Ler a fila do Spotify**, mas só as **duas próximas músicas** depois da atual, não a fila toda.
- **O "melhor mapa possível", original prioritariamente**: o Riff escolhe sozinho, preferindo a versão original (não cover, remix, ao vivo).
- **Opção para apagar automaticamente** os mapas baixados assim: apagados **quando a música passa sem o usuário jogar**.
- Aceitou as suposições apresentadas: recurso desligado de fábrica; não baixar nada quando nenhum resultado for confiável; mapa baixado entra em "Baixados pelo Riff" já casado com a faixa ("mapa encontrado"); o Spotify pede uma autorização a mais na primeira vez; baixar só no Wi-Fi, com uma terceira opção para permitir dados móveis.

## 3. Objetivos e não objetivos

**Objetivos**
- Ter, sem o usuário pedir, o mapa das duas próximas músicas da fila do Spotify antes de elas começarem.
- Escolher bem: só mapas que realmente são da música, originais primeiro.
- Não acumular lixo: o que foi baixado sozinho e não foi jogado some (com a opção ligada).

**Não objetivos**
- A fila inteira, ou playlists e álbuns além das duas próximas.
- Funcionar com o Riff fechado ou em segundo plano (veja §6, decisão minha).
- Escolher entre vários mapas candidatos na tela (a pesquisa manual continua existindo para isso).
- Mudar o modo Biblioteca.

**Anti-objetivos (seria fracasso mesmo funcionando)**
- Baixar o mapa de outra música (um casamento errado é pior que nenhum).
- Gastar o plano de dados do usuário sem ele permitir.
- Apagar um mapa que o usuário baixou na mão ou que já jogou.
- Atrapalhar a partida (travar, pausar, deixar a pista lenta).

## 4. O que o usuário vê

### Configurações

Uma aba nova **Spotify** depois de "Guitarra IA" (a barra de abas já rola de lado), com:

- **Baixar mapas da fila** (desligado de fábrica). Dica: "Enquanto você ouve, o Riff baixa do Encore os mapas das duas próximas músicas da fila do Spotify."
- **Apagar os que eu não joguei** (ligado de fábrica). Dica: "Quando a música passa sem você jogar, o mapa baixado sozinho é apagado do celular." Só vale para os baixados pela fila.
- **Usar dados móveis** (desligado de fábrica). Dica: "Desligado, só baixa no Wi-Fi. Cada mapa tem dezenas de MB."
- **Músicas com mapas já baixados** (escolha entre duas, pedida pelo usuário): quando a próxima música já tem um mapa na biblioteca só como sugestão ("CONFIRMAR MAPA"):
  - **Baixar nova sugestão** (*padrão, decisão minha*: o objetivo é a música chegar pronta para jogar): o Riff procura no Encore um mapa com semelhança alta (§5) e, se achar, baixa e casa com a faixa. O mapa sugerido continua na biblioteca; só o casamento da faixa passa a ser o novo.
  - **Manter o que já tenho**: não baixa nada para essa música; ela continua pedindo confirmação.

As outras três ficam desabilitadas (cinza) com "Baixar mapas da fila" desligada.

### Autorização

Ao ligar "Baixar mapas da fila" pela primeira vez, o Riff abre a tela de autorização do Spotify pedindo para ler o estado do player (a fila). Se o usuário recusar ou cancelar, a opção volta a desligada e aparece: "Sem a autorização do Spotify, o Riff não consegue ver a fila." Com a autorização dada, não pergunta de novo.

### Tocando agora

Nada novo na tela. Quando a música seguinte começa e o mapa dela já foi baixado, ela aparece direto como **MAPA ENCONTRADO** e o botão **Jogar** está pronto. Na pausa de uma partida, ⏭ começa a próxima partida direto (o começo automático que já existe).

### Biblioteca

Os mapas baixados pela fila aparecem como os outros, em "Baixados pelo Riff". Se o usuário jogar um deles, ele vira um mapa normal e nunca é apagado pela fila.

## 5. Escolha do "melhor mapa"

Para cada música da janela (as duas próximas) que ainda não tem mapa na biblioteca (casamento automático ou salvo). Música com mapa só **sugerido** segue a opção "Músicas com mapas já baixados" (§4): com "Baixar nova sugestão" entra na busca; com "Manter o que já tenho" é pulada.

1. Pesquisa no Encore por "artista título" (primeira página, a mesma API da pesquisa manual).
2. Fica só com os resultados que têm guitarra e cuja nota de semelhança com a faixa (`TrackMatcher.score`: título, artista e duração) é **pelo menos `AUTOMATIC_THRESHOLD` (0,92)**, o mesmo limiar do "mapa encontrado" automático. Abaixo disso, não baixa nada.
3. Entre esses, **originais primeiro** (`VersionTitles.isVersion` falso). Só se não houver original, aceita uma versão.
4. Desempate: maior nota de semelhança; depois guitarra separada (olhando o começo do pacote, como a pesquisa faz, no máximo 3 olhadas); depois mais dificuldades de guitarra.
5. Baixa com o `ChartDownloader` existente, casando com a faixa (`matchTrackUri`), que já salva o casamento.

Uma faixa já tentada sem resultado não é pesquisada de novo enquanto o Riff estiver aberto (evita repetir a busca a cada leitura da fila).

## 6. Funcionamento

- **Quando roda:** com o Riff aberto e conectado ao Spotify, em qualquer tela (Tocando agora, Biblioteca, Configurações...). O recurso nunca inicia a conexão com o Spotify sozinho; usa a que o Tocando agora abre. *Decisão minha:* funcionar com o Riff em segundo plano exigiria um serviço sempre ligado, que o Android limita e que gasta bateria; fica para depois, se fizer falta. Uma música "passa" (§7) só conta enquanto o Riff está conectado e vendo a troca.
- **Quando lê a fila:** sempre que a música atual muda (o Riff já recebe isso do Spotify) e, enquanto a mesma música toca, a cada 60 s (a fila pode mudar sem a música mudar).
- **Janela:** as duas primeiras faixas de música da fila depois da atual (episódios de podcast são ignorados). A música atual não entra: só as duas próximas, como pedido.
- **Uma de cada vez:** os downloads da fila são feitos em sequência, e no máximo 3 mapas da fila ainda não jogados e ainda apagáveis existem ao mesmo tempo (os da janela mais o da música atual). Com "Apagar os que eu não joguei" desligada, o limite não se aplica (os mapas viram mapas normais, §7).
- **Durante a partida:** nenhum download novo começa com uma partida em andamento (para não disputar rede, disco e CPU com a pista a 120 fps). Os pendentes esperam a partida acabar.
- **Rede:** sem "Usar dados móveis", só baixa em rede sem cobrança por uso (Wi-Fi). Sem rede adequada, espera.

## 7. Apagar os que não foram jogados

Com "Apagar os que eu não joguei" ligada, um mapa baixado pela fila é apagado do celular (pasta ou `.sng`, a linha na biblioteca e o casamento com a faixa, pelo `ChartDeleter` existente) quando:

- a música dele **tocou no Spotify e passou** para outra **sem uma partida ter começado com esse mapa**; ou
- *decisão minha:* ele **saiu da janela sem tocar** (o usuário mudou a fila, trocou de playlist ou pulou várias) e também não é a música atual.

Um mapa é marcado como **jogado** quando uma partida começa com ele (qualquer modo). Jogado, ele deixa de ser "da fila" para sempre.

Ao abrir o Riff, mapas da fila não jogados que sobraram de uma vez anterior (app fechado no meio) e que não estão na janela atual também são apagados.

Com a opção desligada, nada é apagado: os mapas baixados enquanto ela estava desligada viram mapas normais **para sempre** (*decisão minha*), como os baixados na mão; ligar a opção depois só vale para os próximos downloads.

Desligar "Baixar mapas da fila" para de ler a fila e de baixar. Os mapas da fila ainda não jogados seguem a regra acima: com "Apagar os que eu não joguei" ligada, são apagados na próxima abertura do Riff, como sobras (*decisão minha*).

## 8. Dados

- Room: tabela nova `queue_downloads(chartId PK, trackUri, downloadedAt, played)`. Migração nova.
- Preferências (DataStore): `queue_prefetch` (falso), `queue_delete_unplayed` (verdadeiro), `queue_use_mobile_data` (falso), `queue_replace_suggested` (verdadeiro = "Baixar nova sugestão").
- A linha em `queue_downloads` é gravada no download só se a opção de apagar estiver ligada naquele momento; sem linha, o mapa é normal.
- Autorização da API Web do Spotify: ver §9.

## 9. Spotify: autorização e API Web

- Escopo novo: `user-read-playback-state` (lê a fila em `GET https://api.spotify.com/v1/me/player/queue`).
- Fluxo: **Authorization Code com PKCE** (sem segredo no app), pela biblioteca de autorização do Spotify que o app já usa, trocando o código pelo token em `https://accounts.spotify.com/api/token`. Se essa biblioteca não fizer o PKCE, o fluxo é feito à mão (navegador do Android com Custom Tabs e o redirect `riff://callback` já registrado), também sem segredo (*decisão minha*). O token de acesso dura 1 h e é renovado com o *refresh token*, sem abrir telas.
- **Autorização perdida** (renovação recusada, acesso revogado no Spotify): o Riff **nunca abre a tela de autorização sozinho** (muito menos no meio de uma partida). A opção "Baixar mapas da fila" volta a desligada e as Configurações mostram: "O Spotify pediu para autorizar de novo. Ligue a opção para autorizar." (*decisão minha*).
- Segurança: o *refresh token* é um segredo do usuário. Fica guardado criptografado com a chave do Android Keystore (não em texto puro nas preferências), nunca vai para o log, e é apagado se a renovação falhar com "token inválido" (aí a próxima vez pede autorização de novo).
- O Client ID continua só no `local.properties`. O app no painel do Spotify já está em modo de desenvolvimento; a API Web segue a mesma lista de usuários.
- Falhas da API Web (sem rede, 401, 429, 5xx) não aparecem na tela: o recurso só deixa de baixar naquele momento e tenta na próxima leitura. 401 tenta renovar o token uma vez; 429 respeita o `Retry-After`.

## 10. Erros e casos de borda

- Sem pasta de biblioteca com permissão de escrita: o recurso não baixa nada (o mesmo aviso da pesquisa aparece nas Configurações sob a opção).
- Espaço livre baixo (menos de 200 MB): não baixa.
- Mapa baixado que falha na importação: o provisório é apagado (como no download manual) e a faixa conta como tentada.
- A música começa antes de o download terminar: o download continua; quando termina, o Tocando agora mostra "mapa encontrado" (o casamento já é salvo pelo downloader).
- O usuário baixa na mão um mapa para uma faixa que estava na janela: o da fila (se houver) segue a regra normal; o manual nunca é apagado.
- Faixa local do Spotify ou sem artista: ignorada.

## 11. Testes

- Escolha do melhor mapa: originais primeiro, limiar 0,92, desempates, nada abaixo do limiar, versão só sem original.
- Janela da fila: duas próximas, episódios ignorados, já com mapa pulada, já tentada não repete.
- Regras de apagar: passou sem jogar, saiu da janela, jogado nunca, opção desligada, sobra ao abrir.
- Partida em andamento segura os downloads; Wi-Fi × dados móveis.
- API Web: leitura da fila, renovação do token, 401/429, token guardado criptografado.
- Configurações: as quatro opções, autorização recusada ou perdida volta a desligada com o aviso, dicas.
- Músicas com mapas já baixados: "Baixar nova sugestão" baixa e troca o casamento (o sugerido fica na biblioteca); "Manter o que já tenho" pula.
- No celular: fila com músicas sem mapa, ver o mapa da próxima chegar antes de passar, passar sem jogar e ver o mapa sumir, jogar um e ver que fica.

## 12. O que fica para o construtor

- Onde ficam as peças (provavelmente `:core:spotify` para a API Web e a autorização, `:core:data` para a fila e as regras, Configurações na aba nova), nomes e organização em arquivos.
- Como a tela de partida informa "começou partida com o mapa X" para a marca de jogado.
- O texto exato das mensagens que não estão citadas aqui, seguindo o tom das telas atuais.
