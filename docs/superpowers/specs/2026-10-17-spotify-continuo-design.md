# Spotify contínuo — design

**Data:** 2026-10-17 · **Base:** `feat/ajustes-tocando-biblioteca` (que já contém `feat/fila-spotify`)

## 1. Intenção

No modo Spotify, o usuário quer jogar a fila como um show contínuo: enquanto joga uma música, as próximas já estão sendo preparadas, e quando o Spotify passa para a próxima a partida dela começa sozinha. Hoje a fila espera a partida acabar para baixar (decisão do spec da fila, para não pesar na pista) e o fim da música leva ao Resultado, de onde ele volta ao Tocando agora e aperta Jogar de novo.

Palavras dele: "ele deveria baixar enquanto tô tocando uma música as próximas e, passando de música, já começar a tocar ela; se precisar de confirmação, aparecer no menu pausado da música."

## 2. Objetivos

1. **A fila baixa durante a partida.** Sai a regra "nunca durante uma partida": o download não espera a partida acabar nem é cancelado quando uma abre. Continua valendo: um por vez, só com Wi-Fi (ou dados móveis ligados), espaço e pasta; o mapa da partida aberta nunca é apagado; jogar marca o mapa como jogado.
2. **Nova opção "Ao acabar a música"** (aba Spotify das Configurações, sempre ativa, independente de "Baixar mapas da fila"), com duas escolhas:
   - **Ir direto para a próxima** (*padrão*, escolha do usuário): o resultado é salvo em silêncio (placar e recorde, como hoje) e o Riff segue para a próxima música (§3).
   - **Mostrar o resultado**: o comportamento de hoje (Resultado → Tocando agora).
3. **Próxima música com mapa sugerido** (só sugestão, "CONFIRMAR MAPA"): a partida dela abre **pausada**, com um painel de confirmação no lugar do menu de pausa: o texto da sugestão ("Este mapa parece ser desta música: título — artista") e **Confirmar mapa e jogar** · **Escolher outro** · **Voltar ao menu**. O Spotify segue tocando enquanto isso.
4. **Próxima música sem mapa:** volta ao Tocando agora (como hoje), que mostra o motivo da fila e Escolher mapa / Pesquisar no Encore.

## 3. Como segue para a próxima

Reaproveita o pedido de começo automático que já existe para a troca de música na pausa (`NowPlayingAutoPlay`): a partida volta ao Tocando agora deixando a dificuldade e a música de onde saiu; o Tocando agora espera o Spotify trocar e decide.

- **Quando a partida acaba** no modo Spotify com "Ir direto para a próxima": salva o placar e volta ao Tocando agora com o pedido, em vez de ir ao Resultado. A partida acaba quando o Spotify troca de faixa **ou** quando as notas acabam (última nota + 3 s), o que vier primeiro — no segundo caso a música ainda está no fim (outro, aplausos), e o Tocando agora espera a troca.
- **Quanto esperar:** hoje o pedido vale por 6 s (a troca na pausa é imediata). Quando vem do fim da partida, vale **até o fim da música mais 10 s** (*decisão minha*: o Spotify pode estar no outro; sem troca nesse prazo — fila vazia, Spotify pausado — fica no Tocando agora). Sair do Tocando agora cancela, como hoje.
- **Decisão na música nova** (vale para os dois caminhos, fim da partida e troca na pausa):
  - mapa encontrado com a dificuldade → começa direto (como hoje);
  - mapa encontrado sem a dificuldade → pede a dificuldade no Tocando agora (como hoje);
  - **sugestão com a dificuldade → abre a partida pausada com o painel de confirmação (§2.3)** — novo; sem a dificuldade → fica no Tocando agora (*decisão minha*: confirmar e depois escolher dificuldade seriam dois passos; o Tocando agora já tem os dois);
  - sem mapa → fica no Tocando agora.

- **Mapa ainda chegando** (*decisão minha*): se, quando o Spotify troca, a fila ainda está pesquisando, baixando ou importando o mapa daquela música, o Tocando agora espera a fila terminar (mostrando "Baixando o mapa desta música…" no lugar do motivo) e só então decide. Sem isso, a música que a fila estava preparando cairia em "sem mapa" — justamente o caso que este pedido quer resolver. A fila passa a expor quais músicas estão em andamento. Se o download falhar, decide com o que houver (normalmente "sem mapa", com o motivo).
- **Prazo do fim da música** (*decisão minha*): calculado na hora do pedido pela duração e posição do Spotify (o que falta da faixa) + 10 s. Se o Spotify for pausado nesse meio tempo, o prazo passa e o Riff fica no Tocando agora — o usuário parou a música, então não faz sentido começar outra sozinho.

## 4. O painel de confirmação

- A partida abre carregada mas não começa: não toca a faixa do começo até confirmar. A pista fica parada e escurecida como na pausa.
- **Confirmar mapa e jogar:** grava o casamento (como o Confirmar do Tocando agora) e começa a partida; como toda partida do Spotify, a faixa recomeça do início.
- **Escolher outro:** sai da partida e abre a escolha de mapa da biblioteca para esta faixa (a mesma do "Escolher outro" do Tocando agora).
- **Voltar ao menu:** sai da partida para o Tocando agora, sem confirmar.
- O gesto de voltar do celular = Voltar ao menu.
- **O Spotify passou de música com o painel aberto** (*decisão minha*): a música que pedia confirmação não está mais tocando, então o painel não vale mais — segue a corrente, como num fim de partida: volta ao Tocando agora com o pedido para a música nova (que decide de novo pelas regras do §3). Confirmar nunca começa uma faixa diferente da que o painel mostra.

## 5. Não-objetivos

- Modo biblioteca não muda.
- Sem contagem regressiva nem tela intermediária entre as músicas.
- O Resultado pulado não aparece depois (o placar fica no histórico/recorde como qualquer partida).

## 6. Riscos

- **Desempenho na pista:** baixar (rede + gravação) e importar o `.sng` durante a partida pode atrasar quadros. Mitigação: o download já roda em IO; medir no S25 com `scripts/desempenho.sh medir` + `quadros_travados.py` numa partida com download acontecendo. Critério (*decisão minha*): a partida com download não pode ter mais quadros atrasados que a mesma partida sem download (hoje 0–3 em ~2.500, 120 fps); se tiver, baixar com prioridade de thread baixa e medir de novo.
- **Recomeço da faixa:** a partida nova toca a faixa do começo (o Spotify já estava alguns segundos nela). É o comportamento de toda partida do Spotify e evita jogar com o começo cortado.

## 7. Deixado para quem constrói

Nomes de tipos e chaves; onde exatamente o pedido guarda o prazo; layout do painel (seguir o menu de pausa existente: título, texto, botões empilhados no estilo pôster).
