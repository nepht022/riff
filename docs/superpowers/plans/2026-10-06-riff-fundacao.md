# Riff Fundação: plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** criar o projeto Android do Riff com build, qualidade e CI, um design system e o núcleo em Kotlin puro: modelo de domínio, parsers de mapas do Clone Hero e motor do jogo (relógio, julgamento e pontuação), todos com testes.

**Architecture:** um projeto Gradle multi-módulo com convention plugins em `build-logic/`. `:core:model`, `:core:chart` e `:core:game` são módulos Kotlin/JVM puros, testados com JUnit 5 sem Android. `:core:ui` é uma biblioteca Android com Compose e testes de screenshot (Roborazzi + Robolectric). `:app` mostra um catálogo do design system enquanto as features não existem.

**Tech Stack:** Gradle 9.8.0, AGP 9.4.1 (Kotlin embutido), Kotlin 2.4.20, Compose BOM 2026.09.00, Material 3, JUnit Jupiter 6.1.3, Robolectric 4.17, Roborazzi 1.76.0, Detekt 2.0.0-alpha.6 com o wrapper do ktlint, Kover 0.9.11.

**Spec:** `docs/superpowers/specs/2026-10-05-riff-design.md`. Este plano cobre os passos 1 a 3 da seção 11. Os passos 4 a 8 ficam para planos seguintes.

## Global Constraints

- Pacote base `com.rodrigomachado.riff`. O namespace de cada módulo é `com.rodrigomachado.riff.<caminho do módulo com pontos>`, por exemplo `com.rodrigomachado.riff.core.chart`.
- minSdk 26. compileSdk e targetSdk 36 (a plataforma instalada no SDK).
- Java e Kotlin com alvo JVM 17.
- O fonte Kotlin fica em `src/main/kotlin` e `src/test/kotlin`, inclusive nos módulos Android.
- Strings visíveis ficam em resources: `values/` em pt-BR (padrão) e `values-en/`.
- Os módulos `:core:model`, `:core:chart` e `:core:game` não dependem de Android.
- Sem backend, sem rede e sem Hilt neste plano (YAGNI: o Hilt entra no plano de dados).
- Testes de lógica usam JUnit 5 (`org.junit.jupiter.api.Assertions`). Testes Robolectric e Roborazzi usam JUnit 4, porque o Robolectric só roda com o runner do JUnit 4.
- Todo commit termina com o trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Ambiente:** o Android SDK e o JDK (JBR 21 do Android Studio) estão no Windows. No WSL, o Gradle roda sempre pelo `./scripts/gw <tasks>`. No CI (Linux), roda pelo `./gradlew`.

## Review Focus

1. **`.chart` salvo no Windows** (BOM UTF-8 e quebras de linha CRLF): precisa ser lido igual a um arquivo LF. Teste na Task 6.
2. **`.chart` sem `[SyncTrack]` ou sem andamento no tick 0:** assume 120 BPM até a primeira mudança de andamento. Testes nas Tasks 5 e 6.
3. **`notes.mid` com running status e note-on de velocidade 0 como note-off** (o padrão dos exportadores): as notas e as durações precisam sair corretas. Teste na Task 8.
4. **Duas notas próximas na mesma trilha, dentro da janela:** um toque acerta só a mais antiga, e o toque seguinte acerta a próxima. Teste na Task 11.
5. **Pasta com `song.ini` em maiúsculas, chaves em caixa mista ou sem `song.ini`:** os metadados caem para os do `.chart`, sem erro. Testes nas Tasks 7 e 9.

---

### Task 1: Esqueleto Gradle e app vazio

**Files:**
- Create: `.gitignore`, `.gitattributes`, `.editorconfig`
- Create: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`
- Create: `scripts/gw`
- Create: `local.properties` (não é versionado)
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/RiffSdk.kt`
- Create: `build-logic/convention/src/main/kotlin/ComposeDependencies.kt`
- Create: `build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/AndroidApplicationComposeConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/AndroidLibraryComposeConventionPlugin.kt`
- Create: `build-logic/convention/src/main/kotlin/JvmLibraryConventionPlugin.kt`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/rodrigomachado/riff/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

**Interfaces:**
- Produces: os plugins `riff.android.application`, `riff.android.application.compose`, `riff.android.library`, `riff.android.library.compose` e `riff.jvm.library`, expostos no catálogo como `libs.plugins.riff.*`. O `riff.jvm.library` já configura JUnit 5. Os de Compose já adicionam o BOM, o material3, o ui-tooling-preview e o ui-tooling (só em debug).

- [ ] **Step 1: Arquivos de repositório**

`.gitignore`:
```gitignore
.gradle/
build/
.kotlin/
local.properties
.idea/
*.iml
captures/
.DS_Store
```

`.gitattributes`:
```gitattributes
* text=auto
gradlew text eol=lf
scripts/* text eol=lf
*.bat text eol=crlf
*.jar binary
*.png binary
```

`.editorconfig`:
```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true

[*.{kt,kts}]
indent_size = 4
max_line_length = 120
ktlint_code_style = android_studio
ktlint_function_naming_ignore_when_annotated_with = Composable
```

- [ ] **Step 2: Gradle wrapper sem precisar de Gradle instalado**

```bash
mkdir -p gradle/wrapper scripts
curl -sSfLo gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v9.8.0/gradle/wrapper/gradle-wrapper.jar
curl -sSfLo gradlew https://raw.githubusercontent.com/gradle/gradle/v9.8.0/gradlew
curl -sSfLo gradlew.bat https://raw.githubusercontent.com/gradle/gradle/v9.8.0/gradlew.bat
chmod +x gradlew
SHA=$(curl -sSfL https://services.gradle.org/distributions/gradle-9.8.0-bin.zip.sha256)
cat > gradle/wrapper/gradle-wrapper.properties <<EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip
distributionSha256Sum=$SHA
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
```
Expected: os três arquivos baixados sem erro, e `distributionSha256Sum` preenchido com 64 caracteres hexadecimais.

- [ ] **Step 3: Script `scripts/gw` e `local.properties`**

`scripts/gw`:
```bash
#!/usr/bin/env bash
# Roda o Gradle do Windows a partir do WSL: o Android SDK e o JDK (JBR do Android Studio) estão instalados no Windows.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
export WSLENV="${WSLENV:+$WSLENV:}JAVA_HOME/w"
exec cmd.exe /c gradlew.bat "$@"
```

```bash
chmod +x scripts/gw
printf 'sdk.dir=C\\:\\\\Users\\\\Rodrigo Machado\\\\AppData\\\\Local\\\\Android\\\\Sdk\n' > local.properties
cat local.properties
```
Expected: `sdk.dir=C\:\\Users\\Rodrigo Machado\\AppData\\Local\\Android\\Sdk`

- [ ] **Step 4: Version catalog**

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.20"
composeBom = "2026.09.00"
activityCompose = "1.13.0"
coreKtx = "1.19.1"
junitJupiter = "6.1.3"
junit4 = "4.13.2"
robolectric = "4.17"
roborazzi = "1.76.0"
detekt = "2.0.0-alpha.6"
kover = "0.9.11"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junitJupiter" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
detekt-ktlint-wrapper = { module = "dev.detekt:detekt-rules-ktlint-wrapper", version.ref = "detekt" }
# usados só pelo build-logic
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
kotlin-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
compose-gradlePlugin = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
detekt = { id = "dev.detekt", version.ref = "detekt" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
riff-android-application = { id = "riff.android.application" }
riff-android-application-compose = { id = "riff.android.application.compose" }
riff-android-library = { id = "riff.android.library" }
riff-android-library-compose = { id = "riff.android.library.compose" }
riff-jvm-library = { id = "riff.jvm.library" }
```

- [ ] **Step 5: build-logic**

`build-logic/settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

`build-logic/convention/build.gradle.kts`:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.rodrigomachado.riff.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // O Kotlin embutido no Gradle pode ser mais antigo que o KGP 2.4 usado como dependência.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "riff.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "riff.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "riff.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "riff.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "riff.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
```

`build-logic/convention/src/main/kotlin/RiffSdk.kt`:
```kotlin
internal object RiffSdk {
    const val COMPILE = 36
    const val TARGET = 36
    const val MIN = 26
}
```

`build-logic/convention/src/main/kotlin/ComposeDependencies.kt`:
```kotlin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal fun Project.addComposeDependencies() {
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val bom = libs.findLibrary("androidx-compose-bom").get()
    dependencies {
        add("implementation", platform(bom))
        add("testImplementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}
```

`build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`:
```kotlin
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                compileSdk = RiffSdk.COMPILE
                defaultConfig {
                    minSdk = RiffSdk.MIN
                    targetSdk = RiffSdk.TARGET
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        }
    }
}
```

`build-logic/convention/src/main/kotlin/AndroidApplicationComposeConventionPlugin.kt`:
```kotlin
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("riff.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<ApplicationExtension> {
                buildFeatures { compose = true }
            }
            addComposeDependencies()
        }
    }
}
```

`build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`:
```kotlin
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                compileSdk = RiffSdk.COMPILE
                defaultConfig {
                    minSdk = RiffSdk.MIN
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                testOptions {
                    unitTests.isIncludeAndroidResources = true
                }
            }
        }
    }
}
```

`build-logic/convention/src/main/kotlin/AndroidLibraryComposeConventionPlugin.kt`:
```kotlin
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("riff.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<LibraryExtension> {
                buildFeatures { compose = true }
            }
            addComposeDependencies()
        }
    }
}
```

`build-logic/convention/src/main/kotlin/JvmLibraryConventionPlugin.kt`:
```kotlin
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
                add("testImplementation", libs.findLibrary("junit-jupiter").get())
                add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
            }
            tasks.withType<Test>().configureEach { useJUnitPlatform() }
        }
    }
}
```

- [ ] **Step 6: Build raiz**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "riff"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 7: Módulo `:app`**

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.application.compose)
}

android {
    namespace = "com.rodrigomachado.riff"
    defaultConfig {
        applicationId = "com.rodrigomachado.riff"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
```

`app/proguard-rules.pro`:
```
# Regras do R8 para o release. Vazio por enquanto.
```

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

`app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml` (iguais neste passo):
```xml
<resources>
    <string name="app_name" translatable="false">Riff</string>
</resources>
```
No `values-en`, crie o arquivo só com `<resources />`, porque `app_name` não é traduzível.

`app/src/main/kotlin/com/rodrigomachado/riff/MainActivity.kt`:
```kotlin
package com.rodrigomachado.riff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Text(stringResource(R.string.app_name))
                }
            }
        }
    }
}
```

- [ ] **Step 8: Build**

Run: `./scripts/gw :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, e o arquivo `app/build/outputs/apk/debug/app-debug.apk` existe.
Se o build-logic falhar ao resolver uma API do AGP 9 (por exemplo `compileSdk` ou `defaultConfig`), ajuste para a forma que a mensagem de erro indicar e anote o motivo no commit.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "build: esqueleto Gradle com convention plugins e app vazio" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Detekt, ktlint e CI

**Files:**
- Modify: `build.gradle.kts`
- Create: `config/detekt/detekt.yml`
- Create: `.github/workflows/ci.yml`
- Create: `README.md`

**Interfaces:**
- Produces: a task raiz `detekt`, que analisa `src/main/kotlin` e `src/test/kotlin` de todos os subprojetos e inclui as regras do ktlint. Com `-PdetektAutoCorrect`, ela também formata o código. Tasks futuras rodam `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt` antes de cada commit.

- [ ] **Step 1: Configurar o Detekt na raiz**

`build.gradle.kts` (substitui o conteúdo):
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(subprojects.flatMap { listOf(it.file("src/main/kotlin"), it.file("src/test/kotlin")) })
    autoCorrect = providers.gradleProperty("detektAutoCorrect").isPresent
}

dependencies {
    detektPlugins(libs.detekt.ktlint.wrapper)
}
```

`config/detekt/detekt.yml`:
```yaml
style:
  MagicNumber:
    active: false # parsers de formato de arquivo têm muitos códigos de protocolo
  ReturnCount:
    max: 4
naming:
  FunctionNaming:
    ignoreAnnotated: ['Composable']
complexity:
  LongParameterList:
    ignoreAnnotated: ['Composable']
```

- [ ] **Step 2: Rodar o Detekt**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e em seguida `./scripts/gw detekt`
Expected: `BUILD SUCCESSFUL` na segunda execução.
Se o Detekt 2.0 reclamar de uma chave desconhecida em `detekt.yml`, renomeie a chave para o nome que a mensagem sugerir.

