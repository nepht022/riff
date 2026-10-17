# Ranking online — design

**Data:** 2026-10-17 · **Base:** `main` (`ac0d08a`)

## 1. Intenção

O usuário quer comparar as pontuações dele com as de outras pessoas, cada uma jogando o Riff no próprio celular. O ranking é **por mapa e por dificuldade**, com vários números para ordenar, e a pessoa se vê na lista com destaque.

Palavras dele: "mostrando a minha posição no ranking dentre os outros com um leve destaque, já que sou eu"; "por música, separado por dificuldade"; "só troca quando a nova for melhor, salva a melhor pontuação"; "na tela inicial, tanto biblioteca quanto tocando agora, o usuário preencher a primeira vez que acessar o app seu nome e, ao salvar seu nome, ninguém poder usar o mesmo; na tela de resultados a pontuação é enviada automaticamente pro ranking se for a mais alta que o usuário fez"; "poderia ser trocado nas configurações, deixando o nome antigo disponível pros outros usuários"; as pontuações "poderiam acompanhar o usuário".

## 2. Objetivos

1. **Nome de jogador.** Na primeira abertura do app (Tocando agora ou Biblioteca, o que abrir primeiro), um pedido do nome. Ao salvar, o Riff reserva o nome no ranking online; nome já usado por outro celular → aviso, escolher outro. O nome fica ligado a **este celular**.
2. **Trocar o nome** em Configurações (aba Geral, *decisão minha*): o antigo fica livre para outros; as pontuações acompanham o jogador (aparecem com o nome novo).
3. **Envio automático.** Ao acabar uma partida, se ela for a melhor do jogador naquele **mapa + dificuldade** em pelo menos um número do ranking (comparando com o histórico deste celular), a pontuação vai sozinha para o ranking. O ranking guarda, **por número**, o melhor de cada jogador (*decisão minha*: o ranking por combo mostra o maior combo, mesmo que tenha vindo de outra partida que a maior pontuação).
4. **Números do ranking** (escolha do usuário: tudo menos bons e erros): **Pontos**, **Precisão**, **Maior combo**, **Perfeitos** e **Tempo sem errar** (novo, §5).
5. **Resultado:** sem campo de nome (referência visual: o cartão "Enviado como… / 3º lugar de 27 / Ver ranking" da maquete `C_resultado_enviado.png`). Mostra o que aconteceu com o ranking: "Ranking: 3º de 27" (posição em Pontos), "Enviando…", "Sem internet: vai para o ranking quando voltar" ou "Escolha um nome para entrar no ranking" (link que abre o pedido). Mostra também "Tempo sem errar" junto dos números de hoje e um link **Ver ranking** (abre o placar desse mapa e dificuldade).
6. **Tela Ranking** (maquetes aprovadas: `.superpowers/mockups/ranking/D_ranking_musicas.png` e `E_ranking_placar.png`):
   - **Lista de músicas** que têm pontuação no ranking, com busca por música/artista; cada cartão mostra título, artista, quantos jogadores e "você Nº" se o jogador estiver nele (posição em Pontos, na dificuldade em que tem a melhor posição — *decisão minha*).
   - **Placar** de uma música: dificuldade (Fácil/Médio/Difícil/Expert, só as que têm pontuação), número que ordena, sentido (↓ maior / ↑ menor), busca por nome, lista com posição, nome e valor; a linha do jogador com fundo vermelho translúcido, borda vermelha e "(você)".
7. **Acesso:** "Ranking" no topo do Tocando agora (ao lado de Biblioteca · Configurações · Reportar) e no topo da Biblioteca (*decisão minha*: são os dois menus de início); e o **Ver ranking** do Resultado.

## 3. Não-objetivos / anti-objetivos

