# Pendências conhecidas

Decisões adiadas de propósito, com o motivo e quando retomar.

## Comparação de screenshots no CI

- **O que é:** o CI não roda o `verifyRoborazziDebug`. As referências foram geradas no Windows, e o Robolectric desenha as fontes de forma diferente no Linux.
- **Hoje:** a comparação é local, antes de commitar mudanças de UI.
- **Quando retomar:** ao criar o repositório remoto. Ativar a comparação no CI com tolerância (por exemplo, 1% de pixels), calibrada na primeira execução.

## Ideias para depois

- **Fila de separações (guitarra por IA):** hoje é uma por vez ("Já tem uma separação em andamento"). Uma fila deixaria escolher várias músicas e o celular separaria todas em sequência (por exemplo, carregando à noite), com o ícone mostrando quantas faltam.
- **Backup do projeto:** o repositório só existe neste PC, sem remoto. Um repositório privado (GitHub, por exemplo) protege contra perda do disco; ao criar, ativar também a comparação de screenshots no CI (seção acima). Só com o usuário escolhendo onde.

## Pontos menores em aberto (revisões de 2026-10-09 a 2026-10-13)

Os da calibração já resolvidos saíram da lista (retrocesso pausado, retomada depois de reconectar, pausa antes do primeiro início).

