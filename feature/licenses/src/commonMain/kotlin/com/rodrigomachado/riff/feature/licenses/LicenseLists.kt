package com.rodrigomachado.riff.feature.licenses

import com.mikepenz.aboutlibraries.entity.Library
import com.rodrigomachado.riff.feature.licenses.resources.Res

/** Um item da lista manual: [file] é o texto em composeResources/files/licencas (null: sem texto). */
internal data class ManualItem(val name: String, val author: String, val license: String, val file: String?)

/** A lista manual com os textos lidos dos recursos. */
internal suspend fun List<ManualItem>.toEntries(): List<LicenseEntry> =
    map { LicenseEntry(it.name, it.author, it.license, it.file?.let { file -> licenseText(file) }) }

private suspend fun licenseText(file: String) = Res.readBytes("files/licencas/$file").decodeToString()

/** As bibliotecas do AboutLibraries, sem as da lista manual ([inContent]), em ordem alfabética. */
internal fun List<Library>.toEntries(inContent: Set<String>): List<LicenseEntry> = filter { it.uniqueId !in inContent }
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
