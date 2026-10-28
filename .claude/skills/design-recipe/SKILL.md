---
name: design-recipe
description: >-
  The app's house style, applied. Use whenever you build, add, reshape, restyle or review anything a user sees in the app — a screen, a row, a grid, a card, a button, the slideshow, a dialog, an empty/loading/error state, focus behaviour, or "make this look right". Use it even when the request doesn't mention design ("show the albums in a grid", "add a pause button"), because every screen has to look like it belongs to the manual in docs/design/. Not for data-layer or domain work.
---

# Design recipe

The manual is `docs/design/README.md`: the rules, the tokens and the screen shapes. This skill
is the workflow that applies it in Compose for TV (`androidx.tv.material3` and
`androidx.compose.foundation`). There is no wireframe step: the shapes are decided in the
manual and on its canvas, so the work is to name which shape a screen is and compose it from
the app's own components.

Read `docs/design/README.md` once per session.

## 1. Name the shape

In one line, before code: which screen in the manual's § Screens this is, or which part of one.
None of them → say so and propose the nearest; it usually means the ticket wants two screens.

## 2. Inventory the parts

List the components the screen needs and, for each, the composable in `ui/components/` that
implements it (the top menu, album card, photo tile, primary and secondary buttons, chip,
panel over a photo, empty/failed card, skeleton). A call site picks a variant and passes
nothing that changes the look. The first ticket that needs a part creates it there.

**About to pass a colour, shape, focus style or font size into a shared component? Stop.**
That's a missing variant: add it to the component, record it in the manual, then use it.
Layout on the outside (`Modifier.fillMaxWidth()`, spacing between items) is fine.

## 3. Build with the values

- Everything from the theme (`ui/theme/`): colours from the colour scheme, never a
  `Color(0x…)` outside the theme; corners and type from the manual's tokens. One theme only.
- **Focus**: every focusable part uses the house focus (the 4dp amber outline off the edge,
  via the tv-material `border`/`scale` focus APIs or the shared modifier in `ui/components/`).
  Focus enters a screen on its main action; rows and grids keep the focused item in view;
  Back goes back and never traps.
- **The safe area**: 48dp top, 96dp sides on every screen except the full-screen slideshow.
- **Images**: preview-size thumbnails sized to the tile; the API key goes as a header.

Ship the three states with the happy path: a skeleton that mirrors the loaded layout, an empty
state with one next action, an error state with Try again.

## 4. Check it

Against the manual's rules, one by one, and fix what fails. `@Preview`s exist for each state
(`device = Devices.TV_1080p`), so the screen can be looked at without the TV.

## 5. Record

A decision the manual doesn't cover, or a component that doesn't match the manual → a line in
`docs/design/README.md` § Open decisions, in the same commit.