- [ ] **Step 3: Workflow do CI**

`.github/workflows/ci.yml`:
```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: 21
      - uses: gradle/actions/setup-gradle@v5
      - name: Lint, testes e build
        run: ./gradlew detekt lintDebug test assembleDebug
```

- [ ] **Step 4: README**

`README.md`:
````markdown
# Riff

Jogo de ritmo para Android no estilo Guitar Hero: você toca junto a música que está tocando no Spotify, usando mapas do Clone Hero que você mesmo importa. É um projeto de uso pessoal e não é publicado na Play Store (veja a seção 3 da especificação).

- Especificação: `docs/superpowers/specs/2026-10-05-riff-design.md`
- Planos: `docs/superpowers/plans/`

## Requisitos

- Android Studio (o JBR 21 é usado como JDK) e Android SDK com a plataforma 36
- No WSL, o Gradle roda pelo `./scripts/gw`, que chama o `gradlew.bat` do Windows

## Comandos

```bash
./scripts/gw :app:assembleDebug                # APK de debug
./scripts/gw test                              # testes unitários
./scripts/gw detekt -PdetektAutoCorrect        # formata (ktlint) e corrige
./scripts/gw detekt                            # análise estática
./scripts/gw lintDebug                         # Android Lint
```

No Linux e no macOS, use `./gradlew` no lugar de `./scripts/gw`.
````

- [ ] **Step 5: Verificar**

Run: `./scripts/gw detekt lintDebug test assembleDebug`
Expected: `BUILD SUCCESSFUL`. O lint pode emitir avisos (por exemplo, a falta de ícone), mas nenhum erro.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "build: detekt com ktlint, CI no GitHub Actions e README" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Design system (`:core:ui`) e catálogo no app

**Files:**
- Modify: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/kotlin/com/rodrigomachado/riff/MainActivity.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`
- Create: `core/ui/build.gradle.kts`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/Color.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/ExtendedColors.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/LaneColors.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/Spacing.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/Type.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/theme/RiffTheme.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/StatusBadge.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/TrackCard.kt`
- Create: `core/ui/src/main/kotlin/com/rodrigomachado/riff/core/ui/component/StatusViews.kt`
- Create: `core/ui/src/main/res/values/strings.xml`, `core/ui/src/main/res/values-en/strings.xml`
- Create: `app/src/main/kotlin/com/rodrigomachado/riff/CatalogScreen.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/ComponentScreenshotTest.kt`
- Test: `core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/ErrorStateTest.kt`

**Interfaces:**
- Produces:
  - `RiffTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content)` e `RiffTheme.extendedColors: ExtendedColors`
  - `LaneColors.all: List<Color>` (5 cores) e `LaneColors.forLane(lane: Int): Color`
  - `Spacing.xs/sm/md/lg/xl` (4, 8, 16, 24 e 32 dp) e `ScoreTextStyle: TextStyle`
  - `StatusBadge(text, tone: BadgeTone, modifier)` com `BadgeTone { SUCCESS, WARNING, NEUTRAL, ERROR }`
  - `TrackCard(title, artist, modifier, onClick: (() -> Unit)? = null, badge: (@Composable () -> Unit)? = null)`
  - `LoadingState(modifier, message: String? = null)`
  - `EmptyState(title, message, modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null)`
  - `ErrorState(message, onRetry, modifier)` e `StatusViewsTestTags.RETRY = "status_views_retry"`

- [ ] **Step 1: Registrar o módulo**

Em `settings.gradle.kts`, troque `include(":app")` por:
```kotlin
include(":app")
include(":core:ui")
```
Em `build.gradle.kts`, no bloco `plugins`, adicione `alias(libs.plugins.roborazzi) apply false`.

`core/ui/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.library.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.core.ui"
}

dependencies {
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
```

- [ ] **Step 2: Escrever os testes que falham**

`core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/ErrorStateTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.StatusViewsTestTags
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ErrorStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun retryButtonCallsOnRetry() {
        var retries = 0
        composeRule.setContent {
            RiffTheme(dynamicColor = false) { ErrorState(message = "Falhou", onRetry = { retries++ }) }
        }

        composeRule.onNodeWithTag(StatusViewsTestTags.RETRY).performClick()

        assertEquals(1, retries)
    }
}
```

`core/ui/src/test/kotlin/com/rodrigomachado/riff/core/ui/ComponentScreenshotTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class ComponentScreenshotTest {

    @Test
    fun trackCardDark() = snapshot("TrackCard_dark", darkTheme = true) { SampleTrackCard() }

    @Test
    fun trackCardLight() = snapshot("TrackCard_light", darkTheme = false) { SampleTrackCard() }

    @Test
    fun statusBadgesDark() = snapshot("StatusBadge_dark", darkTheme = true) { AllBadges() }

    @Test
    fun statusBadgesLight() = snapshot("StatusBadge_light", darkTheme = false) { AllBadges() }

    @Test
    fun emptyState() = snapshot("EmptyState_dark", darkTheme = true) {
        EmptyState(
            title = "Nenhum mapa importado",
            message = "Importe uma pasta com mapas do Clone Hero para começar.",
            actionLabel = "Importar pasta",
            onAction = {},
        )
    }

    @Test
    fun errorState() = snapshot("ErrorState_dark", darkTheme = true) {
        ErrorState(message = "Não foi possível conectar ao Spotify.", onRetry = {})
    }

    @Composable
    private fun SampleTrackCard() {
        TrackCard(
            title = "Through the Fire and Flames",
            artist = "DragonForce",
            onClick = {},
            badge = { StatusBadge("Mapa encontrado", BadgeTone.SUCCESS) },
        )
    }

    @Composable
    private fun AllBadges() {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BadgeTone.entries.forEach { StatusBadge(it.name, it) }
        }
    }

    private fun snapshot(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface {
                    Box(Modifier.padding(Spacing.md)) { content() }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Confirmar que falham**

Run: `./scripts/gw :core:ui:testDebugUnitTest`
Expected: falha de compilação (`Unresolved reference: RiffTheme`, `ErrorState` etc.).

- [ ] **Step 4: Tema**

`theme/Color.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF8B5000),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCBE),
    onSecondaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7A757F),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378A),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFFFB86B),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6A3C00),
    onSecondaryContainer = Color(0xFFFFDCBE),
    background = Color(0xFF121016),
    onBackground = Color(0xFFE7E0E8),
    surface = Color(0xFF121016),
    onSurface = Color(0xFFE7E0E8),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCAC4CF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF948F99),
)
```

`theme/ExtendedColors.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Cores de status fora do esquema Material. Ficam fixas mesmo com Dynamic Color, para manter o significado. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
)

internal val LightExtendedColors = ExtendedColors(
    success = Color(0xFFB7F1B0),
    onSuccess = Color(0xFF002204),
    warning = Color(0xFFFFDDB3),
    onWarning = Color(0xFF291800),
)

internal val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF1E5223),
    onSuccess = Color(0xFFB7F1B0),
    warning = Color(0xFF633F00),
    onWarning = Color(0xFFFFDDB3),
)

internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
```

`theme/LaneColors.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.ui.graphics.Color

/** Cores das 5 trilhas (verde, vermelho, amarelo, azul, laranja). São fixas para o jogador memorizá-las. */
object LaneColors {
    val all: List<Color> = listOf(
        Color(0xFF2ECC71),
        Color(0xFFE74C3C),
        Color(0xFFF1C40F),
        Color(0xFF3498DB),
        Color(0xFFE67E22),
    )

    fun forLane(lane: Int): Color = all[lane]
}
```

`theme/Spacing.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
```

`theme/Type.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pontuação com algarismos de largura fixa, para o número não tremer ao mudar. */
val ScoreTextStyle = TextStyle(
    fontSize = 32.sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)
```

`theme/RiffTheme.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

@Composable
fun RiffTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

object RiffTheme {
    val extendedColors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
```

- [ ] **Step 5: Componentes e strings**

`core/ui/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="core_ui_retry">Tentar de novo</string>
</resources>
```
`core/ui/src/main/res/values-en/strings.xml`:
```xml
<resources>
    <string name="core_ui_retry">Try again</string>
</resources>
```

`component/StatusBadge.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.core.ui.theme.Spacing

enum class BadgeTone { SUCCESS, WARNING, NEUTRAL, ERROR }

@Composable
fun StatusBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val extended = RiffTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        BadgeTone.SUCCESS -> extended.success to extended.onSuccess
        BadgeTone.WARNING -> extended.warning to extended.onWarning
        BadgeTone.NEUTRAL -> scheme.surfaceVariant to scheme.onSurfaceVariant
        BadgeTone.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
```

`component/TrackCard.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.theme.Spacing

@Composable
fun TrackCard(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    val cardModifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (badge != null) {
                Spacer(Modifier.width(Spacing.sm))
                badge()
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = cardModifier, content = content)
    } else {
        Card(modifier = cardModifier, content = content)
    }
}
```

`component/StatusViews.kt`:
```kotlin
package com.rodrigomachado.riff.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.rodrigomachado.riff.core.ui.R
import com.rodrigomachado.riff.core.ui.theme.Spacing

object StatusViewsTestTags {
    const val RETRY = "status_views_retry"
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    CenteredColumn(modifier) {
        CircularProgressIndicator()
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CenteredColumn(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    CenteredColumn(modifier) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onRetry, modifier = Modifier.testTag(StatusViewsTestTags.RETRY)) {
            Text(stringResource(R.string.core_ui_retry))
        }
    }
}

@Composable
private fun CenteredColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
```

- [ ] **Step 6: Gravar os screenshots e rodar os testes**

Run: `./scripts/gw :core:ui:recordRoborazziDebug` e depois `./scripts/gw :core:ui:verifyRoborazziDebug`
Expected: os dois com `BUILD SUCCESSFUL`, e 6 PNGs em `core/ui/src/test/screenshots/`. Abra os PNGs com a ferramenta Read e confira: cartão legível nos dois temas, badges com contraste e botão "Tentar de novo" visível. Se algo estiver ilegível, corrija antes do commit.

- [ ] **Step 7: Catálogo no app**

`app/build.gradle.kts`: em `dependencies`, adicione `implementation(project(":core:ui"))`.

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name" translatable="false">Riff</string>
    <string name="catalog_title">Design system</string>
    <string name="catalog_badge_matched">Mapa encontrado</string>
    <string name="catalog_badge_confirm">Confirmar mapa</string>
    <string name="catalog_badge_no_chart">Sem mapa</string>
    <string name="catalog_empty_title">Nenhum mapa importado</string>
    <string name="catalog_empty_message">Importe uma pasta com mapas do Clone Hero para começar.</string>
    <string name="catalog_empty_action">Importar pasta</string>
    <string name="catalog_error_message">Não foi possível conectar ao Spotify.</string>
</resources>
```
`app/src/main/res/values-en/strings.xml`:
```xml
<resources>
    <string name="catalog_title">Design system</string>
    <string name="catalog_badge_matched">Chart found</string>
    <string name="catalog_badge_confirm">Confirm chart</string>
    <string name="catalog_badge_no_chart">No chart</string>
    <string name="catalog_empty_title">No charts imported</string>
    <string name="catalog_empty_message">Import a folder with Clone Hero charts to get started.</string>
    <string name="catalog_empty_action">Import folder</string>
    <string name="catalog_error_message">Couldn\'t connect to Spotify.</string>
</resources>
```

`app/src/main/kotlin/com/rodrigomachado/riff/CatalogScreen.kt`:
```kotlin
package com.rodrigomachado.riff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.ErrorState
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.theme.LaneColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

/** Vitrine temporária do design system, até as features existirem. */
@Composable
fun CatalogScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.headlineSmall) }
        item {
            TrackCard("Through the Fire and Flames", "DragonForce", onClick = {}, badge = {
                StatusBadge(stringResource(R.string.catalog_badge_matched), BadgeTone.SUCCESS)
            })
        }
        item {
            TrackCard("Bohemian Rhapsody", "Queen", onClick = {}, badge = {
                StatusBadge(stringResource(R.string.catalog_badge_confirm), BadgeTone.WARNING)
            })
        }
        item {
            TrackCard("Garota de Ipanema", "Tom Jobim", badge = {
                StatusBadge(stringResource(R.string.catalog_badge_no_chart), BadgeTone.NEUTRAL)
            })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LaneColors.all.forEach { Box(Modifier.size(40.dp).background(it, CircleShape)) }
            }
        }
        item {
            Box(Modifier.height(260.dp)) {
                EmptyState(
                    title = stringResource(R.string.catalog_empty_title),
                    message = stringResource(R.string.catalog_empty_message),
                    actionLabel = stringResource(R.string.catalog_empty_action),
                    onAction = {},
                )
            }
        }
        item {
            Box(Modifier.height(200.dp)) {
                ErrorState(message = stringResource(R.string.catalog_error_message), onRetry = {})
            }
        }
    }
}
```

