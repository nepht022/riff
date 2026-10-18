# Spotify pela sessão de mídia do Android — design

Data: 2026-10-18 · Status: aguardando aprovação do dono

## Por que

O modo Spotify ("Tocando agora") usa hoje o **Spotify App Remote SDK** e a **API web do Spotify**, que lê a fila com login PKCE. Enquanto o app do Riff estiver em modo de desenvolvimento no Spotify, as duas coisas só funcionam para contas que o dono cadastrou à mão em "User Management" no Dashboard, e são poucas. O Spotify também exige Premium para o App Remote e restringe jogos na política de desenvolvedor. O dono quer mandar o APK para amigos sem cadastrar ninguém.

O Android expõe a **sessão de mídia** de qualquer player para apps com "Acesso às notificações". O teste no Galaxy S25 do dono, em 2026-10-18 (`dumpsys media_session` e um app descartável), mostrou o seguinte:

- **Música atual:** a sessão do Spotify entrega `MEDIA_ID = spotify:track:<id>`, que é o mesmo identificador usado hoje. Entrega também título, artista, álbum, `DURATION`, capa e o campo `ADVERTISEMENT`.
- **Estado:** `PlaybackState` traz estado, posição, velocidade, `lastPositionUpdateTime` (no relógio `elapsedRealtime`) e o item ativo da fila. As ações anunciadas incluem play/pause, próxima, anterior, `SEEK_TO` e `PLAY_FROM_URI`.
- **Fila:** `getQueue()` trouxe 39 itens, cada um com título, artista e `mediaId = spotify:track:<id>`, mas **sem duração**.
- **Precisão:** o Spotify só publica estado nas mudanças. Medi 5 ciclos de tocar e pausar, com 5 a 40 s tocando. A posição extrapolada (`position + (agora − updated) × speed`) ficou cerca de **340 ms à frente** da posição informada na pausa seguinte, com variação de ±25 ms e **sem deriva** com o tempo tocando. No começo de uma música (posição perto de 0), a diferença foi de 192 ms.

## Objetivo

Trocar a integração por inteiro: o Riff acompanha e controla o Spotify só pela sessão de mídia do Android. **O SDK, o login do Spotify, a API web da fila e o Client ID saem do app, inclusive para o dono.** Não há fase com os dois modos lado a lado (decisão do dono). Toda função atual do modo Spotify continua funcionando.

## Não-objetivos

- **Outros players:** YouTube Music, Deezer e outros ficam de fora por enquanto (decisão do dono: "só Spotify por enquanto"). O código deve deixar fácil acrescentá-los depois; o filtro por pacote fica num lugar só (decisão minha).
- **Mudanças de tela além do necessário:** nada de redesenho do Tocando agora, da pausa ou das Configurações. Muda só o que depende de permissão e autorização.
- **Tirar ou mudar o modo Biblioteca:** fica como está.

## Anti-objetivos (seria falha mesmo funcionando)

- Perder os mapas que o dono já escolheu para cada música, os ajustes de sincronia salvos ou os registros da fila.
- A partida ficar visivelmente mais dessincronizada que hoje no Spotify Premium.
- Pedir ao jogador qualquer cadastro, login ou Client ID.
- Ler ou guardar notificações de outros apps. A permissão serve só para ler a sessão de mídia, e o app não lê o conteúdo das notificações.

## Como a pessoa usa

### Primeira vez (decisão do dono, com o acréscimo dele)

