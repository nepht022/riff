# Riff no Windows, etapa 4e: instalador — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** um instalador `.msi` do Riff para Windows que leva o próprio Java, abre e toca num PC sem nada instalado, com a IA, o áudio, o controle, o banco e o HTTPS funcionando, e com os avisos de licença do que vai embutido.

**Architecture:** o `compose.desktop.application.nativeDistributions` do Compose Multiplatform gera o `.msi` (o WiX 3 é baixado pelo próprio plugin). O build continua no JBR do Android Studio (`scripts/gw`), mas o empacotamento usa um JDK 21 completo (Temurin, por toolchain do Gradle), porque o JBR não tem o `jdk.jpackage`. Um programa de conferência (`InstallCheck`) roda **dentro do runtime empacotado** e prova que cada peça nativa carrega ali, sem depender do que está instalado no PC.

**Tech Stack:** Compose Multiplatform 1.12.2 (`packageMsi`, `createDistributable`), Gradle toolchains + `org.gradle.toolchains.foojay-resolver-convention`, Temurin 21, ONNX Runtime JVM 1.18.0, bytedeco FFmpeg 8.1.2, LWJGL/GLFW, SQLite do Room (driver empacotado).

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (linhas 25, 67, 81, 93) e `docs/pendencias.md` (seções 4a, 4b, 4c).

## Global Constraints

- "instalação por um instalador baixado do GitHub; o aviso do Windows sobre app desconhecido fica para depois." — sem assinatura de código nesta etapa.
- "instalador do Windows publicado no GitHub Releases (só quando o usuário pedir)." — nenhum `gh release`, `git push` ou tag nesta etapa; o plano só documenta o comando.
- "o ONNX Runtime para PC teve problema com o Java do Android Studio (msvcp140); o instalador precisa levar um Java que funcione."
- Versão do pacote = `riff.versionName` do `gradle.properties` (hoje `0.5.0`); nada de versão escrita à mão no `build.gradle.kts`.
- Dados do usuário em `%APPDATA%\Riff` (já é assim): instalar, atualizar e desinstalar não apagam esses dados. Testes nunca tocam no `%APPDATA%\Riff` real.
- `local.properties` (`report.url`, `ranking.url`) nunca impresso nem commitado; o `.msi` leva os URLs como o app de hoje.
- Textos da interface em português, como o resto do app.

## Decisões

1. **Formato `.msi`, instalação por usuário** (`perUserInstall = true`, sem pedir administrador), atalho na área de trabalho e no menu Iniciar, sem escolher pasta. `upgradeUuid` fixo: `5b0f1a7e-3c2d-4e8a-9f61-7d2b4c8e1a90` (um `.msi` novo substitui o antigo).
2. **JDK do empacotamento: Temurin 21** (OpenJDK, GPLv2 + Classpath Exception, redistribuível; mesma versão de bytecode do JBR 21 do build). Não o Oracle JDK 24 instalado no PC (licença NFTC e `jdk.crypto.ec` fundido no `java.base`).
3. **Módulos explícitos** (`modules(...)`), com `jdk.crypto.ec` e `jdk.unsupported` garantidos; a lista exata sai do `suggestRuntimeModules` mais esses dois, e o `InstallCheck` prova que basta.
4. **JVM do app instalado:** `-Djava.net.useSystemProxies=true` e `-Djavax.net.ssl.trustStoreType=Windows-ROOT` (proxy e certificados do Windows; resolve a pendência da 4a).
5. **Ícone:** a palheta do ícone do celular (`app/src/main/res/drawable/ic_launcher_foreground.xml` sobre `riff_stage_black`) convertida para `desktop/icons/riff.svg` e `desktop/icons/riff.ico` (16, 32, 48, 256 px), commitados. A janela usa o mesmo desenho.

## Review Focus