- A detecção do começo da música supõe que a versão do Spotify começa direto na música; uma introdução baixa pode exagerar o valor (o jogador refina na pausa).
- `MediaCodec.getOutputBuffer` aloca um objeto por bloco decodificado (interno à plataforma, ~40 por segundo); sem efeito medido.
- Se a Activity for recriada durante o login do Spotify, o resultado do login se perde (só um log; a reconexão acontece igual).
- Continuar no meio de uma ligação pausa de novo sem explicar por quê (e, sem retrocesso, alguns ms de música podem tocar antes); uma dica "áudio ocupado" ajudaria.
- Soltar um dedo também pode marcar um erro antes do quadro e religar a guitarra um pouco antes da hora.
- Relatório: o mapa repetido mostra só o nome da pasta ("Song: igual ao de Song" quando há duas pastas "Song" em pacotes diferentes); qual cópia vence depende da ordem de listagem do Android.
- Trocar para uma pasta cuja importação falha já libera o acesso à pasta antiga (os mapas dela continuam listados, mas não tocam).
- Com teclado ou controle, segurar Enter no botão em foco desloca o botão, mas o contorno de foco fica no lugar.
- O teste de altura do botão com fonte 2× não pega regressão (a altura mínima de 48 dp vale sempre); trocar por uma verificação de texto cortado.
- TalkBack: o resumo do resultado junta "NOVO RECORDE" sem pontuação, e "1 pontos" não tem singular.
- A precisão é formatada duas vezes na tela de resultado (só arrumação).
- Decodificador que trava no meio da música: a leitura desiste em 2 a 4 s, mas segura a trava de áudio nesse tempo (pausar e continuar espera; sair fecha o decodificador com a leitura em andamento).
- Troca de canais ou de taxa no meio da faixa: a checagem compara com o início de cada leitura, não com o formato inicial; depois de voltar 0,5 s, a faixa pode tocar com ruído.
- Spotify: uma falha avisada durante a própria reconexão é ignorada (defensivo); um aviso atrasado da conexão antiga ainda marca a nova como falha.
- Partida indisponível (UNEXPECTED, CHART_UNREADABLE) e ajuste não salvo por restrição do banco não deixam registro no logcat.
- Erro do banco ao ler o mapa e arquivo apagado (FileNotFoundException) aparecem como "não deu para ler este mapa".
- Se a saída de áudio nova não abrir ao continuar, a pista anima a volta de 0,5 s e depois pula de volta (a música segue de onde estava).
- A detecção do começo da música só cancela entre blocos: abrir e ler cada faixa ainda pode ocupar o IO por alguns segundos.
- A última repetição da medição da partida termina sem sair dela (a música segue até o script reinstalar o app).
- Sons do menu: o primeiro toque logo ao abrir o app pode sair mudo (os sons e as preferências ainda carregando, dezenas de ms).
- Sons do menu: o som de voltar tem volume médio uns 6 dB abaixo dos outros (picos iguais); com o volume geral baixo, quase some.
- Sons do menu: a lista de telas do detector de volta guarda ids de telas trocadas por "Jogar de novo" (custo desprezível).
- Pesquisa no Encore: a API não é documentada (vista no código do Bridge); se mudar, a pesquisa avisa "o Riff precisa ser atualizado". O filtro de rips oficiais é uma lista de estúdios e ícones de setlist: um rip com outro ícone pode passar.
- Pesquisa: dois downloads ao mesmo tempo com o mesmo "Artista - Nome" podem gerar um nome sem ".sng" (o mapa some no Reimportar); Reimportar enquanto um download termina pode apagar o mapa recém-baixado; sair da tela entre a importação e o registro deixa um mapa sem o arquivo.
- Pesquisa: escolher outra pasta em "Permitir" troca a raiz e libera a antiga sem reimportar (os mapas listados param de abrir até o Reimportar).
- Pesquisa: a consulta do começo do arquivo confia que o servidor respeita o Range.
- `.sng`: checagem de limites com overflow num pacote forjado (vira falha, sem crash); descritor não fechado quando a abertura falha; cada abertura lê 256 KB (importação de centenas de pacotes fica mais lenta); `.part` órfão se o sistema matar o app no meio do download; nome cortado em caracteres, não em bytes.
- Pesquisa: trocar Originais/Versões com "Só com guitarra separada" ligado mostra a contagem do outro modo ("37 de 100" vira "0 de 100").
- Pesquisa: em Versões, quando quase nada passa no filtro, a procura por guitarra separada pode buscar muitas páginas (não há limite de páginas, só de conferidos); e confere o lote inteiro mesmo depois do 10º achado.
- Guitarra por IA: se o Android fechar o app no meio da separação, o arquivo temporário da decodificação (~85 MB numa música de 4 min) fica no cache.
- Guitarra por IA: o mínimo de 50 MB livres é menor que esse temporário; num celular quase cheio a falha aparece como "não deu para separar" em vez de "pouco espaço".
- Guitarra por IA: cada trecho copia ~85 MB na heap (saídas do modelo); em celulares com heap pequena pode faltar memória.
- Guitarra por IA: reimportar exatamente no fim de uma separação pode apagar os arquivos recém-gravados (a linha fica marcada, a música toca inteira com a marca IA).
- Guitarra por IA: a leitura do arquivo decodificado supõe que uma leitura enche a janela de 8 s (vale para arquivos locais; um laço seria mais seguro).
- Guitarra por IA: uma música que decodifica vazia mostra o erro genérico.
- Biblioteca: um toque para jogar pode se perder se a navegação estiver bloqueada naquele instante (o pedido é descartado junto).
- O resto das revisões anteriores (biblioteca e importação, acessibilidade e robustez interna) está no histórico de cada plano; retomar pelo que incomodar no uso.

## Pontos menores do visual da pista (revisão de 2026-10-14)

- **Texturas geradas na thread da tela:** no primeiro quadro da partida e ao abrir a lista de fundos ou de designs da pista (gera até 6 de uma vez). Pode dar um engasgo curto. Corrigir gerando antes, fora da thread da tela (o cache de `HighwayTexture` já é seguro para isso).
- **Neon pode ter emenda:** o desenho só copia os traços uma altura para cima (`-DEPTH, 0`); um traço que nasça perto do topo perde o brilho que passa da borda. Usar `-DEPTH, 0, DEPTH`, como nos outros fundos.
- **Cintilação ao longe:** a textura encolhe ~4× perto do horizonte e não tem mipmap; padrões finos (linhas do Amplificador) podem cintilar. `bitmap.setHasMipMap(true)` ao gerar.
- **Sem capa como reserva:** `background.*` grande demais (mais de 8 MB) ou corrompido não tenta `album.*`.
- **Imagem do mapa grande na memória:** o lado maior fica entre 1280 e 2559 px (até ~26 MB). Decodificar pelo tamanho da tela ou em `RGB_565` (fica sob o véu).
- **Imagem e véu redesenhados a cada quadro:** dividem a camada com a pista. `graphicsLayer` no Canvas da pista e `ColorFilter` na imagem no lugar da caixa do véu.
- **Escolhas muito rápidas:** `setTrackLook` grava os quatro campos a partir do visual da tela; trocar duas opções antes de a preferência gravada voltar pode desfazer a primeira. Aplicar só o campo escolhido sobre o valor atual.
- **Sombra das notas estilizadas:** não esmaece na nota perdida (como a dos discos).
- **Cache de texturas:** guarda até ~6 MB durante o processo; rever se a memória apertar.

