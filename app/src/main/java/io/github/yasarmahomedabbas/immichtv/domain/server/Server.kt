package io.github.yasarmahomedabbas.immichtv.domain.server

import java.net.URI
import java.net.URISyntaxException

/** Where the photo server lives: an http(s) URL with no trailing slash. Only [parse] makes one. */
@ConsistentCopyVisibility
data class ServerAddress private constructor(val url: String) {
    companion object {
        /**
         * Trims [input], adds `http://` when no scheme is given and drops trailing `/`. Null if
         * it is blank, not http(s), or has no host.
         */
        fun parse(input: String): ServerAddress? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            val uri = try {
                URI(withScheme)
            } catch (_: URISyntaxException) {
                return null
            }
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            if (uri.host.isNullOrEmpty()) return null
            return ServerAddress(withScheme.trimEnd('/'))
        }
    }
}

/** The secret that opens the server. [toString] is redacted so it never reaches a log. */
class ApiKey(val value: String) {
    override fun toString() = "ApiKey(****)"
    override fun equals(other: Any?) = other is ApiKey && other.value == value
    override fun hashCode() = value.hashCode()
}

/** A server and the key that opens it: everything the app needs to load photos. */
data class Session(val server: ServerAddress, val apiKey: ApiKey)

/** What asking a server for a session can come back with. */
sealed interface ConnectResult {
    data class Connected(val session: Session, val userName: String) : ConnectResult
    data object InvalidAddress : ConnectResult
    data object MissingKey : ConnectResult

    /** A network failure, a timeout, or the server failing (5xx). */
    data object Unreachable : ConnectResult

    /** Something answered, but not as the photo server the app speaks to. */
    data object NotPhotoServer : ConnectResult

    /** The server is Immich, but older than the oldest version the app speaks; [version] is what it runs. */
    data class ServerTooOld(val version: String) : ConnectResult
    data object KeyRejected : ConnectResult
    data object KeyLacksPermission : ConnectResult
}

/** Asks a server whether it is one we can talk to and whether the key opens it. */
interface ServerConnector {
    suspend fun check(session: Session): ConnectResult
}

/** The one remembered session. */
interface SessionStore {
    suspend fun load(): Session?
    suspend fun save(session: Session)
}