1. PC sem o Visual C++ Redistributable: o ORT 1.18 precisa achar `msvcp140.dll` no `runtime\bin` do app, não no `System32` → teste `msvcRuntimeShipsWithTheApp` (Task 2) e o `InstallCheck` dentro do runtime empacotado.
2. Usuário com espaço/acento no nome (`C:\Users\Rodrigo Machado\AppData\Local\Riff`): o app instalado abre, acha o modelo e grava os dados → `InstallCheck` roda a partir de `desktop/build/compose/binaries/main/app/Riff` (caminho do repo já tem espaço) e o teste manual instala no perfil do usuário.
3. HTTPS atrás de proxy/antivírus que inspeciona TLS: confia nos certificados do Windows → `InstallCheck.tls()` abre o `KeyStore` `Windows-ROOT` e o `SSLContext` com curvas EC (Task 1).
4. Atualizar por cima (instalar o `.msi` da mesma versão ou de uma maior com o Riff já instalado) mantém recordes, mapas e nome → teste manual da Task 4, passo "por cima".
5. Primeiro uso sem internet: o app abre e toca um mapa local; Encore/ranking mostram o erro de sempre → teste manual da Task 4.

---

### Task 1: `InstallCheck` — a conferência das peças nativas

**Files:**
- Create: `desktop/src/main/kotlin/com/rodrigomachado/riff/desktop/InstallCheck.kt`
- Test: `desktop/src/test/kotlin/com/rodrigomachado/riff/desktop/InstallCheckTest.kt`
- Modify: `desktop/build.gradle.kts` (task `runInstallCheck`, como `runSeparationCheck`)

**Interfaces:**
- Produces:
  - `object InstallCheck { fun run(work: File): List<Failure> }` — cada verificação isolada em `try/catch(Throwable)`; lista vazia = tudo carregou.
  - `data class Failure(val part: String, val error: String)`; `part` ∈ `"onnxruntime"`, `"ffmpeg"`, `"sqlite"`, `"glfw"`, `"tls"`, `"modelo"`.
  - `fun main(args: Array<String>)` em `InstallCheck.kt` (`InstallCheckKt`): roda em `createTempDirectory("riff-check")`, imprime `ok <part>`/`FALHOU <part>: <erro>` por linha e sai com `exitProcess(1)` se houver falha.
- Verificações (o mínimo que obriga a nativa a carregar):
  - `onnxruntime`: `OrtEnvironment.getEnvironment()` e `OrtSession` de um ONNX mínimo (a rede de identidade gerada em bytes no teste/código, não o modelo de 130 MB).
  - `modelo`: o recurso `/riff/htdemucs_6s_seg4.onnx` existe e tem mais de 100 MB (só `getResource` + tamanho; não carrega).
  - `ffmpeg`: grava um `.wav` de 100 ms em `work` e `FfmpegPcmSource.open(path, FileRandomReadOpener()).close()`.
  - `sqlite`: `BundledSQLiteDriver().open(File(work, "check.db").path)`, `CREATE TABLE`/`INSERT`/`SELECT`.
  - `glfw`: `GLFW.glfwInit()` true e `glfwTerminate()` (sem janela).
  - `tls`: `KeyStore.getInstance("Windows-ROOT").load(null)` com pelo menos 1 alias, `KeyAgreement.getInstance("ECDH")` e `SSLContext.getInstance("TLSv1.3").init(...)`.

- [ ] **Step 1: Write the failing test**

```kotlin
class InstallCheckTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun everyPartLoadsInTheBuildJava() {
        assertEquals(emptyList<InstallCheck.Failure>(), InstallCheck.run(temp.root))
    }

    @Test fun aBrokenPartIsReportedWithoutStoppingTheOthers() {
        // Pasta de trabalho que é um arquivo: ffmpeg e sqlite falham, o resto passa.
        val notADir = temp.newFile("arquivo")
        val parts = InstallCheck.run(notADir).map { it.part }.toSet()
        assertEquals(setOf("ffmpeg", "sqlite"), parts)
    }
}
```

