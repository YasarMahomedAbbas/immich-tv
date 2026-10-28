package io.github.yasarmahomedabbas.immichtv.domain.server

/** A connector that answers with [result] (or builds it from the session) and records each check. */
class FakeServerConnector(var result: (Session) -> ConnectResult = { ConnectResult.Connected(it, "Ada") }) : ServerConnector {
    val checked = mutableListOf<Session>()

    override suspend fun check(session: Session): ConnectResult {
        checked += session
        return result(session)
    }
}

/** An in-memory session store. */
class FakeSessionStore(var session: Session? = null) : SessionStore {
    var saves = 0

    override suspend fun load(): Session? = session

    override suspend fun save(session: Session) {
        saves++
        this.session = session
    }
}
