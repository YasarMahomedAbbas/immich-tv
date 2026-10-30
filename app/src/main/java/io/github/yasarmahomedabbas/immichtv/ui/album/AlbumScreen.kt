package io.github.yasarmahomedabbas.immichtv.ui.album

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Album
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumContents
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumPage
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowOrder
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.VideoMode
import io.github.yasarmahomedabbas.immichtv.ui.components.BackLink
import io.github.yasarmahomedabbas.immichtv.ui.components.FailedState
import io.github.yasarmahomedabbas.immichtv.ui.components.PhotoTile
import io.github.yasarmahomedabbas.immichtv.ui.components.PrimaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.RefreshOnStart
import io.github.yasarmahomedabbas.immichtv.ui.components.SecondaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.SettingButton
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.CameFrom
import io.github.yasarmahomedabbas.immichtv.ui.components.TileStyle
import io.github.yasarmahomedabbas.immichtv.ui.components.lostFocus
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import io.github.yasarmahomedabbas.immichtv.ui.theme.CANVAS_WIDTH
import java.time.Instant
import java.time.LocalDate

private val MosaicCover = 560.dp
private val MosaicSide = 320.dp

// 284, not the canvas's 300: with 300 the canvas's strip ends at 1088 of 1080, off the screen.
private val MosaicRow = 284.dp
private val MosaicGap = 16.dp
private val StripTile = 232.dp

// Room for the 4dp focus outline, 4dp off a tile, inside the strip's clip.
private val FocusRoom = 8.dp

private val ScreenTop = 48.dp
private val ScreenSide = 96.dp
private val SectionGap = 40.dp
private val BackLinkHeight = 56.dp
private val MosaicToInfo = 72.dp
private val StripHeading = 38.dp
private val SettingGap = 14.dp

/**
 * Two rows of two, not the canvas's one row: four at their 230dp minimum need 962dp and the column
 * beside the mosaic has 760, so the last one was crushed and pushed the strip off the screen.
 */
internal const val SettingsPerRow = 2

/** The width beside the mosaic. */
internal val AlbumInfoWidth = CANVAS_WIDTH.dp - ScreenSide * 2 - MosaicCover - MosaicGap - MosaicSide - MosaicToInfo

/** How far down the canvas the strip's focus outline reaches. */
internal val AlbumContentBottom = ScreenTop + BackLinkHeight + SectionGap + MosaicRow * 2 + MosaicGap + SectionGap +
    StripHeading + (18.dp - FocusRoom) + FocusRoom + StripTile + FocusRoom

/** How wide each setting is, in [AlbumInfoWidth]. */
internal val SettingWidth = (AlbumInfoWidth - SettingGap * (SettingsPerRow - 1)) / SettingsPerRow

/** The back link names where Back goes, as `AppViewModel.back` decides it. */
@StringRes
internal fun albumBackLabel(cameFrom: CameFrom): Int = when (cameFrom) {
    CameFrom.HomeRow, CameFrom.HomeHero -> R.string.album_back_home
    CameFrom.AlbumsTab -> R.string.album_back
}

/**
 * One album: the cover mosaic, its title and counts, Start slideshow (focused when it opens),
 * Browse photos, the slideshow settings and a strip of its first items.
 * [focusBrowse] puts focus on Browse photos instead, for coming Back from it.
 */
@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel,
    onBack: () -> Unit,
    onBrowsePhotos: (Album) -> Unit,
    onStartSlideshow: (Album, SlideshowSettings) -> Unit,
    focusBrowse: Boolean = false,
    cameFrom: CameFrom = CameFrom.AlbumsTab,
) {
    val state by viewModel.state.collectAsState()
    RefreshOnStart(viewModel::refresh)
    AlbumContent(
        state = state,
        focusBrowse = focusBrowse,
        backLabel = albumBackLabel(cameFrom),
        onBack = onBack,
        onStartSlideshow = onStartSlideshow,
        onBrowsePhotos = onBrowsePhotos,
        onCycleSeconds = viewModel::cycleSeconds,
        onCycleOrder = viewModel::cycleOrder,
        onCycleVideos = viewModel::cycleVideos,
        onToggleDateAndPlace = viewModel::toggleDateAndPlace,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun AlbumContent(
    state: AlbumState,
    focusBrowse: Boolean,
    @StringRes backLabel: Int,
    onBack: () -> Unit,
    onStartSlideshow: (Album, SlideshowSettings) -> Unit,
    onBrowsePhotos: (Album) -> Unit,
    onCycleSeconds: () -> Unit,
    onCycleOrder: () -> Unit,
    onCycleVideos: () -> Unit,
    onToggleDateAndPlace: () -> Unit,
    onRetry: () -> Unit,
) {
    val backFocus = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = ScreenTop, start = ScreenSide, end = ScreenSide),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        BackLink(stringResource(backLabel), onClick = onBack, modifier = Modifier.focusRequester(backFocus))
        when (val load = state.load) {
            AlbumLoad.Loading -> {
                // Something has to hold focus while it loads, so the remote works at once.
                LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
                AlbumSkeleton()
            }
            is AlbumLoad.Failed -> FailedState(load.problem, onRetry, Modifier.padding(bottom = 48.dp))
            is AlbumLoad.Loaded -> LoadedAlbum(
                page = load.page,
                settings = state.settings,
                focusBrowse = focusBrowse,
                onStartSlideshow = { onStartSlideshow(load.page.album, state.settings) },
                onBrowsePhotos = { onBrowsePhotos(load.page.album) },
                onCycleSeconds = onCycleSeconds,
                onCycleOrder = onCycleOrder,
                onCycleVideos = onCycleVideos,
                onToggleDateAndPlace = onToggleDateAndPlace,
            )
        }
    }
}