`MainActivity.kt`: troque o `setContent` por:
```kotlin
        setContent {
            RiffTheme {
                Scaffold(Modifier.fillMaxSize()) { padding ->
                    CatalogScreen(Modifier.padding(padding))
                }
            }
        }
```
Ajuste os imports: remova `MaterialTheme`, `Surface`, `Text` e `stringResource`. Adicione `androidx.compose.foundation.layout.padding`, `androidx.compose.material3.Scaffold` e `com.rodrigomachado.riff.core.ui.theme.RiffTheme`.

- [ ] **Step 8: Verificar e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:ui:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, com `ErrorStateTest` e os 6 testes de screenshot passando.

```bash
git add -A
git commit -m "feat(core-ui): design system com tema, cores das trilhas, componentes e screenshots" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Modelo de domínio (`:core:model`)

**Files:**
- Modify: `settings.gradle.kts` (adicione `include(":core:model")`)
- Create: `core/model/build.gradle.kts`
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/Chart.kt`
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/Note.kt`
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/PlaybackState.kt`
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/TrackMatch.kt`
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/Game.kt`
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/ScoreRecord.kt`
- Create: `core/model/src/main/kotlin/com/rodrigomachado/riff/core/model/Settings.kt`
- Test: `core/model/src/test/kotlin/com/rodrigomachado/riff/core/model/NoteTest.kt`

**Interfaces:**
- Produces (pacote `com.rodrigomachado.riff.core.model`):
  - `Chart`, `ChartFormat { CHART, MIDI }` e `Difficulty { EASY, MEDIUM, HARD, EXPERT }`
  - `Note(timeMs: Long, lane: Int, sustainMs: Long, isHopo: Boolean)`, com `Note.LANE_COUNT = 5` e validação de `lane in 0..4` e `sustainMs >= 0`
  - `ChartTrack(chartId, difficulty, notes)`
  - `PlaybackState`, `TrackMatch`, `ScoreRecord` e `Settings` (com `Settings.DEFAULT`)
  - `Judgment { PERFECT, GOOD, MISS }`
  - `GameState(songTimeMs, score, combo, maxCombo, multiplier, counts: Map<Judgment, Int>, activeSustains: Set<Int>)`

- [ ] **Step 1: Teste que falha**

`core/model/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.jvm.library)
}
```

`NoteTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NoteTest {
    @Test
    fun acceptsAllFiveLanes() {
        val lanes = (0 until Note.LANE_COUNT).map { Note(timeMs = 0, lane = it, sustainMs = 0, isHopo = false).lane }
        assertEquals(listOf(0, 1, 2, 3, 4), lanes)
    }

    @Test
    fun rejectsLaneOutsideRange() {
        assertThrows<IllegalArgumentException> { Note(timeMs = 0, lane = 5, sustainMs = 0, isHopo = false) }
        assertThrows<IllegalArgumentException> { Note(timeMs = 0, lane = -1, sustainMs = 0, isHopo = false) }
    }

    @Test
    fun rejectsNegativeSustain() {
        assertThrows<IllegalArgumentException> { Note(timeMs = 0, lane = 0, sustainMs = -1, isHopo = false) }
    }
}
```

Run: `./scripts/gw :core:model:test`
Expected: falha de compilação (`Unresolved reference: Note`).

- [ ] **Step 2: Implementar os modelos**

`Chart.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

enum class ChartFormat { CHART, MIDI }

enum class Difficulty { EASY, MEDIUM, HARD, EXPERT }

/** Mapa importado. [id] é o SHA-1 do arquivo de notas, então reimportar o mesmo mapa não duplica. */
data class Chart(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val charter: String?,
    val durationMs: Long?,
    val folderUri: String,
    val format: ChartFormat,
    val difficulties: Set<Difficulty>,
)
```

`Note.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

/** Uma nota já convertida para milissegundos. Um acorde é representado por várias notas com o mesmo [timeMs]. */
data class Note(
    val timeMs: Long,
    val lane: Int,
    val sustainMs: Long,
    val isHopo: Boolean,
) {
    init {
        require(lane in 0 until LANE_COUNT) { "lane fora de 0..4: $lane" }
        require(sustainMs >= 0) { "sustainMs negativo: $sustainMs" }
    }

    companion object {
        const val LANE_COUNT = 5
    }
}

/** Notas de uma dificuldade, em ordem de tempo. */
data class ChartTrack(val chartId: String, val difficulty: Difficulty, val notes: List<Note>)
```

`PlaybackState.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

/** Estado do player do Spotify. [capturedAtElapsedMs] é o relógio monotônico do momento da leitura. */
data class PlaybackState(
    val trackUri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val positionMs: Long,
    val isPaused: Boolean,
    val capturedAtElapsedMs: Long,
)
```

`TrackMatch.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

/** Ligação entre uma faixa do Spotify e um mapa. [trackOffsetMs] é o ajuste fino de sincronia desta música. */
data class TrackMatch(
    val spotifyTrackUri: String,
    val chartId: String,
    val confidence: Float,
    val isManual: Boolean,
    val trackOffsetMs: Int,
)
```

`Game.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

enum class Judgment { PERFECT, GOOD, MISS }

/** Fotografia do estado da partida, para a UI desenhar. */
data class GameState(
    val songTimeMs: Long,
    val score: Long,
    val combo: Int,
    val maxCombo: Int,
    val multiplier: Int,
    val counts: Map<Judgment, Int>,
    val activeSustains: Set<Int>,
)
```

`ScoreRecord.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

data class ScoreRecord(
    val id: Long,
    val chartId: String,
    val difficulty: Difficulty,
    val score: Long,
    val accuracy: Float,
    val maxCombo: Int,
    val perfect: Int,
    val good: Int,
    val miss: Int,
    val playedAtEpochMs: Long,
)
```

`Settings.kt`:
```kotlin
package com.rodrigomachado.riff.core.model

/** Preferências do jogador. [audioOffsetMs] negativo compensa atraso de áudio (fone Bluetooth). */
data class Settings(
    val audioOffsetMs: Int,
    val noteSpeed: Float,
    val leftHanded: Boolean,
    val defaultDifficulty: Difficulty,
) {
    companion object {
        val DEFAULT = Settings(audioOffsetMs = 0, noteSpeed = 1f, leftHanded = false, defaultDifficulty = Difficulty.MEDIUM)
    }
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:model:test`
Expected: `BUILD SUCCESSFUL`, 3 testes passando.

```bash
git add -A
git commit -m "feat(core-model): modelos de domínio" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Mapa de andamento e montagem de notas (`:core:chart`)

**Files:**
- Modify: `settings.gradle.kts` (adicione `include(":core:chart")`), `build.gradle.kts`, `.github/workflows/ci.yml`
- Create: `core/chart/build.gradle.kts`
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/TempoMap.kt`
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/NoteAssembler.kt`
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/ParsedChart.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/TempoMapTest.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/NoteAssemblerTest.kt`

**Interfaces:**
- Consumes: `Note`, `Difficulty` (Task 4).
- Produces (pacote `com.rodrigomachado.riff.core.chart`):
  - `class TempoMap(resolution: Int, changes: List<TempoMap.TempoChange>)` com `fun tickToMs(tick: Long): Double`. Também `TempoMap.TempoChange(tick: Long, microsPerQuarter: Long)`, `TempoMap.DEFAULT_MICROS_PER_QUARTER = 500_000L` e `TempoMap.microsPerQuarterFromMilliBpm(milliBpm: Long): Long`.
  - `internal enum class Forcing { NONE, FLIP, HOPO, STRUM }`
  - `internal data class RawChord(tick: Long, lengthsByLane: Map<Int, Long>, forcing: Forcing = NONE, isTap: Boolean = false)`
  - `internal object NoteAssembler { fun assemble(chords: List<RawChord>, tempoMap: TempoMap, hopoThresholdTicks: Long): List<Note> }`
  - `data class SongInfo(title, artist, album, charter, songLengthMs, offsetMs = 0)`, todos os campos anuláveis e com padrão `null`
  - `data class ParsedChart(info: SongInfo, tracks: Map<Difficulty, List<Note>>)`. As notas não incluem `info.offsetMs`, que é aplicado pelo `ChartLoader`.
  - `class ChartParseException(message: String, line: Int? = null)`. A mensagem fica com o prefixo `"linha N: "` quando há linha.

- [ ] **Step 1: Módulo e Kover**

`core/chart/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.jvm.library)
    alias(libs.plugins.kover)
}

dependencies {
    api(project(":core:model"))
}
```

`build.gradle.kts` (raiz): adicione `alias(libs.plugins.kover)` ao bloco `plugins` e, no fim do arquivo:
```kotlin
kover {
    reports {
        verify {
            rule {
                minBound(70)
            }
        }
    }
}
```
No bloco `dependencies` da raiz, adicione `kover(project(":core:chart"))`.

Em `.github/workflows/ci.yml`, troque a linha `run:` por:
```yaml
        run: ./gradlew detekt lintDebug test koverVerify assembleDebug
```

- [ ] **Step 2: Testes que falham**

`TempoMapTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.chart.TempoMap.TempoChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TempoMapTest {
    @Test
    fun defaultsTo120BpmWithoutTempoChanges() {
        val map = TempoMap(resolution = 192, changes = emptyList())
        assertEquals(500.0, map.tickToMs(192), 0.001)
    }

    @Test
    fun appliesTempoChangeMidSong() {
        val map = TempoMap(192, listOf(TempoChange(0, 500_000), TempoChange(384, 1_000_000)))
        assertEquals(1000.0, map.tickToMs(384), 0.001)
        assertEquals(2000.0, map.tickToMs(576), 0.001)
    }

    @Test
    fun assumes120BpmBeforeFirstChangeWhenItIsNotAtZero() {
        val map = TempoMap(192, listOf(TempoChange(192, 1_000_000)))
        assertEquals(500.0, map.tickToMs(192), 0.001)
        assertEquals(1500.0, map.tickToMs(384), 0.001)
    }

    @Test
    fun convertsMilliBpmToMicrosPerQuarter() {
        assertEquals(500_000L, TempoMap.microsPerQuarterFromMilliBpm(120_000))
        assertEquals(400_000L, TempoMap.microsPerQuarterFromMilliBpm(150_000))
    }
}
```

`NoteAssemblerTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteAssemblerTest {
    private val tempo = TempoMap(192, emptyList()) // 120 BPM: 192 ticks = 500 ms
    private val threshold = 65L

    private fun assemble(vararg chords: RawChord) = NoteAssembler.assemble(chords.toList(), tempo, threshold)

    private fun single(tick: Long, lane: Int, length: Long = 0, forcing: Forcing = Forcing.NONE, tap: Boolean = false) =
        RawChord(tick, mapOf(lane to length), forcing, tap)

    @Test
    fun farApartNotesAreStrums() {
        val notes = assemble(single(0, 0), single(192, 1))
        assertEquals(listOf(Note(0, 0, 0, false), Note(500, 1, 0, false)), notes)
    }

    @Test
    fun closeNoteOnDifferentLaneIsNaturalHopo() {
        val notes = assemble(single(0, 0), single(48, 1))
        assertEquals(true, notes[1].isHopo)
    }

    @Test
    fun closeNoteOnSameLaneIsNotHopo() {
        val notes = assemble(single(0, 1), single(48, 1))
        assertEquals(false, notes[1].isHopo)
    }

    @Test
    fun chordIsNeverNaturalHopo() {
        val notes = assemble(single(0, 0), RawChord(48, mapOf(1 to 0L, 2 to 0L)))
        assertEquals(listOf(false, false), notes.drop(1).map { it.isHopo })
    }

    @Test
    fun flipForcingInvertsNaturalHopo() {
        val notes = assemble(single(0, 0), single(48, 1, forcing = Forcing.FLIP), single(240, 2, forcing = Forcing.FLIP))
        assertEquals(listOf(false, false, true), notes.map { it.isHopo })
    }

    @Test
    fun explicitForcingWins() {
        val notes = assemble(single(0, 0), single(48, 1, forcing = Forcing.STRUM), single(240, 2, forcing = Forcing.HOPO))
        assertEquals(listOf(false, false, true), notes.map { it.isHopo })
    }

    @Test
    fun tapIsAlwaysHopo() {
        assertEquals(true, assemble(single(0, 3, tap = true)).single().isHopo)
    }

    @Test
    fun convertsSustainLengthToMs() {
        assertEquals(500L, assemble(single(0, 0, length = 192)).single().sustainMs)
    }

    @Test
    fun chordProducesOneNotePerLaneInLaneOrder() {
        val notes = assemble(RawChord(192, mapOf(3 to 0L, 1 to 0L)))
        assertEquals(listOf(Note(500, 1, 0, false), Note(500, 3, 0, false)), notes)
    }

    @Test
    fun skipsChordsWithoutLanes() {
        val notes = assemble(single(0, 0), RawChord(24, emptyMap()), single(48, 1))
        assertEquals(listOf(0L, 125L), notes.map { it.timeMs })
        assertEquals(true, notes[1].isHopo)
    }
}
```

Run: `./scripts/gw :core:chart:test`
Expected: falha de compilação (`Unresolved reference: TempoMap`).

- [ ] **Step 3: Implementar**

`TempoMap.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