- Sem a permissão, o **Tocando agora** mostra um aviso no lugar da música: "Para jogar com a música do Spotify, o Riff precisa ver o que está tocando".
- O aviso tem o botão **Permitir**, que abre `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
- O aviso tem também a saída **"Se preferir, jogue no modo Biblioteca"**, que abre a Biblioteca (pedido do dono).
- Ao voltar com a permissão ligada, o Tocando agora passa a mostrar a música, sem reiniciar o app.

### Outros estados do Tocando agora

Eles substituem os erros atuais de conexão (`SpotifyError`):

| Situação | O que aparece |
|---|---|
| Spotify não instalado | Aviso + saída para a Biblioteca |
| Spotify instalado sem sessão ativa (nada tocou ainda) | "Abra o Spotify e dê play numa música", com um botão que abre o Spotify |
| Anúncio tocando (`ADVERTISEMENT = 1`) | "Anúncio no Spotify: a partida começa quando ele acabar" |

### Contas grátis (decisão do dono)

Podem jogar. Quando entra um anúncio, o Riff espera:
- **No modo contínuo:** a próxima partida só começa depois do anúncio.
- **Anúncio no meio da partida:** a partida pausa sozinha e continua quando a música voltar. É raro, porque o Spotify grátis põe anúncios entre as músicas.

Limitações do Spotify grátis (modo aleatório, poucos pulos, talvez sem avançar o tempo) podem impedir ⏭, ⏮ e "Jogar de novo". Quando um comando não tem efeito, o app não trava: segue o estado que o Spotify informar.

## O que continua igual

Todas as funções do modo Spotify. Identificar a música pelo mesmo `trackUri` mantém o banco como está, sem migração:
- `track_matches.spotifyTrackUri`;
- os ajustes de sincronia por música;
- `queue_downloads.trackUri`.

Continuam, entre outras: o casamento de mapa, a sugestão por duração, os motivos de "sem mapa", ⏮ ⏯ ⏭ no Tocando agora e na pausa, o modo contínuo ("Ao acabar a música"), o painel "Confirmar mapa e jogar", a barra de progresso, o alinhamento automático pelo começo da música, o ajuste por música na pausa, o ranking, a pesquisa no Encore e a calibração.

## Como (decidido)

### Leitura e controle

- **Serviço de notificações:** um `NotificationListenerService` mínimo, que existe só para liberar `MediaSessionManager.getActiveSessions`. Ele não lê notificações (anti-objetivo).
- **Sessão acompanhada:** o `MediaController` do pacote `com.spotify.music`, escutado por `addOnActiveSessionsChangedListener` e `MediaController.Callback` (metadados, estado, fila).
- **Estado do player:** o `PlaybackState` do modelo recebe:
  - `trackUri` = `MEDIA_ID` (ignorar o que não começar com `spotify:track:`, como episódios e anúncios, igual ao filtro de "faixa de música" de hoje);
  - título, artista e `durationMs` (`DURATION`);
  - `positionMs` e `isPaused`, a partir do estado (`PLAYING` = tocando; `PAUSED`, `BUFFERING` e `STOPPED` contam como não tocando para a partida);
  - `capturedAtElapsedMs` = `lastPositionUpdateTime`.

  A extrapolação da posição continua onde já está (`SongClock` / `SpotifySongPlayer`).
- **Comandos (`transportControls`):**

  | Comando | Na sessão de mídia |
  |---|---|
  | `pause` / `resume` | `pause()` / `play()` |
  | `seekTo` | `seekTo()` |
  | `skipNext` / `skipPrevious` | `skipToNext()` / `skipToPrevious()` |
  | `play(trackUri)` (tocar do começo) | Se a faixa já é a atual, `seekTo(0)` + `play()`. Se for a anterior, ver "Jogar de novo" abaixo. **Não usar `playFromUri`**: ele pode trocar a playlist por uma faixa solta e quebrar o modo contínuo e a fila |

- **"Jogar de novo" no modo Spotify (decisão do dono).** É o único caminho em que a partida pede uma faixa que já não é a atual: no Resultado, o Spotify já passou para a seguinte. O Riff volta com ⏮, que nos primeiros segundos vai para a anterior e mantém a playlist e a fila: `skipToPrevious()`, e se preciso de novo até a faixa atual ser a pedida, no máximo 2 vezes. Se a faixa pedida não virar a atual em cerca de 3 s (conta grátis, ou a faixa não era a anterior), a partida não começa sozinha e aparece "Volte a música no Spotify para jogar de novo". O `resumeAfterReconnecting` de hoje (`play(uri)` + `seekTo`) fica sem uso, porque a sessão não "desconecta" como o SDK. Ele vira só reler a sessão e, se a faixa for a mesma, `seekTo`.
- **Interface:** a interface `SpotifyRemote` continua sendo a fronteira, para o resto do app mudar o mínimo. A implementação nova substitui `AppRemoteSpotify`. `connection` passa a refletir permissão, instalação e sessão, no lugar da conexão do SDK. `connect()` e `refresh()` passam a reler a sessão.

### Sincronia

- **Correção fixa:** uma correção constante própria da sessão de mídia, de cerca de **340 ms**, ajustada depois de jogar no aparelho. Ela se soma às correções que já existem e substitui as do SDK que deixarem de valer:
  - "depois de tocar do começo ainda manda a posição antiga";
  - "avisa tocando antes de o áudio andar";
  - "~280 ms tocando depois de pausar".
- **Revalidação:** cada uma dessas correções do SDK precisa ser revalidada com registro temporário (`println("RiffSync …")` + `adb logcat`), como já fizemos antes. As que não valerem para a sessão de mídia saem.
- **Medida contra o SDK antes de removê-lo (decisão do dono).** Os ajustes de sincronia salvos foram feitos com o SDK, que carimbava o tempo na chegada do aviso (`elapsedRealtime()` no callback), não no `lastPositionUpdateTime`. Por isso, a primeira etapa do plano roda os dois lados a lado, uma vez, no S25: o SDK já está autorizado com o SHA-1 novo. São registros temporários das duas posições no mesmo instante: tocar do começo, pausar, retomar, avançar e próxima. A correção da sessão é escolhida para reproduzir a posição do SDK, o que mantém valendo os ajustes salvos por música. Só depois dessa medida o SDK sai. O ±50 ms do critério 4 é medido contra a posição do SDK nessa etapa.
- **Critério de aceite:** jogar no S25 com 3 músicas Premium (uma do começo, uma com pausa e retomada, uma pulando na pausa), com sincronia percebida igual à de hoje e sem salto de pista na retomada.

### Fila

- **Origem:** `SpotifyQueue` passa a ler `getQueue()` da sessão. As próximas são os itens depois do item ativo (`activeQueueItemId`).
- **Sem duração:** `QueuedTrack.durationMs` vira opcional. Sem a duração, o download antecipado procura e escolhe o mapa por título e artista. **O download da fila não grava casamento** (`track_matches`) quando a duração é desconhecida. Ele guarda só o registro em `queue_downloads` (mapa + `trackUri`), para a limpeza "apagar os não jogados" continuar funcionando. Quando a música começa a tocar e a duração chega pelos metadados, o casamento automático que já existe decide: duração igual vira "mapa encontrado", diferente vira sugestão, como hoje com mapas do Encore sem duração. Motivo (decisão minha): hoje `QueueChartPicker.durationAgrees` aceita qualquer mapa quando a duração é nula, e o `ChartDownloader` grava o casamento como manual. Sem a duração da fila, um mapa de outra versão ficaria preso como "mapa encontrado".
- **Opção "Baixar o de duração mais próxima":** sem a duração na fila, ela só tem efeito quando a duração já é conhecida (decisão minha).
- **O que sai:** a autorização da fila (PKCE, `TokenStore`, "Autorização perdida") e os tratamentos de `Retry-After`. Ler a fila fica de graça, então a releitura acontece a cada mudança de fila (`onQueueChanged`), sem o intervalo de antes.

### Permissão

- **Declaração:** o serviço declarado no manifesto com `BIND_NOTIFICATION_LISTENER_SERVICE`. Não há permissão nova em tempo de execução: o jogador liga na tela do sistema.
- **Detecção:** `NotificationManagerCompat.getEnabledListenerPackages(context)`, conferida ao voltar ao app (`ON_RESUME`).
- **Revogação:** se o jogador desligar a permissão com o app aberto, o Tocando agora volta ao aviso. Se for no meio da partida, a partida pausa e mostra o aviso na pausa.

### O que sai do projeto

- O SDK em `third_party/maven` e a dependência `com.spotify.android:auth`.
- `AppRemoteSpotify`, `Reconnector`/`ReconnectPolicy` (se não servirem mais), `ForegroundActivity` (se só servia ao SDK) e todo o pacote `web/` (PKCE, `SpotifyWebAuth`, `TokenStore`, `SpotifyQueueApi`).
- `spotify.clientId` e `spotify.redirectUri` do `local.properties`, e o `SpotifyConfig`.
- O esquema `riff://callback`.
- `scripts/conferir_r8_spotify.sh` e as regras do R8 do SDK.
- O token do Spotify nas regras de backup (`riff_spotify_web.xml`).
- **Configurações → Spotify:** os interruptores de comportamento ficam e a autorização da fila sai. "Baixar mapas da fila" passa a depender só da permissão.
- **Licenças e privacidade:**
  - o item "Spotify App Remote SDK" sai da lista manual, e o AboutLibraries tira o `com.spotify.android:auth` sozinho;
  - o texto passa a dizer que o Riff lê a música tocando e a fila **pelo controle de mídia do Android**, e que isso não vai para o desenvolvedor;
  - o texto do Encore continua.
