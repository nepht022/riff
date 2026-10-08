# Riff: identidade visual "Pôster de turnê"

Data: 2026-10-07 · Status: aprovado em conversa, aguardando revisão do documento
Complementa: `2026-10-05-riff-design.md` (substitui o que aquela especificação dizia sobre tema claro e Dynamic Color)

## 1. Objetivo

Trazer a atmosfera de show do Guitar Hero 3: o jogador deve se sentir no palco. Os menus seguem o estilo de pôster de turnê de rock; a pista do gameplay é um palco escuro com gemas e botões em 3D, chama no acerto e sons de acerto e erro.

**Limite de propriedade intelectual:** reproduzimos o estilo, nunca os elementos do jogo. Nada de logo, fontes proprietárias, artes, personagens, sons ou nomes do Guitar Hero. Fontes vêm do Google Fonts com licença OFL; texturas, gemas e chamas são desenhadas em código; sons são sintetizados por nós ou CC0.

## 2. Paleta

Tema único, escuro, sem Dynamic Color: com Dynamic Color as cores mudariam conforme o papel de parede do celular, e a identidade se perderia.

| Papel | Cor | Uso |
|---|---|---|
| Preto de palco | `#120D0B` | fundo de todas as telas |
| Preto-tinta | `#1B1210` | texto sobre papel, contornos e sombras duras |
| Papel envelhecido | `#E8D9B8` | texto principal sobre preto; fundo de cartões |
| Papel apagado | `#A89D86` | texto secundário sobre preto |
| Vermelho de turnê | `#B3121B` | botões principais, carimbos, molduras e sombras de letreiro |
| Carimbo de sucesso | `#2E6B34` | badge "mapa encontrado" (texto em papel) |
| Carimbo de atenção | `#7A4F00` | badge "confirmar mapa" (texto em papel) |
| Carimbo de erro | `#8E1016` | badge e estados de erro (texto em papel) |
| Trilhas | `#2ECC71` `#E74C3C` `#F1C40F` `#3498DB` `#E67E22` | as 5 trilhas, sem mudança |

**Contraste (WCAG, calculado):**

| Combinação | Razão | Resultado |
|---|---|---|
| Papel sobre preto | 13,8 | AA e AAA |
| Preto-tinta sobre papel | 13,2 | AA e AAA |
| Papel apagado sobre preto | 7,2 | AA e AAA |
| Papel sobre vermelho | 5,0 | AA |
| Papel sobre carimbo de sucesso | 4,6 | AA |
| Papel sobre carimbo de atenção | 5,1 | AA |
| Papel sobre carimbo de erro | 6,7 | AA |
| Vermelho sobre preto | 2,8 | **reprovado** |

**Regra:** vermelho sobre preto nunca carrega texto. Ele serve só para fundos de botão, molduras, carimbos e sombras decorativas de letreiro (onde o texto em papel por cima é que é lido).

## 3. Tipografia

As duas fontes ficam embutidas no app (`res/font`), sem download em tempo de execução, com licença OFL incluída no repositório.

| Fonte | Uso |
|---|---|
| **New Rocker** | títulos de tela, letreiro "RIFF", botões principais, placar, multiplicador e textos de julgamento ("PERFEITO!", "BOM!") |
| **Oswald** (400 e 600) | todo o resto: textos corridos, listas, badges e configurações |

A escala tipográfica do Material 3 é mantida em tamanhos; muda só a família. Fontes continuam escaláveis (sp).

## 4. Componentes do `:core:ui`

A API pública dos componentes existentes não muda; muda a aparência.

- **Letreiro (`PosterTitle`, novo):** texto em New Rocker na cor papel, com duas sombras duras deslocadas (vermelho a 3dp, preto a 6dp). Usado no logo "RIFF" e nos títulos de tela.
- **Botão principal (`PosterButton`, novo):** fundo vermelho, texto em papel em New Rocker, sombra preta dura deslocada (5dp), inclinação de 1°. Ao pressionar, a sombra some e o botão desce 3dp. Alvo de toque mínimo de 48dp.
- **Cartão de música (`TrackCard`):** fundo papel, texto preto-tinta, cantos quase retos (2dp), sombra preta dura (4dp) e inclinação de −1,5°.
- **Badge (`StatusBadge`):** vira carimbo chapado, cantos retos, texto em Oswald 600 maiúsculo com espaçamento; as cores seguem a tabela da seção 2.
- **Estados vazio, erro e carregando:** título em New Rocker, mensagem em papel apagado e ação com `PosterButton`. O carregando usa um indicador na cor papel.
- **Fundo de tela (`StageBackground`, novo):** preto de palco com hachura diagonal sutil (linhas a 2,5% de opacidade).