/** Converte ticks em milissegundos levando em conta as mudanças de andamento. */
class TempoMap(private val resolution: Int, changes: List<TempoChange>) {

    /** Mudança de andamento no [tick], em microssegundos por semínima (a unidade do MIDI). */
    data class TempoChange(val tick: Long, val microsPerQuarter: Long)

    private class Segment(val startTick: Long, val startMs: Double, val microsPerQuarter: Long)

    private val segments: List<Segment>

    init {
        require(resolution > 0) { "resolução precisa ser positiva: $resolution" }
        val sorted = changes.sortedBy { it.tick }
        val withStart = if (sorted.firstOrNull()?.tick == 0L) {
            sorted
        } else {
            listOf(TempoChange(0, DEFAULT_MICROS_PER_QUARTER)) + sorted
        }
        val built = ArrayList<Segment>(withStart.size)
        for (change in withStart) {
            val previous = built.lastOrNull()
            val startMs = previous?.let {
                it.startMs + ticksToMs(change.tick - it.startTick, it.microsPerQuarter)
            } ?: 0.0
            built += Segment(change.tick, startMs, change.microsPerQuarter)
        }
        segments = built
    }

    fun tickToMs(tick: Long): Double {
        val segment = segments.last { it.startTick <= tick }
        return segment.startMs + ticksToMs(tick - segment.startTick, segment.microsPerQuarter)
    }

    private fun ticksToMs(ticks: Long, microsPerQuarter: Long): Double =
        ticks.toDouble() * microsPerQuarter / resolution / MICROS_PER_MS

    companion object {
        /** 120 BPM, o andamento padrão quando o arquivo não define um no tick 0. */
        const val DEFAULT_MICROS_PER_QUARTER = 500_000L
        private const val MICROS_PER_MS = 1000.0
        private const val MICROS_PER_MINUTE_TIMES_1000 = 60_000_000_000L

        fun microsPerQuarterFromMilliBpm(milliBpm: Long): Long {
            require(milliBpm > 0) { "andamento precisa ser positivo: $milliBpm" }
            return MICROS_PER_MINUTE_TIMES_1000 / milliBpm
        }
    }
}
```

`NoteAssembler.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Note
import kotlin.math.roundToLong

/** Como o arquivo força (ou não) um acorde a ser HOPO. */
internal enum class Forcing {
    NONE,

    /** .chart: inverte o HOPO natural. */
    FLIP,

    /** MIDI: força HOPO. */
    HOPO,

    /** MIDI: força palhetada. */
    STRUM,
}

/** Notas que começam no mesmo tick (acorde ou nota simples), ainda em ticks. */
internal data class RawChord(
    val tick: Long,
    val lengthsByLane: Map<Int, Long>,
    val forcing: Forcing = Forcing.NONE,
    val isTap: Boolean = false,
)

/** Converte acordes em ticks em [Note]s em ms, calculando HOPO natural e forçado. */
internal object NoteAssembler {
    fun assemble(chords: List<RawChord>, tempoMap: TempoMap, hopoThresholdTicks: Long): List<Note> {
        val notes = ArrayList<Note>()
        var previous: RawChord? = null
        for (chord in chords.filter { it.lengthsByLane.isNotEmpty() }.sortedBy { it.tick }) {
            val isHopo = chord.isTap || resolveHopo(chord, previous, hopoThresholdTicks)
            val startMs = tempoMap.tickToMs(chord.tick)
            for ((lane, lengthTicks) in chord.lengthsByLane.toSortedMap()) {
                val sustainMs = if (lengthTicks > 0) tempoMap.tickToMs(chord.tick + lengthTicks) - startMs else 0.0
                notes += Note(startMs.roundToLong(), lane, sustainMs.roundToLong(), isHopo)
            }
            previous = chord
        }
        return notes
    }

    private fun resolveHopo(chord: RawChord, previous: RawChord?, thresholdTicks: Long): Boolean {
        val natural = previous != null &&
            chord.lengthsByLane.size == 1 &&
            chord.tick - previous.tick <= thresholdTicks &&
            chord.lengthsByLane.keys.single() !in previous.lengthsByLane.keys
        return when (chord.forcing) {
            Forcing.NONE -> natural
            Forcing.FLIP -> !natural
            Forcing.HOPO -> true
            Forcing.STRUM -> false
        }
    }
}
```

`ParsedChart.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note

/** Metadados de uma música, vindos do song.ini ou da seção [Song] do .chart. */
data class SongInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val charter: String? = null,
    val songLengthMs: Long? = null,
    /** Deslocamento a somar no tempo de todas as notas. */
    val offsetMs: Long = 0,
)

/** Resultado de um parser. As notas ainda não incluem [SongInfo.offsetMs]. */
data class ParsedChart(val info: SongInfo, val tracks: Map<Difficulty, List<Note>>)

class ChartParseException(message: String, val line: Int? = null) :
    Exception(if (line != null) "linha $line: $message" else message)
```

- [ ] **Step 4: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:chart:test`
Expected: `BUILD SUCCESSFUL`, 14 testes passando.

```bash
git add -A
git commit -m "feat(core-chart): mapa de andamento e montagem de notas com HOPO" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Parser de `.chart`

**Files:**
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/ChartFileParser.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/ChartFileParserTest.kt`

**Interfaces:**
- Consumes: `TempoMap`, `NoteAssembler`, `RawChord`, `Forcing`, `SongInfo`, `ParsedChart` e `ChartParseException` (Task 5).
- Produces: `object ChartFileParser { fun parse(text: String): ParsedChart }`. As seções `EasySingle`, `MediumSingle`, `HardSingle` e `ExpertSingle` viram dificuldades. Dificuldades sem notas ficam de fora do mapa.

- [ ] **Step 1: Testes que falham**

`ChartFileParserTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChartFileParserTest {
    // Resolução 192. 120 BPM até o tick 768 (2000 ms) e 60 BPM depois (1000 ms por semínima).
    private val fullChart = """
        [Song]
        {
          Name = "Minha Música"
          Artist = "Banda"
          Album = "Disco"
          Charter = "Fulano"
          Offset = 0.25
          Resolution = 192
        }
        [SyncTrack]
        {
          0 = TS 4
          0 = B 120000
          768 = B 60000
        }
        [ExpertSingle]
        {
          192 = N 0 0
          384 = N 1 192
          768 = N 2 0
          768 = N 3 0
          960 = N 4 0
          1008 = N 3 0
          1008 = N 5 0
          1200 = N 7 0
          1392 = N 0 0
          1392 = N 6 0
        }
        [EasySingle]
        {
          192 = N 0 0
        }
        [HardSingle]
        {
        }
    """.trimIndent()

    @Test
    fun readsSongMetadataAndOffset() {
        val info = ChartFileParser.parse(fullChart).info
        assertEquals(SongInfo("Minha Música", "Banda", "Disco", "Fulano", null, 250), info)
    }

    @Test
    fun convertsExpertNotesWithTempoChangeSustainsChordsAndFlags() {
        val expert = ChartFileParser.parse(fullChart).tracks.getValue(Difficulty.EXPERT)
        assertEquals(
            listOf(
                Note(500, 0, 0, false),
                Note(1000, 1, 500, false),
                Note(2000, 2, 0, false),
                Note(2000, 3, 0, false),
                Note(3000, 4, 0, false),
                Note(3250, 3, 0, false), // HOPO natural invertido pela flag N 5
                Note(5250, 0, 0, true), // tap (N 6); a nota aberta (N 7) do tick 1200 é ignorada
            ),
            expert,
        )
    }

    @Test
    fun omitsMissingAndEmptyDifficulties() {
        assertEquals(setOf(Difficulty.EASY, Difficulty.EXPERT), ChartFileParser.parse(fullChart).tracks.keys)
    }

    @Test
    fun detectsNaturalHopo() {
        val chart = "[ExpertSingle]\n{\n  0 = N 0 0\n  48 = N 1 0\n  96 = N 1 0\n}"
        val notes = ChartFileParser.parse(chart).tracks.getValue(Difficulty.EXPERT)
        assertEquals(listOf(false, true, false), notes.map { it.isHopo })
    }

    @Test
    fun assumes120BpmAndResolution192WithoutSongOrSyncTrack() {
        val chart = "[ExpertSingle]\n{\n  192 = N 0 0\n}"
        val parsed = ChartFileParser.parse(chart)
        assertEquals(500L, parsed.tracks.getValue(Difficulty.EXPERT).single().timeMs)
        assertNull(parsed.info.title)
    }

    @Test
    fun handlesUtf8BomAndCrlfLineEndings() {
        val chart = "\uFEFF[Song]\r\n{\r\n  Resolution = 480\r\n}\r\n[ExpertSingle]\r\n{\r\n  480 = N 2 0\r\n}\r\n"
        assertEquals(listOf(Note(500, 2, 0, false)), ChartFileParser.parse(chart).tracks.getValue(Difficulty.EXPERT))
    }

    @Test
    fun reportsLineOfInvalidNote() {
        val error = assertThrows<ChartParseException> { ChartFileParser.parse("[ExpertSingle]\n{\n  abc = N 0 0\n}") }
        assertEquals(3, error.line)
        assertTrue(error.message!!.startsWith("linha 3:"))
    }

    @Test
    fun rejectsZeroTempo() {
        val chart = "[SyncTrack]\n{\n  0 = B 0\n}\n[ExpertSingle]\n{\n  0 = N 0 0\n}"
        assertEquals(3, assertThrows<ChartParseException> { ChartFileParser.parse(chart) }.line)
    }
}
```

Run: `./scripts/gw :core:chart:test`
Expected: falha de compilação (`Unresolved reference: ChartFileParser`).

- [ ] **Step 2: Implementar**

