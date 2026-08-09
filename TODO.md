# DiegoAddons V2 — work list

`[ ]` open · `[~]` in progress · `[x]` done. Each item ends with a build, a deploy to the Prism
"DiegoAddonsV2 Test" instance, and a run in game before the next starts.

**Current version: 2.5.2.** RenderLib is gone; the mod runs on
[diegos-config-lib](../diegos-config-lib) (`dev.diego:configlib`), consumed through
`includeBuild` in `settings.gradle` — a fresh clone needs that directory beside this one to build.

**The repository is public** as of 2.5.0, and the mod updates itself from its releases. Two things
follow from that and are easy to forget: a shipped version needs a GitHub release or no client will
ever see it (§4), and anything committed here is now readable by anyone.

---

## Open — in the order worth doing

### 1. Dungeon Map — needs a rework, not a fix
Diego, on 2.4.5: "looks weird (thats nothing new)". Long-standing, and the one item on his bug list
that is not a defect with a cause — the map draws what it was told to draw. Get specifics before
touching it: which part reads wrong (tile colours, room sizes, the seams, the stats block, the
player arrows), and ideally a screenshot beside the map it should look like. The drawing itself is
sound and unchanged since before RenderLib, so this is a design pass rather than a bug hunt.

### 2. Things fixed blind that need confirming in game
- [ ] **Door bats** — there is no flag saying which bats came from a door, so 2.4.6 goes on
      downward velocity, exposed as "Ignore door bats" plus a threshold on the Bat ESP card. If real
      secret bats vanish, raise the threshold; if the door crowd still shows, lower it.
- [ ] **Autopet / loadouts** — the chat pattern and the `"loadout"` menu-title match are best-effort
      against Hypixel's documented wording, never seen firing. Inventory HUD → "Debug scan (log)"
      dumps every slot's name and lore; that output is what to tune the constants against.
- [ ] **World labels** (2.4.6) — solver timers and waypoint names were never drawing: the boxes from
      the same loop appeared, so submission was fine and only the text was lost. They now render
      into their own buffer, flushed on the spot. If they are still missing, the log now carries one
      `World labels failed to draw` line with the cause.

### 3. More customization, everywhere — per-feature settings
Theming is done (see Done). What is left is the second half of Diego's ask: **as much customization
as possible on every feature.** A pass has been made over the barest modules; the rest are listed
here so the sweep can be finished rather than restarted.

Still bare (no settings of their own, and not covered by a base class):

- [ ] **Auto Close Chest** — delay before closing, which containers it applies to
- [ ] **Auto GFS** — threshold, whether to announce, cooldown between refills
- [ ] **Command Hotkeys** — the list is the feature, but nothing adjusts *how* it fires
- [ ] **Leap Overlay** — class name / level / colour-by-class; drawing is in `util/LeapOverlay`
- [ ] **Old Master Stars** — the star colour is hard-coded in `util/OldMasterStars`

One setting only, worth a second look: Announce Kick, Borderless Fullscreen, Chest Solver,
Force Nametag, Grotto Finder, Hide Effects, Item Rarity, Mining Routes, Title Screen.

**Note on ESP and HUD modules:** their apparent "no settings" is misleading — `EspModule` gives
every ESP a style and a colour, and `HudModule` now gives every HUD element four appearance rows.
Count what a module *inherits* before deciding it is bare.

### 4. Auto Update — every future version now needs a GitHub release
The repository is **public** as of 2.5.0 and carries a release tagged `v2.5.1` with
`diegoaddonsv2-2.5.1.jar` attached. The feed, the asset name, the redirect and the jar's own
metadata were all checked against the live endpoint, so the check and download paths are known good.

**This is now a standing part of shipping a version:** tag `vX.Y.Z` (the leading `v` is stripped) and
attach the built `diegoaddonsv2-<version>.jar`. Without a release, an updating client simply never
sees the new version — it fails quiet, which is the failure mode to remember. Assets whose names
contain `-sources` or `-dev` are skipped, so attaching more than the mod jar is safe.

- [ ] **Watch one real update happen** — the one part that could not be tested with a single release.
      Expect: game closes, `apply-update.bat` appears in
      `<instance>/minecraft/diegoaddons-updates/`, runs, and the mods folder ends up with the new jar
      plus `diegoaddonsv2-previous.jar.bak`. If it goes wrong the old jar is still there, so the
      worst case is that nothing updated.
- [ ] **Now that the repo is public**, the README is the first thing anyone sees. It was written for
      a repo nobody could open.

### 5. Smaller open items
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

