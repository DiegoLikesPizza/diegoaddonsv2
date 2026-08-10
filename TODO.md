# DiegoAddons V2 — work list

`[ ]` open · `[~]` in progress · `[x]` done. Each item ends with a build, a deploy to the Prism
"DiegoAddonsV2 Test" instance, and a run in game before the next starts.

**Current version: 2.5.3.** RenderLib is gone; the mod runs on
[diegos-config-lib](../diegos-config-lib) (`dev.diego:configlib`), consumed through
`includeBuild` in `settings.gradle` — a fresh clone needs that directory beside this one to build.

**The repository is public** as of 2.5.0, and the mod updates itself from its releases. Two things
follow from that and are easy to forget: a shipped version needs a GitHub release or no client will
ever see it (§4), and anything committed here is now readable by anyone.

---

## Open — in the order worth doing

### 1. Dungeon Map — needs a rework, not a fix
**Parked** (2.5.3) at Diego's call — "we leave it for now". Do not pick this up unsolicited; it needs
his specifics before it is worth any time at all.

Diego, on 2.4.5: "looks weird (thats nothing new)". Long-standing, and the one item on his bug list
that is not a defect with a cause — the map draws what it was told to draw. Get specifics before
touching it: which part reads wrong (tile colours, room sizes, the seams, the stats block, the
player arrows), and ideally a screenshot beside the map it should look like. The drawing itself is
sound and unchanged since before RenderLib, so this is a design pass rather than a bug hunt.

### 2. Things fixed blind that need confirming in game
- [ ] **Door bats** — there is no flag saying which bats came from a door, so 2.4.6 goes on
      downward velocity, exposed as "Ignore door bats" plus a threshold on the Bat ESP card. If real
      secret bats vanish, raise the threshold; if the door crowd still shows, lower it.
- [x] **Autopet / loadouts** — **confirmed working in game** (2.5.3): Diego, "loadouts and pet is
      working". The 2.5.2 `scanEquippedPanel` rewrite is therefore good, and the pet read that came
      with it holds too. If either goes stale again, Inventory HUD → "Debug scan (log)" dumps every
      slot's name and lore, which is what to tune the constants against.
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
Force Nametag, Grotto Finder, Hide Effects, Mining Routes, Title Screen.

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

- [x] **Watch one real update happen** — **confirmed working in game** (2.5.3): Diego, "auto update
      works good!". The whole path is therefore proven end to end, staged jar through to the swap;
      it was the one part a single release could not test.
- [x] **Now that the repo is public**, the README is the first thing anyone sees — rewritten in
      2.5.2. It had described the pre-2.0 three-column ClickGUI, modules that no longer exist (FPS
      Display, Coordinates, Direction), `UiRender`, and a config path two moves out of date.
      It now covers installing, what the 57 modules are, the menu, the file layout, what Auto Update
      does and does not do, what leaves the machine, and building against the composite configlib.
- [ ] **The README has no screenshot.** `preview.png` was deleted with the rewrite — it was a mock of
      the old three-column menu, so it advertised a mod that no longer exists. A real screenshot of
      the settings menu and one of the HUD would be worth more than the mock ever was; they have to
      be taken in game, so they are Diego's to grab.

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

### Minigames (2.5.3) — needs two people to test at all
Tic Tac Toe, Vier gewinnt, Blackjack and Schiffe versenken against another DiegoAddons user, over
whispers: `/da play <name> [ttt|c4|bj|bs]`. **This cannot be tested alone**: two accounts, both with
the module on, both on the same server.

- [ ] **Blackjack's deck.** No card is ever sent — both clients shuffle the same 52 from the same
      seed, which the inviter picks and puts in the invitation, and draw in a fixed order. If the
      two hands ever disagree, the seed did not arrive or the draw order diverged, and that is the
      first thing to print.
- [ ] **Battleships trusts the answer.** Your fleet never leaves your machine, so the other client
      reports hit or miss for its own squares. There is no way around that without a server. Watch
      the "hit keeps the turn" rule especially — it is the part where both sides have to agree who
      is shooting next.
