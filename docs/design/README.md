# Design manual

The house style, written as recipes, so every screen built without a designer in the room still looks like one app. The `design-recipe` skill applies it in code; `design-canvas` draws it as mockups.

The first canvas, with every screen of the first version: https://claude.ai/artifact/8Tq2kuJDNjk3SFiALwbfZH (Home, Albums, Album, Slideshow, Photos, Search).

## The style in one paragraph

A dark, almost black ground so the photos are the colour on screen. One warm accent, amber, used for **one thing only: where the remote's focus is**. Text and the main button are a warm off-white. Corners are round: soft on photo tiles, fully round on buttons, chips and the menu. Surfaces are flat and step up in one order (page → card → raised), separated by tone. One typeface, Manrope, big enough to read from the sofa.

## The rules

1. **One theme.** No light and dark variants. The app always looks the same.
2. **Tokens only.** Every colour is a theme colour; no `Color(0x…)` outside `ui/theme/`.
3. **Amber is focus.** The focused item gets a 4dp amber outline, 4–5dp off its edge. Amber is never decoration, text or a button fill (the voice-search button is the one exception: it is the action on that screen).
4. **One primary button per screen**: off-white fill, dark text. Everything else is `raised` with off-white text.
5. **The photo is the content.** Overlays on photos (the slideshow's date and place, the hero's text) sit on a solid dark panel at 55–80%, never on a gradient wash.
6. **The TV safe area.** Screens keep 48dp top and 96dp side margins; nothing important touches the edge, because some TVs crop it.
7. **Everything works with the D-pad.** A focusable item always shows its focus, a row scrolls to keep focus visible, Back always goes back.
8. **Every data screen ships three more states**: a skeleton shaped like the loaded screen, an empty state with one next action, and an error with Try again that says to check the home network or VPN.
9. **Minimum text 18sp** at 1080p; body 22sp; titles 28sp and up.
10. **1dp is one canvas pixel.** `ImmichTvTheme` provides a density where 1dp = 1/1920 of the screen's width and keeps the system font scale, so every screen is 1920dp wide on any TV (the Chromecast's own density would make it 960dp). Numbers from this manual and the canvas go into the code as written, never halved. Anything drawn outside the theme doesn't get this.

## Tokens

| Token | Value | Use |
| --- | --- | --- |
| `bg` | `#0E0F12` | the page ground |
| `surface` | `#191B20` | cards, settings buttons, the search field |
| `raised` | `#22252C` | secondary buttons, chips at rest, skeleton highlight |
| `fg` | `#F3F1EC` | text, the primary button's fill, a selected chip |
| `mfg` | `#A3A6AE` | secondary text, counts, icons at rest |
| `focus` | `#F5C451` | the focus outline (and the voice button) |
| `on-fg` | `#0E0F12` | text on the primary button and selected chips |
| `scrim` | `#0E0F12` at 55–80% | panels over photos |
| Corners | 12 tiles · 14 album cards · 18 panels · full on buttons and chips | |
| Type | Manrope 500 / 600 / 700 / 800 | one family |

These live in `.claude/skills/design-canvas/assets/artboard.dc.html` for the canvas and in `ui/theme/` for the app; change both together.

## Screens

