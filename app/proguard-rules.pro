# Regras do R8 para o release (o build medido pelo :baselineprofile parte dele).

# Spotify App Remote: o SDK referencia Jackson e uma anotação que não vêm no AAR (usa Gson de fato).
-dontwarn com.fasterxml.jackson.**
-dontwarn com.spotify.base.annotations.NotNull

# Os tipos do protocolo do Spotify são lidos por reflexão (Gson): mantém nomes e campos.
-keep class com.spotify.protocol.** { *; }

# O SDK acha o app do Spotify instanciando o locator por reflexão (Class.forName + getConstructor). As regras do
# próprio AAR mantêm só o nome da classe, e o R8 tirava o construtor: o release dizia "Spotify não instalado".
# Mantém o App Remote e a autorização inteiros (são pequenos). Conferência: scripts/conferir_r8_spotify.sh.
-keep class com.spotify.android.appremote.** { *; }
-keep class com.spotify.sdk.android.auth.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
