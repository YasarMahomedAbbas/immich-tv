package io.github.yasarmahomedabbas.immichtv.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import io.github.yasarmahomedabbas.immichtv.domain.albums.AlbumId
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.home.HomeHero
import io.github.yasarmahomedabbas.immichtv.domain.home.HomePage
import io.github.yasarmahomedabbas.immichtv.domain.home.Memory
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.SlideshowSettings
import io.github.yasarmahomedabbas.immichtv.ui.Tab
import io.github.yasarmahomedabbas.immichtv.ui.components.AlbumCard
import io.github.yasarmahomedabbas.immichtv.ui.components.EmptyState
import io.github.yasarmahomedabbas.immichtv.ui.components.FailedState
import io.github.yasarmahomedabbas.immichtv.ui.components.OverPhotoPanel
import io.github.yasarmahomedabbas.immichtv.ui.components.OverPhotoStyle
import io.github.yasarmahomedabbas.immichtv.ui.components.PhotoTile
import io.github.yasarmahomedabbas.immichtv.ui.components.PrimaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.RefreshOnStart
import io.github.yasarmahomedabbas.immichtv.ui.components.SecondaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.components.TileStyle
import io.github.yasarmahomedabbas.immichtv.ui.main.MainScreen
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val HeroHeight = 380.dp
private val PanelInset = 40.dp
private val PanelWidth = 620.dp
private val PanelPadding = 28.dp
private val BlockGap = 40.dp
private val AlbumWidth = 320.dp
private val AlbumGap = 32.dp
private val RecentSize = 200.dp
private val RecentGap = 18.dp

// Room for the 4dp focus outline, 4dp off the tile, inside the row's clip.
private val FocusRoom = 8.dp

/** The actions on the page, each of them leaving Home. */
private class HomeActions(
    val onPlayMemory: (Memory) -> Unit,
    val onMemoryPhotos: (Memory, String) -> Unit,
    val onPlayAlbum: (Album) -> Unit,
    val onAlbumPhotos: (Album) -> Unit,
    val onOpenAlbum: (AlbumId) -> Unit,
    val onOpenRecent: (Photo) -> Unit,
    val onSeeAll: () -> Unit,
)

/** The Home tab's content, under the top menu: the hero, then the Albums and Recently added rows. */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlayMemory: (Memory) -> Unit,
    onMemoryPhotos: (Memory, String) -> Unit,
    onPlayAlbum: (Album, SlideshowSettings) -> Unit,
    onAlbumPhotos: (Album) -> Unit,
    onOpenAlbum: (AlbumId) -> Unit,
    onOpenRecent: (Photo) -> Unit,
    onSeeAll: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    RefreshOnStart(viewModel::refresh)
    HomeContent(
        state = state,
        onFocused = viewModel::onFocused,
        onRetry = viewModel::retry,
        actions = HomeActions(
            onPlayMemory = onPlayMemory,
            onMemoryPhotos = onMemoryPhotos,
            onPlayAlbum = { album -> scope.launch { onPlayAlbum(album, viewModel.heroAlbumSettings(album)) } },
            onAlbumPhotos = onAlbumPhotos,
            onOpenAlbum = onOpenAlbum,
            onOpenRecent = onOpenRecent,
            onSeeAll = onSeeAll,
        ),
    )
}

@Composable
private fun HomeContent(state: HomeState, onFocused: (HomeFocus) -> Unit, onRetry: () -> Unit, actions: HomeActions) {
    when (val load = state.load) {
        HomeLoad.Loading -> HomeSkeleton()
        is HomeLoad.Failed -> FailedState(load.problem, onRetry)
        is HomeLoad.Loaded -> {
            val target = load.page.focusTarget(state.focused)
            if (target == null) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    hint = stringResource(R.string.home_empty_hint),
                    actionLabel = stringResource(R.string.albums_refresh),
                    onAction = onRetry,
                )
            } else {
                HomeLoaded(load.page, state.focused, target, onFocused, actions)
            }
        }
    }
}

/**
 * Focus goes to [target] once the page is laid out, which beats the top menu's request as the Albums
 * grid's does, and again if a refresh takes away what the remote was on. Each block is brought into
 * view whole as focus enters it, so a row's title comes with it and moving up returns to the hero.
 */