- Sem contas, senha, email ou login Google: só o apelido. Nada de dados do celular no ranking.
- Sem anticheat forte: o endereço do script está no APK; um usuário técnico poderia forjar pontuações. Aceito para um ranking entre amigos; o dono apaga linhas na planilha.
- Sem moderação automática de nomes (só regras de formato, §4).
- Sem ranking geral somando músicas.

## 4. Como funciona (decisões)

- **Servidor:** um **Google Apps Script novo**, publicado como App da Web na conta do usuário (como o do Reportar), com uma **planilha** de duas abas: *Jogadores* (nome, chave do celular em hash, criado/atualizado) e *Pontuações* (jogador, mapa, título, artista, dificuldade, os 5 números, quando). Código em `scripts/ranking_apps_script.gs`, endereço em `local.properties` como `ranking.url` (fora do git). Script separado do Reportar (*decisão minha*: o do Reportar já funciona e não precisa ser republicado).
- **Identidade do celular:** na primeira reserva, o app cria uma **chave aleatória** e guarda só neste celular; o script guarda só o hash dela. Reservar, trocar nome e enviar exigem a chave. Ela entra no backup do Android (*decisão minha*: trocar de celular restaurando o backup mantém o nome), ao contrário do token do Spotify.
- **Nome:** 3–20 caracteres, letras (com acento), números, espaço, `_` e `-`; espaços das pontas removidos; único **sem diferenciar maiúsculas** ("Rodrigo" = "rodrigo").
- **Mapa:** o id do mapa no Riff (hash do arquivo de notas), igual em todos os celulares para o mesmo mapa. Título e artista vão junto para a lista. Mapas diferentes da mesma música são rankings diferentes.
- **Envio:** o app manda os 5 números; o script guarda o máximo de cada um por jogador+mapa+dificuldade (Precisão: maior é melhor; todos são "maior é melhor").
- **Partidas sem Resultado:** no Spotify com "Ir direto para a próxima", a tela de Resultado é pulada; o envio acontece mesmo assim (*decisão minha*: o envio fica no passo que salva o placar, não na tela). Sem a tela, a posição não é mostrada.
- **Antes de ter nome e histórico antigo** (*decisão minha*): ao salvar o nome pela primeira vez, o Riff envia uma vez os melhores do histórico deste celular (por mapa e dificuldade), incluindo partidas jogadas sem nome ou com "Agora não"; assim o ranking não começa vazio para quem já joga. Partidas antigas não têm "Tempo sem errar": vão sem esse número e não aparecem quando o placar é ordenado por ele.
- **Sem internet:** o pedido do nome pode ser adiado ("Agora não") e volta na próxima abertura; envios ficam numa fila local e vão quando houver conexão (na abertura do app e ao terminar uma partida).
- **Limites:** o script recusa pedidos grandes e mais de 60 envios por hora por celular (*decisão minha*: o endereço está no APK).

## 5. "Tempo sem errar"

A maior sequência sem erro da partida, medida no tempo do mapa: do primeiro ao último acerto do maior trecho sem nenhum erro (*decisão minha*: igual ao maior combo, mas em segundos, que é como o usuário falou). Quebra no mesmo que quebra o combo — nota perdida **ou** toque sem nota —, para os dois números contarem a mesma coisa. Mostrado como m:ss. Guardado no histórico local junto do placar (coluna nova).

## 6. Riscos

- **Cota do Apps Script / planilha lenta** com muitos jogadores: aceitável para dezenas de pessoas; a lista do placar vem já ordenada e limitada (100 primeiros + a linha do jogador).
- **Nome reservado por quem desinstalou:** fica preso à chave dele; o dono pode liberar apagando a linha na planilha.
- **Concorrência:** duas reservas do mesmo nome ao mesmo tempo → o script usa trava (LockService).

## 7. Deixado para quem constrói

Formato exato das mensagens do script; nomes de tipos, tabelas e chaves; layout do pedido do nome (seguir o estilo pôster: título, campo, Salvar e "Agora não"); como a fila de envios é guardada.