`ChartFileParser.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import kotlin.math.roundToLong

/** Lê o formato .chart (texto) do Clone Hero e do Moonscraper. */
object ChartFileParser {
    private val TRACK_SECTIONS = mapOf(
        "EasySingle" to Difficulty.EASY,
        "MediumSingle" to Difficulty.MEDIUM,
        "HardSingle" to Difficulty.HARD,
        "ExpertSingle" to Difficulty.EXPERT,
    )
    private const val DEFAULT_RESOLUTION = 192
    private const val FORCED_FLAG = 5
    private const val TAP_FLAG = 6

    // Limite do HOPO natural do Clone Hero: 65/192 de semínima.
    private const val HOPO_THRESHOLD_NUMERATOR = 65L
    private const val HOPO_THRESHOLD_DENOMINATOR = 192L
    private val WHITESPACE = Regex("\\s+")

    fun parse(text: String): ParsedChart {
        val sections = readSections(text)
        val song = sections["Song"].orEmpty().associate { it.key to it.value.removeSurrounding("\"") }
        val resolution = song["Resolution"]?.toIntOrNull() ?: DEFAULT_RESOLUTION
        val tempoMap = TempoMap(resolution, readTempos(sections["SyncTrack"].orEmpty()))
        val threshold = resolution * HOPO_THRESHOLD_NUMERATOR / HOPO_THRESHOLD_DENOMINATOR
        val tracks = TRACK_SECTIONS.mapNotNull { (name, difficulty) ->
            val entries = sections[name] ?: return@mapNotNull null
            val notes = NoteAssembler.assemble(readChords(entries), tempoMap, threshold)
            if (notes.isEmpty()) null else difficulty to notes
        }.toMap()
        val info = SongInfo(
            title = song["Name"],
            artist = song["Artist"],
            album = song["Album"],
            charter = song["Charter"],
            offsetMs = song["Offset"]?.toDoubleOrNull()?.let { (it * 1000).roundToLong() } ?: 0,
        )
        return ParsedChart(info, tracks)
    }

    private data class Entry(val line: Int, val key: String, val value: String)

    private fun readSections(text: String): Map<String, List<Entry>> {
        val sections = mutableMapOf<String, MutableList<Entry>>()
        var current: MutableList<Entry>? = null
        text.removePrefix("\uFEFF").lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() || line == "{" -> Unit
                line == "}" -> current = null
                line.startsWith("[") && line.endsWith("]") ->
                    current = sections.getOrPut(line.substring(1, line.length - 1)) { mutableListOf() }
                else -> {
                    val separator = line.indexOf('=')
                    if (separator > 0) {
                        current?.add(Entry(index + 1, line.substring(0, separator).trim(), line.substring(separator + 1).trim()))
                    }
                }
            }
        }
        return sections
    }

    private fun readTempos(entries: List<Entry>): List<TempoMap.TempoChange> = entries.mapNotNull { entry ->
        val parts = entry.value.split(WHITESPACE)
        if (parts.first() != "B") return@mapNotNull null
        val tick = entry.key.toLongOrNull() ?: throw ChartParseException("tick inválido: ${entry.key}", entry.line)
        val milliBpm = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw ChartParseException("andamento inválido: ${entry.value}", entry.line)
        TempoMap.TempoChange(tick, TempoMap.microsPerQuarterFromMilliBpm(milliBpm))
    }

    private class ChordBuilder {
        val lengths = mutableMapOf<Int, Long>()
        var forced = false
        var tap = false
    }

    private fun readChords(entries: List<Entry>): List<RawChord> {
        val byTick = sortedMapOf<Long, ChordBuilder>()
        for (entry in entries) {
            val parts = entry.value.split(WHITESPACE)
            if (parts.first() != "N") continue
            val tick = entry.key.toLongOrNull()
            val fret = parts.getOrNull(1)?.toIntOrNull()
            val length = parts.getOrNull(2)?.toLongOrNull()
            if (tick == null || fret == null || length == null || length < 0) {
                throw ChartParseException("nota inválida: ${entry.key} = ${entry.value}", entry.line)
            }
            val chord = byTick.getOrPut(tick) { ChordBuilder() }
            when (fret) {
                in 0 until Note.LANE_COUNT -> chord.lengths[fret] = length
                FORCED_FLAG -> chord.forced = true
                TAP_FLAG -> chord.tap = true
                else -> Unit // 7 = nota aberta, fora do MVP
            }
        }
        return byTick.map { (tick, chord) ->
            RawChord(tick, chord.lengths, if (chord.forced) Forcing.FLIP else Forcing.NONE, chord.tap)
        }
    }
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:chart:test`
Expected: `BUILD SUCCESSFUL`, 22 testes passando.

```bash
git add -A
git commit -m "feat(core-chart): parser de .chart" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Parser de `song.ini`

**Files:**
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/SongIniParser.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/SongIniParserTest.kt`

**Interfaces:**
- Consumes: `SongInfo` (Task 5).
- Produces: `object SongIniParser { fun parse(text: String): SongInfo }`. As chaves lidas da seção `[song]` (sem diferenciar maiúsculas) são `name`, `artist`, `album`, `charter` (com `frets` como alternativa), `song_length` e `delay` (que vira `offsetMs`).

- [ ] **Step 1: Testes que falham**

`SongIniParserTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SongIniParserTest {
    @Test
    fun readsKnownKeys() {
        val ini = """
            [song]
            name = Minha Música
            artist = Banda
            album = Disco
            charter = Fulano
            song_length = 215000
            delay = -120
        """.trimIndent()
        assertEquals(SongInfo("Minha Música", "Banda", "Disco", "Fulano", 215_000, -120), SongIniParser.parse(ini))
    }

    @Test
    fun ignoresCaseOfSectionAndKeysAndUsesFretsAsCharter() {
        val ini = "[Song]\nNAME = Música\nArtist = Banda\nfrets = Beltrano"
        assertEquals(SongInfo(title = "Música", artist = "Banda", charter = "Beltrano"), SongIniParser.parse(ini))
    }

    @Test
    fun ignoresOtherSectionsCommentsAndEmptyValues() {
        val ini = "[other]\nname = Errado\n[song]\n; name = Comentário\nname = Certo\nartist =\nsong_length = 0"
        assertEquals(SongInfo(title = "Certo"), SongIniParser.parse(ini))
    }

    @Test
    fun handlesBomAndCrlf() {
        assertEquals(SongInfo(title = "X"), SongIniParser.parse("\uFEFF[song]\r\nname = X\r\n"))
    }
}
```

Run: `./scripts/gw :core:chart:test`
Expected: falha de compilação (`Unresolved reference: SongIniParser`).

- [ ] **Step 2: Implementar**

`SongIniParser.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

/** Lê o song.ini do Clone Hero (seção [song], chaves sem diferenciar maiúsculas). */
object SongIniParser {
    fun parse(text: String): SongInfo {
        val values = mutableMapOf<String, String>()
        var inSong = false
        for (rawLine in text.removePrefix("\uFEFF").lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("[") -> inSong = line.equals("[song]", ignoreCase = true)
                line.startsWith(";") || line.startsWith("#") -> Unit
                inSong && '=' in line ->
                    values[line.substringBefore('=').trim().lowercase()] = line.substringAfter('=').trim()
            }
        }
        fun value(key: String) = values[key]?.takeIf { it.isNotEmpty() }
        return SongInfo(
            title = value("name"),
            artist = value("artist"),
            album = value("album"),
            charter = value("charter") ?: value("frets"),
            songLengthMs = value("song_length")?.toLongOrNull()?.takeIf { it > 0 },
            offsetMs = value("delay")?.toLongOrNull() ?: 0,
        )
    }
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:chart:test`
Expected: `BUILD SUCCESSFUL`, 26 testes passando.

```bash
git add -A
git commit -m "feat(core-chart): parser de song.ini" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Parser de `notes.mid`

**Files:**
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/MidiReader.kt`
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/MidiChartParser.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/MidiBuilder.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/MidiChartParserTest.kt`

**Interfaces:**
- Consumes: `TempoMap`, `NoteAssembler`, `RawChord`, `Forcing`, `SongInfo`, `ParsedChart` e `ChartParseException` (Task 5).
- Produces: `object MidiChartParser { fun parse(bytes: ByteArray): ParsedChart }`.
  - Lê a trilha `PART GUITAR`. As teclas base são 60 (Easy), 72 (Medium), 84 (Hard) e 96 (Expert). Base+5 força HOPO e base+6 força palhetada, para as notas dentro do intervalo do marcador.
  - Sustain de até `resolução / 3` ticks vira 0. O limite do HOPO natural é `resolução × 170 / 480`.
  - O `info` vem vazio, porque os metadados ficam no song.ini.

- [ ] **Step 1: Builder de MIDI para os testes**

`MidiBuilder.kt` (em `src/test`):
```kotlin
package com.rodrigomachado.riff.core.chart

import java.io.ByteArrayOutputStream

/** Monta um Standard MIDI File (formato 1) em memória para os testes. */
class MidiBuilder(private val ticksPerQuarter: Int = 480) {
    private val tracks = mutableListOf<TrackBuilder>()

    fun track(name: String? = null, block: TrackBuilder.() -> Unit = {}): MidiBuilder {
        tracks += TrackBuilder(name).apply(block)
        return this
    }

    fun build(): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeAscii("MThd")
        out.writeInt32(6)
        out.writeInt16(1)
        out.writeInt16(tracks.size)
        out.writeInt16(ticksPerQuarter)
        for (track in tracks) {
            val body = track.encode()
            out.writeAscii("MTrk")
            out.writeInt32(body.size)
            out.write(body)
        }
        return out.toByteArray()
    }

    class TrackBuilder(private val name: String?) {
        private val events = mutableListOf<Pair<Long, ByteArray>>()

        fun tempo(tick: Long, microsPerQuarter: Int) =
            raw(tick, 0xFF, 0x51, 0x03, microsPerQuarter shr 16 and 0xFF, microsPerQuarter shr 8 and 0xFF, microsPerQuarter and 0xFF)

        fun note(tick: Long, key: Int, lengthTicks: Long) {
            raw(tick, 0x90, key, 100)
            raw(tick + lengthTicks, 0x80, key, 0)
        }

        /** Bytes crus após o delta time; sem byte de status, vira running status. */
        fun raw(tick: Long, vararg bytes: Int) {
            events += tick to ByteArray(bytes.size) { bytes[it].toByte() }
        }

        internal fun encode(): ByteArray {
            val out = ByteArrayOutputStream()
            if (name != null) {
                out.writeVarLen(0)
                out.write(0xFF)
                out.write(0x03)
                out.writeVarLen(name.length.toLong())
                out.writeAscii(name)
            }
            var lastTick = 0L
            for ((tick, bytes) in events.sortedBy { it.first }) {
                out.writeVarLen(tick - lastTick)
                out.write(bytes)
                lastTick = tick
            }
            out.writeVarLen(0)
            out.write(0xFF)
            out.write(0x2F)
            out.write(0x00)
            return out.toByteArray()
        }
    }
}

private fun ByteArrayOutputStream.writeAscii(text: String) = write(text.toByteArray(Charsets.US_ASCII))