@Composable
private fun LoadedAlbum(
    page: AlbumPage,
    settings: SlideshowSettings,
    focusBrowse: Boolean,
    onStartSlideshow: () -> Unit,
    onBrowsePhotos: () -> Unit,
    onCycleSeconds: () -> Unit,
    onCycleOrder: () -> Unit,
    onCycleVideos: () -> Unit,
    onToggleDateAndPlace: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val album = page.album
    val contents = page.contents
    val startFocus = remember { FocusRequester() }
    val browseFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { (if (focusBrowse) browseFocus else startFocus).requestFocus() }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MosaicToInfo)) {
        Mosaic(album.cover, contents.firstItems)
        Column(Modifier.weight(1f).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                album.name,
                color = colors.onSurface,
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                albumSummary(contents.photoCount, contents.videoCount, album.startDate, album.endDate),
                color = colors.onSurfaceVariant,
                fontSize = 24.sp,
            )
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.album_start_slideshow),
                    onClick = onStartSlideshow,
                    icon = painterResource(R.drawable.ic_play),
                    modifier = Modifier.focusRequester(startFocus),
                )
                SecondaryButton(
                    text = stringResource(R.string.album_browse_photos),
                    onClick = onBrowsePhotos,
                    modifier = Modifier.focusRequester(browseFocus),
                )
            }
            Text(
                stringResource(R.string.album_settings_heading),
                modifier = Modifier.padding(top = 28.dp),
                color = colors.onSurfaceVariant,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Settings(settings, onCycleSeconds, onCycleOrder, onCycleVideos, onToggleDateAndPlace)
            Text(stringResource(R.string.album_settings_hint), color = colors.onSurfaceVariant, fontSize = 18.sp)
        }
    }
    Strip(contents.firstItems, onFocusLost = { runCatching { startFocus.requestFocus() } })
}

/** The cover over two rows, then the album's next two items; a missing tile stays `surface`. */
@Composable
private fun Mosaic(cover: PhotoId?, items: List<Photo>) {
    val rest = items.filter { it.id != cover }
    Row(horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
        PhotoTile(cover ?: rest.firstOrNull()?.id, Modifier.size(MosaicCover, MosaicRow * 2 + MosaicGap), style = TileStyle.Mosaic)
        val side = if (cover == null) rest.drop(1) else rest
        Column(verticalArrangement = Arrangement.spacedBy(MosaicGap)) {
            for (i in 0..1) PhotoTile(side.getOrNull(i)?.id, Modifier.size(MosaicSide, MosaicRow), style = TileStyle.Mosaic)
        }
    }
}

@Composable
private fun Settings(
    settings: SlideshowSettings,
    onCycleSeconds: () -> Unit,
    onCycleOrder: () -> Unit,
    onCycleVideos: () -> Unit,
    onToggleDateAndPlace: () -> Unit,
) {
    val all = listOf(
        Triple(
            stringResource(R.string.setting_seconds),
            pluralStringResource(R.plurals.setting_seconds_value, settings.secondsPerPhoto, settings.secondsPerPhoto),
            onCycleSeconds,
        ),
        Triple(stringResource(R.string.setting_order), stringResource(settings.order.label), onCycleOrder),
        Triple(stringResource(R.string.setting_videos), stringResource(settings.videos.label), onCycleVideos),
        Triple(
            stringResource(R.string.setting_date_place),
            stringResource(if (settings.showDateAndPlace) R.string.setting_on else R.string.setting_off),
            onToggleDateAndPlace,
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(SettingGap)) {
        for (row in all.chunked(SettingsPerRow)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SettingGap)) {
                for ((label, value, onClick) in row) SettingButton(label, value, onClick, Modifier.weight(1f))
            }
        }
    }
}

