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
