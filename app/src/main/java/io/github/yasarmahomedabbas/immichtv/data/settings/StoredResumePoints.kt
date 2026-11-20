package io.github.yasarmahomedabbas.immichtv.data.settings

import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ResumePoint
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ResumePointStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Each album's remembered slideshow spot on the device, one key per album (`resume.<id>`) holding
 * `index|seed|photoId`, the seed empty when the show wasn't shuffled. The id goes last so a `|` in it
 * survives. Anything missing or unreadable is no spot.
 */
internal class StoredResumePoints(private val storage: StringStorage) : ResumePointStore {

    override suspend fun load(album: AlbumId): ResumePoint? = withContext(Dispatchers.IO) {
        storage.get(key(album))?.let(::decode)
    }

    override suspend fun save(album: AlbumId, point: ResumePoint) = withContext(Dispatchers.IO) {
        storage.put(mapOf(key(album) to encode(point)))
    }

    private fun key(album: AlbumId) = "resume.${album.value}"

    private fun encode(p: ResumePoint) = listOf(p.index, p.shuffleSeed ?: "", p.photo.value).joinToString("|")

    private fun decode(value: String): ResumePoint? {
        val parts = value.split("|", limit = 3)
        if (parts.size != 3) return null
        val seed = if (parts[1].isEmpty()) null else parts[1].toLongOrNull() ?: return null
        return ResumePoint(
            photo = PhotoId(parts[2].ifEmpty { return null }),
            index = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null,
            shuffleSeed = seed,
        )
    }
}
