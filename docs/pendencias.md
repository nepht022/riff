# Pendências conhecidas

Decisões adiadas de propósito, com o motivo e quando retomar.

## Comparação de screenshots no CI

- **O que é:** o CI não roda o `verifyRoborazziDebug`. As referências foram geradas no Windows, e o Robolectric desenha as fontes de forma diferente no Linux.
- **Hoje:** a comparação é local, antes de commitar mudanças de UI.
- **Quando retomar:** ao criar o repositório remoto. Ativar a comparação no CI com tolerância (por exemplo, 1% de pixels), calibrada na primeira execução.

## Pontos menores em aberto (revisões de 2026-10-09)

Os da calibração já resolvidos saíram da lista (retrocesso pausado, retomada depois de reconectar, pausa antes do primeiro início).

- Se criar a saída de áudio nova ao voltar 0,5 s falhar, a saída antiga fica liberada e o próximo `play` quebra (raro).
- Salvar um ajuste de sincronia de um mapa removido no meio da partida pode lançar `SQLiteConstraintException`.
- A detecção do começo da música supõe que a versão do Spotify começa direto na música e não é cancelável; uma introdução baixa pode exagerar o valor (o jogador refina na pausa).
- Na calibração, duas corrotinas leem a posição da saída ao mesmo tempo (efeito desprezível).
- Continuar no meio de uma ligação pausa de novo sem explicar por quê (e, sem retrocesso, alguns ms de música podem tocar antes); uma dica "áudio ocupado" ajudaria.
- Uma falha ao pedir prioridade de áudio derrubaria a thread de áudio (improvável: a prioridade é permitida a apps).
- `GameSession.press(lane)`/`release(lane)` ainda são públicos; usados direto, desalinhariam o mapa de dedos (hoje só os testes os usam).
- Soltar um dedo também pode marcar um erro antes do quadro e religar a guitarra um pouco antes da hora.
- Partida: falhas fora da leitura do mapa (sons, banco) e permissão revogada no meio da leitura aparecem como "não deu para ler este mapa" em vez do motivo certo.
- Relatório: o mapa repetido mostra só o nome da pasta ("Song: igual ao de Song" quando há duas pastas "Song" em pacotes diferentes); qual cópia vence depende da ordem de listagem do Android.
- `song.ini` com `delay_seconds = NaN` (ou infinito) faz o mapa inteiro aparecer como inválido.
- Trocar para uma pasta cuja importação falha já libera o acesso à pasta antiga (os mapas dela continuam listados, mas não tocam).
- Com teclado ou controle, segurar Enter no botão em foco desloca o botão, mas o contorno de foco fica no lugar.
- O teste de altura do botão com fonte 2× não pega regressão (a altura mínima de 48 dp vale sempre); trocar por uma verificação de texto cortado.
- TalkBack: o resumo do resultado junta "NOVO RECORDE" sem pontuação, e "1 pontos" não tem singular.
- A precisão é formatada duas vezes na tela de resultado (só arrumação).
- O resto das revisões anteriores (biblioteca e importação, acessibilidade e robustez interna) está no histórico de cada plano; retomar pelo que incomodar no uso.