- **README:**
  - "Instalar e configurar → Spotify" vira "Spotify: permitir o acesso a notificações";
  - "Enviar o APK" deixa de falar em User Management e Premium;
  - "Mapas da fila do Spotify" e os Créditos são atualizados.

## Riscos e o que fazer

1. **⏮ não chegar à faixa pedida em "Jogar de novo"** (conta grátis, aleatório). Coberto pelo aviso "Volte a música no Spotify para jogar de novo". A Biblioteca nunca abre partida no modo Spotify (`GameplayRoute` sem `spotifyTrackUri`).
2. **Precisão diferente nas retomadas e trocas de música.** O teste mediu só tocar e pausar. Seek, próxima e começo da música precisam de medida no aparelho, e a correção fixa pode variar por caso.
3. **Fila sem itens em alguns contextos** (rádio, aleatório, conta grátis). Nesses casos, a fila simplesmente não adianta downloads, e a música atual continua sendo casada quando começa.
4. **O Spotify mudar a sessão** numa atualização (por exemplo, deixar de mandar `MEDIA_ID`). Sem `MEDIA_ID`, a faixa não é casada e o Tocando agora diz "Não deu para reconhecer a música no Spotify". O README registra isso como dependência.
5. **Fabricantes que matam o serviço de notificações** em segundo plano. Só importa com o app aberto, e ao voltar o Riff relê as sessões.

