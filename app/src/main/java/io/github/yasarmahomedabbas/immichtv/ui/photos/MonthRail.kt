package io.github.yasarmahomedabbas.immichtv.ui.photos

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.ui.components.houseFocusBorder
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

internal val RailWidth = 200.dp
private val MonthShape = RoundedCornerShape(12.dp)

// Room for the 4dp focus outline, 4dp off a month, inside the rail's clip.
private val FocusRoom = 8.dp

/** What the rail lists: a year's label, then its months. */
internal sealed interface RailEntry {
    data class Year(val year: Int) : RailEntry
    data class Month(val month: YearMonth) : RailEntry
}

/** A [RailEntry.Year] before each run of months in that year, keeping the sections' order. */
internal fun railEntries(sections: List<MonthSection>): List<RailEntry> = buildList {
    var year: Int? = null
    for (section in sections) {
        if (section.month.year != year) {
            year = section.month.year
            add(RailEntry.Year(section.month.year))
        }
        add(RailEntry.Month(section.month))
    }
}

/**
 * The months by year, down the left of the Photos tab. Years are labels: only months take focus.
 * OK on a month chooses it; moving along the rail changes nothing else. Focus coming in lands on
 * the [selected] month, kept in view as it changes; if that isn't laid out, on the nearest month.
 */
@OptIn(ExperimentalComposeUiApi::class) // focusProperties' enter: what focus restoring is built on.
@Composable
internal fun MonthRail(
    entries: List<RailEntry>,
    selected: YearMonth?,
    onChoose: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    val selectedIndex = selected?.let { entries.indexOf(RailEntry.Month(it)) } ?: -1
    LaunchedEffect(selectedIndex) {
        val shown = listState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex }
        // One above the month, so its year's label comes along when it starts the year.
        if (selectedIndex >= 0 && !shown) listState.scrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .width(RailWidth)
            .fillMaxHeight()
            .focusProperties {
                enter = {
                    val shown = listState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex }
                    if (shown) selectedFocus else FocusRequester.Default
                }
            }
            .focusGroup(),
        contentPadding = PaddingValues(start = FocusRoom, end = FocusRoom, top = FocusRoom, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(
            entries,
            key = { entry -> entry.toString() },
            contentType = { entry -> entry::class },
        ) { entry ->
            when (entry) {
                is RailEntry.Year -> YearLabel(entry.year)
                is RailEntry.Month -> MonthItem(
                    month = entry.month,
                    selected = entry.month == selected,
                    onClick = { onChoose(entry.month) },
                    modifier = if (entry.month == selected) Modifier.focusRequester(selectedFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun YearLabel(year: Int) {
    Text(
        year.toString(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun MonthItem(month: YearMonth, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = if (selected) colors.primary else colors.background
    val text = if (selected) colors.onPrimary else colors.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(MonthShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fill,
            contentColor = text,
            focusedContainerColor = fill,
            focusedContentColor = if (selected) text else colors.onSurface,
            pressedContainerColor = fill,
            pressedContentColor = text,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = houseFocusBorder(MonthShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            fontSize = 22.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
