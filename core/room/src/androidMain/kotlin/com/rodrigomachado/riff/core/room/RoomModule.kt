package com.rodrigomachado.riff.core.room

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import com.rodrigomachado.riff.core.datastore.RiffPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** A Sala no celular: o Firebase do `local.properties` ([RoomConfig]) pelo HTTP do Java, com o aparelho celular. */
@Module
@InstallIn(SingletonComponent::class)
object RoomModule {
    /** Um só no app: um DataStore por arquivo (ver [noBackupRoomIdentityStore]). */
    @Provides
    @Singleton
    fun identityStore(@ApplicationContext context: Context): RoomIdentityStore = noBackupRoomIdentityStore(context)

    /** Um só no app: o login anônimo (o `idToken` na memória) vale para todas as telas da Sala. */
    @Provides
    @Singleton
    fun roomRepository(identity: RoomIdentityStore): RoomRepository {
        val clock = System::currentTimeMillis
        val auth = AnonymousAuth(UrlConnectionRoomHttp, identity, RoomConfig.API_KEY, clock)
        val db = FirebaseDb(UrlConnectionRoomHttp, auth, RoomConfig.DB_URL)
        return RoomRepository(db, auth, RoomDevice.PHONE, clock)
    }

    /**
     * Um só no app: o batimento da sala continua entre as telas (a partida, o Resultado, a Biblioteca de escolha);
     * o app liga e desliga com o segundo plano ([RoomPresence.setForeground]).
     */
    @Provides
    @Singleton
    fun roomPresence(repository: RoomRepository): RoomPresence = RoomPresence(repository)
}

/**
 * A identidade da Sala no celular, num DataStore só dela na pasta `no_backup` (a [Context.getNoBackupFilesDir], que o
 * Android nunca leva no backup nem na troca de celular). O `riff_settings` (o nome, os ajustes) continua no backup;
 * restaurado noutro aparelho, o app começa outra conta anônima em vez de dividir o uid (e o `refreshToken`) com o
 * antigo. No PC não há backup: lá a identidade fica nas preferências de sempre.
 */
internal fun noBackupRoomIdentityStore(context: Context): RoomIdentityStore {
    val dataStore = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { File(context.noBackupFilesDir, "riff_room.preferences_pb") },
    )
    return DataStoreRoomIdentityStore(RiffPreferences(dataStore))
}
