package io.github.yasarmahomedabbas.immichtv.ui.photos

import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class MonthTilesTest {
    private val month = YearMonth.of(2021, 10)
    private val photos = listOf(Photo(PhotoId("a"), false), Photo(PhotoId("b"), true))

    @Test
    fun `a month still loading has a tile for each of its photos, so the D-pad can land there`() {
        assertEquals(listOf(null, null), MonthSection(month, 2).tiles())
        assertEquals(listOf(null, null), MonthSection(month, 2, MonthPhotos.Loading).tiles())
    }

    @Test
    fun `a loaded month's photos take the same places`() {
        assertEquals(photos, MonthSection(month, 2, MonthPhotos.Loaded(photos)).tiles())
    }

    private val august = YearMonth.of(2025, 8)
    private val july = YearMonth.of(2025, 7)
    private val june = YearMonth.of(2025, 6)

    @Test
    fun `a month's place counts a heading and every tile before it, loaded or not`() {
        val sections = listOf(
            MonthSection(august, 2, MonthPhotos.Loaded(photos)),
            MonthSection(july, 3),
            MonthSection(june, 1),
        )
        assertEquals(0, gridIndexOfMonth(sections, august))
        assertEquals(3, gridIndexOfMonth(sections, july))
        assertEquals(7, gridIndexOfMonth(sections, june))
        assertNull(gridIndexOfMonth(sections, YearMonth.of(2020, 1)))
    }

    @Test
    fun `a failed month takes one row however many it holds`() {
        val sections = listOf(MonthSection(august, 40, MonthPhotos.Failed(Problem.Unreachable)), MonthSection(july, 1))
        assertEquals(2, gridIndexOfMonth(sections, july))
    }

    @Test
    fun `a loaded month counts what it holds, not what the server said it would`() {
        val sections = listOf(MonthSection(august, 40, MonthPhotos.Loaded(photos)), MonthSection(july, 1))
        assertEquals(3, gridIndexOfMonth(sections, july))
    }

    @Test
    fun `a photo's place is after its heading and the months before it`() {
        val later = Photo(PhotoId("c"), false)
        val sections = listOf(
            MonthSection(august, 2, MonthPhotos.Loaded(photos)),
            MonthSection(july, 4),
            MonthSection(june, 1, MonthPhotos.Loaded(listOf(later))),
        )
        assertEquals(1, gridIndexOfPhoto(sections, PhotoId("a")))
        assertEquals(2, gridIndexOfPhoto(sections, PhotoId("b")))
        assertEquals(9, gridIndexOfPhoto(sections, later.id))
    }

    @Test
    fun `a photo not in a loaded month has no place`() {
        val sections = listOf(MonthSection(august, 2), MonthSection(july, 1, MonthPhotos.Failed(Problem.Unreachable)))
        assertNull(gridIndexOfPhoto(sections, PhotoId("a")))
    }
}
