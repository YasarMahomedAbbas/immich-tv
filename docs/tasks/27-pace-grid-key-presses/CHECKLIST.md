# Checklist — bug: a fast run of Down presses on the Photos grid freezes the app until Android kills it (ANR)

Issue: #27 — bug: a fast run of Down presses on the Photos grid freezes the app until Android kills it (ANR) (https://github.com/YasarMahomedAbbas/immich-tv/issues/27)
Branch: `fix/27-pace-grid-key-presses`
Problem: A fast run of Down presses on the Photos grid stops the app responding, and Android kills it and drops us back on the TV's launcher with no message.

## Done when

- The burst from the ticket (about 20 to 33 Down presses with no gap, as one `adb` command) moves through the Photos grid without an ANR.
- A paced stand-in for a held Down (about 20 to 30 keys a second) moves through the grid without an ANR. This stands in for the ticket's held Down on the real remote, which no pipeline can press: it stays a hand check for the user, listed under Outstanding in the PR (decided in NOTES).
- A test that fails without the fix covers the pacing logic. The cause itself can't be reproduced off the device, so the NOTES say why and record the check on the TV.

## References

- Immich API: none; no call is added or changed.
- Compose: `LazyLayoutBeyondBoundsModifierLocal.layout`, read from androidx-main, not at the BOM this app uses (2024.12.01).
- Found in the on-TV check of #7. The earlier grid ANR fix: `docs/tasks/4-albums-and-album-screen/NOTES.md`.

## Context

`PhotoGrid` is one `LazyVerticalGrid` of focusable tiles, used by the Photos tab and an album's photos. Compose's focus search past the grid's edge starts at the last placed item, adds one item at a time and remeasures synchronously after each. The likely cause, not proven, is that with no frames between keys the scroll never runs, so each press walks further from the same edge. The fix bounds the work rather than the cause: an Up or Down press that comes too soon after the last accepted move is consumed before focus search runs. A move needs both 100 ms since the last accepted one, measured by the clock at handling so a stalled main thread doesn't accept its backlog, and a frame since it, so the layout gets a turn. Up and Down only: Left and Right stay within a row, and were fine in the ticket's checks. The logic is a plain class, in the shape of `lostFocus` and its test, so it runs on the JVM; the project has no Robolectric or Compose UI tests.

## Test plan

- Unit: `MovePace`: the first move is accepted; a move too soon is refused; a move after the gap but with no frame is refused; a move after both is accepted; a refused move doesn't restart the gap. Only `MovePaceTest` runs while implementing; the full CI command runs once, in `raise-pr`.
- MockWebServer: none, no call changes.
- Real server: none, no call changes.
- On the TV (only this ticket's path, the lock held 10 minutes at most and released before any fix; skip and record after about 14:25 CET on 2026-10-30, per the issue's comment; `mkdir /tmp/immich-tv-adb.lock` first, only a successful mkdir holds it):
  - Main path: the ticket's burst of 33 Downs on the Photos grid as one command, twice. The app stays open, focus visible, and `dumpsys activity exit-info` shows no new `reason=6`.
  - Held stand-in: about 20 to 30 Downs a second for about 10 seconds, through `adb shell monkey --port` key events or the closest paced sender available. Same checks.
  - Edges: single presses at about 1.5 a second all land, a row per press; a burst of Ups back to the top menu; a burst into a month still loading; Left to the rail and OK on a tile still work straight after a burst.
  - The same burst on an album's photos, which share the grid. If 100 ms feels slow or still ANRs, change the constant and note the value and why.

---

## Checkpoint 1 — pace up and down moves on the photo grid · commit: `fix(photos): pace up and down moves on the photo grid`

Goal: a burst of Up or Down presses can't queue focus searches on the grid; single presses move as before.

### [x] 1.1 — The pacing logic, with its test

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/photos/MovePace.kt`
Plain Kotlin, no Compose or Android imports:

```kotlin
internal const val MIN_MOVE_GAP_MILLIS = 100L

internal class MovePace(private val minGapMillis: Long = MIN_MOVE_GAP_MILLIS) {
    /** True if a move at [nowMillis] may go ahead; accepting one starts the next wait. */
    fun accept(nowMillis: Long): Boolean
    /** A frame has run since the last accepted move. */
    fun frameDrawn()
}
```

The first move is always accepted. Only an accepted move updates the last time and clears the frame flag.

File: `app/src/test/java/io/github/yasarmahomedabbas/immichtv/ui/photos/MovePaceTest.kt`
The unit cases in the test plan, plus one that an accepted move needs a new frame before the next, mirroring `MonthTilesTest`.

### [x] 1.2 — Apply it to the grid

File: `app/src/main/java/io/github/yasarmahomedabbas/immichtv/ui/photos/PhotoGrid.kt`
`val pace = remember { MovePace() }`, `val scope = rememberCoroutineScope()`. On the `LazyVerticalGrid`'s modifier, `modifier.fillMaxSize().onPreviewKeyEvent { ... }`: for `KeyEventType.KeyDown` of `Key.DirectionUp` or `Key.DirectionDown`, call `pace.accept(SystemClock.uptimeMillis())`. Accepted: `scope.launch { withFrameNanos { }; pace.frameDrawn() }` and return false, so focus search runs. Refused: return true, consuming the press. Every other key and key up returns false. Add a line to the KDoc saying why: a backlog of presses froze the app until an ANR.

### Verify

- `./build.sh ./gradlew --console=plain testDebugUnitTest --tests '*MovePaceTest'`
- The tests fail with `accept` always returning true, and pass with the real logic.

---

## Out of scope

- Upgrading Compose or the BOM.
- Instant scrolling, or any other change to how the grid scrolls.
- The month rail, the Albums grid, the search grid and the full-screen viewer's Left and Right.
- Left and Right on the photo grid.
- Release build settings such as minify.
- The 10:43 ANR in `LazyGridMeasure` from the ticket, unless the TV check shows it again.
