package io.github.yasarmahomedabbas.immichtv.domain.albums

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.map

/** The albums on the photo server. */
interface AlbumRepository {
    /** Every album the user owns or has been shared. */
    suspend fun albums(): ServerResult<List<Album>>

    suspend fun album(id: AlbumId): ServerResult<Album>

    /** [album]'s counts and its first [limit] items. */
    suspend fun contents(album: Album, limit: Int): ServerResult<AlbumContents>
}

/** Everything on the album screen. */
data class AlbumPage(val album: Album, val contents: AlbumContents)

class LoadAlbums(private val repository: AlbumRepository) {
    suspend operator fun invoke(): ServerResult<List<Album>> = repository.albums()
}

class LoadAlbum(private val repository: AlbumRepository) {
    suspend operator fun invoke(id: AlbumId): ServerResult<AlbumPage> {
        val album = when (val result = repository.album(id)) {
            is ServerResult.Ok -> result.value
            is ServerResult.Failed -> return result
        }
        return repository.contents(album, STRIP_SIZE).map { AlbumPage(album, it) }
    }

    companion object {
        /** How many items the album screen's strip shows. */
        const val STRIP_SIZE = 30
    }
}
