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