/**
 * "In this album": the first items as focusable tiles (opening one comes with the photo viewer).
 * [onFocusLost] is called when the tile the remote was last on is gone after a refresh.
 */
@Composable
private fun Strip(items: List<Photo>, onFocusLost: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var lastFocused by remember { mutableStateOf<PhotoId?>(null) }
    LaunchedEffect(items) {
        if (lostFocus(lastFocused, items.map { it.id })) {
            lastFocused = null
            onFocusLost()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp - FocusRoom)) {
        Text(stringResource(R.string.album_in_this_album), color = colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (items.isEmpty()) {
            Text(stringResource(R.string.album_empty), color = colors.onSurfaceVariant, fontSize = 22.sp, modifier = Modifier.padding(top = FocusRoom))
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(FocusRoom),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(items, key = { it.id.value }) { photo ->
                    PhotoTile(
                        photo.id,
                        Modifier.size(StripTile).onFocusChanged { if (it.isFocused) lastFocused = photo.id },
                        isVideo = photo.isVideo,
                        focusable = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(MosaicToInfo)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
            SkeletonBlock(Modifier.size(MosaicCover, MosaicRow * 2 + MosaicGap), corner = TileStyle.Mosaic.corner)
            Column(verticalArrangement = Arrangement.spacedBy(MosaicGap)) {
                repeat(2) { SkeletonBlock(Modifier.size(MosaicSide, MosaicRow), corner = TileStyle.Mosaic.corner) }
            }
        }
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBlock(Modifier.width(520.dp).height(64.dp), round = true)
            SkeletonBlock(Modifier.width(420.dp).height(28.dp), round = true)
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                SkeletonBlock(Modifier.width(320.dp).height(76.dp), round = true)
                SkeletonBlock(Modifier.width(260.dp).height(76.dp), round = true)
            }
            Spacer(Modifier.height(56.dp))
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(SettingGap)) {
                    repeat(SettingsPerRow) { SkeletonBlock(Modifier.width(SettingWidth).height(88.dp), corner = 16.dp) }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SkeletonBlock(Modifier.width(220.dp).height(32.dp), round = true)
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(7) { SkeletonBlock(Modifier.size(StripTile)) }
        }
    }
}

private val SlideshowOrder.label: Int
    get() = when (this) {
        SlideshowOrder.OldestFirst -> R.string.setting_order_oldest
        SlideshowOrder.Shuffle -> R.string.setting_order_shuffle
        SlideshowOrder.NewestFirst -> R.string.setting_order_newest
    }

private val VideoMode.label: Int
    get() = when (this) {
        VideoMode.PlayMuted -> R.string.setting_videos_muted
        VideoMode.PlayWithSound -> R.string.setting_videos_sound
        VideoMode.Skip -> R.string.setting_videos_skip
    }

private val previewAlbum = Album(
    id = AlbumId("a"),
    name = "Holiday 2025",
    itemCount = 226,
    cover = PhotoId("c"),
    shared = false,
    ownedByMe = true,
    updatedAt = Instant.parse("2025-09-01T00:00:00Z"),
    startDate = LocalDate.of(2025, 6, 3),
    endDate = LocalDate.of(2025, 8, 28),
    newestFirst = true,
)

private val previewPage = AlbumPage(
    previewAlbum,
    AlbumContents(214, 12, List(30) { Photo(PhotoId("p$it"), isVideo = it % 7 == 3) }),
)

@Composable
private fun AlbumPreview(state: AlbumState) {
    ImmichTvTheme { AlbumContent(state, false, R.string.album_back, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}) }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumLoadedPreview() = AlbumPreview(AlbumState(AlbumLoad.Loaded(previewPage)))

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumChangedSettingsPreview() = AlbumPreview(
    AlbumState(AlbumLoad.Loaded(previewPage), SlideshowSettings(20, SlideshowOrder.Shuffle, VideoMode.Skip, false)),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumEmptyPreview() = AlbumPreview(
    AlbumState(AlbumLoad.Loaded(AlbumPage(previewAlbum.copy(cover = null, itemCount = 0, startDate = null, endDate = null), AlbumContents(0, 0, emptyList())))),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumSkeletonPreview() = AlbumPreview(AlbumState())

@Preview(device = Devices.TV_1080p)
@Composable
private fun AlbumFailedPreview() = AlbumPreview(AlbumState(AlbumLoad.Failed(Problem.Unreachable)))
