# Pendências conhecidas

Decisões adiadas de propósito, com o motivo e quando retomar.

## Comparação de screenshots no CI

- **O que é:** o CI não roda o `verifyRoborazziDebug`. As referências foram geradas no Windows, e o Robolectric desenha as fontes de forma diferente no Linux.
- **Hoje:** a comparação é local, antes de commitar mudanças de UI.
- **Quando retomar:** ao criar o repositório remoto. Ativar a comparação no CI com tolerância (por exemplo, 1% de pixels), calibrada na primeira execução.

## Pontos menores em aberto (revisões de 2026-10-09 a 2026-10-12)

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
- O resto das revisões anteriores (biblioteca e importação, acessibilidade e robustez interna) está no histórico de cada plano; retomar pelo que incomodar no uso.
