package com.rodrigomachado.riff.core.separation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rodrigomachado.riff.core.data.SeparationQueue
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Escopo que vive com o app: acompanha o WorkManager e a fila. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SeparationScope

/**
 * A fila fica no banco e um trabalho único do WorkManager a esvazia ([SeparationQueueLoop]). Sempre que há fila sem
 * trabalho vivo (pedido novo, processo morto antes de pedir, música que entrou quando o laço acabava), um trabalho é
 * pedido; com um vivo, o laço pega as novas sozinho.
 */
@Singleton
@Suppress("LongParameterList") // a fila, o trabalho e a opção do carregador
class WorkSeparationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progress: SeparationProgress,
    device: DeviceInfo,
    private val separationQueue: SeparationQueue,
    private val control: SeparationQueueControl,
    settings: SettingsRepository,
    @SeparationScope private val scope: CoroutineScope,
) : SeparationScheduler {
    override val status: StateFlow<SeparationStatus> = progress.status
    override val lowMemoryDevice = device.totalRamBytes < LOW_RAM_BYTES
    private val workManager get() = WorkManager.getInstance(context)
    private val waiting = MutableStateFlow(false)
    override val waitingForCharger: StateFlow<Boolean> = waiting.asStateFlow()

    override val queue: StateFlow<List<QueuedSeparation>> = combine(
        separationQueue.items,
        progress.status,
    ) { items, s ->
        val current = (s as? SeparationStatus.Running)?.chartId
        items.filterNot { it.chartId == current }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        val onlyCharging = settings.settings.map { it.separateOnlyCharging }.distinctUntilChanged()
        scope.launch {
            combine(workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK), separationQueue.items, onlyCharging) {
                    infos,
                    items,
                    charging,
                ->
                Triple(infos, items, charging)
            }.collect { (infos, items, charging) -> reconcile(infos, items, charging) }
        }
    }

    private fun reconcile(infos: List<WorkInfo>, items: List<QueuedSeparation>, onlyCharging: Boolean) {
        val live = infos.filterNot { it.state.isFinished }
        val parked = live.singleOrNull()?.takeIf { it.state == WorkInfo.State.ENQUEUED }
        when {
            items.isEmpty() -> Unit

            live.isEmpty() -> request(onlyCharging, ExistingWorkPolicy.KEEP)

            // Mudou a opção com a fila parada: o pedido parado é refeito com a opção nova.
            parked != null && parked.constraints.requiresCharging() != onlyCharging ->
                request(onlyCharging, ExistingWorkPolicy.REPLACE)
        }
        waiting.value = items.isNotEmpty() && onlyCharging && parked != null
        // Um trabalho que acabou sem o runner avisar (cancelado antes de começar) não deixa o "separando" preso.
        if (live.isEmpty() && infos.isNotEmpty() && progress.status.value is SeparationStatus.Running) {
            progress.set(SeparationStatus.Idle)
        }
    }

    private fun request(onlyCharging: Boolean, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<SeparationWorker>()
            .setConstraints(Constraints.Builder().setRequiresCharging(onlyCharging).build())
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }

    override fun enqueue(songs: List<QueuedSeparation>) {
        scope.launch { separationQueue.add(songs) }
    }

    override fun cancelCurrent(chartId: String) = control.cancelCurrent(chartId)

    override fun remove(chartId: String) {
        scope.launch { separationQueue.remove(chartId) }
    }

    override fun cancelAll() {
        scope.launch {
            // Nesta ordem: com a fila vazia, o laço não começa a próxima ao ver a atual cancelada.
            separationQueue.clear()
            workManager.cancelUniqueWork(UNIQUE_WORK)
            control.cancelAny()
        }
        // Cancelado antes de o runner começar, ninguém mais tiraria o "separando".
        progress.set(SeparationStatus.Idle)
    }

    override fun dismissFailure() {
        if (progress.status.value is SeparationStatus.Failed) progress.set(SeparationStatus.Idle)
    }

    private companion object {
        const val LOW_RAM_BYTES = 4L shl 30
    }
}