- [ ] **Step 2:** `./scripts/gw :desktop:test --tests "*InstallCheckTest" 2>&1 | tail -c 3000` → FAIL (`InstallCheck` não existe).
- [ ] **Step 3:** implementar `InstallCheck.kt` e registrar `runInstallCheck` (`JavaExec`, `mainClass = "com.rodrigomachado.riff.desktop.InstallCheckKt"`).
- [ ] **Step 4:** o teste passa; `./scripts/gw :desktop:runInstallCheck 2>&1 | tail -c 2000` imprime seis `ok`.
- [ ] **Step 5:** `./scripts/gw :desktop:test detekt 2>&1 | tail -c 2000` → BUILD SUCCESSFUL; commit `feat: conferência das peças nativas do PC (InstallCheck)`.

### Task 2: o pacote — JDK, módulos, ícone e a conferência dentro dele

**Files:**
- Modify: `settings.gradle.kts` (plugin `org.gradle.toolchains.foojay-resolver-convention` versão `1.0.0` no bloco `plugins {}`)
- Modify: `desktop/build.gradle.kts` (`compose.desktop.application`: `javaHome`, `jvmArgs`, `nativeDistributions`; task `verifyDistributable`)
- Create: `desktop/icons/riff.svg`, `desktop/icons/riff.ico`
- Modify: `desktop/src/main/kotlin/com/rodrigomachado/riff/desktop/Main.kt` (`icon = painterResource(...)` do `riff.svg` copiado para os recursos)
- Test: `desktop/src/test/kotlin/com/rodrigomachado/riff/desktop/DistributableTest.kt`

**Interfaces:**
- Consumes: `InstallCheckKt` (Task 1).
- Produces:
  - `./scripts/gw :desktop:createDistributable` → `desktop/build/compose/binaries/main/app/Riff/` (`Riff.exe`, `runtime/`, `app/`).
  - `./scripts/gw :desktop:packageMsi` → `desktop/build/compose/binaries/main/msi/Riff-<versão>.msi`.
  - `./scripts/gw :desktop:verifyDistributable` (depende de `createDistributable`): roda `runtime\bin\java.exe -cp "app\*" com.rodrigomachado.riff.desktop.InstallCheckKt` e falha se a saída tiver `FALHOU` ou o código for ≠ 0. (Esconder o `System32` do `PATH` não prova nada: o Windows sempre procura lá. O que garante o PC sem o Redistributable é o `msvcp140.dll` no `runtime\bin`, que o `jvm.dll` já carrega antes do ORT — por isso o teste `msvcRuntimeShipsWithTheApp`.)
- Configuração (valores fixos):
  - `javaHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21); vendor = JvmVendorSpec.ADOPTIUM }.get().metadata.installationPath.asFile.absolutePath`
  - `jvmArgs += listOf("-Djava.net.useSystemProxies=true", "-Djavax.net.ssl.trustStoreType=Windows-ROOT")`
  - `targetFormats(TargetFormat.Msi)`, `packageName = "Riff"`, `packageVersion = providers.gradleProperty("riff.versionName")`, `vendor = "Rodrigo Machado"`, `description = "Riff — jogo de ritmo com guitarra"`
  - `windows { perUserInstall = true; menu = true; menuGroup = "Riff"; shortcut = true; dirChooser = false; upgradeUuid = "5b0f1a7e-3c2d-4e8a-9f61-7d2b4c8e1a90"; iconFile.set(project.file("icons/riff.ico")) }`
  - `modules(...)`: saída de `./scripts/gw :desktop:suggestRuntimeModules` + `"jdk.crypto.ec"`, `"jdk.unsupported"`; comentário dizendo que a lista vem dali.

- [ ] **Step 1: Write the failing test** (`DistributableTest`, ignorado com `Assume` quando a pasta não existe, para o `:desktop:test` comum não depender do empacotamento):

