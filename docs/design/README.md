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
| Home | top menu, a memory hero, rows (Albums, Recently added) |
| Albums | top menu, filter chips + sort, a 5-column grid of album cards |
| Album | back link, cover mosaic, title and counts, Start slideshow, slideshow settings, a strip of photos |
| Slideshow | full screen; date and place bottom-left, controls bottom-centre (shown on a key press, fade after a few seconds), a thin progress bar on top |
| Photos | top menu, a month rail on the left, a 6-column grid per month |
| Search | top menu, the search field with the voice button, suggestion chips, People, results in a 7-column grid with Play as slideshow |
| Setup | a centred panel: title, hint, server address and API key fields (Show beside the key), Connect, the problem under it |

The top menu is Home · Albums · Photos · Search, with the clock on the right.

Settings is a gear button at the right end, beside the clock; until the top menu exists, Home shows it top-right.

A text field (`HouseTextField`) is `raised` with 18dp corners, an 18sp `mfg` label over a 22sp value, so it reads on a `surface` panel; OK on the remote opens the on-screen keyboard.

## Open decisions

- Whether the slideshow controls fade out, and after how long.
- How the server address and API key are entered without typing them on the remote (a QR code from the phone is the likely answer).
