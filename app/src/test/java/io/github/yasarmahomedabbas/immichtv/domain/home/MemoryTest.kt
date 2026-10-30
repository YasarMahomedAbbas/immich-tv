package io.github.yasarmahomedabbas.immichtv.domain.home

import io.github.yasarmahomedabbas.immichtv.domain.ServerResult
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.photos.TimelineMonth
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MemoryTest {
    private val photos = listOf(Photo(PhotoId("a"), false), Photo(PhotoId("b"), false))
    private val memory = Memory(LocalDate.of(2024, 10, 30), photos.map { Slide(it, false, null, null) })

    @Test
    fun `a memory's photos are its slides' photos`() {
        assertEquals(photos, memory.photos)
    }

    @Test
    fun `its timeline is one month of its photos`() = runTest {
        val timeline = memory.timeline()

        assertEquals(ServerResult.Ok(listOf(TimelineMonth(YearMonth.of(2024, 10), 2))), timeline.months())
        assertEquals(ServerResult.Ok(photos), timeline.photos(YearMonth.of(2024, 10)))
    }

    @Test
    fun `its show starts at the chosen photo, in the grid's order`() {
        val playlist = memory.playlistFrom(PhotoId("b"))
        assertEquals(memory.slides, playlist.slides)
        assertEquals(1, playlist.start)
    }

    @Test
    fun `its show from a photo no longer there starts at the first`() {
        assertEquals(0, memory.playlistFrom(PhotoId("gone")).start)
    }

    @Test
    fun `any other month of its timeline is empty`() = runTest {
        assertEquals(ServerResult.Ok(emptyList<Photo>()), memory.timeline().photos(YearMonth.of(2024, 9)))
    }
}
