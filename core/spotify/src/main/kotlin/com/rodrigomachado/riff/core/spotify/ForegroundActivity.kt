package com.rodrigomachado.riff.core.spotify

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * A Activity em primeiro plano. O pedido de autorização do Spotify só abre a partir de uma tela, então a
 * conexão precisa dela. Registrado no `Application.onCreate`, antes de qualquer tela abrir.
 */
object ForegroundActivity : Application.ActivityLifecycleCallbacks {
    private var resumed: WeakReference<Activity>? = null

    val current: Activity? get() = resumed?.get()

    /** Chamado a cada tela que volta ao primeiro plano. */
    var onResumed: ((Activity) -> Unit)? = null

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
        onResumed?.invoke(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
