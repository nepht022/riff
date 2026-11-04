package com.rodrigomachado.riff.feature.search

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.encore.EncoreClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** O [SearchViewModel] criado pelo Hilt no celular: a consulta inicial vem do argumento `query` da rota. */
@HiltViewModel
class HiltSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    encore: EncoreClient,
    downloader: ChartDownloader,
    downloads: EncoreDownloads,
    folderAccess: FolderAccess,
    folders: LibraryFolderStore,
) : SearchViewModel(savedStateHandle["query"] ?: "", encore, downloader, downloads, folderAccess, folders)
