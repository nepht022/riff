package com.rodrigomachado.riff.feature.settings

import com.rodrigomachado.riff.core.audio.SfxPlayer
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AiSeparationState
import com.rodrigomachado.riff.core.data.SeparatedFiles
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.SeparationUsage
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class FakeSettingsRepository(initial: Settings = Settings.DEFAULT) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun update(transform: (Settings) -> Settings) {
        settings.value = transform(settings.value)
    }
}

class FakeSfxPlayer : SfxPlayer {
    val played = mutableListOf<String>()
    var hitVolume = -1f
    var missVolume = -1f
    var released = false

    override fun hit(lane: Int) {
        played += "hit:$lane"
    }

    override fun miss() {
        played += "miss"
    }

    override fun setVolumes(hit: Float, miss: Float) {
        hitVolume = hit
        missVolume = miss
    }

    override fun release() {
        released = true
    }
}

class FakeAiSeparations : AiSeparationRepository {
    val calls = mutableListOf<String>()
    override val separatedIds = MutableStateFlow(emptySet<String>())
    override val usage = MutableStateFlow(SeparationUsage(0, 0))
    override suspend fun stateOf(chartId: String): AiSeparationState? = null
    override suspend fun separatedFiles(chartId: String): SeparatedFiles? = null
    override fun workDir(chartId: String) = File("x")
    override suspend fun commit(chartId: String, workDir: File) = Unit
    override suspend fun decline(chartId: String) = Unit
    override suspend fun resetDeclines() {
        calls += "resetDeclines"
    }
    override suspend fun deleteAll() {
        calls += "deleteAll"
    }
    override suspend fun pruneOrphans() = Unit
}

class FakeAppLanguage : com.rodrigomachado.riff.core.data.AppLanguageStore {
    override var current = com.rodrigomachado.riff.core.model.AppLanguage.PORTUGUESE

    override fun set(language: com.rodrigomachado.riff.core.model.AppLanguage) {
        current = language
    }
}

class FakeWebAuth : com.rodrigomachado.riff.core.spotify.web.SpotifyWebAuth {
    override val state = kotlinx.coroutines.flow.MutableStateFlow(
        com.rodrigomachado.riff.core.spotify.web.WebAuthState.NOT_AUTHORIZED,
    )
    var accept = true
    var asked = 0

    override suspend fun authorize(): Boolean {
        asked++
        if (accept) state.value = com.rodrigomachado.riff.core.spotify.web.WebAuthState.AUTHORIZED
        return accept
    }

    override suspend fun accessToken(): String? = null

    override suspend fun onUnauthorized(): String? = null
}

class FakeLibraryFolder(var writable: Boolean = true) :
    com.rodrigomachado.riff.core.data.LibraryFolderStore,
    com.rodrigomachado.riff.core.data.folder.FolderAccess {
    override val rootUri = kotlinx.coroutines.flow.MutableStateFlow<String?>("content://pasta")

    override suspend fun setRootUri(uri: String) {
        rootUri.value = uri
    }

    override fun persistAccess(folderUri: String) = Unit

    override fun canWrite(folderUri: String): Boolean = writable
}
