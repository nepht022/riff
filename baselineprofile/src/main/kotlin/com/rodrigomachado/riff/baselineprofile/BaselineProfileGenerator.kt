package com.rodrigomachado.riff.baselineprofile

import android.util.Log
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gera o Baseline Profile percorrendo abertura, biblioteca e uma partida (spec do polimento §2). O perfil de
 * abertura (startup-prof.txt) leva só a abertura; a biblioteca e a partida entram só no perfil geral.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForHome()
    }

    @Test
    fun libraryAndMatch() = rule.collect(packageName = PACKAGE, includeInStartupProfile = false) {
        pressHome()
        startActivityAndWait()
        waitForHome()
        browseLibrary()
        if (!playSong()) Log.w(TAG, "'${songTitle()}' não está na biblioteca: perfil sem a partida")
    }
}
