package io.github.yasarmahomedabbas.immichtv.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import io.github.yasarmahomedabbas.immichtv.ui.photos.GridLoad
import io.github.yasarmahomedabbas.immichtv.ui.photos.MonthPhotos
import io.github.yasarmahomedabbas.immichtv.ui.photos.MonthSection
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGrid
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridSkeleton
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridState
import io.github.yasarmahomedabbas.immichtv.ui.photos.PhotoGridViewModel
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import java.time.YearMonth

/** Browse photos: "‹ <album>" over the album's photos by month. */
@Composable
fun AlbumPhotosScreen(albumName: String, viewModel: PhotoGridViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    AlbumPhotosContent(albumName, state, onBack, viewModel::loadMonth, viewModel::retryMonth, viewModel::retry)
}

@Composable
private fun AlbumPhotosContent(
    albumName: String,
    state: PhotoGridState,
    onBack: () -> Unit,
    onNeedMonth: (YearMonth) -> Unit,
    onRetryMonth: (YearMonth) -> Unit,
    onRetry: () -> Unit,
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
                PhotoGrid(state.sections, onNeedMonth, onRetryMonth)
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
    ImmichTvTheme { AlbumPhotosContent("Holiday 2025", state, {}, {}, {}, {}) }
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
