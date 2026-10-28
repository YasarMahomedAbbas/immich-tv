package io.github.yasarmahomedabbas.immichtv.domain.server

/** Checks what the user typed against the server and remembers the session when it works. */
class ConnectToServer(private val connector: ServerConnector, private val store: SessionStore) {
    suspend operator fun invoke(address: String, apiKey: String): ConnectResult {
        val server = ServerAddress.parse(address) ?: return ConnectResult.InvalidAddress
        val key = apiKey.trim()
        if (key.isEmpty()) return ConnectResult.MissingKey
        val result = connector.check(Session(server, ApiKey(key)))
        if (result is ConnectResult.Connected) store.save(result.session)
        return result
    }
}

/** The remembered session, or null when the app has not been set up. */
class GetSession(private val store: SessionStore) {
    suspend operator fun invoke(): Session? = store.load()
}
