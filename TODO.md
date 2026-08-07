# DiegoAddons V2 — work list

`[ ]` open · `[~]` in progress · `[x]` done. Each item ends with a build, a deploy to the Prism
"DiegoAddonsV2 Test" instance, and a run in game before the next starts.

**Current version: 2.4.0.** RenderLib is gone; the mod runs on
[diegos-config-lib](../diegos-config-lib) (`dev.diego:configlib`), consumed through
`includeBuild` in `settings.gradle` — a fresh clone needs that directory beside this one to build.

---

## Open — in the order worth doing

### 1. The seven custom HUD elements
All ten HUD elements register and are placeable, but only Clock, Performance and Mining Ability look
right. The rest fall back to configlib's `labelValue` prefab and draw as plain text:

- [ ] **Dungeon Map** — room grid, checkmarks, player heads
- [ ] **Crystal Hollows Map** — coloured region blocks
- [ ] **Inventory HUD** — slot grid with item models
- [ ] **Custom Scoreboard** — multi-line sidebar
- [ ] **Player HUD** — armour / model / equipment columns
- [ ] **Pet HUD** — icon plus level
- [ ] **Music Display** — cover art plus track

**Head start:** `DungeonMapModule` and `CrystalHollowsMapModule` still contain their complete
pre-RenderLib immediate-mode drawing (`drawSeams`, `drawRooms`, `drawLabels`, `drawPlayers`,
`drawStats`). Nothing calls it. Reviving those two is composition into a
`HudWidget.of(w, h, renderer)`, not a rewrite. The other five had their drawing entirely inside the
deleted element classes and need real work — `HudTemplates` has `bar()`, `stats()`, `icon()` and
`image()`, which may cover the pet and the scoreboard.

**Mining Ability** is a behaviour change, not just a look: it was drawn large in the centre of the
screen by `MiningAbilityOverlay`, not as a placeable chip. It wants a plain HUD render callback
rather than a `HudWidget` in the editor.

This is also what keeps `gui/UiRender`, `gui/Fonts`, `gui/Theme` and `gui/Themes` alive — plus
`Toasts` and `ModuleManager.renderHud` (item rarity, ability cooldown, ESP HUD).

### 2. Persistence — configlib should own the config
Every option is currently `SpecBuilder.notPersisted()`; `ConfigManager` still owns
`config/diegoaddonsv2.json` and configlib writes only HUD positions and menu settings to
`config/diegoaddonsv2-configlib.json`.

- [ ] Move module settings onto configlib's store and retire `ConfigManager`
- [ ] **Migrate, do not reset.** The two files have incompatible schemas and share a mod id — this
      is why the handle is pointed at a separate path today. Get this wrong and a real config is
      lost, so do it with someone watching, not before bed.

### 3. More customization, everywhere
Broad ask from Diego — scope it with him before building, but the concrete gaps are known:

- [ ] **The theme system is currently dead.** `ConfigManager.get().theme` ("Galaxy" and the rest)
      still exists and `Themes.current()` still drives the leftover hand-drawn bits — toasts, item
      rarity, the unported HUD elements — but **configlib's GUI ignores it entirely**.
      `ConfigHandle.theme()` returns `Theme.DEFAULT` and configlib's own comment says there is
      "deliberately no way to swap it". So the theme picker changes almost nothing now. Either give
      configlib a consumer-supplied theme, or drop the mod's picker; having it present and inert is
      the worst of the three.
- [ ] **HUD styling is available and unused.** `handle.hudStyle(Supplier<HudStyle>)` is re-read
      every frame, so pointing it at config fields lets the whole HUD be restyled live — text
      colour, plate opacity, and so on. The mod never calls it. Cheapest real win on this list.
- [ ] **Per-element HUD style** — `HudTemplate.style(...)` already lets one element opt out of the
      shared look. Nothing exposes it.
