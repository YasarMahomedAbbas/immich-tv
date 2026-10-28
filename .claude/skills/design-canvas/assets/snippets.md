# Artboard snippets

Building blocks for `__MAIN__` in `artboard.dc.html`. **The copy is sample text**: replace all of it, and never add a block because it's here. Every colour and radius is a `var(--…)` from the template's `:root`; photos are `.ph0`–`.ph9`; the focused item gets `class="focus"`. The full set of screens is on the first canvas linked from `docs/design/README.md`.

## Top menu

Home · Albums · Photos · Search, the current one filled; the clock on the right. For a full-screen board (the slideshow), leave it out and set the root's padding to 0.

```html
<header style="display:flex;align-items:center;gap:48px;height:56px;flex-shrink:0"><div style="display:flex;align-items:center;gap:12px;font-weight:800;font-size:26px"><svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="#F5C451" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="4"></rect><circle cx="9" cy="9" r="2"></circle><path d="M21 15l-5-5L5 21"></path></svg><span>Immich TV</span></div><nav style="display:flex;gap:8px" aria-label="Main"><a href="Main.dc.html" style="font-size:22px;font-weight:700;padding:12px 26px;border-radius:999px;background:var(--fg);color:var(--on-fg)">Home</a><a href="Albums.dc.html" style="font-size:22px;font-weight:600;padding:12px 26px;border-radius:999px;color:#C9CBD1">Albums</a><a href="Photos.dc.html" style="font-size:22px;font-weight:600;padding:12px 26px;border-radius:999px;color:#C9CBD1">Photos</a><a href="Search.dc.html" style="font-size:22px;font-weight:600;padding:12px 26px;border-radius:999px;color:#C9CBD1">Search</a></nav><div style="flex-grow:1"></div><span style="font-size:24px;font-weight:600;color:var(--mfg)">18:42</span></header>
```

## Section title

```html
<div style="display:flex;align-items:baseline;gap:20px"><h2 style="margin:0;font-size:28px;font-weight:700">Albums</h2><a href="Albums.dc.html" style="font-size:20px;font-weight:600;color:var(--mfg)">See all</a></div>
```

## Album card (in a row or a 5-column grid)

```html
<a href="Album.dc.html" style="display:flex;flex-direction:column;gap:12px;width:320px"><div class="ph1 focus" style="width:320px;height:200px;border-radius:var(--r-card)"></div><div style="display:flex;flex-direction:column;gap:2px"><span style="font-size:22px;font-weight:700">Holiday 2025</span><span style="font-size:18px;color:var(--mfg)">214 photos</span></div></a>
```

## Photo grid (6 columns)

```html
<div style="display:grid;grid-template-columns:repeat(6, minmax(0, 1fr));gap:14px"><div class="ph0" style="aspect-ratio:1/1;border-radius:var(--r-tile)"></div><div class="ph3 focus" style="aspect-ratio:1/1;border-radius:var(--r-tile)"></div><div class="ph6" style="aspect-ratio:1/1;border-radius:var(--r-tile)"></div></div>
```

## Buttons

The one primary, a secondary, and a chip (selected and at rest):

```html
<a href="Slideshow.dc.html" style="display:flex;align-items:center;gap:12px;font-size:26px;font-weight:800;padding:20px 40px;border-radius:999px;background:var(--fg);color:var(--on-fg)"><svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 4.5v15l12-7.5z"></path></svg>Start slideshow</a>
<button style="font-size:24px;font-weight:600;padding:20px 34px;border:0;border-radius:999px;background:var(--raised);color:var(--fg)">Browse photos</button>
<button style="border:0;font-size:20px;font-weight:800;padding:10px 22px;border-radius:999px;background:var(--fg);color:var(--on-fg)">All albums</button>
<button style="border:0;font-size:20px;font-weight:600;padding:10px 22px;border-radius:999px;background:var(--raised);color:#D6D7DB">Shared with me</button>
```

## Panel over a photo

```html
<div style="position:absolute;left:96px;bottom:54px;padding:18px 26px;border-radius:16px;background:var(--scrim);display:flex;flex-direction:column;gap:4px"><span style="font-size:30px;font-weight:800">14 August 2025</span><span style="font-size:22px;font-weight:500;color:#D6D7DB">Lisbon, Portugal</span></div>
```

## Empty / failed card

```html
<div style="margin:120px auto 0;width:720px;padding:40px;border-radius:var(--r-panel);background:var(--surface);text-align:center;display:flex;flex-direction:column;gap:12px;align-items:center"><span style="font-size:30px;font-weight:700">Couldn't load your photos</span><span style="font-size:22px;color:var(--mfg);line-height:1.5">Check the TV is on the home network or the VPN, then try again.</span><button class="focus" style="margin-top:16px;font-size:24px;font-weight:700;padding:16px 36px;border:0;border-radius:999px;background:var(--fg);color:var(--on-fg)">Try again</button></div>
```

## Skeleton tile

```html
<div class="shim" style="width:320px;height:200px;border-radius:var(--r-card)"></div>
```
