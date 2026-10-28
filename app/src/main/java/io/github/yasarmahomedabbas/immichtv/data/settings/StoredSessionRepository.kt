package io.github.yasarmahomedabbas.immichtv.data.settings

import io.github.yasarmahomedabbas.immichtv.domain.server.ApiKey
import io.github.yasarmahomedabbas.immichtv.domain.server.ServerAddress
import io.github.yasarmahomedabbas.immichtv.domain.server.Session
import io.github.yasarmahomedabbas.immichtv.domain.server.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The session on the device: the address in the clear, the key only as sealed by [cipher]. */
internal class StoredSessionRepository(
    private val storage: StringStorage,
    private val cipher: SecretCipher,
) : SessionStore {

    override suspend fun load(): Session? = withContext(Dispatchers.IO) {
        val url = storage.get(SERVER_URL) ?: return@withContext null
        val sealed = storage.get(API_KEY_SEALED) ?: return@withContext null
        // A key that can't be opened (the Keystore key was lost, or the value is corrupt) means
        // no session: the app goes back to Setup rather than failing.
        val plain = try {
            cipher.decrypt(sealed)
        } catch (_: Exception) {
            return@withContext null
        }
        val server = ServerAddress.parse(url) ?: return@withContext null
        Session(server, ApiKey(plain))
    }

    override suspend fun save(session: Session) = withContext(Dispatchers.IO) {
        storage.put(
            mapOf(
                SERVER_URL to session.server.url,
                API_KEY_SEALED to cipher.encrypt(session.apiKey.value),
            ),
        )
    }

    private companion object {
        const val SERVER_URL = "server_url"
        const val API_KEY_SEALED = "api_key_sealed"
    }
}