| Screen | Shape |
| --- | --- |
| Home | top menu, then a scrolling column: a 380dp hero with 22dp corners (the memory's first photo, or the most recently updated album's cover), its `Hero` panel bottom-left at 40dp and 620dp wide holding a label 18sp 700 `mfg` caps (not the canvas's amber), a title 46sp 800, a line 22sp `mfg`, Play slideshow and View photos; an Albums row (28sp 700 title, compact See all, up to 10 album cards 320dp wide with 32dp gaps); a Recently added row (up to 20 focusable 200dp tiles with 18dp gaps). A block comes into view whole as focus enters it; a row with nothing in it is left out. Up from an album card goes to See all and from there to View photos; Right stops at a row's last item. Focus enters on Play slideshow and comes Back to where it was |
| Albums | top menu, filter chips + sort, a 5-column grid of album cards |
| Album | back link, cover mosaic, title and counts, Start slideshow, Browse photos, slideshow settings in two rows of two, a strip of photos (the mosaic's rows are 284dp: the canvas's one row of settings and 300dp rows don't fit the screen) |
| Album photos | back link, a 6-column grid per month (`PhotoGrid`, shared with Photos) |
| Slideshow | full screen with no safe area; photos crossfade over 1.5 s, each zooming to 1.08 and drifting 1%; a portrait photo sits on a blurred copy of itself darkened 42%. A video's preview crossfades in the same way, without the zoom and fitted rather than cropped (a portrait one on the same backdrop), then the video plays over it, fitted and without motion, until its end. Date and place bottom-left, the clock top-right, controls bottom-centre (shown on any key, fade after 4 s, also while paused). The controls are indicators and nothing in them takes focus: left/right always skip and OK always pauses, so the play/pause circle keeps its amber outline. A thin progress bar on top, off-white (rule 3) rather than the canvas's amber, shows the position in the show. A library show starts from the photo held on the Photos grid, with fixed defaults and photos only, and says "‹ Photos" where an album's says "‹ Album"; a show started on Home says "‹ Home", and a memory's plays with the same fixed defaults, photos only; a show of search results is photos only too, in the order shown, and says "‹ Search" |
| Photos | top menu, a 200dp month rail on the left grouped by year (years aren't focusable; OK on a month jumps the grid there with its first tile focused; moving along the rail alone changes nothing), a 48dp gap, a 6-column grid per month; the rail's selection follows the focused tile. A `KeyHint` bottom-right while the grid is loaded: OK opens, hold OK starts a slideshow from there |
| Photo | full screen with no safe area; the whole photo fitted on `bg`, no overlays. Left/right move through the timeline and stop at both ends; a video is a still with the play badge bottom-left at the slideshow's margins. Back returns to the last viewed tile, or to Home when opened from its Recently added row |
| Search | top menu, the search field with the voice button, suggestion chips, People, results in a 7-column grid with Play as slideshow. All one scrolling grid. The field is `surface`, fully round, 88dp tall and 1100dp wide, with a search icon and 32sp 700 text; beside it the 88dp amber voice button (`VoiceButton`, focus outline `fg`) and a 22sp `mfg` hint. People are 120dp circles of the face with amber focus and the name under them, 18sp 600 on one line. Voice is the system speech prompt, not the canvas's inline "Listening…" |
| Setup | a centred panel: title, hint, server address and API key fields (Show beside the key), Connect, the problem under it |

The top menu is Home · Albums · Photos · Search, with the clock on the right.

Settings is a gear button at the right end, beside the clock.

## Components

What each shared part in `ui/components/` looks like. A call site picks a variant; it never passes a colour, shape or font size.

- **Top menu** (`TopMenu`), 56dp tall: the logo (the image glyph and "Immich TV" 26sp 800, both `fg`: the canvas's amber logo would break rule 3), then the tab pills, a spacer, the clock (24sp `mfg`, the device's 12/24-hour format) and the Settings gear. A pill is 22sp, fully round; the selected one is `fg` with `on-fg` 800 text, the others have no fill and `mfg` text that turns `fg` on focus. OK selects; focus enters a main screen on the selected pill.
- **Buttons** (`PrimaryButton`, `SecondaryButton`), fully round: primary 26sp 800 with 40×20dp padding; secondary 24sp 600 with 34×20dp; a compact secondary 20sp 600 with 22×10dp, among chips or beside a field (Sort, Show). tv-material's built-in padding is for 960dp screens, so always set it.
- **Icon button** (`IconButton`): a 56dp `raised` circle with one 28dp icon, for the gear.
- **Back link** (`BackLink`): "‹ Albums" top-left ("‹ Home" on an album opened from Home), 22sp 600, no fill, `mfg` at rest and `fg` on focus. It does what Back does.
- **Chip** (`Chip`): 20sp, fully round; `raised` with `fg` 600 text at rest, `fg` with `on-fg` 800 text when selected.
- **Album card** (`AlbumCard`): a 16:10 cover with 14dp corners (the focusable part), the name 22sp 700 on one line, then "1,032 photos · Shared" 18sp `mfg` ("Shared" only for a shared album; the count is photos and videos together).
- **Photo tile** (`PhotoTile`): the preview cropped to fill, `surface` while it loads; a video has a small play badge bottom-left on a dark circle. Corners by `TileStyle`: 12 tiles, 14 album cards, 18 the album's cover mosaic, 22 Home's hero.
- **Month rail** (`MonthRail`): 200dp wide. A year is 18sp 700 `mfg`, not focusable. A month is 22sp with 12dp corners and 18×12dp padding, its name in the device's locale: the selected one `fg` with `on-fg` 800 text, the others `mfg` 600 with no fill, turning `fg` on focus. Focus coming in lands on the selected month.
- **Key hint** (`KeyHint`): a `surface` pill, fully round, of 20sp 600 text, each key a small `fg` chip with `on-fg` 800 text. Never focusable.
- **Setting button** (`SettingButton`): `surface`, 16dp corners, at least 230dp wide; the label 18sp 600 `mfg` (the canvas's 17sp is under the minimum) over the value 23sp 700. OK steps to the next value.
- **Empty and failed states** (`EmptyState`, `FailedState`): a `surface` card with 18dp corners in the middle of the content area: a 32sp 800 title, a 22sp `mfg` hint, one primary button that takes focus. Failed says "Couldn't load this" and why: can't reach the server (check the home network or VPN), the key was refused (enter a new one in Settings), the key lacks album.read, asset.read and asset.view, or (searching) smart search is off on the server; the button is Try again. The Albums empty state is "No albums yet" / "Make one in Immich and it shows up here." / Refresh; a filter with nothing in it is "Nothing here" / Show all albums. Home with nothing at all is "Nothing here yet" / "Add photos in Immich and they show up here." / Refresh.
- **Panel over a photo** (`OverPhotoPanel`): `scrim` flat, by `OverPhotoStyle`: `Info` 55% with 18dp corners (date and place), `Pill` 55% fully round (the clock), `Controls` 80% fully round, `Hero` 78% with 18dp corners (Home's hero).
- **Skeleton** (`SkeletonBlock`): `raised` blocks with the corner of what they stand for (fully round for text lines and buttons), pulsing slowly; each screen lays them out like its loaded layout.

A text field (`HouseTextField`) is `raised` with 18dp corners, an 18sp `mfg` label over a 22sp value, so it reads on a `surface` panel; OK on the remote opens the on-screen keyboard.

## Open decisions

- OK on an album's Browse photos grid does nothing yet: opening a photo from there waits for its own ticket (#23).
- How the server address and API key are entered without typing them on the remote (a QR code from the phone is the likely answer).