## Pontos menores das Configurações em abas e do idioma (revisão de 2026-10-16)

- **Acessibilidade das abas e da lista de idiomas:** a barra não tem `selectableGroup()` (o TalkBack não diz "aba 2 de 5") e as opções de idioma usam `clickable` em vez de `selectable(role = RadioButton)`; a área de toque das abas fica perto de 47 dp.
- **Aba fora da tela:** se a tela for refeita numa aba que não aparece na barra (num celular estreito), a barra não rola até ela.
- **Testes:** o desvio para Android antigo do `SystemAppLanguageStore` não roda com `sdk` abaixo de 33; não há teste da aba sobrevivendo à tela refeita.
- **"Padrão do sistema":** escolhido nas configurações do celular, volta a Português na próxima abertura (o app não distingue de "nunca escolheu"). Na primeira abertura num celular em outro idioma, a primeira tela pode piscar.
- **Estilo:** nomes completos em `Fakes.kt` de settings; `StageBlack.copy(alpha = 0f)` em vez de `Color.Transparent`; `tools:targetApi` na tag `<application>` inteira.

## Pontos menores de "Reportar problema" (revisão de 2026-10-17)

- **Mensagens da tela:** "Não deu para enviar" e "Espere alguns minutos" continuam enquanto a pessoa edita (e a espera continua aparecendo depois dos 5 minutos); a espera só aparece ao tocar em Enviar. O leitor de tela não anuncia essas mensagens nem o "Obrigado" (`liveRegion`).
- **Fonte grande:** os três botões de tipo ficam numa linha fixa e podem ser cortados; usar `FlowRow`.
- **Privacidade:** a tela não diz para onde o relato vai nem para que serve o email; antes de distribuir, uma linha explicando e uma política de privacidade (Play Store).
- **API antiga:** `getPackageInfo(String, Int)` está obsoleto no Android 13+ (só aviso do compilador).
- **Revisão das categorias (2026-10-17):**
  - `setCategory` muda o tipo na tela mas não grava o tipo no estado salvo; uma categoria de outro tipo, restaurada depois do app fechar, passaria (a tela de hoje não deixa isso acontecer). Gravar o tipo e filtrar a categoria pelo tipo ao restaurar e ao enviar.
  - O script registra em Execuções o assunto do email, que traz o começo do texto do relato; registrar só tipo e categoria (minimização de dados, LGPD).
  - O logcat do app guarda o começo da resposta do servidor; melhor só o `motivo` (ou "resposta não é do script"). Uma falha que não seja de rede passa sem linha no log.
  - Permissão `script.scriptapp` no manifesto do script provavelmente sobra (o script não usa `ScriptApp`); tirar, rodar `testarEnvio` e publicar de novo.
  - Campo de categoria no leitor de tela: escolhido, lê só o nome ("Spotify, lista"), sem "Categoria" nem que é obrigatório; a seta "▾" é lida; nada diz por que o Enviar está desligado.
  - O auxiliar dos testes do ViewModel escolhe uma categoria sempre (esconde a exigência); preferir parâmetro explícito.

## Pontos menores da barra de progresso (revisão de 2026-10-17)