```kotlin
@Test fun msvcRuntimeShipsWithTheApp() {
    val bin = File(APP, "runtime/bin")
    assumeTrue(bin.isDirectory)
    listOf("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll", "ucrtbase.dll").forEach {
        assertTrue("$it no runtime do app", File(bin, it).isFile)
    }
}

@Test fun theRuntimeIsAJava21WithTheCryptoModule() {
    val release = File(APP, "runtime/release")
    assumeTrue(release.isFile)
    val text = release.readText()
    assertTrue(text.contains("JAVA_VERSION=\"21"))
    assertTrue(text.contains("jdk.crypto.ec"))
    assertTrue(text.contains("jdk.unsupported"))
}

@Test fun theModelGoesInsideTheApp() {
    val jars = File(APP, "app").listFiles { f -> f.name.endsWith(".jar") }.orEmpty()
    assumeTrue(jars.isNotEmpty())
    assertTrue(jars.any { jar -> ZipFile(jar).use { it.getEntry("riff/htdemucs_6s_seg4.onnx") != null } })
}
// APP = File("build/compose/binaries/main/app/Riff") relativo ao diretório do módulo.
```

- [ ] **Step 2:** `./scripts/gw :desktop:createDistributable` (ainda sem a configuração) e depois `:desktop:test --tests "*DistributableTest"` → FAIL em `theRuntimeIsAJava21WithTheCryptoModule` (runtime do JBR, sem módulos explícitos) ou `msvcRuntimeShipsWithTheApp`.
- [ ] **Step 3:** aplicar a configuração acima, gerar o ícone (SVG a partir do vetor do celular; `.ico` com 16/32/48/256 px gerado por um script descartável no scratchpad, só o `.ico` commitado), ícone da janela no `Main.kt`, registrar `verifyDistributable`.
- [ ] **Step 4:** `./scripts/gw :desktop:createDistributable :desktop:test --tests "*DistributableTest" :desktop:verifyDistributable 2>&1 | tail -c 4000` → os três testes passam (não pulados: conferir no XML do resultado `skipped="0"`) e seis `ok`.
- [ ] **Step 5:** `./scripts/gw :desktop:packageMsi 2>&1 | tail -c 2000` gera o `.msi`; anotar o tamanho (`ls -l`) no relatório. Commit `feat: instalador .msi do Riff para Windows (Java 21 próprio, ícone)`.

### Task 3: avisos de licença do que vai embutido

**Files:**
- Create em `feature/licenses/src/commonMain/composeResources/files/licencas/`: `openjdk-gpl2-ce.txt`, `ffmpeg-bibliotecas.txt`, `skia.txt`, `icu.txt`
- Modify: `feature/licenses/src/commonMain/composeResources/files/licencas/ffmpeg-lgpl3.txt` (onde está o código-fonte: FFmpeg 8.1.2 e a tag `1.5.14` de `github.com/bytedeco/javacpp-presets`)
- Modify: `feature/licenses/src/desktopMain/kotlin/com/rodrigomachado/riff/feature/licenses/DesktopLicenseSource.kt` (itens novos no `CONTENT`)
- Test: `feature/licenses/src/desktopTest/kotlin/com/rodrigomachado/riff/feature/licenses/DesktopLicenseSourceTest.kt`

**Interfaces:**
- Consumes: `ManualItem(name, author, license, file)` existente.
- Produces: itens novos:
  - `ManualItem("OpenJDK (Java do app)", "Oracle e colaboradores do OpenJDK; Eclipse Adoptium", "GPL 2.0 com Classpath Exception", "openjdk-gpl2-ce.txt")`
  - `ManualItem("Bibliotecas dentro do FFmpeg", "Vários autores", "Várias (ver texto)", "ffmpeg-bibliotecas.txt")` — um texto com, para cada uma, nome, copyright e licença com o texto completo quando a licença pede: lame (LGPL 2), speex (BSD-3), opencore-amr e vo-amrwbenc (Apache 2.0), openssl (Apache 2.0), openh264 (BSD-2), libvpx (BSD-3), freetype (FTL), harfbuzz (MIT), opus (BSD-3), libxml2 (MIT), srt (MPL 2.0), libwebp (BSD-3), aom (BSD-2), svt-av1 (BSD-3-Clause-Clear), zimg (WTFPL), libmfx (MIT), winpthreads (MIT e BSD).
  - `ManualItem("Skia", "Google", "BSD 3-Clause", "skia.txt")`, `ManualItem("ICU", "Unicode, Inc.", "Unicode License v3", "icu.txt")`.