- [ ] Settings menu: seven categories, 57 module cards, switches **on** the cards
- [ ] Sliders drag; dropdowns stay open; text boxes and keybind capture keep focus
- [ ] Colour picker: the hex field accepts a pasted value
- [ ] List editors (blocked players, words, hotkeys, GFS, routes) — add / reorder / delete
- [ ] Player HUD "Section order" screen
- [ ] Sound picker for Secret Chime
- [ ] HUD editor: ten draggable elements, and a position that survives a restart
- [ ] Chat search (Ctrl+F) — jump and copy
- [x] Intro screen on a fresh instance — **seen twice, wrong both times.** 2.5.1 fixed the doubled
      size; the screenshot after it showed the panel far too small for the window, "VERSION 2" drawn
      through the middle of the "DiegoAddons" title, and the "Not now" button sitting on top of the
      "You can change everything later." note. Re-laid out in 2.5.2 (see Done) and **not looked at
      since**. To see it again, set `introShown` back to false in
      `config/diegoaddonsv2-configlib.json`.
- [ ] Custom title screen, the Join Hypixel button on both title screens, and the DiegoAddons button
      — **now the default** (2.5.1), so a fresh instance lands on it rather than the vanilla screen
- [ ] Chat: scroll no longer jumps, separators no longer compacted

### Auto Update (2.5.0) — never seen, and the only module that writes to the mods folder
- [ ] **The card** — mode dropdown, the interval slider, and "Check now". Pressing the button on an
      up-to-date install should say so in chat rather than looking like it did nothing; that is what
      the verbose path exists for.
- [ ] **`/da update`** with the module switched off — it should report and never download.
- [ ] **A real update, end to end** — see §4. The safest way to force one is to publish a release
      one patch above whatever you are running, with that jar attached.

### Appearance (2.4.3 / 2.4.4) — never seen
- [ ] The Appearance page: theme picker changes the HUD, the toasts **and** the menu's accent live,
      without a restart. Custom accent toggle hides/shows the colour row.
- [ ] Check the accent stays readable on a light theme — `Theme.readableOn` picks near-black or
      near-white by Rec. 601 luma, and that maths has never been looked at on screen.
- [ ] Clock and Performance should now match the custom elements rather than being white-on-dark.
- [ ] Per-element override on one element, e.g. a red Clock while everything else follows the theme.
- [ ] The four modules whose `background` toggle was replaced (Pet, Inventory, Player, Scoreboard) —
      their plate should now follow "Background plate" on the same card.
- [ ] Fullbright strength below 100% — does a cave still read as a cave?

### Persistence (2.4.2) — the one thing to check first
The whole config now goes through configlib, and none of it has been seen working:

- [ ] **Start once with the old `diegoaddonsv2.json` in place.** The import should log
      "Imported the old config" and rename it to `.imported`. Check the 12 enabled modules came
      back on and that the Player ESP colour is still orange→cyan gradient.
- [ ] **Then restart again.** Nothing should be re-imported (`legacyImported` is saved), and
      everything should still be there — that second start is what proves configlib is actually
      saving rather than the import doing the work each time.
- [ ] Change a setting, close the menu, restart: it should stick. Same for a keybind, a colour,
      and the Secret Chime sound (that last one is the risky case — its row is an action button,
      so its value is persisted through a separate hidden option).
- [ ] **A module moving category resets its settings.** Ids are qualified with the category, so
      `render.customscoreboard.background` becomes `hud.customscoreboard.background` if it is
      ever moved. This was not true before — the old file keyed on module id alone. Worth fixing
      properly if the categories are ever reshuffled.

### The seven rebuilt HUD elements (2.4.1) — written blind, never seen
All seven compile, register and size themselves, but none has been looked at in game. The editor
preview path is the fastest way to check most of them, since it draws without live data:

- [ ] **Dungeon Map** — the only true revival; its drawing was unchanged, so it is the most likely
      to be right. Check the stats block sits under the grid rather than overlapping it.
- [ ] **Crystal Hollows Map** — drawing rewritten from scratch off the old element's geometry.
      Check the player dot lands where you actually are and the facing tick points the right way.
- [ ] **Custom Scoreboard** — check the server's own line colours survived the re-facing
      (`Fonts.reface`), and that the panel width follows the widest line.
- [ ] **Inventory HUD** — check slot indices: storage is `9 + row*9 + col`, hotbar is `0..8`, and
      the selected-slot accent ring lands on the right one.
- [ ] **Player HUD** — the model is now the game's own inventory preview
      (`InventoryScreen.extractEntityInInventoryFollowsMouse`). Check it faces forward, fits its
      frame at both height extremes (40 and 160), and that the section order screen still reorders it.
