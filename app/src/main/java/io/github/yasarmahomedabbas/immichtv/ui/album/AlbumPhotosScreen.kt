package io.github.yasarmahomedabbas.immichtv.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.ui.components.BackLink
import io.github.yasarmahomedabbas.immichtv.ui.components.EmptyState
import io.github.yasarmahomedabbas.immichtv.ui.components.FailedState
import io.github.yasarmahomedabbas.immichtv.ui.components.KeyHint
import io.github.yasarmahomedabbas.immichtv.ui.components.RefreshOnStart
import io.github.yasarmahomedabbas.immichtv.ui.photos.GridLoad
import io.github.yasarmahomedabbas.immichtv.ui.photos.MonthPhotos
import io.github.yasarmahomedabbas.immichtv.ui.photos.MonthSection
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGrid
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridSkeleton
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridState
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridViewModel
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import java.time.YearMonth

/** Browse photos: "‹ <album>" over the album's photos by month. [returning]: focus goes back to the tile last viewed. */
@Composable
fun AlbumPhotosScreen(
    albumName: String,
    viewModel: PhotoGridViewModel,
    onBack: () -> Unit,
    returning: Boolean,
    onOpenPhoto: () -> Unit,
    onStartSlideshow: (PhotoId) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    RefreshOnStart(viewModel::refresh)
    AlbumPhotosContent(
        albumName = albumName,
        state = state,
        onBack = onBack,
        onNeedMonth = viewModel::loadMonth,
        onRetryMonth = viewModel::retryMonth,
        onRetry = viewModel::retry,
        onOpen = { photo ->
            viewModel.open(photo)
            onOpenPhoto()
        },
        onHold = { photo ->
            // The show doesn't move this, so focus comes back to the tile it started on.
            viewModel.open(photo)
            onStartSlideshow(photo.id)
        },
        restoreTo = if (returning) state.viewing?.id else null,
    )
}

@Composable
private fun AlbumPhotosContent(
    albumName: String,
    state: PhotoGridState,
    onBack: () -> Unit,
    onNeedMonth: (YearMonth) -> Unit,
    onRetryMonth: (YearMonth) -> Unit,
    onRetry: () -> Unit,
    onOpen: (Photo) -> Unit,
    onHold: (Photo) -> Unit,
    restoreTo: PhotoId?,
) {
    val backFocus = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 48.dp, start = 96.dp, end = 96.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        BackLink(albumName, onClick = onBack, modifier = Modifier.focusRequester(backFocus))
        when (val load = state.load) {
            GridLoad.Loading -> {
                // Something has to hold focus while it loads, so the remote works at once.
                LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
                PhotoGridSkeleton()
            }
            GridLoad.Empty -> EmptyState(
                title = stringResource(R.string.album_empty),
                hint = stringResource(R.string.album_photos_empty_hint),
                actionLabel = stringResource(R.string.album_photos_back),
                onAction = onBack,
                modifier = Modifier.padding(bottom = 48.dp),
            )
            is GridLoad.Failed -> FailedState(load.problem, onRetry, Modifier.padding(bottom = 48.dp))
            GridLoad.Loaded -> {
                // Until the first month's tiles are there, Back holds focus.
                LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
                Box(Modifier.fillMaxSize()) {
                    PhotoGrid(state.sections, onNeedMonth, onRetryMonth, onOpen = onOpen, restoreTo = restoreTo, onHold = onHold)
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
    }
}

private val previewSections = listOf(
    MonthSection(YearMonth.of(2025, 8), 14, MonthPhotos.Loaded(List(14) { Photo(PhotoId("a$it"), isVideo = it == 4) })),
    MonthSection(YearMonth.of(2025, 7), 9, MonthPhotos.Loading),
)

@Composable
private fun AlbumPhotosPreview(state: PhotoGridState) {
    ImmichTvTheme { AlbumPhotosContent("Holiday 2025", state, {}, {}, {}, {}, {}, {}, null) }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumPhotosLoadedPreview() = AlbumPhotosPreview(PhotoGridState(GridLoad.Loaded, previewSections))

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumPhotosMonthFailedPreview() = AlbumPhotosPreview(
    PhotoGridState(GridLoad.Loaded, listOf(previewSections[0], previewSections[1].copy(photos = MonthPhotos.Failed(Problem.Unreachable)))),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumPhotosSkeletonPreview() = AlbumPhotosPreview(PhotoGridState())

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumPhotosEmptyPreview() = AlbumPhotosPreview(PhotoGridState(GridLoad.Empty))

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumPhotosFailedPreview() = AlbumPhotosPreview(PhotoGridState(GridLoad.Failed(Problem.KeyLacksPermission)))