- Textos copiados das fontes oficiais de cada projeto (os arquivos LICENSE/COPYING da versão embutida); o implementador anota de onde veio cada texto no topo do arquivo.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun theNativeCodeInsideThePcAppHasItsNotices() = runTest {
    val names = DesktopLicenseSource { null }.content().map { it.name }
    listOf("OpenJDK (Java do app)", "Bibliotecas dentro do FFmpeg", "Skia", "ICU").forEach {
        assertTrue("$it na lista do PC", it in names)
    }
}

@Test fun theFfmpegNoticeSaysWhereTheSourceIs() = runTest {
    val text = /* o texto do item "FFmpeg", lido como o teste existente lê os outros */
    assertTrue(text.contains("8.1.2"))
    assertTrue(text.contains("javacpp-presets"))
    assertTrue(text.contains("1.5.14"))
}

@Test fun everyLibraryInsideFfmpegIsNamed() = runTest {
    val text = /* o texto de "Bibliotecas dentro do FFmpeg" */
    listOf("LAME", "Speex", "opencore-amr", "vo-amrwbenc", "OpenSSL", "OpenH264", "libvpx", "FreeType",
        "HarfBuzz", "Opus", "libxml2", "SRT", "libwebp", "AOM", "SVT-AV1", "zimg", "libmfx", "winpthreads",
    ).forEach { assertTrue("$it no aviso", text.contains(it, ignoreCase = true)) }
}
```

- [ ] **Step 2:** `./scripts/gw :feature:licenses:desktopTest 2>&1 | tail -c 3000` → FAIL.
- [ ] **Step 3:** criar os textos e os itens.
- [ ] **Step 4:** testes passam; `./scripts/gw :feature:licenses:desktopTest :feature:licenses:verifyRoborazziDesktop :desktop:test detekt 2>&1 | tail -c 3000` → BUILD SUCCESSFUL (regravar screenshots de Licenças se a lista mudou, `recordRoborazziDesktop`, e conferir as imagens).
- [ ] **Step 5:** commit `feat: avisos de licença do Java, do FFmpeg, do Skia e do ICU no PC`.

### Task 4: documentação e teste no Windows

**Files:**
- Modify: `README.md` (seção "Riff no PC": como gerar o `.msi`, onde ele fica, `verifyDistributable`, e o comando de publicação **só para quando o usuário pedir**: `gh release create v<versão> desktop/build/compose/binaries/main/msi/Riff-<versão>.msi --title "Riff <versão>" --notes-file <notas>`)
- Modify: `docs/pendencias.md` (seção "Riff no Windows, etapa 4e"; tirar das seções 4a/4b/4c o que esta etapa resolveu: `jdk.crypto.ec`/proxy, avisos de licença, ORT no Java do instalador; corrigir a linha da 4d "falta republicar" — republicado e conferido em 2026-11-04; tirar a linha do `DesktopControlsFlowTest` da 4b — corrigido em `24ed86c`)
- Modify: `.github/workflows/ci.yml` só se algum job precisar do toolchain novo para continuar verde (o CI não empacota).

- [ ] **Step 1:** README e pendências; commit `docs: instalador do PC no README e pendências da 4e`.
- [ ] **Step 2: teste do usuário no Windows** (roteiro para ele):
  1. Abrir `desktop\build\compose\binaries\main\msi\Riff-0.5.0.msi`; o aviso do SmartScreen é esperado ("Mais informações" → "Executar assim mesmo").
  2. Abrir pelo atalho da área de trabalho: os mapas, recordes e nome de hoje aparecem (mesmo `%APPDATA%\Riff`).
  3. Tocar uma música; tocar com o controle; pesquisar e baixar no Encore; mandar uma pontuação; abrir Licenças.
  4. Separar uma música pela IA (ou ver a separação de uma já na fila andar).
  5. Instalar o mesmo `.msi` de novo por cima: os dados continuam.
  6. Desinstalar em "Aplicativos instalados": o `%APPDATA%\Riff` continua lá; reinstalar e os dados voltam.
- [ ] **Step 3:** registrar no ledger o que ele relatou (sem relato = não aceito).
