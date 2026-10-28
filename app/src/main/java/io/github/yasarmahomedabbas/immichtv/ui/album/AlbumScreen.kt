package io.github.yasarmahomedabbas.immichtv.ui.album

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import io.github.yasarmahomedabbas.immichtv.ui.components.SecondaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.SettingButton
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.components.TileStyle
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import java.time.Instant

private val MosaicCover = 560.dp
private val MosaicSide = 320.dp
private val MosaicRow = 300.dp
private val MosaicGap = 16.dp
private val StripTile = 232.dp

// Room for the 4dp focus outline, 4dp off a tile, inside the strip's clip.
private val FocusRoom = 8.dp

/**
 * One album: the cover mosaic, its title and counts, Start slideshow (focused when it opens; it
 * plays in #5), Browse photos, the slideshow settings and a strip of its first items.
 * [focusBrowse] puts focus on Browse photos instead, for coming Back from it.
 */
@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel,
    onBack: () -> Unit,
    onBrowsePhotos: (Album) -> Unit,
    focusBrowse: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    AlbumContent(
        state = state,
        focusBrowse = focusBrowse,
        onBack = onBack,
        onStartSlideshow = {},
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
    onBack: () -> Unit,
    onStartSlideshow: () -> Unit,
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
            .padding(top = 48.dp, start = 96.dp, end = 96.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        BackLink(stringResource(R.string.album_back), onClick = onBack, modifier = Modifier.focusRequester(backFocus))
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
                onStartSlideshow = onStartSlideshow,
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
    Row(horizontalArrangement = Arrangement.spacedBy(72.dp)) {
        Mosaic(album.cover, contents.firstItems)
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
    Strip(contents.firstItems)
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
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingButton(
            stringResource(R.string.setting_seconds),
            pluralStringResource(R.plurals.setting_seconds_value, settings.secondsPerPhoto, settings.secondsPerPhoto),
            onCycleSeconds,
        )
        SettingButton(stringResource(R.string.setting_order), stringResource(settings.order.label), onCycleOrder)
        SettingButton(stringResource(R.string.setting_videos), stringResource(settings.videos.label), onCycleVideos)
        SettingButton(
            stringResource(R.string.setting_date_place),
            stringResource(if (settings.showDateAndPlace) R.string.setting_on else R.string.setting_off),
            onToggleDateAndPlace,
        )
    }
}

/** "In this album": the first items as focusable tiles (opening one comes with the photo viewer). */
@Composable
private fun Strip(items: List<Photo>) {
    val colors = MaterialTheme.colorScheme
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
                    PhotoTile(photo.id, Modifier.size(StripTile), isVideo = photo.isVideo, focusable = true)
                }
            }
        }
    }
}

@Composable
private fun AlbumSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(72.dp)) {
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
                SkeletonBlock(Modifier.width(280.dp).height(64.dp), round = true)
                SkeletonBlock(Modifier.width(220.dp).height(64.dp), round = true)
            }
            Spacer(Modifier.height(56.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { SkeletonBlock(Modifier.width(230.dp).height(88.dp), corner = 16.dp) }
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
    startDate = Instant.parse("2025-06-03T10:00:00Z"),
    endDate = Instant.parse("2025-08-28T10:00:00Z"),
    newestFirst = true,
)

private val previewPage = AlbumPage(
    previewAlbum,
    AlbumContents(214, 12, List(30) { Photo(PhotoId("p$it"), isVideo = it % 7 == 3) }),
)

@Composable
private fun AlbumPreview(state: AlbumState) {
    ImmichTvTheme { AlbumContent(state, false, {}, {}, {}, {}, {}, {}, {}, {}) }
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
