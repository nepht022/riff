# Regras do R8 para o release (o build medido pelo :baselineprofile parte dele).

# Spotify App Remote: o SDK referencia Jackson e uma anotação que não vêm no AAR (usa Gson de fato).
-dontwarn com.fasterxml.jackson.**
-dontwarn com.spotify.base.annotations.NotNull

# Os tipos do protocolo do Spotify são lidos por reflexão (Gson): mantém nomes e campos.
-keep class com.spotify.protocol.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
