package io.github.yasarmahomedabbas.immichtv.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.yasarmahomedabbas.immichtv.R
import io.github.yasarmahomedabbas.immichtv.domain.Problem
import io.github.yasarmahomedabbas.immichtv.domain.albums.Photo
import io.github.yasarmahomedabbas.immichtv.domain.albums.PhotoId
import io.github.yasarmahomedabbas.immichtv.domain.search.LoadPhotosOf
import io.github.yasarmahomedabbas.immichtv.domain.search.Person
import io.github.yasarmahomedabbas.immichtv.domain.search.PersonFace
import io.github.yasarmahomedabbas.immichtv.domain.search.PersonId
import io.github.yasarmahomedabbas.immichtv.domain.search.SearchPhotos
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.Slide
import io.github.yasarmahomedabbas.immichtv.domain.slideshow.playable
import io.github.yasarmahomedabbas.immichtv.ui.components.Chip
import io.github.yasarmahomedabbas.immichtv.ui.components.EmptyState
import io.github.yasarmahomedabbas.immichtv.ui.components.FailedState
import io.github.yasarmahomedabbas.immichtv.ui.components.PhotoTile
import io.github.yasarmahomedabbas.immichtv.ui.components.PrimaryButton
import io.github.yasarmahomedabbas.immichtv.ui.components.RefreshOnStart
import io.github.yasarmahomedabbas.immichtv.ui.components.SkeletonBlock
import io.github.yasarmahomedabbas.immichtv.ui.components.VoiceButton
import io.github.yasarmahomedabbas.immichtv.ui.components.focusOutline
import io.github.yasarmahomedabbas.immichtv.ui.components.houseFocusBorder
import io.github.yasarmahomedabbas.immichtv.ui.theme.ImmichTvTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat

private const val COLUMNS = 7
private const val SKELETON_TILES = 14
private const val TILE_RATIO = 4f / 3f
private val Gap = 16.dp

// Room for the 4dp focus outline, 4dp off an item, inside the grid's clip.
private val FocusRoom = 8.dp
private val FieldHeight = 88.dp
private val FieldWidth = 1100.dp
private val PersonSize = 120.dp
private val StateHeight = 480.dp

// The items above the results: the field and the chips, then People when there are any.
private const val ITEMS_ABOVE_PEOPLE = 2

/**
 * The Search tab: the field and voice button, suggestions, People and, once something was asked
 * for, its results, all in one scrolling grid. Back clears the results before it leaves the tab.
 */
@Composable
fun SearchScreen(viewModel: SearchViewModel, onVoice: () -> Unit, onPlay: (List<Slide>) -> Unit, focusResults: Boolean) {
    val state by viewModel.state.collectAsState()
    RefreshOnStart(viewModel::refreshPeople)
    SearchContent(
        state = state,
        focusResults = focusResults,
        onEdit = viewModel::edit,
        onSubmit = viewModel::submit,
        onSuggestion = viewModel::submit,
        onVoice = onVoice,
        onPerson = viewModel::choose,
        onPlay = onPlay,
        onRetry = viewModel::retry,
        onClear = viewModel::clear,
    )
}

