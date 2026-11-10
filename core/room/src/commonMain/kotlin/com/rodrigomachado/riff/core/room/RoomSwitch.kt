package com.rodrigomachado.riff.core.room

/** A Sala só liga com o Firebase configurado no `local.properties` ([RoomConfig]); sem ele, fica "em breve". */
object RoomSwitch {
    val isOn: Boolean get() = isOn(RoomConfig.API_KEY, RoomConfig.DB_URL)

    fun isOn(apiKey: String, dbUrl: String): Boolean = apiKey.isNotBlank() && dbUrl.isNotBlank()
}
