package io.github.yasarmahomedabbas.immichtv.domain

/** What asking the photo server for something comes back with. */
sealed interface ServerResult<out T> {
    data class Ok<T>(val value: T) : ServerResult<T>
    data class Failed(val problem: Problem) : ServerResult<Nothing>
}

/** Why the server didn't give us what we asked for; each has its own message on screen. */
enum class Problem {
    /** A network failure, a timeout, the server failing (5xx), or an answer we can't read. */
    Unreachable,
    KeyRejected,
    KeyLacksPermission,

    /** No session is stored: the app has not been set up. */
    NotSignedIn,
}

/** [transform] on success; a failure passes through. */
inline fun <T, R> ServerResult<T>.map(transform: (T) -> R): ServerResult<R> = when (this) {
    is ServerResult.Ok -> ServerResult.Ok(transform(value))
    is ServerResult.Failed -> this
}
