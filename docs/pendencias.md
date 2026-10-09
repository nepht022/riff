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

## `delay_seconds` no song.ini

- **O que é:** o YARG aceita `delay_seconds` no `song.ini` como alternativa ao `delay` (em ms). Aqui só o `delay` é lido.
- **Efeito:** é raro na prática. Nesses casos, vale o `Offset` do `.chart`.
- **Quando retomar:** se aparecer um mapa real que use esse campo.

## Pontos menores em aberto (revisões de 2026-10-09)

Os da calibração já resolvidos saíram da lista (retrocesso pausado, retomada depois de reconectar, pausa antes do primeiro início).

- Se criar a saída de áudio nova ao voltar 0,5 s falhar, a saída antiga fica liberada e o próximo `play` quebra (raro).
- Salvar um ajuste de sincronia de um mapa removido no meio da partida pode lançar `SQLiteConstraintException`.
- A detecção do começo da música supõe que a versão do Spotify começa direto na música e não é cancelável; uma introdução baixa pode exagerar o valor (o jogador refina na pausa).
- Na calibração, duas corrotinas leem a posição da saída ao mesmo tempo (efeito desprezível).
- Spotify: reconectar antes de ele ter começado a nossa faixa e continuar força uma pausa (o prazo de 5 s ainda conta do primeiro play).
- Dedo que ficou na tela durante a pausa ou o retrocesso: ao sair, pode soltar a nota longa de outro dedo na mesma trilha (caso estreito do contador de dedos).
- Toque nos últimos ~16–30 ms da janela de acerto, processado depois do quadro que marcou o erro, continua erro (falta uma pequena folga na marcação de erros).
- Se o Android negar o foco de áudio ao continuar (por exemplo, no meio de uma ligação), a música toca assim mesmo.
- O resto das revisões anteriores (biblioteca e importação, acessibilidade e robustez interna) está no histórico de cada plano; retomar pelo que incomodar no uso.