private fun ByteArrayOutputStream.writeInt16(value: Int) {
    write(value shr 8 and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeInt32(value: Int) {
    writeInt16(value shr 16 and 0xFFFF)
    writeInt16(value and 0xFFFF)
}

private fun ByteArrayOutputStream.writeVarLen(value: Long) {
    var buffer = value and 0x7F
    var rest = value shr 7
    while (rest > 0) {
        buffer = (buffer shl 8) or ((rest and 0x7F) or 0x80)
        rest = rest shr 7
    }
    while (true) {
        write((buffer and 0xFF).toInt())
        if (buffer and 0x80 != 0L) buffer = buffer shr 8 else return
    }
}
```

- [ ] **Step 2: Testes que falham**

`MidiChartParserTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MidiChartParserTest {
    // 480 ticks por semínima, 120 BPM: 480 ticks = 500 ms.
    private fun midi(guitar: MidiBuilder.TrackBuilder.() -> Unit, tempos: MidiBuilder.TrackBuilder.() -> Unit = { tempo(0, 500_000) }) =
        MidiBuilder().track(block = tempos).track("PART GUITAR", guitar).build()

    private fun expert(bytes: ByteArray) = MidiChartParser.parse(bytes).tracks.getValue(Difficulty.EXPERT)

    @Test
    fun convertsExpertNotesAndCutsShortSustains() {
        val notes = expert(midi({ note(960, 96, 480); note(1440, 97, 60) }))
        assertEquals(listOf(Note(1000, 0, 500, false), Note(1500, 1, 0, false)), notes)
    }

    @Test
    fun mapsEachDifficultyFromItsKeyRange() {
        val tracks = MidiChartParser.parse(midi({ note(0, 60, 60); note(0, 76, 60); note(0, 88, 60) })).tracks
        assertEquals(Note(0, 0, 0, false), tracks.getValue(Difficulty.EASY).single())
        assertEquals(Note(0, 4, 0, false), tracks.getValue(Difficulty.MEDIUM).single())
        assertEquals(Note(0, 4, 0, false), tracks.getValue(Difficulty.HARD).single())
        assertEquals(setOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD), tracks.keys)
    }

    @Test
    fun forceHopoMarkerCoversNotesInsideItsSpan() {
        val notes = expert(midi({ note(0, 96, 60); note(1900, 101, 100); note(1920, 98, 60) }))
        assertEquals(listOf(false, true), notes.map { it.isHopo })
    }

    @Test
    fun forceStrumMarkerCancelsNaturalHopo() {
        val natural = expert(midi({ note(0, 96, 60); note(120, 97, 60) }))
        val forced = expert(midi({ note(0, 96, 60); note(120, 97, 60); note(120, 102, 10) }))
        assertEquals(true, natural[1].isHopo)
        assertEquals(false, forced[1].isHopo)
    }

    @Test
    fun appliesTempoChangesFromTempoTrack() {
        val notes = expert(midi({ note(1440, 96, 60) }, tempos = { tempo(0, 500_000); tempo(960, 1_000_000) }))
        assertEquals(2000L, notes.single().timeMs)
    }

    @Test
    fun handlesRunningStatusAndNoteOnWithZeroVelocityAsNoteOff() {
        val notes = expert(
            midi({
                raw(0, 0x90, 96, 100)
                raw(480, 96, 0)
                raw(960, 97, 100)
                raw(1440, 97, 0)
            }),
        )
        assertEquals(listOf(Note(0, 0, 500, false), Note(1000, 1, 500, false)), notes)
    }

    @Test
    fun failsWithoutGuitarTrack() {
        val bytes = MidiBuilder().track { tempo(0, 500_000) }.track("PART BASS") { note(0, 96, 60) }.build()
        assertThrows<ChartParseException> { MidiChartParser.parse(bytes) }
    }

    @Test
    fun failsOnNonMidiAndTruncatedData() {
        assertThrows<ChartParseException> { MidiChartParser.parse("não é midi".toByteArray()) }
        val valid = midi({ note(0, 96, 60) })
        assertThrows<ChartParseException> { MidiChartParser.parse(valid.copyOf(valid.size - 6)) }
    }
}
```

Run: `./scripts/gw :core:chart:test`
Expected: falha de compilação (`Unresolved reference: MidiChartParser`).

- [ ] **Step 3: Implementar o leitor de MIDI**

`MidiReader.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

/** Leitor mínimo de Standard MIDI File (formatos 0 e 1), suficiente para mapas do Clone Hero. */
internal object MidiReader {
    data class MidiFile(val ticksPerQuarter: Int, val tracks: List<MidiTrack>)

    data class MidiTrack(val name: String?, val events: List<MidiEvent>)

    sealed interface MidiEvent {
        val tick: Long
    }

    data class NoteOn(override val tick: Long, val key: Int) : MidiEvent

    data class NoteOff(override val tick: Long, val key: Int) : MidiEvent

    data class Tempo(override val tick: Long, val microsPerQuarter: Long) : MidiEvent

    private const val META = 0xFF
    private const val SYSEX = 0xF0
    private const val SYSEX_ESCAPE = 0xF7
    private const val META_TRACK_NAME = 0x03
    private const val META_TEMPO = 0x51
    private const val NOTE_OFF = 0x80
    private const val NOTE_ON = 0x90
    private const val PROGRAM_CHANGE = 0xC0
    private const val CHANNEL_PRESSURE = 0xD0

    fun read(bytes: ByteArray): MidiFile {
        val input = ByteReader(bytes)
        if (input.readAscii(4) != "MThd") throw ChartParseException("arquivo MIDI inválido: cabeçalho MThd ausente")
        val headerLength = input.readInt32()
        input.readUInt16() // formato 0 ou 1: tratados igual
        val trackCount = input.readUInt16()
        val division = input.readUInt16()
        if (division and 0x8000 != 0) throw ChartParseException("MIDI com divisão SMPTE não é suportado")
        input.skip(headerLength - 6)
        return MidiFile(division, List(trackCount) { readTrack(input) })
    }

    private fun readTrack(input: ByteReader): MidiTrack {
        if (input.readAscii(4) != "MTrk") throw ChartParseException("arquivo MIDI inválido: trilha sem MTrk")
        val end = input.readInt32() + input.position
        var tick = 0L
        var runningStatus = 0
        var name: String? = null
        val events = mutableListOf<MidiEvent>()
        while (input.position < end) {
            tick += input.readVarLen()
            var status = input.readUInt8()
            if (status < 0x80) {
                if (runningStatus == 0) throw ChartParseException("MIDI inválido: running status sem status anterior")
                input.position -= 1 // o byte lido já era o primeiro dado do evento
                status = runningStatus
            }
            when (status) {
                META -> {
                    val type = input.readUInt8()
                    val data = input.readBytes(input.readVarLen().toInt())
                    if (type == META_TRACK_NAME && name == null) name = String(data, Charsets.ISO_8859_1)
                    if (type == META_TEMPO) events += Tempo(tick, readTempo(data))
                }
                SYSEX, SYSEX_ESCAPE -> input.skip(input.readVarLen().toInt())
                else -> {
                    runningStatus = status
                    val type = status and 0xF0
                    val key = input.readUInt8()
                    val velocity = if (type == PROGRAM_CHANGE || type == CHANNEL_PRESSURE) 0 else input.readUInt8()
                    when {
                        type == NOTE_ON && velocity > 0 -> events += NoteOn(tick, key)
                        type == NOTE_ON || type == NOTE_OFF -> events += NoteOff(tick, key)
                    }
                }
            }
        }
        input.position = end
        return MidiTrack(name, events)
    }

    private fun readTempo(data: ByteArray): Long {
        if (data.size < 3) throw ChartParseException("MIDI inválido: evento de andamento curto")
        return ((data[0].toLong() and 0xFF) shl 16) or ((data[1].toLong() and 0xFF) shl 8) or (data[2].toLong() and 0xFF)
    }
}

internal class ByteReader(private val bytes: ByteArray) {
    var position = 0

    fun readUInt8(): Int {
        if (position >= bytes.size) throw ChartParseException("arquivo MIDI truncado")
        return bytes[position++].toInt() and 0xFF
    }

    fun readUInt16(): Int = (readUInt8() shl 8) or readUInt8()

    fun readInt32(): Int = (readUInt16() shl 16) or readUInt16()

    fun readVarLen(): Long {
        var value = 0L
        repeat(4) {
            val byte = readUInt8()
            value = (value shl 7) or (byte and 0x7F).toLong()
            if (byte and 0x80 == 0) return value
        }
        throw ChartParseException("MIDI inválido: número de tamanho variável longo demais")
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0 || position + count > bytes.size) throw ChartParseException("arquivo MIDI truncado")
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun readAscii(count: Int): String = String(readBytes(count), Charsets.US_ASCII)

    fun skip(count: Int) {
        readBytes(count)
    }
}
```

- [ ] **Step 4: Implementar o parser da trilha de guitarra**

`MidiChartParser.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note

/** Extrai as notas da trilha PART GUITAR de um notes.mid no padrão Rock Band / Clone Hero. */
object MidiChartParser {
    private const val GUITAR_TRACK = "PART GUITAR"
    private val BASE_KEYS = mapOf(
        Difficulty.EASY to 60,
        Difficulty.MEDIUM to 72,
        Difficulty.HARD to 84,
        Difficulty.EXPERT to 96,
    )
    private const val FORCE_HOPO_OFFSET = 5
    private const val FORCE_STRUM_OFFSET = 6

    // Limite do HOPO natural do Clone Hero para MIDI: 170/480 de semínima.
    private const val HOPO_THRESHOLD_NUMERATOR = 170L
    private const val HOPO_THRESHOLD_DENOMINATOR = 480L

    // Notas mais curtas que 1/3 de semínima não são sustain.
    private const val SUSTAIN_CUTOFF_DIVISOR = 3L

    fun parse(bytes: ByteArray): ParsedChart {
        val midi = MidiReader.read(bytes)
        val tempos = midi.tracks
            .flatMap { it.events }
            .filterIsInstance<MidiReader.Tempo>()
            .map { TempoMap.TempoChange(it.tick, it.microsPerQuarter) }
        val tempoMap = TempoMap(midi.ticksPerQuarter, tempos)
        val guitar = midi.tracks.firstOrNull { it.name?.trim() == GUITAR_TRACK }
            ?: throw ChartParseException("trilha $GUITAR_TRACK não encontrada")
        val spans = noteSpans(guitar.events)
        val resolution = midi.ticksPerQuarter.toLong()
        val threshold = resolution * HOPO_THRESHOLD_NUMERATOR / HOPO_THRESHOLD_DENOMINATOR
        val sustainCutoff = resolution / SUSTAIN_CUTOFF_DIVISOR
        val tracks = BASE_KEYS.mapNotNull { (difficulty, base) ->
            val notes = NoteAssembler.assemble(chordsFor(spans, base, sustainCutoff), tempoMap, threshold)
            if (notes.isEmpty()) null else difficulty to notes
        }.toMap()
        return ParsedChart(SongInfo(), tracks)
    }

    private data class Span(val key: Int, val startTick: Long, val endTick: Long) {
        fun covers(tick: Long) = tick == startTick || tick in startTick until endTick
    }

    private fun noteSpans(events: List<MidiReader.MidiEvent>): List<Span> {
        val open = mutableMapOf<Int, Long>()
        val spans = mutableListOf<Span>()
        for (event in events) {
            when (event) {
                is MidiReader.NoteOn -> {
                    open.remove(event.key)?.let { spans += Span(event.key, it, event.tick) }
                    open[event.key] = event.tick
                }
                is MidiReader.NoteOff -> open.remove(event.key)?.let { spans += Span(event.key, it, event.tick) }
                is MidiReader.Tempo -> Unit
            }
        }
        open.forEach { (key, start) -> spans += Span(key, start, start) }
        return spans
    }

    private fun chordsFor(spans: List<Span>, base: Int, sustainCutoff: Long): List<RawChord> {
        val hopoMarkers = spans.filter { it.key == base + FORCE_HOPO_OFFSET }
        val strumMarkers = spans.filter { it.key == base + FORCE_STRUM_OFFSET }
        return spans
            .filter { it.key in base until base + Note.LANE_COUNT }
            .groupBy { it.startTick }
            .map { (tick, group) ->
                val lengths = group.associate { span ->
                    val length = span.endTick - span.startTick
                    (span.key - base) to if (length > sustainCutoff) length else 0L
                }
                val forcing = when {
                    hopoMarkers.any { it.covers(tick) } -> Forcing.HOPO
                    strumMarkers.any { it.covers(tick) } -> Forcing.STRUM
                    else -> Forcing.NONE
                }
                RawChord(tick, lengths, forcing)
            }
    }
}
```

- [ ] **Step 5: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:chart:test`
Expected: `BUILD SUCCESSFUL`, 34 testes passando.

```bash
git add -A
git commit -m "feat(core-chart): parser de notes.mid (PART GUITAR)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Carregador de pasta de música (`ChartLoader`)

**Files:**
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/ChartLoader.kt`
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/ChartLoaderTest.kt`

**Interfaces:**
- Consumes: `ChartFileParser`, `MidiChartParser`, `SongIniParser`, `SongInfo` e `ChartParseException`.
- Produces:
  - `fun interface ChartFiles { fun read(name: String): ByteArray? }`. A implementação deve ignorar maiúsculas no nome. A versão com SAF vem no plano de dados.
  - `data class LoadedChart(id: String, format: ChartFormat, info: SongInfo, tracks: Map<Difficulty, List<Note>>)`. `id` é o SHA-1 do arquivo de notas em hexadecimal minúsculo. `info.offsetMs` é o offset do `.chart` somado ao `delay` do song.ini, e esse total já está aplicado nas notas.
  - `object ChartLoader { fun load(files: ChartFiles): LoadedChart }`. Se a pasta tiver `notes.chart` e `notes.mid`, prefere o `.chart`. Nos metadados, o song.ini tem prioridade sobre o `[Song]` do `.chart`.

- [ ] **Step 1: Testes que falham**

`ChartLoaderTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest

class ChartLoaderTest {
    private val chartText = """
        [Song]
        {
          Name = "Do Chart"
          Artist = "Banda do Chart"
          Offset = 0.1
        }
        [ExpertSingle]
        {
          192 = N 0 0
        }
    """.trimIndent()
    private val midiBytes = MidiBuilder().track { tempo(0, 500_000) }.track("PART GUITAR") { note(480, 96, 60) }.build()

    private fun folder(vararg files: Pair<String, ByteArray>) = ChartFiles { name ->
        files.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
    }

    @Test
    fun loadsChartWithItsOwnMetadataAndOffset() {
        val loaded = ChartLoader.load(folder("notes.chart" to chartText.toByteArray()))
        assertEquals(ChartFormat.CHART, loaded.format)
        assertEquals("Do Chart", loaded.info.title)
        assertEquals(100L, loaded.info.offsetMs)
        assertEquals(600L, loaded.tracks.getValue(Difficulty.EXPERT).single().timeMs)
    }

    @Test
    fun songIniOverridesMetadataAndAddsDelay() {
        val ini = "[Song]\nName = Do Ini\nDelay = 50"
        val loaded = ChartLoader.load(folder("NOTES.CHART" to chartText.toByteArray(), "Song.ini" to ini.toByteArray()))
        assertEquals("Do Ini", loaded.info.title)
        assertEquals("Banda do Chart", loaded.info.artist)
        assertEquals(150L, loaded.info.offsetMs)
        assertEquals(650L, loaded.tracks.getValue(Difficulty.EXPERT).single().timeMs)
    }

    @Test
    fun usesMidiWhenThereIsNoChart() {
        val ini = "[song]\nname = Do Ini\nartist = Banda"
        val loaded = ChartLoader.load(folder("notes.mid" to midiBytes, "song.ini" to ini.toByteArray()))
        assertEquals(ChartFormat.MIDI, loaded.format)
        assertEquals("Do Ini", loaded.info.title)
        assertEquals(500L, loaded.tracks.getValue(Difficulty.EXPERT).single().timeMs)
    }

    @Test
    fun prefersChartOverMidi() {
        val loaded = ChartLoader.load(folder("notes.mid" to midiBytes, "notes.chart" to chartText.toByteArray()))
        assertEquals(ChartFormat.CHART, loaded.format)
    }

    @Test
    fun midiWithoutSongIniHasNoMetadata() {
        val loaded = ChartLoader.load(folder("notes.mid" to midiBytes))
        assertNull(loaded.info.title)
    }

    @Test
    fun idIsSha1OfNotesFile() {
        val bytes = chartText.toByteArray()
        val expected = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, ChartLoader.load(folder("notes.chart" to bytes)).id)
    }

    @Test
    fun failsWithoutNotesFile() {
        assertThrows<ChartParseException> { ChartLoader.load(folder("song.ini" to "[song]".toByteArray())) }
    }

    @Test
    fun failsWhenThereIsNoGuitarDifficulty() {
        val noNotes = "[Song]\n{\n  Name = \"Vazio\"\n}"
        assertThrows<ChartParseException> { ChartLoader.load(folder("notes.chart" to noNotes.toByteArray())) }
    }
}
```

Run: `./scripts/gw :core:chart:test`
Expected: falha de compilação (`Unresolved reference: ChartLoader`).

- [ ] **Step 2: Implementar**

`ChartLoader.kt`:
```kotlin
package com.rodrigomachado.riff.core.chart

