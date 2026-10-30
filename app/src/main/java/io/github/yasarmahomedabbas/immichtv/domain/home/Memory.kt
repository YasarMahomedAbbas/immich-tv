package io.github.yasarmahomedabbas.immichtv.domain.home

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.photos.Timeline
import io.github.yasarmahomedabbas.immichtv.domain.photos.TimelineMonth
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Playlist
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.playlistFrom as playlistOf
import java.time.LocalDate
import java.time.YearMonth

/** What the user took on this calendar day in an earlier year. */
data class Memory(val takenOn: LocalDate, val slides: List<Slide>) {
    val photos: List<Photo> get() = slides.map { it.photo }
}

interface MemoryRepository {
    /** The "On this day" memories for [day], photos and videos, in any order. */
    suspend fun memoriesOn(day: LocalDate): ServerResult<List<Memory>>
}

/** This memory as a one-month [Timeline], for browsing it in a grid. */
fun Memory.timeline(): Timeline = object : Timeline {
    private val month = YearMonth.from(takenOn)

    override suspend fun months(): ServerResult<List<TimelineMonth>> =
        ServerResult.Ok(listOf(TimelineMonth(month, slides.size)))

    override suspend fun photos(month: YearMonth): ServerResult<List<Photo>> =
        ServerResult.Ok(if (month == this.month) photos else emptyList())
}

/** This memory as a show beginning at [from], in the order of its grid. */
fun Memory.playlistFrom(from: PhotoId): Playlist = playlistOf(slides, slides, from)
