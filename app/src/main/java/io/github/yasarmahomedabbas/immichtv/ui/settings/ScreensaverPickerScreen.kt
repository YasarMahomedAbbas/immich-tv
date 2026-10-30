package io.github.yasarmahomedabbas.immichtv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.ScreensaverSource
import io.github.yasarmahomedabbas.immichtv.ui.albums.AlbumsLoad
import io.github.yasarmahomedabbas.immichtv.ui.components.BackLink
import io.github.yasarmahomedabbas.immichtv.ui.components.FailedState
import io.github.yasarmahomedabbas.immichtv.ui.components.PickerRow
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import java.time.Instant

private val RowGap = 12.dp
private val FailedHeight = 420.dp
private const val SKELETON_ROWS = 5
private val SkeletonRow = 70.dp
private val SkeletonCorner = 16.dp

/**
 * What the screensaver plays: the library, then each album. OK saves and goes back. The random row
 * works whatever state the albums are in, and it needs no server.
 */
@Composable
fun ScreensaverPickerScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.load() }
    ScreensaverPickerContent(
        state,
        onChoose = {
            viewModel.choose(it)
            onBack()
        },
        onRetry = viewModel::retry,
        onBack = onBack,
    )
}

@Composable
private fun ScreensaverPickerContent(
    state: SettingsState,
    onChoose: (ScreensaverSource) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val enterFocus = remember { FocusRequester() }
    val selectedRow = state.selectedRow
    // Again once the albums load, when the selected album's row first exists.
    LaunchedEffect(selectedRow) {
        listState.scrollToItem(selectedRow)
        withFrameNanos { }
        runCatching { enterFocus.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 48.dp, start = 96.dp, end = 96.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        BackLink(stringResource(R.string.settings), onClick = onBack)
        Text(stringResource(R.string.screensaver_title), color = colors.onSurface, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(RowGap),
            // Room for the focus outline at the edges, and off the bottom of the screen.
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 48.dp),
        ) {
            item(key = "random") {
                PickerRow(
                    name = stringResource(R.string.screensaver_random),
                    selected = state.source == ScreensaverSource.Library,
                    onClick = { onChoose(ScreensaverSource.Library) },
                    modifier = if (selectedRow == 0) Modifier.focusRequester(enterFocus) else Modifier,
                )
            }
            when (val load = state.albums) {
                AlbumsLoad.Loading -> items(SKELETON_ROWS) {
                    SkeletonBlock(Modifier.fillMaxWidth().height(SkeletonRow), corner = SkeletonCorner)
                }
                is AlbumsLoad.Failed -> item(key = "failed") {
                    FailedState(load.problem, onRetry, Modifier.height(FailedHeight))
                }
                is AlbumsLoad.Loaded -> {
                    if (load.all.isEmpty()) {
                        item(key = "none") {
                            Text(stringResource(R.string.screensaver_no_albums), color = colors.onSurfaceVariant, fontSize = 22.sp)
                        }
                    }
                    items(state.choices, key = { it.id.value }) { album ->
                        val source = ScreensaverSource.Album(album.id)
                        PickerRow(
                            name = album.name,
                            selected = state.source == source,
                            onClick = { onChoose(source) },
                            modifier = if (state.choices.indexOf(album) + 1 == selectedRow) Modifier.focusRequester(enterFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerPreview(state: SettingsState) = ImmichTvTheme { ScreensaverPickerContent(state, {}, {}, {}) }

private val previewAlbums = listOf("Lisbon", "Summer", "Wedding").map { name ->
    Album(
        id = AlbumId(name),
        name = name,
        itemCount = 10,
        cover = PhotoId(name),
        shared = false,
        ownedByMe = true,
        updatedAt = Instant.EPOCH,
        startDate = null,
        endDate = null,
        newestFirst = true,
    )
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun PickerLoadedPreview() = PickerPreview(SettingsState(ScreensaverSource.Album(AlbumId("Summer")), albums = AlbumsLoad.Loaded(previewAlbums)))

@Preview(device = Devices.TV_1080p)
@Composable
private fun PickerLoadingPreview() = PickerPreview(SettingsState())

@Preview(device = Devices.TV_1080p)
@Composable
private fun PickerEmptyPreview() = PickerPreview(SettingsState(albums = AlbumsLoad.Loaded(emptyList())))

@Preview(device = Devices.TV_1080p)
@Composable
private fun PickerFailedPreview() = PickerPreview(SettingsState(albums = AlbumsLoad.Failed(Problem.Unreachable)))