- **Áudio que acaba antes da última nota:** a partida também termina quando a música acaba (`player.isCompleted`); se o áudio for mais curto que o mapa (outra edição da música no Spotify), o resultado vem com a barra incompleta. Em músicas normais a diferença é de 1–2%. Corrigir exige a duração no `SongPlayer`.
- **Ordem de declaração:** `endMs` em `GameSession` vem depois das propriedades que o usam (seguro hoje; mover para junto de `lastNoteEndMs`).
- **Testes:** faltam o recuo da barra no retrocesso, o fim antecipado pelo áudio e o uso da etiqueta `PROGRESS`; o teste "sem notas" só confere o começo.
- **Idiomas da direita para a esquerda:** a barra sempre enche da esquerda (irrelevante em pt/en).

## Pontos menores dos controles do Spotify (revisão de 2026-10-17)

- **Sem conexão com o Spotify:** na pausa, ⏮/⏭ encerram a partida mesmo sem conseguir trocar a música (o comando se perde); esconder ou desligar os botões sem conexão. Uma recusa do Spotify (por exemplo, ⏮ em conta Free) também não é registrada.
- **Continuar e trocar no mesmo instante:** ⏯ seguido de ⏭ antes de o menu sumir pode deixar a música nova pausada (janela de um quadro).
- **Sons:** ⏮/⏭ na pausa tocam "navegar" e, ao voltar, a tela toca "voltar" (dois sons seguidos); ⏯ na pausa toca "navegar" enquanto Continuar toca "confirmar".
- **Leitor de tela e visual:** na pausa, "Próxima música" não avisa que encerra a partida; o efeito de toque do botão vermelho redondo sai quadrado.
- **Sem confirmação:** um toque sem querer em ⏭ na pausa perde a partida (combinado assim; avaliar se incomodar).
- **Testes:** falta o caso sem conexão.
- **Revisão do layout e do começo automático (2026-10-17):**
  - Se o Android fechar o app nos ~6 s de espera pela música nova, o pedido se perde (fica no Tocando agora, sem partida).
  - O tempo da música avança de 500 em 500 ms, então os segundos às vezes atrasam até meio segundo; os screenshots usam a música tocando e dependem do relógio do Robolectric (usar pausada).
  - Botões do pé da tela com largura fixa (172 + 140 dp): em fonte grande do Android ou tela de 320 dp, o texto aperta ("Confirmar mapa" já sai mais largo).
  - Faltam testes da espera pela tela voltar à frente e da navegação que deixa o pedido no Tocando agora.
  - O começo automático e o ⏮ da partida foram conferidos no celular pelo usuário (2026-10-17).

## Pontos menores dos mapas da fila do Spotify (revisão de 2026-10-17)

- **Falha de rede conta como "tentada":** uma falha momentânea do Encore ou do Wi-Fi deixa a música sem mapa até reabrir o Riff (só resultado vazio ou arquivo quebrado deveriam contar).
- **Mapa apagado não volta:** a música continua "tentada"; com ⏮ ou uma playlist que repete, não baixa de novo nesta abertura.
- **Retry-After:** `0` vira espera de zero; trocar de música lê a fila na hora, ignorando o Retry-After pendente.
- **Mesmo mapa já na biblioteca com outros dados:** a fila poderia baixar de novo e marcar como apagável o mapa que já era seu (improvável; conferir `EncoreDownloads.chartIdOf(md5)` antes).
- **Rede conferida só antes de baixar:** trocar de Wi-Fi para dados móveis durante o download não é percebido.
- **App fechado logo depois de importar:** o registro da fila vem depois da importação; nesse intervalo o mapa vira normal para sempre.
- **Autorização tocada duas vezes:** com a tela de autorização aberta, um segundo toque deixa o primeiro esperando para sempre; desabilitar o interruptor durante a autorização.
- **Aviso de autorização perdida:** não sobrevive a reabrir o app.
- **Logs:** o título da música vai para o logcat ("pronto" já não leva título; conferir os outros).
- **Estilo/testes:** `queue!!` na leitura da fila; interruptor desabilitado fica esmaecido duas vezes; faltam testes do caminho do Keystore no aparelho.