@Composable
private fun SearchContent(
    state: SearchState,
    focusResults: Boolean,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestion: (String) -> Unit,
    onVoice: () -> Unit,
    onPerson: (Person) -> Unit,
    onPlay: (List<Slide>) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val fieldFocus = remember { FocusRequester() }
    val firstTile = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val headingIndex = ITEMS_ABOVE_PEOPLE + if (state.people.isEmpty()) 0 else 1
    val load = state.load

    // Whether the remote was last on something the next load takes away. Only ever set on gaining
    // focus: by the time the load is seen, the item that had it may have gone.
    var onResults by remember { mutableStateOf(false) }
    var awaitingResults by remember { mutableStateOf(false) }

    suspend fun focusField() {
        gridState.scrollToItemIfHidden(FIELD_KEY, 0)
        withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
    }

    LaunchedEffect(load) {
        when (load) {
            SearchLoad.Loading -> {
                awaitingResults = true
                if (onResults) {
                    onResults = false
                    focusField()
                }
            }
            is SearchLoad.Loaded -> if (awaitingResults) {
                awaitingResults = false
                withFrameNanos { }
                gridState.scrollToItemIfHidden(tileKey(0), headingIndex)
                withFrameNanos { }
                runCatching { firstTile.requestFocus() }
            }
            else -> awaitingResults = false
        }
    }
    LaunchedEffect(focusResults) {
        if (focusResults && load is SearchLoad.Loaded) {
            withFrameNanos { }
            gridState.scrollToItemIfHidden(HEADING_KEY, headingIndex)
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
        }
    }
    val clearAndFocusField = {
        onClear()
        scope.launch { focusField() }
        Unit
    }
    BackHandler(enabled = state.subject != null, onBack = clearAndFocusField)

    val onTop = Modifier.onFocusChanged { if (it.hasFocus) onResults = false }
    val inResults = Modifier.onFocusChanged { if (it.hasFocus) onResults = true }
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = FocusRoom, end = FocusRoom, top = FocusRoom, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
    ) {
        item(key = FIELD_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "field") {
            FieldRow(state.text, onEdit, onSubmit, onVoice, onTop, Modifier.focusRequester(fieldFocus))
        }
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }, contentType = "chips") {
            Suggestions(state.subject, onSuggestion, onTop)
        }
        if (state.people.isNotEmpty()) {
            item(key = "people", span = { GridItemSpan(maxLineSpan) }, contentType = "people") {
                PeopleRow(state.people, onPerson, onTop)
            }
        }
        val subject = state.subject ?: return@LazyVerticalGrid
        item(key = HEADING_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
            ResultsHeading(subject, load, onPlay, inResults.focusRequester(playFocus))
        }
        when (load) {
            SearchLoad.Idle -> Unit
            SearchLoad.Loading -> items(SKELETON_TILES, key = { "s-$it" }, contentType = { "skeleton" }) {
                SkeletonBlock(Modifier.aspectRatio(TILE_RATIO))
            }
            SearchLoad.Empty -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "state") {
                EmptyState(
                    title = stringResource(R.string.search_empty_title),
                    hint = stringResource(R.string.search_empty_hint),
                    actionLabel = stringResource(R.string.search_clear),
                    onAction = clearAndFocusField,
                    modifier = inResults.height(StateHeight),
                )
            }
            is SearchLoad.Failed -> item(key = "failed", span = { GridItemSpan(maxLineSpan) }, contentType = "state") {
                FailedState(load.problem, onRetry, inResults.height(StateHeight))
            }
            is SearchLoad.Loaded -> itemsIndexed(
                load.slides,
                key = { index, _ -> tileKey(index) },
                contentType = { _, _ -> "tile" },
            ) { index, slide ->
                // OK does nothing until the photo viewer (#7), as in PhotoGrid.
                PhotoTile(
                    photo = slide.photo.id,
                    isVideo = slide.photo.isVideo,
                    focusable = true,
                    modifier = inResults
                        .aspectRatio(TILE_RATIO)
                        .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier),
                )
            }
        }
    }
}

private const val FIELD_KEY = "field"
private const val HEADING_KEY = "heading"

private fun tileKey(index: Int) = "r-$index"

/** Brings the item at [index] to the top unless the one with [key] is on screen: only composed items take focus. */
private suspend fun LazyGridState.scrollToItemIfHidden(key: Any, index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.key == key }) scrollToItem(index)
}

