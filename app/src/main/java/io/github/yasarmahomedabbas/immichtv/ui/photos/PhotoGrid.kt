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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.ui.components.PhotoTile
import io.github.yasarmahomedabbas.immichtv.ui.components.SecondaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.components.TileStyle
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val COLUMNS = 6
private val Gap = 14.dp

// Room for the 4dp focus outline, 4dp off a tile, inside the grid's clip.
private val FocusRoom = 8.dp

/**
 * Photos by month in a 6-column grid of square previews, a heading per month. A month not
 * loaded yet shows skeleton tiles and asks for its photos ([onNeedMonth]) when the grid reaches
 * it; a month that failed says so with Try again. Focus goes to the first tile once it is there.
 * OK on a tile calls [onOpen] (nothing yet: the photo viewer comes with #7).
 */
@Composable
fun PhotoGrid(
    sections: List<MonthSection>,
    onNeedMonth: (YearMonth) -> Unit,
    onRetryMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (Photo) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val firstTile = remember { FocusRequester() }
    val firstReady = (sections.firstOrNull()?.photos as? MonthPhotos.Loaded)?.photos?.isNotEmpty() == true
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(firstReady) {
        if (firstReady && !focusedOnce) {
            withFrameNanos { }
            focusedOnce = runCatching { firstTile.requestFocus() }.isSuccess
        }
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
            monthSection(section, isFirst = index == 0, firstTile, onNeedMonth, onRetryMonth, onOpen)
        }
    }
}

private fun LazyGridScope.monthSection(
    section: MonthSection,
    isFirst: Boolean,
    firstTile: FocusRequester,
    onNeedMonth: (YearMonth) -> Unit,
    onRetryMonth: (YearMonth) -> Unit,
    onOpen: (Photo) -> Unit,
) {
    val key = section.month.toString()
    item(key = "h-$key", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
        // Reaching a month's heading is reason enough to start loading it.
        LaunchedEffect(section.month) { onNeedMonth(section.month) }
        MonthHeading(section, Modifier.padding(top = if (isFirst) 0.dp else 26.dp))
    }
    when (val photos = section.photos) {
        is MonthPhotos.Loaded -> items(photos.photos, key = { "p-${it.id.value}" }, contentType = { "tile" }) { photo ->
            val first = isFirst && photo == photos.photos.first()
            PhotoTile(
                photo = photo.id,
                isVideo = photo.isVideo,
                focusable = true,
                onClick = { onOpen(photo) },
                modifier = Modifier
                    .aspectRatio(1f)
                    .then(if (first) Modifier.focusRequester(firstTile) else Modifier),
            )
        }
        is MonthPhotos.Failed -> item(key = "f-$key", span = { GridItemSpan(maxLineSpan) }, contentType = "failed") {
            MonthFailed { onRetryMonth(section.month) }
        }
        MonthPhotos.NotLoaded, MonthPhotos.Loading -> items(section.count, key = { "s-$key-$it" }, contentType = { "skeleton" }) {
            LaunchedEffect(section.month) { onNeedMonth(section.month) }
            SkeletonBlock(Modifier.aspectRatio(1f), corner = TileStyle.Tile.corner)
        }
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
private fun MonthFailed(onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.photos_month_failed), color = colors.onSurfaceVariant, fontSize = 22.sp)
        SecondaryButton(text = stringResource(R.string.try_again), onClick = onRetry)
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