import com.rodrigomachado.riff.core.model.ChartFormat
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.Note
import java.security.MessageDigest

/** Acesso aos arquivos de uma pasta de música. No Android, a implementação usa SAF. */
fun interface ChartFiles {
    /** Lê o arquivo pelo nome, sem diferenciar maiúsculas, ou retorna null se ele não existir. */
    fun read(name: String): ByteArray?
}

/** Mapa carregado, com [SongInfo.offsetMs] já aplicado às notas. */
data class LoadedChart(
    val id: String,
    val format: ChartFormat,
    val info: SongInfo,
    val tracks: Map<Difficulty, List<Note>>,
)

object ChartLoader {
    fun load(files: ChartFiles): LoadedChart {
        val chartBytes = files.read("notes.chart")
        val notesBytes: ByteArray
        val format: ChartFormat
        val parsed: ParsedChart
        if (chartBytes != null) {
            notesBytes = chartBytes
            format = ChartFormat.CHART
            parsed = ChartFileParser.parse(chartBytes.decodeToString())
        } else {
            notesBytes = files.read("notes.mid") ?: throw ChartParseException("pasta sem notes.chart ou notes.mid")
            format = ChartFormat.MIDI
            parsed = MidiChartParser.parse(notesBytes)
        }
        if (parsed.tracks.isEmpty()) throw ChartParseException("nenhuma dificuldade de guitarra encontrada")

        val ini = files.read("song.ini")?.let { SongIniParser.parse(it.decodeToString()) } ?: SongInfo()
        val info = SongInfo(
            title = ini.title ?: parsed.info.title,
            artist = ini.artist ?: parsed.info.artist,
            album = ini.album ?: parsed.info.album,
            charter = ini.charter ?: parsed.info.charter,
            songLengthMs = ini.songLengthMs ?: parsed.info.songLengthMs,
            offsetMs = parsed.info.offsetMs + ini.offsetMs,
        )
        val tracks = if (info.offsetMs == 0L) {
            parsed.tracks
        } else {
            parsed.tracks.mapValues { (_, notes) -> notes.map { it.copy(timeMs = it.timeMs + info.offsetMs) } }
        }
        return LoadedChart(sha1Hex(notesBytes), format, info, tracks)
    }

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
}
```

- [ ] **Step 3: Testes, cobertura e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:chart:test koverVerify`
Expected: `BUILD SUCCESSFUL`, 42 testes passando e cobertura de pelo menos 70%. Para ver o relatório, rode `./scripts/gw koverHtmlReport` e abra `build/reports/kover/html/index.html`.

```bash
git add -A
git commit -m "feat(core-chart): carregador de pasta com song.ini, offset e id por SHA-1" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Relógio da música (`:core:game`)

**Files:**
- Modify: `settings.gradle.kts` (adicione `include(":core:game")`), `build.gradle.kts` (adicione `kover(project(":core:game"))` nas dependências da raiz)
- Create: `core/game/build.gradle.kts`
- Create: `core/game/src/main/kotlin/com/rodrigomachado/riff/core/game/SongClock.kt`
- Test: `core/game/src/test/kotlin/com/rodrigomachado/riff/core/game/SongClockTest.kt`

**Interfaces:**
- Consumes: nada de tasks anteriores além do módulo `:core:model`, que é usado na Task 11.
- Produces:
  - `fun interface TimeSource { fun nowMs(): Long }`
  - `class SongClock(timeSource: TimeSource, offsetMs: Long = 0)` com `fun sync(positionMs: Long, isPaused: Boolean, capturedAtMs: Long)`, `fun positionMs(): Long` e `val isPaused: Boolean`
  - `SongClock.HARD_RESET_THRESHOLD_MS = 80` e `SongClock.SLEW_RATE = 0.3` (ms de correção por ms decorrido)

- [ ] **Step 1: Testes que falham**

`core/game/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.jvm.library)
    alias(libs.plugins.kover)
}

dependencies {
    api(project(":core:model"))
}
```

`SongClockTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.game

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SongClockTest {
    private class FakeTime(var now: Long = 0) : TimeSource {
        override fun nowMs() = now
    }

    private val time = FakeTime()
    private val clock = SongClock(time)

    @Test
    fun startsPausedAtZero() {
        time.now = 5_000
        assertEquals(0L, clock.positionMs())
        assertEquals(true, clock.isPaused)
    }

    @Test
    fun extrapolatesWhilePlaying() {
        time.now = 5_000
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 5_000)
        time.now = 5_100
        assertEquals(1_100L, clock.positionMs())
    }

    @Test
    fun accountsForCaptureInThePast() {
        time.now = 5_000
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 4_950)
        assertEquals(1_050L, clock.positionMs())
    }

    @Test
    fun freezesWhilePaused() {
        clock.sync(positionMs = 1_000, isPaused = true, capturedAtMs = 0)
        time.now = 3_000
        assertEquals(1_000L, clock.positionMs())
    }

    @Test
    fun appliesOffset() {
        val shifted = SongClock(time, offsetMs = -200)
        shifted.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 0)
        assertEquals(800L, shifted.positionMs())
    }

    @Test
    fun correctsSmallDriftGradually() {
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 0)
        time.now = 1_000
        assertEquals(2_000L, clock.positionMs())

        clock.sync(positionMs = 2_040, isPaused = false, capturedAtMs = 1_000) // o player está 40 ms à frente
        assertEquals(2_000L, clock.positionMs()) // sem salto
        time.now = 1_100
        assertEquals(2_130L, clock.positionMs()) // corrigiu 30 ms dos 40 em 100 ms
        time.now = 1_200
        assertEquals(2_240L, clock.positionMs()) // convergiu
    }

    @Test
    fun appliesLargeJumpImmediately() {
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 0)
        time.now = 1_000
        clock.sync(positionMs = 30_000, isPaused = false, capturedAtMs = 1_000)
        assertEquals(30_000L, clock.positionMs())
        clock.sync(positionMs = 5_000, isPaused = false, capturedAtMs = 1_000) // voltou a barra de progresso
        assertEquals(5_000L, clock.positionMs())
    }

    @Test
    fun pauseToggleAppliesImmediately() {
        clock.sync(positionMs = 1_000, isPaused = false, capturedAtMs = 0)
        time.now = 500
        clock.sync(positionMs = 1_520, isPaused = true, capturedAtMs = 500)
        time.now = 2_000
        assertEquals(1_520L, clock.positionMs())
        assertEquals(true, clock.isPaused)
    }
}
```

Run: `./scripts/gw :core:game:test`
Expected: falha de compilação (`Unresolved reference: SongClock`).

- [ ] **Step 2: Implementar**

`SongClock.kt`:
```kotlin
package com.rodrigomachado.riff.core.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Fonte de tempo monotônica em ms. No Android, `SystemClock.elapsedRealtime()`. */
fun interface TimeSource {
    fun nowMs(): Long
}

/**
 * Relógio da música. Extrapola a última posição informada pelo player e corrige desvios pequenos
 * aos poucos, para as notas não pularem. Desvios grandes (o usuário mexeu na barra) e mudanças de
 * pausa são aplicados na hora.
 *
 * [offsetMs] é somado à posição. Valores negativos compensam atraso de áudio (fone Bluetooth).
 */
class SongClock(private val timeSource: TimeSource, private val offsetMs: Long = 0) {
    private var anchorPositionMs = 0L
    private var anchorAtMs = 0L
    private var paused = true
    private var synced = false
    private var residualMs = 0.0
    private var lastQueryAtMs = 0L

    val isPaused: Boolean get() = paused

    fun sync(positionMs: Long, isPaused: Boolean, capturedAtMs: Long) {
        val shownAtCapture = rawAt(capturedAtMs) + residualMs
        val drift = positionMs - shownAtCapture
        val hardReset = !synced || isPaused != paused || abs(drift) >= HARD_RESET_THRESHOLD_MS
        anchorPositionMs = positionMs
        anchorAtMs = capturedAtMs
        paused = isPaused
        residualMs = if (hardReset) 0.0 else -drift
        synced = true
        lastQueryAtMs = timeSource.nowMs()
    }

    fun positionMs(): Long {
        val now = timeSource.nowMs()
        val step = (now - lastQueryAtMs).coerceAtLeast(0) * SLEW_RATE
        residualMs = if (residualMs > 0) max(0.0, residualMs - step) else min(0.0, residualMs + step)
        lastQueryAtMs = now
        return (rawAt(now) + residualMs).roundToLong() + offsetMs
    }

    private fun rawAt(timeMs: Long): Double =
        if (paused) anchorPositionMs.toDouble() else (anchorPositionMs + (timeMs - anchorAtMs)).toDouble()

    companion object {
        const val HARD_RESET_THRESHOLD_MS = 80

        /** Até 5 ms de correção a cada frame de 16,7 ms. */
        const val SLEW_RATE = 0.3
    }
}
```

- [ ] **Step 3: Testes passam e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:game:test`
Expected: `BUILD SUCCESSFUL`, 8 testes passando.

```bash
git add -A
git commit -m "feat(core-game): relógio da música com correção suave de desvio" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Motor do jogo (julgamento e pontuação)

**Files:**
- Create: `core/game/src/main/kotlin/com/rodrigomachado/riff/core/game/GameEngine.kt`
- Test: `core/game/src/test/kotlin/com/rodrigomachado/riff/core/game/GameEngineTest.kt`

**Interfaces:**
- Consumes: `Note`, `Judgment` e `GameState` (Task 4).
- Produces:
  - `data class JudgmentWindows(perfectMs: Long = 50, goodMs: Long = 100)`
  - `class GameEngine(notes: List<Note>, windows: JudgmentWindows = JudgmentWindows())`, com:
    - `val notes: List<Note>` (ordenadas por tempo e trilha) e `fun judgmentOf(index: Int): Judgment?`
    - `fun onPress(lane: Int, timeMs: Long): Judgment?`. Retorna null num toque fantasma, que quebra o combo.
    - `fun onRelease(lane: Int, timeMs: Long)` e `fun update(timeMs: Long)`
    - `val state: GameState`, `val score: Long`, `val multiplier: Int`, `val isFinished: Boolean` e `fun accuracy(): Float`
  - **Pontuação:** Perfeito vale 100 e Bom vale 50, multiplicados pelo multiplicador (`min(4, 1 + combo / 10)`, calculado depois de somar o acerto ao combo). Sustain vale 25 pontos a cada 500 ms segurados, vezes o multiplicador.

- [ ] **Step 1: Testes que falham**

`GameEngineTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.game

