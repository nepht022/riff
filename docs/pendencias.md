# Pendências conhecidas

Decisões adiadas de propósito, com o motivo e quando retomar.

## Notas abertas mudam o HOPO da nota seguinte

- **O que é:** o parser descarta as notas abertas (`N 7` no `.chart`) antes do cálculo de HOPO natural. A nota seguinte passa a se comparar com a nota anterior à aberta. Exemplo: verde no tick 0, aberta no 48 e verde no 96. No Clone Hero, o verde do 96 é HOPO; aqui, vira palhetada.
- **Efeito hoje:** nenhum. O `isHopo` não muda a jogabilidade por toque.
- **Quando retomar:** quando as notas abertas entrarem no jogo ou quando existir o visual de HOPO.

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
- O resto das revisões anteriores (biblioteca e importação, acessibilidade e robustez interna) está no histórico de cada plano; retomar pelo que incomodar no uso.