- [ ] **`smoothCorners`** — a real setting that now only reaches `UiRender`, so it affects the
      leftover hand-drawn drawing and nothing in configlib.
- [ ] Decide how far this goes: accent colour only, a full palette per theme, or user-defined
      themes. The answer changes whether configlib needs a `Theme` builder or just an accent
      override.

### 4. Smaller open items
- [ ] **Shade behind the title-screen wordmark** (configlib `:menu`) — a slider for a dark scrim
      behind the mod name and subtitle on the custom main menu, so the text stays readable over a
      bright wallpaper. Distinct from the existing `MenuSettings.dim()`, which dims the whole
      background: this is a local gradient or rounded panel behind the text only, so a wallpaper can
      stay bright while the name is still legible. Goes in `MenuSettings` next to `dim`/`glow` as a
      percent, a row in `MenuCustomizeScreen`, and drawn in `MainMenuScreen` around the wordmark at
      `MainMenuScreen.java:186` and the subtitle just below it.
- [ ] **Door & Key ESP / Voidgloom beacon + nukekebi** — left on their semantic colours (a wither
      door is black, a blood key is red). They should get the style setting without the colour one.
- [ ] **Inventory Buttons** — removed in 2.2.2 rather than ported. Wants a rework, not a port.
- [ ] **configlib has no GitHub remote** — committed locally only. Needs a repo name and visibility
      before it can be pushed.
- [ ] **`IMPLEMENTATION_PLAN.md` is stale** — written for the RenderLib era. Rewrite or delete.

---

## Needs eyes on it — built and deployed, never actually seen

Everything below compiles, boots and registers, but was written without being able to click it.
Worth a pass before trusting any of it:

- [ ] Settings menu: seven categories, 56 module cards, switches **on** the cards
- [ ] Sliders drag; dropdowns stay open; text boxes and keybind capture keep focus
- [ ] Colour picker: the hex field accepts a pasted value
- [ ] List editors (blocked players, words, hotkeys, GFS, routes) — add / reorder / delete
- [ ] Player HUD "Section order" screen
- [ ] Sound picker for Secret Chime
- [ ] HUD editor: ten draggable elements, and a position that survives a restart
- [ ] Chat search (Ctrl+F) — jump and copy
- [ ] Intro screen on a fresh instance
- [ ] Custom title screen, the Join Hypixel button on both title screens, and the DiegoAddons button
- [ ] Chat: scroll no longer jumps, separators no longer compacted

---

## Done

- **RenderLib removed entirely** (2.1.0) — no source reference, no dependency, no bundled jar
- **Screen extensions** → Fabric `ScreenEvents` + real `AbstractWidget`s
- **World rendering / ESP** → `LevelRenderEvents`, vertex work shared in `WorldGeometry`
- **Settings menu** → configlib, from a spec walked off `ModuleManager`
- **HUD** → configlib, ten elements on the `labelValue` prefab
- **Five list editors** → one configlib `ListOption` screen
- **Section order** → configlib `OrderOption`; **sound** → configlib `PickerOption`
- **Chat search** and **intro** → rebuilt on configlib's drawing layer
- **Custom title screen** → configlib `:menu`, behind the Title Screen module
- **Achievements** removed in full (2.0.30); **Inventory Buttons** removed (2.2.2)
- **Chat fixes** — compacting no longer eats scroll position or Hypixel separator rules

### Resolved along the way

- **The right edge of the HUD.** RenderLib's placement screen was a fixed 1920×1080 canvas while the
  live HUD was not, so the last strip of a wide window could not be reached by dragging. configlib's
  `HudPos` is a screen fraction plus an `Anchor`, so an element pinned to an edge stays pinned at any
  window shape. No longer a problem.
- **Ping.** The client cannot time a round trip of its own — the play protocol has no
  client-initiated ping, and the only number available is the one the server hands out
  (`PlayerInfo.setLatency`), measured behind Hypixel's proxy. The row is gone: better no number than
  a confident wrong one.
