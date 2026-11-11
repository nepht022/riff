package com.rodrigomachado.riff.core.ui.art

import androidx.compose.ui.graphics.ImageBitmap
import com.rodrigomachado.riff.core.model.ChartArtUnavailableException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class ChartThumbImagesTest {
    @Test
    fun thumbnailsAreCachedAndLoadedOffTheMainThread() = runTest {
        val background = StandardTestDispatcher(testScheduler, name = "capas")
        val calls = mutableListOf<String>()
        val interceptors = mutableListOf<ContinuationInterceptor?>()
        val source = ChartArtImages { id ->
            calls += id
            interceptors += currentCoroutineContext()[ContinuationInterceptor]
            ImageBitmap(1, 1)
        }
        val thumbs = ChartThumbImages(source, capacity = 2, dispatcher = background)

        assertNull(thumbs.cached("a"))
        val a = thumbs.load("a")
        // A segunda vez (a lista rolou de volta) vem da memória, na hora.
        assertSame(a, thumbs.load("a"))
        assertSame(a, thumbs.cached("a"))
        assertEquals(listOf("a"), calls)
        // A leitura roda no despachante de fundo, não no de quem pediu (a tela).
        assertEquals(listOf<ContinuationInterceptor?>(background), interceptors)

        // Poucos na memória: sai o usado há mais tempo.
        thumbs.load("b")
        thumbs.load("a")
        thumbs.load("c")
        assertNull(thumbs.cached("b"))
        assertNotNull(thumbs.cached("a"))
        assertNotNull(thumbs.cached("c"))
        thumbs.load("b")
        assertEquals(listOf("a", "b", "c", "b"), calls)
    }

    @Test
    fun defaultReadsOutsideTheCallerThread() = runTest {
        var thread: Thread? = null
        val thumbs = ChartThumbImages(
            ChartArtImages {
                thread = Thread.currentThread()
                null
            },
        )
        thumbs.load("a")
        assertNotEquals(Thread.currentThread(), thread)
    }

    @Test
    fun missingOrBrokenArtIsNotReadAgain() = runTest {
        var calls = 0
        val thumbs = ChartThumbImages(
            ChartArtImages { id ->
                calls++
                if (id == "broken") error("imagem corrompida") else null
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        assertNull(thumbs.load("broken"))
        assertNull(thumbs.load("broken"))
        assertNull(thumbs.load("missing"))
        assertNull(thumbs.load("missing"))
        assertEquals(2, calls)
    }

    /**
     * "Não deu para procurar" (o mapa ainda não está no banco: o convidado vai baixar a música; ou a pasta perdeu o
     * acesso) não fica guardado: o próximo pedido procura de novo.
     */
    @Test
    fun artThatCouldNotBeLookedUpIsAskedAgain() = runTest {
        val image = ImageBitmap(1, 1)
        var downloaded = false
        val thumbs = ChartThumbImages(
            ChartArtImages { if (downloaded) image else throw ChartArtUnavailableException("ainda não baixou") },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertNull(thumbs.load("a"))
        assertNull(thumbs.cached("a"))
        downloaded = true
        assertSame(image, thumbs.load("a"))
    }

    /** Depois de uma importação, o "sem imagem" guardado vale de novo: a pasta pode ter ganhado a capa. */
    @Test
    fun clearForgetsWhatWasKept() = runTest {
        val image = ImageBitmap(1, 1)
        var calls = 0
        val thumbs = ChartThumbImages(
            ChartArtImages { if (calls++ == 0) null else image },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertNull(thumbs.load("a"))
        thumbs.clear()
        assertNull(thumbs.cached("a"))
        assertSame(image, thumbs.load("a"))
    }
}
