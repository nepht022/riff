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

/**
 * O conteúdo de fora do Gradle (lista abaixo, textos em composeResources/files/licencas) e as bibliotecas do
 * AboutLibraries.
 */
class AndroidLicenseSource @Inject constructor(@ApplicationContext private val context: Context) : LicenseSource {
    override suspend fun content() = withContext(Dispatchers.IO) { CONTENT.toEntries() }

    override suspend fun libraries() = withContext(Dispatchers.IO) {
        Libs.Builder().withContext(context).build().libraries.toEntries(IN_CONTENT)
    }

    private companion object {
        /** Itens que ficam só na lista manual, com o aviso de copyright de verdade. */
        val IN_CONTENT = setOf(
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
            ManualItem("Demucs v4 (htdemucs_6s)", "Meta Platforms", "MIT", "demucs.txt"),
            ManualItem("demucs.onnx", "Sevag Hanssian", "MIT", "demucs-onnx.txt"),
            ManualItem("ONNX Runtime", "Microsoft Corporation", "MIT", "onnxruntime.txt"),
            ManualItem("JTransforms", "Piotr Wendykier", "BSD 2-Clause", "jtransforms.txt"),
            ManualItem("JLargeArrays", "University of Warsaw, ICM; visnow.org", "BSD 2-Clause", "jlargearrays.txt"),
            ManualItem("Protocol Buffers", "Google", "BSD 3-Clause", "protobuf.txt"),
            ManualItem(
                "New Rocker",
                "Pablo Impallari, Brenda Gallo",
                "SIL Open Font License 1.1",
                "new-rocker-ofl.txt",
            ),
            ManualItem("Oswald", "The Oswald Project Authors", "SIL Open Font License 1.1", "oswald-ofl.txt"),
            ManualItem(
                "Gravações de guitarra (Freesound)",
                "chiller345, SpeedY, gazbooth, Joao_Janz",
                "CC0 1.0",
                "cc0-1.0.txt",
            ),
            ManualItem("Mapas do Encore (enchor.us)", "Comunidade do Clone Hero", "De cada autor", null),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LicensesModule {
    @Binds
    abstract fun licenseSource(impl: AndroidLicenseSource): LicenseSource
}
