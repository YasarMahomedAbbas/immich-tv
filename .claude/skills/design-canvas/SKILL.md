---
name: design-canvas
description: >-
  Build a Claude Design canvas (a published artboard canvas on claude.ai) that mocks up a ticket's screens in the app's house style: one row per screen, one 1920×1080 TV artboard per state. Use when the user asks for a design, canvas or mockup ("design this", "build a design canvas for #9", "mock up the album screen", "update the design canvas"), or when the pipeline runs a `design`-labelled ticket. Not during implementation or review (that is design-recipe).
---

# Design canvas

Draws the screens a ticket will build as a Claude **Design** canvas: one row per screen, one
1920×1080 TV artboard per state, an open-questions sticky beside the first row.

The app's style is in `docs/design/README.md`: **one theme**, a near-black ground, an amber
**focus** outline, an off-white primary button, round corners, Manrope. The first canvas
(linked from the manual) shows every screen of the first version; match it.

Assets: `assets/artboard.dc.html` (the board shell) and `assets/snippets.md` (house markup
for each kind of part, with sample copy to replace).

## 1. Gather the brief

- **A ticket**: `gh issue view <n> --json title,body,comments`, and the tickets it names, far
  enough to know the data each screen shows and what can fail.
- **An existing screen**: read its composables under `ui/` and draw it whole, the untouched
  parts as they are in code.
- Read `docs/design/README.md`: the rules, the tokens and the screen shapes.

Write down before drawing: **the canvas title**, **the screens**, **the states per screen**.

### The state list

Keep each state the screen can actually have:

| Column | State | Board title |
| --- | --- | --- |
| 1 | populated | `<Screen> — view` |
| 2 | interacting | `<Screen> — focus on <item>` / `— listening` / `— paused` |
| 3 | empty | `<Screen> — empty` / `— no results` |
| 4 | loading | `<Screen> — loading` (skeleton, shape for shape with column 1) |
| 5 | failed | `<Screen> — load failed` (error card with **Try again**) |
| 6 | offline | `<Screen> — can't reach the server` (when the server being away changes what shows) |

One extra row per flow (a dialog, a settings panel), one board per variant, titled
`<Flow> — <variant>`, directly under the screen it opens from.

**Every board shows where the focus is**: exactly one item with the amber outline. A TV
screen without a focused item isn't a real state.

## 2. Tokens

The template's `:root` line holds the house tokens. Before the first board, read
`docs/design/README.md` § Tokens (and `ui/theme/Theme.kt`); where they differ, the manual
wins and the template is updated in the same change.

## 3. Lay out the canvas

`project/canvas.json`:

```json
{"v":3,"createdOnFiles":{"v":1,"at":"<now, ISO Z>"},"title":"<feature name>",
 "launch":{"view":"canvas"},"pages":[],"designSystems":[],
 "boards":{"Main.dc.html":{"x":0,"y":0,"w":1920,"h":1080,"title":"<Screen> — view"}, …},
 "order":["Main.dc.html", …],
 "notes":{"<screen>Title":{"kind":"title1","text":"<Screen>","x":0,"y":-260,"maxW":<row width>},
          "open":{"text":"Open questions\n\n1. …","x":<row width + 80>,"y":0,"w":520,"size":"l","fill":"orange"}}}
```

- **Columns**: every board 1920×1080; `x = col × 2000` (80px gap): 0, 2000, 4000, 6000, …
- **Rows**: next row at `y = previous y + 1080 + 400`.
- **Row title**: a `title1` note per row at `x 0`, `y = row y − 260`, `maxW = boards × 2000 − 80`.
- **Open questions**: one orange sticky, id `open`, beside the first row. Only real questions.
- **Files**: the first board is `Main.dc.html`, the rest `<Screen><State>.dc.html` in
  PascalCase (`AlbumsEmpty`, `SlideshowPaused`). `order` runs row by row.
- Boards with working links or controls get `"is_interactive": true`.

## 4. Draw each artboard

Copy `assets/artboard.dc.html`; replace `__BOARD_TITLE__` and `__MAIN__`. Leave the
`support.js` line, the `<helmet>` and the script block alone apart from token fixes. Build
`__MAIN__` from `assets/snippets.md`, keeping the markup and replacing every word of sample
copy.

- **Photos are placeholders**: the `.ph0`–`.ph9` classes (a sky-and-ground block in muted
  tones). Never a real or stock photo.
- **Real content**: the ticket's copy verbatim; plausible album names, counts and dates. No
  lorem. A person's name is `[Name]`; an unknown is a bracketed placeholder.
- **Loading boards** are the view board with each shape replaced by a `.shim` block.
- **Empty boards**: the screen's chrome, then one statement, one muted line, one action.
- **Failed boards**: `Couldn't load your photos` / `Check the TV is on the home network or the
  VPN, then try again.` / **Try again** (focused).
- Real `<button>`s and `<a href>`s, `aria-label` on icon-only buttons, inline stroke SVG
  icons, never emoji. One primary (off-white) button per screen.
- Respect the safe area: 48px top, 96px sides.
- Rationale stays off the boards: in the reply or the sticky.

## 5. Publish

1. `Artifact` `action: "quickstart"`, `intent: "design"`, `design_systems: false` (the style
   is inlined in every board).
2. Create once with the returned `type_url`, `title: "<feature name>"`,
   `auto_open: "after_first_write"`. Keep the `url`.
3. **Re-read the type instructions the create call returns.** Where they disagree with this
   skill on mechanics, they win.
4. Work in `<scratchpad>/design-canvas/<slug>/`, files at their canvas paths.
5. First publish: `canvas.json` + `Main.dc.html` together; then one board per publish, in
   `order`. Never two Writes in a row.
6. Don't render or screenshot it to check; the user looks at it.

## 6. After publishing

Reply with the link, one line per row (`<Screen> — view, empty, loading, load failed`), and
the open questions as a numbered list. Add the link to the ticket as a comment
(`Design: <url>`). If the ticket has a folder (`docs/tasks/<n>-*/`), append to its `DESIGN.md`:

```markdown
## <YYYY-MM-DD> — <canvas title>

- Canvas: <url>
- Rows: <Screen> (<states>) · <Flow> (<variants>)
- Open questions: <n>, on the canvas
```

## Revising a canvas

`Artifact` `action: "read"` on its `url` first, then the type's revising rules: change only
the boards asked about, publish them in one call to the same `url`, send `canvas.json` only
when the layout changes.
