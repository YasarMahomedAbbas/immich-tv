package io.github.yasarmahomedabbas.immichtv.ui.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.ui.components.PhotoTile
import io.github.yasarmahomedabbas.immichtv.ui.components.SecondaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.components.lostFocus
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val COLUMNS = 6
private val Gap = 14.dp

// Room for the 4dp focus outline, 4dp off a tile, inside the grid's clip.
private val FocusRoom = 8.dp

/**
 * Photos by month in a 6-column grid of square previews, a heading per month. A month not
 * loaded yet shows empty `surface` tiles and asks for its photos ([onNeedMonth]) when the grid reaches
 * it; a month that failed says so with Try again. Focus goes to the first tile once it is there.
 * OK on a tile calls [onOpen]. [onMonthFocused] says which month the remote is on. A [jumpTo] month
 * is scrolled to with its first tile focused (its Try again, if it failed), then [onJumped] is called.
 * The screen is torn down when a photo opens, so [restoreTo] says which tile to come back to, scrolled
 * there and focused in place of the first; one no longer there falls back to the first.
 */
@Composable
fun PhotoGrid(
    sections: List<MonthSection>,
    onNeedMonth: (YearMonth) -> Unit,
    onRetryMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (Photo) -> Unit = {},
    onMonthFocused: (YearMonth) -> Unit = {},
    jumpTo: YearMonth? = null,
    onJumped: () -> Unit = {},
    restoreTo: PhotoId? = null,
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = restoreTo?.let { gridIndexOfPhoto(sections, it) } ?: 0)
    val restoreFocus = remember { FocusRequester() }
    val firstOfMonth = remember { mutableMapOf<YearMonth, FocusRequester>() }
    val requesterOf = { month: YearMonth -> firstOfMonth.getOrPut(month) { FocusRequester() } }
    val focusFirst = {
        sections.firstOrNull()?.let { section -> runCatching { requesterOf(section.month).requestFocus() }.isSuccess } ?: false
    }
    val firstReady = (sections.firstOrNull()?.photos as? MonthPhotos.Loaded)?.photos?.isNotEmpty() == true
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(firstReady) {
        if (focusedOnce) return@LaunchedEffect
        val restoring = restoreTo?.let { gridIndexOfPhoto(sections, it) } != null
        if (restoring || firstReady) {
            withFrameNanos { }
            focusedOnce = if (restoring) runCatching { restoreFocus.requestFocus() }.isSuccess else focusFirst()
        }
    }
    // A refresh can take away the tile the remote is on; focus then starts again from the top.
    var lastFocused by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sections) {
        val present = sections.flatMap { section -> section.tiles().indices.map { tileKey(section.month, it) } }
        if (lostFocus(lastFocused, present)) {
            lastFocused = null
            gridState.scrollToItem(0)
            withFrameNanos { }
            focusFirst()
        }
    }
    LaunchedEffect(jumpTo) {
        val month = jumpTo ?: return@LaunchedEffect
        gridIndexOfMonth(sections, month)?.let { gridState.scrollToItem(it) }
        withFrameNanos { }
        runCatching { requesterOf(month).requestFocus() }
        onJumped()
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = FocusRoom, end = FocusRoom, top = FocusRoom, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        sections.forEachIndexed { index, section ->
            monthSection(section, isFirst = index == 0, requesterOf(section.month), restoreTo, restoreFocus, { lastFocused = it }, onNeedMonth, onRetryMonth, onOpen, onMonthFocused)
        }
    }
}

private fun tileKey(month: YearMonth, index: Int) = "t-$month-$index"

private fun LazyGridScope.monthSection(
    section: MonthSection,
    isFirst: Boolean,
    firstOfMonth: FocusRequester,
    restoreTo: PhotoId?,
    restoreFocus: FocusRequester,
    onTileFocused: (String) -> Unit,
    onNeedMonth: (YearMonth) -> Unit,
    onRetryMonth: (YearMonth) -> Unit,
    onOpen: (Photo) -> Unit,
    onMonthFocused: (YearMonth) -> Unit,
) {
    val key = section.month.toString()
    item(key = "h-$key", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
        // Reaching a month's heading is reason enough to start loading it.
        LaunchedEffect(section.month) { onNeedMonth(section.month) }
        MonthHeading(section, Modifier.padding(top = if (isFirst) 0.dp else 26.dp))
    }
    if (section.photos is MonthPhotos.Failed) {
        item(key = "f-$key", span = { GridItemSpan(maxLineSpan) }, contentType = "failed") {
            MonthFailed(
                onRetry = { onRetryMonth(section.month) },
                buttonModifier = Modifier
                    .focusRequester(firstOfMonth)
                    .onFocusChanged { if (it.isFocused) onMonthFocused(section.month) },
            )
        }
        return
    }
    val tiles = section.tiles()
    // The same key and the same focusable tile before and after the photo arrives: the remote can
    // always move on to a month still loading, and stays on its tile when the photo comes. Tiles
    // the D-pad couldn't land on sent focus search laying out the grid until the app stopped responding.
    itemsIndexed(tiles, key = { index, _ -> tileKey(section.month, index) }, contentType = { _, _ -> "tile" }) { index, photo ->
        if (photo == null) LaunchedEffect(section.month) { onNeedMonth(section.month) }
        PhotoTile(
            photo = photo?.id,
            isVideo = photo?.isVideo == true,
            focusable = true,
            onClick = { if (photo != null) onOpen(photo) },
            modifier = Modifier
                .aspectRatio(1f)
                .then(if (index == 0) Modifier.focusRequester(firstOfMonth) else Modifier)
                .then(if (photo != null && photo.id == restoreTo) Modifier.focusRequester(restoreFocus) else Modifier)
                .onFocusChanged {
                    if (it.isFocused) {
                        onTileFocused(tileKey(section.month, index))
                        onMonthFocused(section.month)
                    }
                },
        )
    }
}

@Composable
private fun MonthHeading(section: MonthSection, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val format = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(section.month.format(format), color = colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            pluralStringResource(R.plurals.photo_count, section.count, NumberFormat.getIntegerInstance().format(section.count)),
            color = colors.onSurfaceVariant,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun MonthFailed(onRetry: () -> Unit, buttonModifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.photos_month_failed), color = colors.onSurfaceVariant, fontSize = 22.sp)
        SecondaryButton(text = stringResource(R.string.try_again), onClick = onRetry, modifier = buttonModifier)
    }
}

/** The grid before its months are known: a heading and three rows of tiles. */
@Composable
fun PhotoGridSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(FocusRoom), verticalArrangement = Arrangement.spacedBy(Gap)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBlock(Modifier.width(260.dp).height(36.dp), round = true)
            SkeletonBlock(Modifier.width(110.dp).height(22.dp), round = true)
        }
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                repeat(COLUMNS) { SkeletonBlock(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
    }
}
