package io.github.yasarmahomedabbas.immichtv.ui.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.ui.Tab
import io.github.yasarmahomedabbas.immichtv.ui.components.EmptyState
import io.github.yasarmahomedabbas.immichtv.ui.components.FailedState
import io.github.yasarmahomedabbas.immichtv.ui.components.KeyHint
import io.github.yasarmahomedabbas.immichtv.ui.components.RefreshOnStart
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.main.MainScreen
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import java.time.YearMonth

private val RailGap = 48.dp

/** The Photos tab's content, under the top menu: the whole library by month, a rail beside the grid. */
@Composable
fun PhotosScreen(
    viewModel: PhotoGridViewModel,
    onOpenPhoto: () -> Unit,
    onStartSlideshow: (PhotoId) -> Unit,
    mayTakeFirstFocus: () -> Boolean,
) {
    val state by viewModel.state.collectAsState()
    RefreshOnStart(viewModel::refresh)
    PhotosContent(
        state = state,
        onNeedMonth = viewModel::loadMonth,
        onRetryMonth = viewModel::retryMonth,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onMonthFocused = viewModel::monthFocused,
        onOpen = { photo ->
            viewModel.open(photo)
            onOpenPhoto()
        },
        onHold = { photo ->
            // The show doesn't move this, so focus comes back to the tile it started on.
            viewModel.open(photo)
            onStartSlideshow(photo.id)
        },
        mayTakeFirstFocus = mayTakeFirstFocus,
    )
}

@Composable
internal fun PhotosContent(
    state: PhotoGridState,
    onNeedMonth: (YearMonth) -> Unit,
    onRetryMonth: (YearMonth) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onMonthFocused: (YearMonth) -> Unit,
    onOpen: (Photo) -> Unit,
    onHold: (Photo) -> Unit,
    mayTakeFirstFocus: () -> Boolean,
) {
    when (val load = state.load) {
        // Nothing here takes focus: it stays on the menu, where MainScreen put it and the remote can move it.
        GridLoad.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(RailGap)) {
            RailSkeleton()
            PhotoGridSkeleton()
        }
        GridLoad.Empty -> EmptyState(
            title = stringResource(R.string.photos_empty_title),
            hint = stringResource(R.string.photos_empty_hint),
            actionLabel = stringResource(R.string.albums_refresh),
            onAction = onRefresh,
            modifier = Modifier.padding(bottom = 48.dp),
        )
        is GridLoad.Failed -> FailedState(load.problem, onRetry, Modifier.padding(bottom = 48.dp))
        GridLoad.Loaded -> Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(RailGap)) {
                // Left from the grid's first column reaches the rail by focus search, and up from its first row the menu.
                var jumpTo by remember { mutableStateOf<YearMonth?>(null) }
                MonthRail(railEntries(state.sections), state.selectedMonth, onChoose = { jumpTo = it })
                PhotoGrid(
                    sections = state.sections,
                    onNeedMonth = onNeedMonth,
                    onRetryMonth = onRetryMonth,
                    modifier = Modifier.weight(1f),
                    onOpen = onOpen,
                    onMonthFocused = onMonthFocused,
                    jumpTo = jumpTo,
                    onJumped = { jumpTo = null },
                    restoreTo = state.viewing?.id,
                    onHold = onHold,
                    mayTakeFirstFocus = mayTakeFirstFocus,
                )
            }
            KeyHint(
                hints = listOf(
                    stringResource(R.string.hint_ok) to stringResource(R.string.hint_open),
                    stringResource(R.string.hint_hold_ok) to stringResource(R.string.hint_slideshow_from_here),
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 32.dp),
            )
        }
    }
}

/** The rail before its months are known: a year and a few months. */
@Composable
private fun RailSkeleton() {
    Column(Modifier.width(RailWidth).padding(8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SkeletonBlock(Modifier.width(72.dp).height(24.dp), round = true)
        repeat(6) { SkeletonBlock(Modifier.fillMaxWidth().height(52.dp)) }
    }
}

private val previewSections = listOf(
    MonthSection(YearMonth.of(2025, 1), 14, MonthPhotos.Loaded(List(14) { Photo(PhotoId("a$it"), isVideo = it == 4) })),
    MonthSection(YearMonth.of(2024, 12), 9, MonthPhotos.Loading),
    MonthSection(YearMonth.of(2024, 11), 30),
)

@Composable
private fun PhotosPreview(state: PhotoGridState) {
    ImmichTvTheme {
        MainScreen(Tab.Photos, {}, {}, albums = {}, photos = { PhotosContent(state, {}, {}, {}, {}, {}, {}, {}, mayTakeFirstFocus = { true }) })
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun PhotosLoadedPreview() = PhotosPreview(PhotoGridState(GridLoad.Loaded, previewSections))

@Preview(device = Devices.TV_1080p)
@Composable
private fun PhotosMonthFailedPreview() = PhotosPreview(
    PhotoGridState(GridLoad.Loaded, listOf(previewSections[0], previewSections[1].copy(photos = MonthPhotos.Failed(Problem.Unreachable)))),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun PhotosSkeletonPreview() = PhotosPreview(PhotoGridState())

@Preview(device = Devices.TV_1080p)
@Composable
private fun PhotosEmptyPreview() = PhotosPreview(PhotoGridState(GridLoad.Empty))

@Preview(device = Devices.TV_1080p)
@Composable
private fun PhotosFailedPreview() = PhotosPreview(PhotoGridState(GridLoad.Failed(Problem.Unreachable)))