import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameEngineTest {
    private fun note(timeMs: Long, lane: Int = 0, sustainMs: Long = 0) = Note(timeMs, lane, sustainMs, isHopo = false)

    @Test
    fun judgesByDistanceToNoteIncludingWindowEdges() {
        val engine = GameEngine(listOf(note(1_000), note(2_000), note(3_000), note(4_000)))
        assertEquals(Judgment.PERFECT, engine.onPress(0, 1_050))
        assertEquals(Judgment.GOOD, engine.onPress(0, 1_949))
        assertEquals(Judgment.GOOD, engine.onPress(0, 3_100))
        assertNull(engine.onPress(0, 3_899))
    }

    @Test
    fun ghostTapBreaksComboWithoutCountingAsMiss() {
        val engine = GameEngine(listOf(note(1_000), note(5_000)))
        engine.onPress(0, 1_000)
        assertNull(engine.onPress(2, 1_200))
        assertEquals(0, engine.state.combo)
        assertEquals(0, engine.state.counts.getValue(Judgment.MISS))
    }

    @Test
    fun marksMissWhenNotePassesWindow() {
        val engine = GameEngine(listOf(note(1_000), note(2_000)))
        engine.onPress(0, 1_000)
        engine.update(2_101)
        assertEquals(Judgment.MISS, engine.judgmentOf(1))
        assertEquals(0, engine.state.combo)
        assertEquals(1, engine.state.maxCombo)
    }

    @Test
    fun pressHitsOnlyTheEarliestOfCloseNotesOnSameLane() {
        val engine = GameEngine(listOf(note(1_000), note(1_060)))
        assertEquals(Judgment.PERFECT, engine.onPress(0, 1_030))
        assertEquals(Judgment.PERFECT, engine.judgmentOf(0))
        assertNull(engine.judgmentOf(1))
        assertEquals(Judgment.PERFECT, engine.onPress(0, 1_070))
        assertEquals(2, engine.state.combo)
    }

    @Test
    fun chordNotesAreJudgedIndependently() {
        val engine = GameEngine(listOf(note(1_000, lane = 1), note(1_000, lane = 3)))
        assertEquals(Judgment.PERFECT, engine.onPress(3, 1_010))
        assertNull(engine.judgmentOf(0))
        assertEquals(Judgment.GOOD, engine.onPress(1, 1_080))
    }

    @Test
    fun multiplierGrowsEveryTenHitsUpToFour() {
        val engine = GameEngine(List(40) { note(it * 1_000L) })
        repeat(10) { engine.onPress(0, it * 1_000L) }
        assertEquals(2, engine.multiplier)
        assertEquals(9 * 100L + 200L, engine.score)
        repeat(30) { engine.onPress(0, (it + 10) * 1_000L) }
        assertEquals(4, engine.multiplier)
    }

    @Test
    fun goodScoresHalfOfPerfect() {
        val engine = GameEngine(listOf(note(1_000)))
        engine.onPress(0, 1_080)
        assertEquals(50L, engine.score)
    }

    @Test
    fun heldSustainScores25PointsPerHalfSecond() {
        val engine = GameEngine(listOf(note(1_000, sustainMs = 1_000)))
        engine.onPress(0, 1_000)
        assertEquals(setOf(0), engine.state.activeSustains)
        engine.update(1_500)
        assertEquals(125L, engine.score)
        engine.update(2_300)
        assertEquals(150L, engine.score)
        assertTrue(engine.state.activeSustains.isEmpty())
    }

    @Test
    fun releasingEarlyStopsSustainScoring() {
        val engine = GameEngine(listOf(note(1_000, sustainMs = 1_000)))
        engine.onPress(0, 1_000)
        engine.onRelease(0, 1_250)
        engine.update(2_000)
        assertEquals(112L, engine.score)
    }

    @Test
    fun latePressScoresSustainOnlyFromPressTime() {
        val engine = GameEngine(listOf(note(1_000, sustainMs = 1_000)))
        engine.onPress(0, 1_080)
        engine.update(2_000)
        assertEquals(50L + 46L, engine.score)
    }

    @Test
    fun accuracyWeighsGoodAsHalf() {
        val engine = GameEngine(listOf(note(1_000), note(2_000), note(3_000), note(4_000)))
        engine.onPress(0, 1_000)
        engine.onPress(0, 2_080)
        engine.update(3_200)
        engine.onPress(0, 4_000)
        assertEquals(0.625f, engine.accuracy(), 0.0001f)
        assertEquals(mapOf(Judgment.PERFECT to 2, Judgment.GOOD to 1, Judgment.MISS to 1), engine.state.counts)
    }

    @Test
    fun finishesWhenAllNotesJudgedAndSustainsOver() {
        val engine = GameEngine(listOf(note(1_000, sustainMs = 500)))
        assertFalse(engine.isFinished)
        engine.onPress(0, 1_000)
        assertFalse(engine.isFinished)
        engine.update(1_500)
        assertTrue(engine.isFinished)
    }

    @Test
    fun timeGoingBackwardsKeepsJudgments() {
        val engine = GameEngine(listOf(note(1_000), note(2_000)))
        engine.update(3_000)
        engine.update(500)
        assertEquals(Judgment.MISS, engine.judgmentOf(0))
        assertNull(engine.onPress(0, 1_000))
    }

    @Test
    fun emptyChartIsFinishedWithZeroAccuracy() {
        val engine = GameEngine(emptyList())
        assertTrue(engine.isFinished)
        assertEquals(0f, engine.accuracy())
    }
}
```

Run: `./scripts/gw :core:game:test`
Expected: falha de compilação (`Unresolved reference: GameEngine`).

- [ ] **Step 2: Implementar**

`GameEngine.kt`:
```kotlin
package com.rodrigomachado.riff.core.game

import com.rodrigomachado.riff.core.model.GameState
import com.rodrigomachado.riff.core.model.Judgment
import com.rodrigomachado.riff.core.model.Note
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class JudgmentWindows(val perfectMs: Long = 50, val goodMs: Long = 100) {
    init {
        require(perfectMs in 1..goodMs) { "janelas inválidas: perfeito=$perfectMs, bom=$goodMs" }
    }
}

/**
 * Regras da partida: julga toques, conta combo e multiplicador, pontua notas longas.
 * Não conhece relógio nem UI: recebe sempre o tempo da música em ms.
 */
class GameEngine(notes: List<Note>, private val windows: JudgmentWindows = JudgmentWindows()) {
    val notes: List<Note> = notes.sortedWith(compareBy<Note> { it.timeMs }.thenBy { it.lane })

    private val judgments = arrayOfNulls<Judgment>(this.notes.size)
    private val counts = IntArray(Judgment.entries.size)
    private val sustains = mutableMapOf<Int, ActiveSustain>()
    private var firstPending = 0
    private var hitScore = 0L
    private var sustainScore = 0.0
    private var combo = 0
    private var maxCombo = 0
    private var songTimeMs = 0L

    private class ActiveSustain(val endMs: Long, var scoredUntilMs: Long)

    val multiplier: Int get() = min(MAX_MULTIPLIER, 1 + combo / HITS_PER_MULTIPLIER_STEP)

    val score: Long get() = hitScore + sustainScore.toLong()

    val isFinished: Boolean get() = counts.sum() == notes.size && sustains.isEmpty()

    val state: GameState
        get() = GameState(
            songTimeMs = songTimeMs,
            score = score,
            combo = combo,
            maxCombo = maxCombo,
            multiplier = multiplier,
            counts = Judgment.entries.associateWith { counts[it.ordinal] },
            activeSustains = sustains.keys.toSet(),
        )

    fun judgmentOf(index: Int): Judgment? = judgments[index]

    fun accuracy(): Float = if (notes.isEmpty()) {
        0f
    } else {
        (counts[Judgment.PERFECT.ordinal] + HALF * counts[Judgment.GOOD.ordinal]) / notes.size
    }

    fun update(timeMs: Long) {
        songTimeMs = timeMs
        markMisses(timeMs)
        accrueSustains(timeMs)
    }

    fun onPress(lane: Int, timeMs: Long): Judgment? {
        update(timeMs)
        val index = findHittable(lane, timeMs)
        if (index == null) {
            combo = 0
            return null
        }
        val note = notes[index]
        val judgment = if (abs(note.timeMs - timeMs) <= windows.perfectMs) Judgment.PERFECT else Judgment.GOOD
        record(index, judgment)
        combo++
        maxCombo = max(maxCombo, combo)
        hitScore += (if (judgment == Judgment.PERFECT) PERFECT_POINTS else GOOD_POINTS) * multiplier
        if (note.sustainMs > 0) {
            sustains[lane] = ActiveSustain(note.timeMs + note.sustainMs, max(timeMs, note.timeMs))
        }
        return judgment
    }

    fun onRelease(lane: Int, timeMs: Long) {
        update(timeMs)
        sustains.remove(lane)
    }

    private fun findHittable(lane: Int, timeMs: Long): Int? {
        var i = firstPending
        while (i < notes.size && notes[i].timeMs <= timeMs + windows.goodMs) {
            val note = notes[i]
            if (judgments[i] == null && note.lane == lane && note.timeMs >= timeMs - windows.goodMs) return i
            i++
        }
        return null
    }

    private fun markMisses(timeMs: Long) {
        while (firstPending < notes.size && notes[firstPending].timeMs < timeMs - windows.goodMs) {
            if (judgments[firstPending] == null) {
                record(firstPending, Judgment.MISS)
                combo = 0
            }
            firstPending++
        }
    }

    private fun accrueSustains(timeMs: Long) {
        val iterator = sustains.values.iterator()
        while (iterator.hasNext()) {
            val sustain = iterator.next()
            val until = min(timeMs, sustain.endMs)
            if (until > sustain.scoredUntilMs) {
                sustainScore += (until - sustain.scoredUntilMs) * SUSTAIN_POINTS_PER_MS * multiplier
                sustain.scoredUntilMs = until
            }
            if (timeMs >= sustain.endMs) iterator.remove()
        }
    }

    private fun record(index: Int, judgment: Judgment) {
        judgments[index] = judgment
        counts[judgment.ordinal]++
    }

    private companion object {
        const val PERFECT_POINTS = 100L
        const val GOOD_POINTS = 50L
        const val SUSTAIN_POINTS_PER_MS = 25.0 / 500.0
        const val MAX_MULTIPLIER = 4
        const val HITS_PER_MULTIPLIER_STEP = 10
        const val HALF = 0.5f
    }
}
```

- [ ] **Step 3: Testes, cobertura e commit**

Run: `./scripts/gw detekt -PdetektAutoCorrect` e depois `./scripts/gw detekt :core:game:test koverVerify`
Expected: `BUILD SUCCESSFUL`, 22 testes em `:core:game` e cobertura agregada de pelo menos 70%.

```bash
git add -A
git commit -m "feat(core-game): motor de julgamento, combo, multiplicador e sustain" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Verificação completa e README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Rodar o mesmo comando do CI**

Run: `./scripts/gw detekt lintDebug test koverVerify assembleDebug`
Expected: `BUILD SUCCESSFUL`. Lint sem erros.

- [ ] **Step 2: Instalar e abrir o app (verificação manual)**

Com um aparelho ou emulador conectado ao `adb` do Windows:
```bash
cmd.exe /c "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```
Abra o Riff e confira que o catálogo aparece com os cartões, as 5 cores das trilhas e os estados de vazio e de erro, no tema do sistema. Se não houver aparelho, registre que esta verificação ficou pendente.

- [ ] **Step 3: Atualizar o README**

Acrescente ao `README.md`:
````markdown
## Módulos

| Módulo | Conteúdo |
|---|---|
| `:app` | Activity; por enquanto mostra o catálogo do design system |
| `:core:ui` | Tema (claro, escuro e Dynamic Color), cores das trilhas e componentes, com screenshots em `src/test/screenshots` |
| `:core:model` | Modelos de domínio |
| `:core:chart` | Leitura de mapas do Clone Hero (`notes.chart`, `notes.mid` e `song.ini`) |
| `:core:game` | Relógio da música e motor do jogo (julgamento, combo e pontuação) |

## Screenshots

```bash
./scripts/gw :core:ui:recordRoborazziDebug   # regrava as referências depois de uma mudança visual intencional
./scripts/gw :core:ui:verifyRoborazziDebug   # compara com as referências
```

## Status

Fundação pronta: build, CI, design system, parsers e motor do jogo. Próximos planos: dados e biblioteca, gameplay com relógio local, integração com o Spotify, calibração e configurações.
````

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: README com módulos, screenshots e status" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
