package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.datastore.RiffPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Pasta escolhida como raiz da biblioteca. */
interface LibraryFolderStore {
    val rootUri: Flow<String?>

    suspend fun setRootUri(uri: String)
}

class PreferencesLibraryFolderStore @Inject constructor(private val preferences: RiffPreferences) : LibraryFolderStore {
    override val rootUri: Flow<String?> = preferences.libraryRootUri

    override suspend fun setRootUri(uri: String) = preferences.setLibraryRootUri(uri)
}
