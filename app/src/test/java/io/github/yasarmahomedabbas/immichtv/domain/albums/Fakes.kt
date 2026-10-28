package io.github.yasarmahomedabbas.immichtv.domain.albums

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import java.time.Instant

/** An album with sensible defaults; override what a test is about. */
fun album(
    id: String = "a1",
    name: String = "Summer",
    itemCount: Int = 10,
    ownedByMe: Boolean = true,
    updatedAt: String = "2025-08-01T00:00:00Z",
    newestFirst: Boolean = true,
    startDate: String? = "2025-06-01T00:00:00Z",
    endDate: String? = "2025-08-31T00:00:00Z",
) = Album(
    id = AlbumId(id),
    name = name,
    itemCount = itemCount,
    cover = PhotoId("$id-cover"),
    shared = !ownedByMe,
    ownedByMe = ownedByMe,
    updatedAt = Instant.parse(updatedAt),
    startDate = startDate?.let(Instant::parse),
    endDate = endDate?.let(Instant::parse),
    newestFirst = newestFirst,
)

/** An in-memory repository whose answers a test sets, counting the calls. */
class FakeAlbumRepository(
    var albums: ServerResult<List<Album>> = ServerResult.Ok(listOf(album())),
    var contents: ServerResult<AlbumContents> = ServerResult.Ok(AlbumContents(3, 1, emptyList())),
) : AlbumRepository {
    var albumsCalls = 0
    val contentsLimits = mutableListOf<Int>()
    var albumFailure: Problem? = null

    override suspend fun albums(): ServerResult<List<Album>> {
        albumsCalls++
        return albums
    }

    override suspend fun album(id: AlbumId): ServerResult<Album> {
        albumFailure?.let { return ServerResult.Failed(it) }
        val all = (albums as? ServerResult.Ok)?.value.orEmpty()
        return all.firstOrNull { it.id == id }?.let { ServerResult.Ok(it) } ?: ServerResult.Failed(Problem.Unreachable)
    }

    override suspend fun contents(album: Album, limit: Int): ServerResult<AlbumContents> {
        contentsLimits += limit
        return contents
    }
}
