package io.github.yasarmahomedabbas.immichtv

import android.content.Context
import io.github.yasarmahomedabbas.immichtv.data.immich.ImmichServerConnector
import io.github.yasarmahomedabbas.immichtv.data.immich.immichHttpClient
import io.github.yasarmahomedabbas.immichtv.data.settings.KeystoreCipher
import io.github.yasarmahomedabbas.immichtv.data.settings.SharedPreferencesStorage
import io.github.yasarmahomedabbas.immichtv.data.settings.StoredSessionRepository
import io.github.yasarmahomedabbas.immichtv.domain.server.ConnectToServer
import io.github.yasarmahomedabbas.immichtv.domain.server.GetSession

/**
 * The one place that names a `data/` class: it builds the Immich implementations and hands the
 * rest of the app only domain use cases. Swapping the photo server means changing this wiring.
 */
class AppContainer(context: Context) {
    private val http = immichHttpClient()
    private val sessions = StoredSessionRepository(
        SharedPreferencesStorage(context.getSharedPreferences("session", Context.MODE_PRIVATE)),
        KeystoreCipher(),
    )

    val connectToServer = ConnectToServer(ImmichServerConnector(http), sessions)
    val getSession = GetSession(sessions)
}
