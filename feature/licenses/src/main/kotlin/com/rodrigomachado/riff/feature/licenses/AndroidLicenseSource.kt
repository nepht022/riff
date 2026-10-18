package com.rodrigomachado.riff.feature.licenses

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** O conteúdo de fora do Gradle (lista abaixo, textos em assets/licencas) e as bibliotecas do AboutLibraries. */
class AndroidLicenseSource @Inject constructor(@ApplicationContext private val context: Context) : LicenseSource {
    override suspend fun content() = withContext(Dispatchers.IO) {
        CONTENT.map { LicenseEntry(it.name, it.author, it.license, it.file?.let(::asset)) }
    }

    override suspend fun libraries() = withContext(Dispatchers.IO) {
        Libs.Builder().withContext(context).build().libraries
            .filter { it.uniqueId !in IN_CONTENT }
            .map { library ->
                val licenses = library.licenses
                LicenseEntry(
                    name = library.name,
                    author = library.organization?.name ?: library.developers.mapNotNull { it.name }.joinToString(),
                    license = licenses.joinToString { it.name },
                    text = licenses.mapNotNull { it.licenseContent }.joinToString("\n\n").ifBlank { null },
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun asset(file: String) = context.assets.open("licencas/$file").use { it.readBytes().decodeToString() }

    private data class Item(val name: String, val author: String, val license: String, val file: String?)

    private companion object {
        /** O AAR do Spotify vem sem licença no pom: fica só na lista manual (Apache 2.0, do manifesto do AAR). */
        val IN_CONTENT = setOf(
            "com.spotify.android:spotify-app-remote",
            // MIT e BSD pedem o aviso de copyright de verdade; o AboutLibraries traz só o modelo da licença.
            "com.microsoft.onnxruntime:onnxruntime-android",
            "com.github.wendykierp:JTransforms",
            "org.visnow:JLargeArrays",
            "androidx.datastore:datastore-preferences-external-protobuf",
            // Vêm junto do JLargeArrays no Gradle, mas o R8 tira do APK.
            "junit:junit",
            "org.hamcrest:hamcrest-core",
        )

        val CONTENT = listOf(
            Item("Demucs v4 (htdemucs_6s)", "Meta Platforms", "MIT", "demucs.txt"),
            Item("demucs.onnx", "Sevag Hanssian", "MIT", "demucs-onnx.txt"),
            Item("Spotify App Remote SDK", "Spotify AB", "Apache 2.0", "apache-2.0.txt"),
            Item("ONNX Runtime", "Microsoft Corporation", "MIT", "onnxruntime.txt"),
            Item("JTransforms", "Piotr Wendykier", "BSD 2-Clause", "jtransforms.txt"),
            Item("JLargeArrays", "University of Warsaw, ICM; visnow.org", "BSD 2-Clause", "jlargearrays.txt"),
            Item("Protocol Buffers", "Google", "BSD 3-Clause", "protobuf.txt"),
            Item("New Rocker", "Pablo Impallari, Brenda Gallo", "SIL Open Font License 1.1", "new-rocker-ofl.txt"),
            Item("Oswald", "The Oswald Project Authors", "SIL Open Font License 1.1", "oswald-ofl.txt"),
            Item(
                "Gravações de guitarra (Freesound)",
                "chiller345, SpeedY, gazbooth, Joao_Janz",
                "CC0 1.0",
                "cc0-1.0.txt",
            ),
            Item("Mapas do Encore (enchor.us)", "Comunidade do Clone Hero", "De cada autor", null),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LicensesModule {
    @Binds
    abstract fun licenseSource(impl: AndroidLicenseSource): LicenseSource
}