- [ ] **Pet HUD** — check the 32px icon is centred and the card widens for a long pet name.
- [ ] **Music Display** — check cover art actually appears (it is now a texture id from `CoverArt`,
      not a URL) and the progress bar tracks. **Note:** with neither cover nor bar switched on this
      element now draws the mod's own panel rather than configlib's `labelValue` prefab, so it looks
      slightly different from before — deliberate, but worth a second opinion.
- [ ] **Mining Ability / Hydration** — both now draw from `hud/CentreOverlay` in the plain HUD pass.
      Mining Ability is deliberately gone from the HUD editor (`HudModule.placeable()` is false), and
      its old saved position is an orphaned key in configlib's file, which is harmless.

---

## Done

- **The welcome screen was tiny, and two of its lines drew through each other** (2.5.2) — with the
  hi-res fix from 2.5.1 in place the panel was finally the size it had always asked for, and that
  size was wrong: 620×420 units against the settings panel's 1320, so it read as a small box in the
  middle of a large window. It is now `min(960, window - 80)` wide by 500 tall, with the paddings,
  the brand tile, the buttons and the row rhythm scaled to match.
  The two overlaps were both hand-guessed offsets. The title and the eyebrow were positioned by two
  independent centre-of-band calls whose bands overlapped, which put "VERSION 2" through the middle
  of the name; they are now **two stacked bands that add up to the brand tile's height**, so neither
  can wander into the other. The footer note was drawn at a fixed x with no width limit while the
  buttons were drawn on top of it — it is now truncated against the left edge of the "Not now"
  button, so the note gives way rather than the controls. Button widths and hit boxes come from the
  same constants now (`PRIMARY_W` / `SECONDARY_W`), instead of the literals that had to be kept in
  step by hand between drawing and `mouseClicked`.
- **The music overlay is a card** (2.5.1) — cover on the left, title over artist, a progress bar
  under them with the time right-aligned beneath it, off a mockup Diego drew. It was a stack of
  equal text lines with a bar the width of the longest one; the hierarchy is the point, so the title
  is the theme's plain text and the artist its muted shade rather than both taking the accent, which
  the bar keeps. Switching the per-element override on hands the title back to the chosen colour.
  The card has a **fixed width** (a setting, default 130) rather than sizing to its text: the bar and
  the time lay out against a width, and a card that resized per track title would jump around the
  HUD as songs changed. Long titles are truncated with an ellipsis instead.
  Progress bar and time now default **on**, since they are most of the card. The album cover still
  defaults **off** — it sends the track title to iTunes, and that stays an opt-in.
- **The HUD editor showed every element as visible** (2.5.1, configlib) — Diego: "why can i move
  every HUD Element in HUD Editor even when theyre not even toggled on". Showing the disabled ones
  is deliberate — that is how you place an element before switching it on, and they are meant to be
  drawn in red, labelled "(hidden)", with the side panel saying so. None of that ever appeared:
  `HudEditorScreen` read `HudPos.enabled`, but this mod backs every element's on/off with the
  module's own flag through `HudNode.bindEnabled`, which leaves `pos.enabled` a field nobody writes,
  sitting at its default of true. So every element drew as visible whatever its module said, and
  Enter toggled that dead field instead of the module. The editor now goes through
  `enabledFlag()` / `setEnabledFlag()` everywhere, which is what the click toggle and the snapping
  already did — those two were right, which is why it was only the *look* that lied.
- **The last of RenderLib** (2.5.1) — `CoverArt.artworkUrl` and the URL map behind it existed only
  because RenderLib's image component loaded a URL rather than a texture; nothing had called them
  since the element was rebuilt. Same for `MusicDisplayModule`'s `showCover` / `showProgress` /
  `customLayout`. All gone. What remains of RenderLib in the source is two comments that explain why
  code looks the way it does, which is history worth keeping, not a dependency.
- **The welcome screen drew at double size, and the custom menu is the default** (2.5.1) —
  `IntroScreen` positions and draws in configlib units, where one unit is half a screen pixel, but
  it never entered hi-res drawing: every unit coordinate was taken as a screen pixel, so the panel
  came out twice as large from twice its centre offset and only its top-left quarter was on screen.
  One `beginHiRes`/`endHiRes` pair, which `ChatSearchScreen` — the same kind of screen, drawn the
  same way — has always had. **If another hand-positioned screen ever looks like this, that is the
  first thing to check.**
  The custom main menu now defaults on, which would have broken the welcome a second and quieter
  way: configlib's menu is a plain `Screen`, not a `TitleScreen`, and it can take over before the
  intro check runs, so keying on the vanilla class alone meant the welcome never appeared for
  anyone using the custom menu. `DiegoAddonsV2Client.atTitleScreen` now accepts either.