- [ ] **Rematch is two verbs** (`re` offer, `ra` accept) and the accepting side moves first. Check
      it does not restart at one end only.

- [ ] **The whisper format.** `GameLink.FROM` expects `From [MVP+] Name: text` with the colour codes
      stripped. If nothing ever arrives, that pattern against a real Hypixel whisper is the first
      thing to check — turn "Protokollzeilen ausblenden" **off** and the raw lines become visible,
      which is the whole diagnosis.
- [ ] **Hypixel's chat limits.** Every line carries a nonce so no two are identical ("You cannot say
      the same message twice"), and the outbox sends at most one line per 400 ms. Watch for a move
      that never lands, and for any warning from the server.
- [ ] **The invitation buttons** — `[Annehmen]` / `[Ablehnen]` are chat components running
      `/da accept` and `/da decline`.
- [ ] **Both boards agreeing.** Every move is validated at both ends (your turn, empty square, game
      not over) and dropped otherwise. If the two boards ever disagree, that is the bug that matters.
- [ ] **Rematch** swaps who starts, so nobody has the first move twice.
- [ ] Timeouts: an invitation dies after a minute, a silent opponent ends the game after two.

### Storage Overlay (2.5.3) — the whole feature is written blind
The NEU sheet: open `/storage` and the overlay draws over that menu — every ender chest page and
backpack side by side, your own inventory under them, a search box at the bottom. It exists **only**
over the storage menu (Diego's call), so there is no keybind and no `/da storage`.
Written without a SkyBlock session, and the parts that read Hypixel's menus are guesses at strings.
**Turn on "Debug scan (log)" on the Storage Overlay card first**: it dumps each menu's title and
every slot's item name once per menu, which is what the patterns have to be tuned against.

- [ ] **Does the sheet appear at all**, i.e. does the storage menu's title start with "Storage"?
      That one string is what raises it. `StorageScanner` also keys pages on titles containing
      "backpack" and "ender chest".
- [ ] **Live clicks.** The page you are actually in is outlined in the accent and its slots are the
      menu's own — left click picks up, right click halves, shift moves to your inventory, and your
      inventory at the bottom is always live. This is the half that touches your items: **check it
      on a page of junk first.**
- [ ] **Navigation.** Clicking a slot on any *other* page sends that page's command and waits for
      the menu to actually be that page before doing anything; shift-clicking additionally
      quick-moves that slot once it arrives. Watch for: the wrong page opening, or the follow-up
      quick-move landing on the wrong slot.
- [ ] **Refusal while carrying.** Clicking another page with something on the cursor is refused with
      a toast, because changing container hands the item back. Check the refusal actually fires
      rather than the item being silently returned.
- [ ] **Which backpack is which.** A backpack's own menu may not say what slot it came from, so the
      index is taken from "(Slot #N)" in the title, else from the storage-menu icon that was clicked
      to get there, else from a unique name match — and if none answers, the scan is *skipped*
      rather than filed under a guess. Watch for a backpack that stays "unread" after being opened.
- [ ] **The three commands are guesses**, which is why they are text boxes on the card rather than
      constants: `/ec %d`, `/backpack %d`, `/storage`. `%d` is where the page number goes. These are
      now load-bearing — navigation is built on them, not just convenience.
- [ ] **Locked storage slots.** Icons offering to *buy* a slot are skipped by their lore ("click to
      purchase", "cost:", "locked", "unlock"). If unbought slots show up as empty backpacks, that
      list is what to extend.
- [ ] **The search box takes focus on open**, so number keys type instead of swapping hotbar slots
      while the sheet is up. Escape closes. Worth a second opinion on that trade.
- [ ] **Is it still slow?** The first cut ran at a crawl; see the Done entry for what was doing it.
      If it is still heavy, the next thing to look at is the item models themselves — a thousand of
      them is a thousand model submissions, and the only lever left is drawing fewer (a smaller
      sheet, or icons below some size).
- [ ] **Is one navigation row right?** "Navigation rows to hide" defaults to 1, so the top nine
      slots of every page are dropped from the sheet and from its click mapping. If a page looks
      shifted by a row, or its last row is missing, that setting is the first thing to move.
- [ ] **The cache file** — `config/diegoaddons/storage/<account>-<profile>.json`, written at most
      every three seconds and once more on disconnect. Check a second session shows the same items
      before any page is opened, and that swapping profile swaps the contents rather than merging.


Everything below compiles, boots and registers, but was written without being able to click it.
Worth a pass before trusting any of it:

- [ ] Settings menu: seven categories, 60 module cards, switches **on** the cards
- [ ] Sliders drag; dropdowns stay open; text boxes and keybind capture keep focus
- [ ] Colour picker: the hex field accepts a pasted value
- [ ] List editors (blocked players, words, hotkeys, GFS, routes) — add / reorder / delete
- [ ] Player HUD "Section order" screen
- [ ] Sound picker for Secret Chime
- [ ] **Custom sounds** (2.5.2) — an MP3 in `config/diegoaddons/sounds/` picked on the Hydration
      Reminder card and actually heard, at the volume the slider says. Written blind: nothing here
      has been played once. If it is silent, the log carries the reason (`could not decode`,
      `could not play`) rather than failing quietly.
- [ ] **Dragging both scroll bars** (2.5.2) — the card grid's and the drawer's: grab the thumb, click
      the empty track, and drag off sideways without losing it.
- [ ] **Auto Update on the Client page** (2.5.2) — that it is there at all, and that a mode and
      interval set before the move came across rather than resetting.
- [ ] **The config move** (2.5.2) — start once with an existing
      `config/diegoaddonsv2-configlib.json`: it should end up as `config/diegoaddons/config.json`
      with every setting intact, and the log should say it was moved.
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

### Auto Update (2.5.0) — a real update has now been seen
- [x] **A real update, end to end** — **works** (2.5.3), per Diego. The download, the staged jar and
      the swap are all proven; this module is no longer the risky one.
- [ ] **`/da update`** with the module switched off — it should report and never download. The one
      path the working update did not exercise.

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

- **A module with no settings had no card at all** (2.5.3, configlib) — Diego, on the new No Cursor
  Reset module: "ich kann die setting zu no cursor reset nicht finden. Sie ist nicht da". It was
  registered, it was in the jar, and it still could not be turned on from the menu.
  `PanelLayout.rebuildCards` draws a card only where `SectionGroup.visibleCount() > 0`, and that
  count walked the section's **options** while a module's on/off switch is its section *toggle* —
  drawn on the card rather than as a row in it. So a module whose only content is its switch counted
  as zero and was dropped. `CategoryNode.options()` had always counted the toggle, with a note
  saying why ("leaving it out would mean a switch that cannot be found, reset, or counted"); the
  count simply never got the same treatment. It does now.
  **This was not only the new module.** Auto Close Chests, Leap Overlay and Old Master Stars have no
  settings and no base class adding any, so all three have been invisible in the settings menu -
  unreachable except through `/da` - since the menu was rebuilt. They are the same three §3 lists as
  "still bare"; nobody noticed bare had quietly become absent. ESP and HUD modules were never
  affected, since their base classes declare rows for them.
- **Item Rarity: your own items everywhere, and the accessory bag** (2.5.3) — Diego asked for the
  rarity colours to always show in his own inventory, and for the accessories in the accessory bag.
  The old rule was one line - draw only if the screen *is* the inventory screen - so the colours went
  away the instant any SkyBlock menu was open on top of it, which is most of the game. Your own
  slots are now coloured in every menu: they are the last 36 of any container, and in the inventory
  screen the armour and offhand are yours too, so the line moves to the start there.
  A server menu's own slots are still left alone, and that is not laziness - most of a SkyBlock menu
  is filler (panes, close buttons, cosmetic heads) and colouring it is confetti rather than
  information. The accessory bag is the exception worth making, since every slot in it is something
  you own; matched on the title containing "accessory bag", so the paged form ("(1/3)") counts too.
  Both are toggles on the card, on by default, which also takes Item Rarity off the "one setting
  only" list in §3.
- **The storage sheet was unusably slow, and drew the navigation row** (2.5.3) — Diego, on the first
  cut: "why it so laggy" and "top row is not needed since its the navigation row". Four things, all
  of them per-slot work at a thousand slots:
  **Rounded corners.** Every slot plate was a rounded rect, which costs a fill per scanline of each
  corner — about twenty-five fills for a 4-unit radius, doubled by the rarity tint on top. At the
  slots one sheet shows that was roughly fifty thousand fills a frame, for corners two units across
  that nobody can see at that size. Slots are flat rects now (one fill), and their outlines are four.
  The panel and the page blocks are still rounded: there are a dozen of those, not a thousand.
  **Search text and rarity, per slot per frame.** Matching a query meant stripping the colour codes
  out of a full SkyBlock lore for every visible item, sixty times a second, and the rarity colour
  walked the lore again. Both are now derived once per page, when the page is read, and the sheet
  draws every block — including the open one — from that.
  **A capture every tick.** The scan re-copied and re-derived the open page twenty times a second to
  save what was already saved. The menu hands out the same stack objects until the server sends new
  ones, so identity now says whether anything happened, and nineteen ticks in twenty do nothing.
  **Re-sorting the page list** on every one of the several calls per frame that needed it, and
  re-counting every item for the header. Both are held until something changes them.
  The navigation row is gone from the sheet: SkyBlock puts close, back and the page arrows along the
  top of a page, so "Navigation rows to hide" (default 1) drops them. It is an **offset**, not a
  trim — the cached array still holds the whole container, because a click has to go back to the
  menu as the slot number the menu itself uses, and trimming would make every index in the overlay a
  lie that had to be undone at exactly one place, correctly.
- **No Cursor Reset** (2.5.3) — a Misc module: the mouse stays where it was when one menu closes and
  the next opens. SkyBlock is played through chest menus, and moving between them passes through a
  frame with no screen at all, where the game grabs the mouse for gameplay and then releases it —
  and a release puts the cursor in the middle of the window. So every click on a menu that opens
  another one threw your hand back to the centre.
  Both halves are in vanilla's own two methods: `grabMouse` overwrites `xpos`/`ypos` with the centre,
  and `releaseMouse` moves the real cursor to whatever they say. The position is therefore taken at
  the head of `grabMouse`, before it can be lost, and put back in `releaseMouse` in place of the
  centre. It is clamped to the window in case it was resized while the mouse was held, and with
  nothing yet remembered vanilla is left to do exactly what it always did.
  **The first cut did not work**, and the reason is worth keeping: it restored the position through
  `InputConstants.grabOrReleaseMouse`, which sets the cursor position *first* and the input mode
  second. Leaving `GLFW_CURSOR_DISABLED` is exactly when GLFW puts the cursor back where it was when
  the cursor was disabled — the centre, since vanilla had just put it there — so the restore went in
  and was overwritten one line later. The bug the module exists to fix, reproduced faithfully inside
  the fix. It now sets the mode first and the position second, so GLFW does its restore and ours
  lands last.
- **Storage Overlay** (2.5.3) — the NEU sheet: open `/storage` and every ender chest page and
  backpack is drawn at once, three across, your own inventory beneath them and a search across all
  of it. A Misc module, off by default.
  **It takes over the menu rather than replacing it.** `StorageOverlayMixin` cancels the container
  screen's drawing and nothing else, so the menu stays exactly as the server believes it to be —
  which is what makes the clicks real. The page you are standing in is **live**: its slots *are* the
  menu's slots, so a click is `handleContainerInput` on that slot and items move the way they always
  did. Your inventory is live too, in every menu, since those 36 slots are always the last 36.
  Clicking a slot on any other page sends that page's command and **waits for the title to say it
  arrived** before doing anything else — a delay-then-click would eventually click whatever happened
  to be under the cursor on some other menu. Shift-clicking a cached slot queues a quick-move for
  when it lands, which is the one-click "get this out of storage". Navigation is refused outright
  while something is on the cursor: changing container returns the carried item, and doing that
  silently looks exactly like the overlay eating it.
  What is not live comes from a **cache with a date on it**, per profile in
  `config/diegoaddons/storage/<account>-<profile>.json` through `ItemStack.CODEC` — the same route
  the pet cache uses, so enchants, skull textures and SkyBlock's lore survive a restart rather than
  being flattened to a name and a count. Keyed on account *and* profile, because either alone shows
  somebody else's ender chest, and the registry view is held past the disconnect on purpose since
  the last write happens when there is no connection left to ask. A page never opened is drawn as
  "unread" rather than as an empty grid: confusing "I have not looked" with "there is nothing there"
  is the one way a storage overlay actively lies to you.
  Search veils what does not match instead of removing it — the sheet is a map of where your things
  are, and a map that reflows as you type is not one.
  Input goes through Fabric's screen events rather than more injections; only the drawing needs a
  mixin, because nothing else can cancel it.
- **The scoreboard drew a blank square for SkyBlock's symbols** (2.5.3) — Diego: the custom
  scoreboard "doesnt recognize some icons and just defaults to a square". Not a scoreboard bug: it
  refaces every sidebar line into Poppins (`Fonts.reface`), and each of the mod's ten font
  definitions listed **only** its `ttf` provider. Poppins covers Latin and little else, so every
  character it lacks — which is exactly SkyBlock's `⏣ ✦ ❤ ☠ ♲ ⸎` — had nothing to fall through to
  and came out as the missing-glyph box.
  Every `assets/diegoaddonsv2/font/*.json` now ends with the vanilla fallbacks,
  `minecraft:include/default` then `minecraft:include/unifont`, which is the same layout vanilla's
  own `default.json` uses. `FontSet.computeGlyphInfo` walks the providers in order and takes the
  first that holds the glyph, so Poppins still wins for everything it has and the fallbacks only
  answer for what it does not — checked in the 26.1.2 bytecode before shipping rather than assumed,
  since the opposite priority would have replaced the whole typeface with unifont.
  Two knock-ons: symbols now render in the vanilla pixel font beside Poppins text, which is the
  usual look for a SkyBlock mod but is a visible mix; and on the supersampled menu faces
  (`uih_*`, 22-38 units) a fallback glyph draws small rather than boxed.
- **Both scroll bars are draggable** (2.5.2, configlib) — the card grid's and the drawer's. Each was
  an indicator that happened to look like a control: the drawer's bar is drawn just *outside* the
  list's own bounds, so the hit test that covered the rows could never reach it, and the grid's sat
  over the cards, where a press would have landed on whichever card was under it. Both are now
  grabbed before their own content is tested.
  The grab area is wider than the bar (4px and 6px are not hand-sized), a press on the empty track
  jumps the thumb to the cursor and drags on from there, the grab offset inside the thumb is kept so
  it does not snap under your hand on the first pixel, and the drag survives the pointer leaving the
  track — a bar you lose when your hand drifts sideways is worse than one that never moved. The
  scroll animation is snapped while dragging: the easing exists to soften a wheel step, and against
  a drag it only reads as the bar trailing your hand. Geometry is behind shared
  `trackX`/`thumbH`/`thumbY` helpers, since drawing and grabbing disagreeing is exactly how a bar
  ends up not being where it looks like it is.
- **Appearance is now Client, and Auto Update lives in it** (2.5.2) — the page is what the mod does
  to itself rather than to the game, so keeping itself current belongs there. Two things stay put on
  purpose: the category **id** is still `appearance`, because every option id carries the category
  it was declared in and renaming it would hand everyone their theme back at the default; and Auto
  Update is still registered under `Category.MISC`, which is where the code and `/da` list it — only
  its card moved, via `ModuleSpec.IN_CLIENT`.
  Moving the card does re-key its settings, so `ModFiles.rekey` renames them in the file *before*
  configlib reads it — after the read the defaults are already in the settings and writing them back
  is what would make the loss permanent. It renames the bare `misc.autoupdate` as well as
  `misc.autoupdate.*`: a module's on/off state is saved under the bare id, so matching only the
  dotted form would have carried the settings across and left the switch behind, silently turning
  the module off. Verified against a copy of a real config before shipping.
- **Everything the mod owns lives in `config/diegoaddons/`** (2.5.2) — the settings file and the
  pre-2.4.2 config used to sit loose in `config/` while the skins and the media script were already
  in a folder, so where a file belonged depended on which year the feature was written. `ModFiles`
  is the one place that answers that now, and `ModFiles.migrate()` moves the two old paths in at
  startup, before configlib is handed its path — otherwise the settings would be written out fresh
  at their defaults beside a file nobody reads again. A move that fails is logged and left alone.
  `config/diegoaddonsv2-configlib.json` is now `config/diegoaddons/config.json`. The only file still
  outside is the staged update jar, which is in the game directory on purpose — it is a jar waiting
  for the mods folder, not config.
- **Custom sounds for the Hydration Reminder** (2.5.2) — drop an **MP3**, OGG, WAV, AIFF or AU into
  `config/diegoaddons/sounds/` and pick it on the card; the picker lists the folder lazily, so a file
  added while the game is running is there the next time it is opened. Plus a volume slider, which
  the game's own chime now follows too.
  The game cannot play any of this: its sound engine takes OGG Vorbis out of loaded resource packs
  and nothing else, so a file would need a pack and a resource reload before it could be heard.
  `CustomSounds` decodes to PCM and plays through Java's audio output instead — **JLayer** (bundled,
  LGPL, ~100 KB) for MP3, which neither the game nor Java can read; `javax.sound` for WAV/AIFF/AU;
  STB Vorbis, which ships with the game's LWJGL, for OGG. Decoding and playing are on a daemon
  thread, volume is applied to the samples rather than through the line's optional gain control, and
  it is multiplied by the game's master slider so turning the game down turns this down.
- **Item Rarity drew over the items in the hotbar** (2.5.2) — Diego: with the inventory open it looks
  right, on the hotbar the colours are on top of the items. It was drawn from the mod's HUD pass,
  which runs after the whole vanilla GUI, so "Filled" and "Circle" landed on the item rather than
  behind it while the same two were correct in an inventory. It now draws from
  `HotbarSlotRarityMixin`, at the head of vanilla's `Gui.extractSlot` — the one method handed a
  slot's position and its stack just before the item is submitted, so there is no ordering left to
  guess. Position comes from vanilla rather than being worked out from the window size, which also
  gets the offhand slot for free.
- **A loadout swap now shows on the HUD at once** (2.5.2) — the Loadouts menu is in two halves, which
  is the part that was missed. Down the **left** is a panel of what is equipped *right now*: a column
  of trees and stones (HotF, HotM, power stone, tuning template), then the four equipment pieces,
  then the four armour pieces, with the active pet beside the chestplate. The **3×4 grid on the
  right** is saved presets — icons that name their contents in a tooltip and hold none of them.
  The scan only ever read the preset tooltips, so a piece could be drawn only if this session had
  already seen it in some other menu, and the menu does not close when you swap, so nothing came
  along to correct it. `scanEquippedPanel` reads the panel instead, and the tooltips are the
  fallback for when it is not where it is expected.
  Nothing is hard-coded to a slot number: the equipment is found by its own rarity lines, which
  places the column and the four rows, and the armour and the pet are read relative to that. A
  preset cannot be mistaken for the panel — its bottom lore line is a price or a date, so it carries
  no equipment category at all — and if two columns ever disagreed the scan gives up rather than
  mixing them. The pet is now read here too, which is one more place an Autopet swap cannot go stale.
  Armour is cached but **only** used when every live armour slot is empty. The inventory is the
  truth; falling back per empty slot would leave a helmet you took off sitting on the HUD.
  Two things fell out of this: `equipmentLocked` was written in four places and read in none, so it
  is gone; and `persist` wrote the config on every tick a matching menu was open — twenty encodes
  and a disk write per second, to save what was already saved — so it now writes only on a change.
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