## O que fica para quem constrói

- **Organização:** o módulo continua `:core:spotify` (ou é renomeado, se valer a pena). Também cabe a quem constrói a organização das classes novas e a forma de testar o `MediaController`, com um fake atrás de uma interface própria, como `FakeSpotifyRemote` hoje.
- **Textos:** a redação exata dos avisos, no tom dos textos atuais, em pt e en.
- **Banco:** se alguma coluna ou opção ficar sem uso (por exemplo, o estado da autorização da fila no DataStore), limpar com uma migração. Os dados de casamento não podem ser apagados.
- **Medida e testes:** a medida no aparelho para fixar a correção de cerca de 340 ms, e os testes de unidade da extrapolação e dos estados.

## Critérios de aceite

1. Com o APK novo instalado num celular sem nenhum cadastro no Dashboard, o Tocando agora mostra a música do Spotify depois de ligar a permissão. Vale também para uma conta grátis.
2. Os mapas já escolhidos pelo dono continuam casados, com os mesmos ajustes de sincronia, depois de atualizar o app (sem reinstalar).
3. ⏮ ⏯ ⏭, o modo contínuo, a fila (2 próximas baixadas também durante a partida) e o painel de sugestão funcionam no S25 (Premium).
4. A sincronia percebida no S25 fica igual à de hoje nas 3 partidas de teste. Com registro temporário, o erro da posição fica dentro de ±50 ms depois da correção fixa.
5. O APK não tem mais o SDK do Spotify, e o build não pede `spotify.clientId`.
6. A suíte completa passa (unidade, detekt, lint, screenshots, debug e release), e o README e as Licenças estão atualizados.
