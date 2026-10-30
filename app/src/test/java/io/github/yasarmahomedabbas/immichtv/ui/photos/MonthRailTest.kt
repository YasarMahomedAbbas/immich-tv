package io.github.yasarmahomedabbas.immichtv.ui.photos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class MonthRailTest {
    private fun sections(vararg months: YearMonth) = months.map { MonthSection(it, 1) }

    private fun year(year: Int) = RailEntry.Year(year)

    private fun month(year: Int, month: Int) = RailEntry.Month(YearMonth.of(year, month))

    @Test
    fun `nothing has no entries`() {
        assertEquals(emptyList<RailEntry>(), railEntries(emptyList()))
    }

    @Test
    fun `one year has one label, over its months`() {
        assertEquals(
            listOf(year(2025), month(2025, 8), month(2025, 7)),
            railEntries(sections(YearMonth.of(2025, 8), YearMonth.of(2025, 7))),
        )
    }

    @Test
    fun `a new year starts at the year boundary, newest first`() {
        assertEquals(
            listOf(year(2025), month(2025, 1), year(2024), month(2024, 12), month(2024, 3), year(2021), month(2021, 10)),
            railEntries(sections(YearMonth.of(2025, 1), YearMonth.of(2024, 12), YearMonth.of(2024, 3), YearMonth.of(2021, 10))),
        )
    }

    @Test
    fun `months keep the sections' order`() {
        assertEquals(
            listOf(year(2024), month(2024, 3), month(2024, 12), year(2025), month(2025, 1)),
            railEntries(sections(YearMonth.of(2024, 3), YearMonth.of(2024, 12), YearMonth.of(2025, 1))),
        )
    }
}
