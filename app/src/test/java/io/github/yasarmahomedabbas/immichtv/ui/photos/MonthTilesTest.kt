package io.github.yasarmahomedabbas.immichtv.ui.photos

import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import org.junit.Assert.assertEquals
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
}
