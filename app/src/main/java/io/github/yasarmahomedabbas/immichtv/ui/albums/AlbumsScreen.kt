package io.github.yasarmahomedabbas.immichtv.ui.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumFilter
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumSort
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.ui.Tab
import io.github.yasarmahomedabbas.immichtv.ui.components.AlbumCard
import io.github.yasarmahomedabbas.immichtv.ui.components.Chip
import io.github.yasarmahomedabbas.immichtv.ui.components.EmptyState
import io.github.yasarmahomedabbas.immichtv.ui.components.FailedState
import io.github.yasarmahomedabbas.immichtv.ui.components.SecondaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.components.TileStyle
import io.github.yasarmahomedabbas.immichtv.ui.main.MainScreen
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import java.time.Instant

private const val COLUMNS = 5
private val Gap = 32.dp

// Room for the 4dp focus outline, 4dp off the card, inside the grid's clip.
private val FocusRoom = 8.dp

/** The Albums tab's content, under the top menu. */
@Composable
fun AlbumsScreen(viewModel: AlbumsViewModel, onOpenAlbum: (AlbumId) -> Unit) {
    val state by viewModel.state.collectAsState()
    AlbumsContent(
        state = state,
        onFilter = viewModel::setFilter,
        onCycleSort = viewModel::cycleSort,
        onFocused = viewModel::onFocused,
        onOpen = onOpenAlbum,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun AlbumsContent(
    state: AlbumsState,
    onFilter: (AlbumFilter) -> Unit,
    onCycleSort: () -> Unit,
    onFocused: (AlbumId) -> Unit,
    onOpen: (AlbumId) -> Unit,
    onRetry: () -> Unit,
) {
    when (val load = state.load) {
        AlbumsLoad.Loading -> AlbumsSkeleton()
        is AlbumsLoad.Failed -> FailedState(load.problem, onRetry)
        is AlbumsLoad.Loaded -> if (load.all.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.albums_empty_title),
                hint = stringResource(R.string.albums_empty_hint),
                actionLabel = stringResource(R.string.albums_refresh),
                onAction = onRetry,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Toolbar(state.filter, state.sort, onFilter, onCycleSort)
                Spacer(Modifier.height(32.dp - FocusRoom))
                val visible = state.visible
                if (visible.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.albums_filter_empty_title),
                        hint = stringResource(R.string.albums_filter_empty_hint),
                        actionLabel = stringResource(R.string.albums_show_all),
                        onAction = { onFilter(AlbumFilter.All) },
                    )
                } else {
                    AlbumGrid(visible, state.focused, onFocused, onOpen)
                }
            }
        }
    }
}

@Composable
private fun Toolbar(filter: AlbumFilter, sort: AlbumSort, onFilter: (AlbumFilter) -> Unit, onCycleSort: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for (option in AlbumFilter.entries) {
            Chip(text = stringResource(option.label), selected = option == filter, onClick = { onFilter(option) })
        }
        Spacer(Modifier.weight(1f))
        SecondaryButton(
            text = stringResource(R.string.albums_sort, stringResource(sort.label)),
            onClick = onCycleSort,
            icon = painterResource(R.drawable.ic_sort),
        )
    }
}

/**
 * The 5-column grid. Focus goes to the album the remote was last on (coming Back from it), or
 * else the first, once the cards are laid out; the grid scrolls to keep the focused card in view.
 */
@Composable
private fun AlbumGrid(albums: List<Album>, focused: AlbumId?, onFocused: (AlbumId) -> Unit, onOpen: (AlbumId) -> Unit) {
    val target = albums.indexOfFirst { it.id == focused }.coerceAtLeast(0)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = target - target % COLUMNS)
    val targetFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { targetFocus.requestFocus() }
    }
    // A lazy grid follows its first item's key, so a new filter or sort would leave it scrolled to
    // wherever that album landed. A new list starts from the top.
    var shown by remember { mutableStateOf(albums) }
    LaunchedEffect(albums) {
        if (albums != shown) {
            shown = albums
            gridState.scrollToItem(0)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = FocusRoom, end = FocusRoom, top = FocusRoom, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        items(albums, key = { it.id.value }) { album ->
            val isTarget = album.id == albums[target].id
            AlbumCard(
                album = album,
                onClick = { onOpen(album.id) },
                coverModifier = Modifier
                    .then(if (isTarget) Modifier.focusRequester(targetFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocused(album.id) },
            )
        }
    }
}

@Composable
private fun AlbumsSkeleton() {
    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (width in listOf(170, 160, 210)) SkeletonBlock(Modifier.width(width.dp).height(48.dp), round = true)
            Spacer(Modifier.weight(1f))
            SkeletonBlock(Modifier.width(300.dp).height(56.dp), round = true)
        }
        Spacer(Modifier.height(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    repeat(COLUMNS) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 10f), corner = TileStyle.AlbumCard.corner)
                            SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(24.dp), round = true)
                            SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(18.dp), round = true)
                        }
                    }
                }
            }
        }
    }
}

private val AlbumFilter.label: Int
    get() = when (this) {
        AlbumFilter.All -> R.string.albums_filter_all
        AlbumFilter.Mine -> R.string.albums_filter_mine
        AlbumFilter.SharedWithMe -> R.string.albums_filter_shared
    }

private val AlbumSort.label: Int
    get() = when (this) {
        AlbumSort.RecentlyUpdated -> R.string.albums_sort_recent
        AlbumSort.Name -> R.string.albums_sort_name
        AlbumSort.MostItems -> R.string.albums_sort_most
    }

private fun previewAlbum(i: Int, name: String, count: Int, mine: Boolean = true) = Album(
    id = AlbumId("a$i"),
    name = name,
    itemCount = count,
    cover = PhotoId("p$i"),
    shared = !mine,
    ownedByMe = mine,
    updatedAt = Instant.parse("2025-08-01T00:00:00Z").minusSeconds(i * 86_400L),
    startDate = null,
    endDate = null,
    newestFirst = true,
)

private val previewAlbums = listOf(
    "Summer 2025" to 226, "Wedding" to 1032, "Lisbon" to 88, "Garden" to 14, "Birthdays" to 301,
    "Snow" to 57, "Dogs" to 640, "Road trip" to 199, "First steps" to 1, "Concerts" to 45,
).mapIndexed { i, (name, count) -> previewAlbum(i, name, count, mine = i % 3 != 1) }

@Composable
private fun AlbumsPreview(state: AlbumsState) {
    ImmichTvTheme {
        MainScreen(tab = Tab.Albums, onSelectTab = {}, onOpenSettings = {}) {
            AlbumsContent(state, {}, {}, {}, {}, {})
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumsLoadedPreview() = AlbumsPreview(AlbumsState(AlbumsLoad.Loaded(previewAlbums)))

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumsSkeletonPreview() = AlbumsPreview(AlbumsState(AlbumsLoad.Loading))

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumsEmptyPreview() = AlbumsPreview(AlbumsState(AlbumsLoad.Loaded(emptyList())))

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumsFilterEmptyPreview() =
    AlbumsPreview(AlbumsState(AlbumsLoad.Loaded(previewAlbums.filter { it.ownedByMe }), filter = AlbumFilter.SharedWithMe))

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumsFailedPreview() = AlbumsPreview(AlbumsState(AlbumsLoad.Failed(Problem.Unreachable)))

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumsNoPermissionPreview() = AlbumsPreview(AlbumsState(AlbumsLoad.Failed(Problem.KeyLacksPermission)))