@Composable
private fun FieldRow(
    text: String,
    onEdit: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier,
    fieldModifier: Modifier,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SearchField(text, onEdit, onSubmit, modifier.then(fieldModifier))
        VoiceButton(stringResource(R.string.search_voice), onVoice, modifier)
        Text(
            stringResource(R.string.search_voice_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Where a key sends focus out of the search field; null for a key the field handles itself. */
internal fun fieldFocusMove(key: Key): FocusDirection? = when (key) {
    Key.DirectionUp -> FocusDirection.Up
    Key.DirectionDown -> FocusDirection.Down
    Key.DirectionLeft -> FocusDirection.Left
    Key.DirectionRight -> FocusDirection.Right
    else -> null
}

/** The big round field: OK opens the on-screen keyboard, whose Search key searches. */
@Composable
private fun SearchField(text: String, onEdit: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // A writable text field opens the keyboard as soon as it gains focus, and the D-pad then moves
    // inside the keyboard: the mic and chips can't be reached. So it stays read-only until OK.
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(editing) { if (editing) keyboard?.show() }
    BasicTextField(
        value = text,
        onValueChange = onEdit,
        readOnly = !editing,
        modifier = modifier
            .width(FieldWidth)
            .height(FieldHeight)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) editing = false
            }
            .onPreviewKeyEvent { event ->
                // The field would take these as cursor moves, and the remote could never leave it.
                val direction = fieldFocusMove(event.key)
                if (direction != null) {
                    if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                    return@onPreviewKeyEvent true
                }
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (ok && event.type == KeyEventType.KeyUp) {
                    if (editing) keyboard?.show() else editing = true
                }
                ok
            }
            .focusOutline(focused, FieldHeight / 2),
        singleLine = true,
        textStyle = TextStyle(color = colors.onSurface, fontSize = 32.sp, fontWeight = FontWeight.Bold),
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboard?.hide()
                editing = false
                onSubmit()
            },
        ),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxSize()
                    .background(colors.surface, CircleShape)
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(36.dp))
                Box {
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.search_placeholder), color = colors.onSurfaceVariant, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun Suggestions(subject: SearchSubject?, onSuggestion: (String) -> Unit, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.search_try), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp)
        for (suggestion in SUGGESTIONS) {
            Chip(
                text = suggestion,
                selected = subject == SearchSubject.Description(suggestion),
                onClick = { onSuggestion(suggestion) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PeopleRow(people: List<Person>, onPerson: (Person) -> Unit, modifier: Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.search_people), color = MaterialTheme.colorScheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        LazyRow(
            contentPadding = PaddingValues(FocusRoom),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(people, key = { it.id.value }) { person ->
                PersonCircle(person, onClick = { onPerson(person) }, modifier)
            }
        }
    }
}

@Composable
private fun PersonCircle(person: Person, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.width(PersonSize),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(PersonSize),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = colors.surface,
                focusedContainerColor = colors.surface,
                pressedContainerColor = colors.surface,
            ),
            border = ClickableSurfaceDefaults.border(focusedBorder = houseFocusBorder(CircleShape)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            AsyncImage(
                model = PersonFace(person.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            person.name,
            color = colors.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ResultsHeading(subject: SearchSubject, load: SearchLoad, onPlay: (List<Slide>) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val slides = (load as? SearchLoad.Loaded)?.slides.orEmpty()
    val playable = slides.playable()
    val title = when (subject) {
        is SearchSubject.Description -> stringResource(R.string.search_heading, subject.text)
        is SearchSubject.OfPerson -> subject.person.name
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            title,
            color = colors.onSurface,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (slides.isNotEmpty()) {
            Text(countLabel(subject, slides.size), color = colors.onSurfaceVariant, fontSize = 22.sp)
        }
        Spacer(Modifier.weight(1f))
        if (playable.isNotEmpty()) {
            PrimaryButton(
                text = stringResource(R.string.search_play),
                onClick = { onPlay(playable) },
                modifier = modifier,
                icon = painterResource(R.drawable.ic_play),
            )
        }
    }
}

@Composable
private fun countLabel(subject: SearchSubject, count: Int): String {
    val limit = when (subject) {
        is SearchSubject.Description -> SearchPhotos.RESULT_LIMIT
        is SearchSubject.OfPerson -> LoadPhotosOf.PERSON_LIMIT
    }
    val wording = resultCount(subject, count, limit)
    val number = NumberFormat.getIntegerInstance().format(wording.count)
    return when (wording) {
        is ResultCount.Photos -> pluralStringResource(R.plurals.photo_count, wording.count, number)
        is ResultCount.BestMatches -> stringResource(R.string.search_best_matches, number)
        is ResultCount.NewestPhotos -> stringResource(R.string.search_newest_photos, number)
    }
}

private val previewPeople = listOf(Person(PersonId("1"), "Ada"), Person(PersonId("2"), "Grace"))
private val previewSlides = List(16) { Slide(Photo(PhotoId("a$it"), isVideo = it == 3), false, null, null) }
private val previewSubject = SearchSubject.Description("dog on the beach")

@Composable
private fun SearchPreview(state: SearchState) {
    ImmichTvTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(96.dp)) {
            SearchContent(state, false, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun SearchIdlePreview() = SearchPreview(SearchState(people = previewPeople))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SearchLoadedPreview() = SearchPreview(
    SearchState("dog on the beach", previewSubject, SearchLoad.Loaded(previewSlides), previewPeople),
)

@Preview(device = Devices.TV_1080p)
@Composable
private fun SearchSkeletonPreview() = SearchPreview(SearchState("dog on the beach", previewSubject, SearchLoad.Loading, previewPeople))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SearchEmptyPreview() = SearchPreview(SearchState("dog on the beach", previewSubject, SearchLoad.Empty, previewPeople))

@Preview(device = Devices.TV_1080p)
@Composable
private fun SearchFailedPreview() = SearchPreview(
    SearchState("dog on the beach", previewSubject, SearchLoad.Failed(Problem.SearchUnavailable), previewPeople),
)
