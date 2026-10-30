# Notes — fix: albums and album screens never refresh once loaded

Issue: #18 — fix: albums and album screens never refresh once loaded (https://github.com/YasarMahomedAbbas/immich-tv/issues/18)
Branch: `fix/18-refresh-albums-on-return`

## Planning

- Approach: each screen refreshes silently on entry and on returning to the app, through a quiet path that never shows the skeleton. It reads one cheap summary and fetches more only if that changed; a failure leaves the screen alone.
- Decided (pipeline, on the user's behalf; flag: ux): Q1.1, refresh silently on returning to the app and on entering a screen, with no button. Rejected: only on returning to the app (Q1.2), a Refresh action on each screen (Q1.3).
- Decided (pipeline, on the user's behalf; flag: ux): Q2.1, a failed background refresh keeps what is loaded, with no message, and tries again next time. A deleted album or revoked key isn't announced until something fails to open. Rejected: switching to the failed screen with Try again (Q2.2).
- Decided (pipeline, on the user's behalf; flag: api): Q3.1, re-read one summary and fetch more only if it changed: the album's own record for the album screen, the month list for the grid (only months whose count changed). A same-count swap with no asset edit could stay stale. Rejected: re-reading everything each time (Q3.2), about 30 requests per entry for the 699-item album. Checked in the v3.3.5 server source: the album's count and last-modified time are a live query over its non-trashed assets, and adding assets bumps its updated time.
- Decided (pipeline, flagged to the user's stand-in; flag if you disagree):
  - A refresh never passes through the skeleton, and identical data causes no redraw, so focus and scroll stay put. Filter and sort are kept.
  - When data did change, the focused item keeps focus and the grid scrolls to keep it visible (a newly updated album jumps to the top in the default sort).
  - If the focused item is gone, focus moves to the first item. No item ever sits unfocusable.
  - A failed screen retries silently on return.
  - Empty and failed states keep their existing buttons. Refresh runs alongside them.
- Open risks (flag: open-risk): self-verification only makes read-only calls, so on the TV it can prove "nothing changed keeps focus". Changed-data behaviour rests on unit tests, as the project has no Compose UI tests, unless someone edits an album during that run.
