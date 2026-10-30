package io.github.yasarmahomedabbas.immichtv.domain.home

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumFilter
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumRepository
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumSort
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.filterAndSort
import io.github.yasarmahomedabbas.immichtv.domain.photos.LibraryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/** The big block at the top of Home: today's memory, or else an album. */
sealed interface HomeHero {
    /** [memory] holds photos only, so its count, its show and its grid agree. */
    data class FromMemory(val memory: Memory, val yearsAgo: Int) : HomeHero
    data class FromAlbum(val album: Album) : HomeHero
}

/** Everything on the Home tab; a [hero] is null when there is nothing to show in it. */
data class HomePage(val hero: HomeHero?, val albums: List<Album>, val recent: List<Photo>)

/**
 * Reads what Home shows. Albums and recent photos are what the page is made of, so either failing
 * fails it. A memory is a bonus: failing to read one, for any reason, including a key without the
 * permission, counts as no memory today.
 */
class LoadHome(
    private val albums: AlbumRepository,
    private val memories: MemoryRepository,
    private val library: LibraryRepository,
    private val today: () -> LocalDate = LocalDate::now,
) {
    suspend operator fun invoke(): ServerResult<HomePage> = coroutineScope {
        val day = today()
        val allAlbums = async { albums.albums() }
        val todays = async { memories.memoriesOn(day) }
        val recent = async { library.recent(RECENT_ROW) }

        val sorted = when (val result = allAlbums.await()) {
            is ServerResult.Ok -> result.value.filterAndSort(AlbumFilter.All, AlbumSort.RecentlyUpdated)
            is ServerResult.Failed -> return@coroutineScope result
        }
        val photos = when (val result = recent.await()) {
            is ServerResult.Ok -> result.value
            is ServerResult.Failed -> return@coroutineScope result
        }
        val memory = (todays.await() as? ServerResult.Ok)?.value.orEmpty()
            .map { memory -> memory.copy(slides = memory.slides.filterNot { it.photo.isVideo }) }
            .filter { it.slides.isNotEmpty() }
            .maxByOrNull { it.takenOn }
        val hero = when {
            memory != null -> HomeHero.FromMemory(memory, day.year - memory.takenOn.year)
            else -> sorted.firstOrNull { it.itemCount > 0 }?.let { HomeHero.FromAlbum(it) }
        }
        ServerResult.Ok(HomePage(hero, sorted.take(ALBUM_ROW), photos))
    }

    companion object {
        const val ALBUM_ROW = 10
        const val RECENT_ROW = 20
    }
}