- **Auto Update** (2.5.0) — a Misc module, off by default, that asks GitHub for the newest release
  and can carry it all the way in. Three modes on one card, because "download and run code for me"
  is not one decision: *Notify only*, *Download* (into `<game>/diegoaddons-updates/`, outside the
  mods folder — two jars with the same id there is a crash, not a choice), and *Download & install*.
  Plus check-on-start, a re-check interval, pre-releases, a chat announcement, and a "Check now"
  button; `/da update` checks by hand and works with the module off, in which case it only ever
  reports.
  The install waits for the JVM to exit, because the running jar is locked on Windows. The shutdown
  hook renames the old jar to `diegoaddonsv2-previous.jar.bak` (Fabric only scans `.jar`, so it is
  inert and it is the way back) and moves the new one in; when the rename fails — the usual Windows
  case — it writes `apply-update.bat`, which waits up to a minute for the process to die and finishes
  the swap. Ordered so a half-done swap still leaves a bootable mods folder, and it gives up rather
  than retrying forever: a failed update means you are still on the old version, which is the right
  way to fail. A downloaded jar is verified before any of that — it must open as a zip and its
  `fabric.mod.json` must name this mod and a newer version — and it is only fetched from a GitHub
  host. The repo was made public and a release published to give it something to read; see the open
  item above for what that now obliges every release to do.
- **Theming, and the start of the per-feature sweep** (2.4.3 / 2.4.4) — there was **no theme picker
  at all**: `Themes.select(...)` had zero callers, so the five themes drove the whole HUD while being
  unreachable except by hand-editing the file. There is now an **Appearance** category (declared
  before the module categories, so it leads the sidebar) with the theme picker, a custom accent
  colour, and `smoothCorners`.
  configlib gained one nullable **accent override** (`ConfigHandle.accent`, `Theme.withAccent`) —
  the palette is still not swappable, per its own design note, but the accent follows the mod.
  `handle.hudStyle` is wired to the theme, which fixes a split 2.4.1 created: the seven custom
  elements were themed while the `labelValue` chips were still configlib white-on-dark.
  Per-element override is `HudModule.style()`, four rows on every HUD card, the detail rows hidden
  unless the override is on. The four modules that had their own `background` toggle now read
  `style().plate()` instead, so there is one control per thing rather than two.
  Per-feature settings added to Auto Sprint, Ability Cooldown, Fullbright and Structure Finder.
- **configlib owns the config** (2.4.2) — every setting is persistent and holds its own value;
  `ConfigManager` is now only the non-setting state plus the one call that asks for a write, and
  does no file IO. `AddonConfig`'s fields are hidden options. Settings that had no home — the
  chooser-backed strings and the Player HUD's section order — are hidden options too.
  Diego waived the "migrate, don't reset" requirement, but `LegacyImport` does it anyway: it reads
  a pre-2.4.2 `diegoaddonsv2.json` once, writes each value through the setting that owns it, and
  renames the old file to `.imported` rather than deleting it. Verified offline against a real
  36 KB config: 12 enabled modules and 25 setting values carried over. Delete `LegacyImport`,
  `ModuleConfig` and `AddonConfig.legacyImported` once no instance runs anything older.
- **The seven custom HUD elements** (2.4.1) — every element draws itself again, rebuilt on
  configlib's `HudWidget`. `HudModule.hudWidget()` returns null by default, so an element without
  custom drawing keeps the `labelValue` text chip rather than vanishing; that hook is what let the
  seven be converted one at a time. Mining Ability and Hydration moved to `hud/CentreOverlay`,
  off the editor entirely, via `HudModule.placeable()`.
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

- **Two things had been silently drawing nothing since the RenderLib removal**, both found while
  rebuilding the elements rather than reported. **Custom Scoreboard** was the worse one:
  `ScoreboardSidebarMixin` cancels the vanilla sidebar whenever the module is enabled, and the
  replacement was never drawn — so switching the module on removed your sidebar and put nothing
  back. **Hydration Reminder** had the same shape: `message()` was still computed every tick and
  no longer read by anything, because the overlay that drew it was deleted. Both draw again.

- **The right edge of the HUD.** RenderLib's placement screen was a fixed 1920×1080 canvas while the
  live HUD was not, so the last strip of a wide window could not be reached by dragging. configlib's
  `HudPos` is a screen fraction plus an `Anchor`, so an element pinned to an edge stays pinned at any
  window shape. No longer a problem.
- **Ping.** The client cannot time a round trip of its own — the play protocol has no
  client-initiated ping, and the only number available is the one the server hands out
  (`PlayerInfo.setLatency`), measured behind Hypixel's proxy. The row is gone: better no number than
  a confident wrong one.
