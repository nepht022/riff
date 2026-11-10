package com.rodrigomachado.riff.core.room

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** A Sala no celular: o Firebase do `local.properties` ([RoomConfig]) pelo HTTP do Java, com o aparelho celular. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RoomModule {
    @Binds
    abstract fun identityStore(impl: DataStoreRoomIdentityStore): RoomIdentityStore

    companion object {
        /** Um só no app: o login anônimo (o `idToken` na memória) vale para todas as telas da Sala. */
        @Provides
        @Singleton
        fun roomRepository(identity: RoomIdentityStore): RoomRepository {
            val clock = System::currentTimeMillis
            val auth = AnonymousAuth(UrlConnectionRoomHttp, identity, RoomConfig.API_KEY, clock)
            val db = FirebaseDb(UrlConnectionRoomHttp, auth, RoomConfig.DB_URL)
            return RoomRepository(db, auth, RoomDevice.PHONE, clock)
        }
    }
}