**Inclinações:** só em elementos de destaque (botão principal, cartão em evidência, carimbo de multiplicador), entre 1° e 2°. Listas longas ficam retas, para leitura.

## 5. Pista do gameplay

As peças de desenho ficam em `:core:ui` como funções de `DrawScope`, testadas por screenshot; a pista em movimento é montada no plano do gameplay.

- **Perspectiva real:** a pista é vista por uma câmera atrás da linha de acerto. As posições são dadas em distância à frente da linha, e a escala é `câmera / (câmera + distância)`: notas igualmente espaçadas no tempo nascem pequenas e juntas no fundo e crescem e se espaçam ao chegar.
- **Pista:** braço escuro (gradiente do preto ao marrom-avermelhado) com trama própria em papel a 6% de opacidade, moldura de papel (3dp) e vermelho (4dp), linhas de trilha e de traste em papel a baixa opacidade.
- **Gema:** disco deitado na pista, visto de lado: sombra, lateral da cor da trilha, aro cromado e tampa com reflexo branco.
- **Gema HOPO:** mesma gema com o centro da tampa mais claro.
- **Nota longa:** rastro da cor da trilha atrás da gema; brilha enquanto está sendo segurada.
- **Alvo da linha de acerto:** anel deitado na pista, com aro cromado, anel da cor da trilha e centro escuro. Pressionado: o centro acende na cor da trilha, com um halo, mesmo sem nota para acertar.
- **Chama no acerto:** jato de fogo de cerca de 250 ms saindo do botão, com 3 faíscas. No máximo uma chama por trilha (5 simultâneas). Nenhuma alocação por frame.
- **Placar:** New Rocker em papel com sombras duras vermelha e preta. **Multiplicador:** carimbo vermelho inclinado −4°.
- **Julgamento:** "PERFEITO!" e "BOM!" em New Rocker, com brilho laranja e salto curto de escala. O erro não mostra texto: só som e quebra de combo.
- **Animações reduzidas:** se o sistema estiver com "remover animações", a chama vira um brilho no botão e o texto de julgamento aparece sem salto.

## 6. Sons

- **Acerto:** estalo curto. **Erro:** ruído de corda abafada.
- **Origem:** sintetizados por um script nosso versionado no repositório, ou arquivos CC0 com a origem registrada. Nada copiado do jogo.
- **Reprodução:** `SoundPool` (baixa latência), disparado no instante do toque. Os sons não usam o offset de calibração: são resposta ao dedo, não à música.
- **Volume:** `Settings.sfxVolume`, de 0 a 1 (0 desliga), padrão 0,6, ajustável nas Configurações.

## 7. Divisão da implementação

- **Plano "Identidade visual" (agora):** seções 2 a 4 inteiras, mais as peças de desenho da seção 5 (pista estática, gema, gema HOPO, botão solto e pressionado, chama em quadros-chave, placar, multiplicador e julgamento) com screenshots, mais o campo `sfxVolume` no modelo `Settings`.
- **Plano do gameplay:** pista em movimento, chama animada no acerto real, sons (arquivos e `SoundPool`), animações reduzidas e o controle de volume na tela de Configurações.

## 8. Testes

- Screenshots Roborazzi de todos os componentes da seção 4 e das peças da seção 5, só no tema escuro (o claro deixa de existir).
- Teste de que o tema não usa Dynamic Color mesmo no Android 12+.
- Teste unitário do valor padrão e dos limites de `Settings.sfxVolume` (0 a 1).
- O contraste da paleta é verificado por um teste que calcula a razão WCAG das combinações da tabela da seção 2, para uma mudança de cor futura não quebrar o AA sem ninguém notar.
