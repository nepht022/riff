# Firebase da Sala (multiplayer) — passo a passo

A **Sala** usa um projeto do Firebase no plano gratuito (Spark): o login anônimo e o Realtime Database. Criar o projeto
e publicar as regras é feito uma vez, à mão, no console. Sem a configuração no `local.properties`, a Sala continua
desligada ("em breve").

## 1. Criar o projeto

1. Abra <https://console.firebase.google.com> com a sua conta Google e clique em **Criar um projeto** (ou
   **Adicionar projeto**).
2. Nome: `riff` (ou outro; o Firebase acrescenta um sufixo se o nome estiver em uso). Pode desligar o Google
   Analytics: o Riff não usa.
3. O projeto novo já fica no plano **Spark** (gratuito). Não precisa pôr cartão nem mudar de plano.

## 2. Ligar o login anônimo

1. No menu da esquerda: **Criação** (Build) → **Authentication** → **Vamos começar**.
2. Aba **Método de login** (Sign-in method) → **Anônimo** → ative → **Salvar**.

É só isso: o app cria um usuário anônimo por aparelho no primeiro uso da Sala.

## 3. Criar o Realtime Database

1. **Criação** → **Realtime Database** → **Criar banco de dados**.
2. Local: o que estiver mais perto (`us-central1`, Estados Unidos, serve bem para o Brasil).
3. Regras de segurança: escolha **Iniciar no modo bloqueado**. As regras de verdade entram no passo seguinte.

## 4. Publicar as regras

1. Ainda no Realtime Database, aba **Regras**.
2. Apague tudo o que está no editor e cole o conteúdo inteiro de `firebase/database.rules.json` (com os comentários
   `//`; o console aceita).
3. **Publicar**.

Sempre que `firebase/database.rules.json` mudar (as próximas etapas do multiplayer acrescentam campos), repita este
passo: colar o arquivo inteiro e **Publicar**. **A M2 (música e começo juntos) mudou as regras: republique antes de
usar a versão nova do app** (as regras novas continuam aceitando tudo o que o app da M1 grava). **A M3 (placar ao
vivo e Resultado da sala) mudou as regras de novo (`placar`): republique antes de usar a versão da M3** (sem
republicar, o placar ao vivo fica vazio; o app da M2 continua funcionando com as regras novas). As regras são testadas no emulador antes (`scripts/testar_regras_sala.sh`,
ver `firebase/README.md`).

## 5. Copiar a chave e o endereço para o `local.properties`

1. **Chave da API Web:** ícone de engrenagem → **Configurações do projeto** → aba **Geral** → **Chave de API da Web**
   (Web API Key). Se aparecer vazia, registre um app da Web em **Seus apps** → ícone `</>` (qualquer apelido, sem
   Hosting) e copie o `apiKey` que aparece.
2. **Endereço do banco:** na página do Realtime Database, aba **Dados**, o endereço no topo, por exemplo
   `https://riff-xxxxx-default-rtdb.firebaseio.com` (fora dos Estados Unidos ele termina em
   `.firebasedatabase.app`). Copie sem a barra no fim.
3. No `local.properties` da raiz do repositório (fora do git, ao lado do `ranking.url`), acrescente:

   ```properties
   firebase.apiKey=AIza...
   firebase.dbUrl=https://riff-xxxxx-default-rtdb.firebaseio.com
   ```

4. Gere de novo o app do celular e o do PC: a chave e o endereço entram no build (como o `ranking.url`).

Nunca commite o `local.properties` nem cole a chave em issues ou conversas.

## Opcional: restringir a chave

No Google Cloud Console (<https://console.cloud.google.com>, mesmo projeto) → **APIs e serviços** → **Credenciais** →
a chave do navegador criada pelo Firebase → **Restrições de API** → limitar a **Identity Toolkit API** e
**Token Service API**, que são as duas que o login anônimo usa (o Realtime Database é protegido pelas regras, não pela
chave).

## Conferir

- Na aba **Dados** do Realtime Database, criar uma sala no app faz aparecer `salas/ABCD` (o código da sala) com
  `dono`, `jogadores` e `vagas`; sair da sala como último jogador apaga a sala.
- Em **Authentication** → **Usuários**, cada aparelho aparece como um usuário anônimo.
