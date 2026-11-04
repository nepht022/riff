package com.rodrigomachado.riff.core.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

/** O nome do `.sng` baixado vale também no Windows (no PC a biblioteca fica numa pasta do Windows). */
class DownloadFileNameTest {
    @Test
    fun aValidTitleKeepsItsName() {
        assertEquals("Nirvana - Smells Like Teen Spirit.sng", downloadFileName("Nirvana", "Smells Like Teen Spirit"))
        assertEquals("CONSOLE - Nul.sng", downloadFileName("CONSOLE", "Nul"))
    }

    @Test
    fun charactersWindowsForbidsBecomeUnderscores() {
        assertEquals("AC_DC - AC_DC_ Back In Black_.sng", downloadFileName("AC/DC", "AC:DC: Back In Black?"))
        assertEquals("a_b - _c_d_e_f_.sng", downloadFileName("a*b", "\"c<d>e|f\\"))
    }

    @Test
    fun controlCharactersBecomeUnderscores() {
        assertEquals("Banda - Linha_um_dois.sng", downloadFileName("Banda", "Linha\num\tdois"))
    }

    /** O Windows lê o nome só até o primeiro ponto: "Nul. - CON.sng" seria o NUL (o dispositivo). */
    @Test
    fun aReservedWindowsNameBecomesAnother() {
        assertEquals("_Nul. - CON.sng", downloadFileName("Nul.", "CON"))
        assertEquals("_com1.ao vivo - x.sng", downloadFileName("com1.ao vivo", "x"))
        assertEquals("_LPT9 .b - x.sng", downloadFileName("LPT9 .b", "x"))
    }
}