@OptIn(ExperimentalComposeUiApi::class) // FocusRequester.Cancel: how a row's last item stops Right.
@Composable
private fun HomeLoaded(
    page: HomePage,
    focused: HomeFocus,
    target: HomeFocus,
    onFocused: (HomeFocus) -> Unit,
    actions: HomeActions,
) {
    val targetFocus = remember { FocusRequester() }
    val seeAllFocus = remember { FocusRequester() }
    val viewPhotosFocus = remember { FocusRequester() }
    val albumRow = rememberLazyListState(page.albums.indexOfFirst { HomeFocus.AlbumCard(it.id) == target }.coerceAtLeast(0))
    val recentRow = rememberLazyListState(page.recent.indexOfFirst { HomeFocus.Recent(it.id) == target }.coerceAtLeast(0))
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { targetFocus.requestFocus() }
    }
    val lost = target != focused
    LaunchedEffect(lost) {
        if (!lost) return@LaunchedEffect
        albumRow.scrollToItem(0)
        recentRow.scrollToItem(0)
        withFrameNanos { }
        runCatching { targetFocus.requestFocus() }
    }
    val focusOn: Modifier.(HomeFocus) -> Modifier = { focus ->
        then(if (focus == target) Modifier.focusRequester(targetFocus) else Modifier)
            .then(
                when (focus) {
                    HomeFocus.SeeAll -> Modifier.focusRequester(seeAllFocus)
                    HomeFocus.ViewPhotos -> Modifier.focusRequester(viewPhotosFocus)
                    else -> Modifier
                },
            )
            .focusProperties {
                when (page.upFrom(focus)) {
                    HomeFocus.SeeAll -> up = seeAllFocus
                    HomeFocus.ViewPhotos -> up = viewPhotosFocus
                    else -> Unit
                }
                if (page.endsRow(focus)) right = FocusRequester.Cancel
            }
            .onFocusChanged { if (it.isFocused) onFocused(focus) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(BlockGap),
    ) {
        page.hero?.let { hero -> HomeBlock { Hero(hero, focusOn, actions) } }
        if (page.albums.isNotEmpty()) HomeBlock { AlbumsRow(page.albums, albumRow, focusOn, actions) }
        if (page.recent.isNotEmpty()) HomeBlock { RecentRow(page.recent, recentRow, focusOn, actions.onOpenRecent) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBlock(content: @Composable ColumnScope.() -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .onFocusChanged { if (it.hasFocus) scope.launch { requester.bringIntoView() } },
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** What the hero says and does, for one [HeroBlock] call: a refresh that swaps memory and album keeps the focused button. */
private class HeroContent(val photo: PhotoId?, val label: String, val title: String, val line: String, val onPlay: () -> Unit, val onPhotos: () -> Unit)

@Composable
private fun Hero(hero: HomeHero, focusOn: Modifier.(HomeFocus) -> Modifier, actions: HomeActions) {
    val content = when (hero) {
        is HomeHero.FromMemory -> {
            val title = pluralStringResource(R.plurals.home_years_ago, hero.yearsAgo, hero.yearsAgo)
            val date = hero.memory.takenOn.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
            HeroContent(
                photo = hero.memory.photos.first().id,
                label = stringResource(R.string.home_label_memory),
                title = title,
                line = "$date · ${photoCount(hero.memory.slides.size)}",
                onPlay = { actions.onPlayMemory(hero.memory) },
                onPhotos = { actions.onMemoryPhotos(hero.memory, title) },
            )
        }
        is HomeHero.FromAlbum -> HeroContent(
            photo = hero.album.cover,
            label = stringResource(R.string.home_label_album),
            title = hero.album.name,
            line = photoCount(hero.album.itemCount),
            onPlay = { actions.onPlayAlbum(hero.album) },
            onPhotos = { actions.onAlbumPhotos(hero.album) },
        )
    }
    HeroBlock(content.photo, content.label, content.title, content.line, focusOn, content.onPlay, content.onPhotos)
}

@Composable
private fun photoCount(count: Int): String =
    pluralStringResource(R.plurals.photo_count, count, NumberFormat.getIntegerInstance().format(count))

@Composable
private fun HeroBlock(
    photo: PhotoId?,
    label: String,
    title: String,
    line: String,
    focusOn: Modifier.(HomeFocus) -> Modifier,
    onPlay: () -> Unit,
    onPhotos: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        PhotoTile(photo, Modifier.fillMaxSize(), style = TileStyle.Hero)
        HeroPanel(label, title, line, focusOn, onPlay, onPhotos)
    }
}

@Composable
private fun BoxScope.HeroPanel(
    label: String,
    title: String,
    line: String,
    focusOn: Modifier.(HomeFocus) -> Modifier,
    onPlay: () -> Unit,
    onPhotos: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OverPhotoPanel(
        OverPhotoStyle.Hero,
        Modifier.align(Alignment.BottomStart).padding(start = PanelInset, bottom = PanelInset).width(PanelWidth),
    ) {
        Column(Modifier.padding(PanelPadding)) {
            Text(label, color = colors.onSurfaceVariant, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                title,
                color = colors.onSurface,
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(line, color = colors.onSurfaceVariant, fontSize = 22.sp, maxLines = 1)
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryButton(
                    text = stringResource(R.string.home_play_slideshow),
                    onClick = onPlay,
                    modifier = Modifier.focusOn(HomeFocus.Play),
                    icon = painterResource(R.drawable.ic_play),
                )
                SecondaryButton(
                    text = stringResource(R.string.home_view_photos),
                    onClick = onPhotos,
                    modifier = Modifier.focusOn(HomeFocus.ViewPhotos),
                )
            }
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun AlbumsRow(albums: List<Album>, state: LazyListState, focusOn: Modifier.(HomeFocus) -> Modifier, actions: HomeActions) {
    Row(Modifier.fillMaxWidth().padding(end = FocusRoom), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { RowTitle(stringResource(R.string.home_albums)) }
        SecondaryButton(
            text = stringResource(R.string.home_see_all),
            onClick = actions.onSeeAll,
            modifier = Modifier.focusOn(HomeFocus.SeeAll),
            compact = true,
        )
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(AlbumGap),
    ) {
        items(albums, key = { it.id.value }) { album ->
            AlbumCard(
                album = album,
                onClick = { actions.onOpenAlbum(album.id) },
                modifier = Modifier.width(AlbumWidth),
                coverModifier = Modifier.focusOn(HomeFocus.AlbumCard(album.id)),
            )
        }
    }
}

@Composable
private fun RecentRow(recent: List<Photo>, state: LazyListState, focusOn: Modifier.(HomeFocus) -> Modifier, onOpen: (Photo) -> Unit) {
    RowTitle(stringResource(R.string.home_recent))
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = FocusRoom),
        horizontalArrangement = Arrangement.spacedBy(RecentGap),
    ) {
        items(recent, key = { it.id.value }) { photo ->
            PhotoTile(
                photo = photo.id,
                modifier = Modifier.focusOn(HomeFocus.Recent(photo.id)).size(RecentSize),
                isVideo = photo.isVideo,
                focusable = true,
                onClick = { onOpen(photo) },
            )
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(BlockGap)) {
        SkeletonBlock(Modifier.fillMaxWidth().height(HeroHeight), corner = TileStyle.Hero.corner)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SkeletonBlock(Modifier.width(200.dp).height(36.dp), round = true)
            Row(horizontalArrangement = Arrangement.spacedBy(AlbumGap)) {
                repeat(5) {
                    Column(Modifier.width(AlbumWidth), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(16f / 10f), corner = TileStyle.AlbumCard.corner)
                        SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(24.dp), round = true)
                        SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(18.dp), round = true)
                    }
                }
            }
        }
    }
}

private fun previewAlbum(i: Int, name: String, count: Int) = Album(
    id = AlbumId("a$i"),
    name = name,
    itemCount = count,
    cover = PhotoId("c$i"),
    shared = false,
    ownedByMe = true,
    updatedAt = Instant.parse("2025-08-01T00:00:00Z"),
    startDate = null,
    endDate = null,
    newestFirst = true,
)

private val previewAlbums = listOf("Summer 2025" to 226, "Wedding" to 1032, "Lisbon" to 88, "Garden" to 14, "Snow" to 57, "Dogs" to 640)
    .mapIndexed { i, (name, count) -> previewAlbum(i, name, count) }

private val previewRecent = List(12) { Photo(PhotoId("r$it"), isVideo = it == 2) }

private val previewMemory = Memory(
    takenOn = LocalDate.of(2025, 10, 30),
    slides = List(23) { Slide(Photo(PhotoId("m$it"), false), false, null, null) },
)

@Composable
private fun HomePreview(state: HomeState) {
    ImmichTvTheme {
        MainScreen(
            tab = Tab.Home,
            onSelectTab = {},
            onOpenSettings = {},
            home = { HomeContent(state, {}, {}, HomeActions({}, { _, _ -> }, {}, {}, {}, {}, {})) },
            albums = {},
            photos = {},
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeMemoryPreview() = HomePreview(
    HomeState(HomeLoad.Loaded(HomePage(HomeHero.FromMemory(previewMemory, yearsAgo = 1), previewAlbums, previewRecent))),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeAlbumFallbackPreview() = HomePreview(
    HomeState(HomeLoad.Loaded(HomePage(HomeHero.FromAlbum(previewAlbums.first()), previewAlbums, previewRecent))),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeSkeletonPreview() = HomePreview(HomeState(HomeLoad.Loading))

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeFailedPreview() = HomePreview(HomeState(HomeLoad.Failed(Problem.Unreachable)))

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeEmptyPreview() = HomePreview(HomeState(HomeLoad.Loaded(HomePage(null, emptyList(), emptyList()))))
