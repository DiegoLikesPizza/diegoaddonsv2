# DiegoAddons V2 — work list

`[ ]` open · `[~]` in progress · `[x]` done. Each item ends with a build, a deploy to the Prism
"DiegoAddonsV2 Test" instance, and a run in game before the next starts.

**Current version: 2.5.5-b-4** — a beta, `mod_version` in `gradle.properties`. **2.5.4 is released**
(tag `v2.5.4`, jar attached, marked Latest) and supersedes every `2.5.4-b-N` on every instance,
whether or not pre-releases are switched on, because a release outranks any pre-release of the same
numbers.

---

## 2.5.5, in progress

### 12. Pick your own SkyBlock level colour (2.5.5-b-4)
Diego: "mach mal dass man seine level color aussuchen kann zwischen denen die man schon unlocked
hat" — the badge in front of your name, and only ever an **earlier** colour than the one you are
wearing.

`module/modules/LevelColorModule` (Misc) is one dropdown; the work is in `util/LevelColor`.

- **Applied at the font, like the word replacer.** `FontMixin` is the one choke point every drawn
  string passes through, so the tab list, the name plate over your head and every chat line carrying
  your badge are covered by one hook rather than three. Purely visual and purely local.
- **Your level is read out of the badge being recoloured.** No menu scan, no cached number: the
  badge carries the level, so the same string that says "recolour me" also says what you are allowed
  to recolour it to. Nothing to go stale, and it works on the first frame after a login.
- **The unlock rule is the table.** One colour per 40 levels, last at 480, thirteen in all — your
  tier is `level / 40`, and anything at or below it is yours. A pick above your level falls back to
  the tier you are in, which is the colour you already had, so it simply does nothing until you get
  there. The menu still lists all thirteen with their level beside them (`Gold (400)`), because the
  spec is built once at startup, when your level is not known — a list built then could only lie.
- **Whose badge it is, is decided by what stands between it and your name**: spaces and bracketed
  tags (a rank, a guild tag), nothing else. "Your name somewhere in the next few characters" was
  tried first and gives away your colour on `[200] Someone: hey Diego`, and picks the wrong badge on
  a line carrying two.
- **The style is put back after the closing bracket**, so the colour stops at the badge instead of
  running on into your rank and name.
- Checked with a harness over 16 cases — chat with a rank, tab, a bare name plate, two badges on one
  line, a longer name starting with yours, bold, a locked pick, and the brackets that are not badges.
  All pass.

- [ ] **The colour ramp is from memory and is the one thing to check by eye**: gray, white, yellow,
      green, dark green, aqua, dark aqua, blue, light purple, dark purple, gold, red, dark red. Pick
      the tier you are actually wearing — if the badge changes colour, that entry is wrong and
      `LevelColor.CODES` is the only thing to fix.
- [ ] **Pick something below your level and look at the tab list, your name plate and a chat line** —
      all three come from the one hook, so all three should change together.
- [ ] **Pick something above your level**: nothing should happen, and that is the unlock rule
      working, not a bug. Worth deciding whether it should say so instead of staying silent.

### 11. Fire Freeze Timer for F3/M3 (2.5.5-b-4)
Diego went looking through old 1.8.9 mods for this one: "a timer on screen like the hydration
reminder that counts down after P1 in the boss fight to the point before P2 where you have to fire
freeze so the professor is frozen."

The staff freezes five seconds **after** it is cast, so the cast has to happen before there is
anything on screen to react to - which is why this is a timer and not a highlight. The one readable
event is the Professor's own line when the Guardians die, and the gap from there to the right cast is
fixed. Cue and delay are taken from [SkyImprover](https://github.com/ItzGreenCat/SkyImprover)'s
`M3FreezeHelper`: the message fragment `You found my Guardians' one weakness?` and **5.25 seconds**.
Both are settings-adjustable anyway - a fixed number a quarter-second off is a feature nobody can fix
from in game.

`module/modules/FireFreezeModule` (Dungeons), a HUD element like every other: countdown to one
decimal, `§cNOW` for two seconds, then it clears itself. Title and sound on by default, a per-second
tick off by default.

- [ ] **Run an F3 and watch it fire once** on the Guardians line, not on entering the room.
- [ ] **Does the freeze actually land on him?** If he is frozen a beat early or late, that is the
      5.25 slider and not the cue - move it a quarter second and say which way.
- [ ] **Nothing left on the HUD after the boss dies** - it clears two seconds after the call.

### 10. The Farming Session, second pass (2.5.5-b-4)
Pests and copper read correctly now. Crop and profit do not, and the parse has been tightened twice
against a widget nobody has actually printed yet:

- The heading is matched on the word **Milestone** rather than the exact string `Crop Milestones`,
  so a widget named in the singular can no longer fail the whole read on one letter.
- **Jacob's Contest is excluded by name.** It sits near the milestone widget and carries both a crop
  and a big number, and either would be adopted as the harvest - a wrong figure, which is worse than
  the missing one.
- The counter is only ever taken from a `Counter:` line or from **the crop's own line**, never from a
  neighbouring one in the same block.

- [ ] **The dump settles it.** Debug scan (log) on, thirty seconds in the Garden, then read
      `[FarmingSession] tab |` in `logs/latest.log` - the widget's real shape is in there and every
      remaining guess here can be deleted.

### 9. The Farming Session showed a clock and copper and nothing else (2.5.5-b-4)
Diego: "Farming Session tracker is broken. It shows only the session time and copper. No profit and
no pests killed amount." Four separate faults, each one enough on its own to hide a line - and every
one of them a string that was guessed from a screenshot and shipped without being read back.

- **No crop, so no crops line and no profit.** The milestone widget was parsed by taking the line
  under `Crop Milestones` and treating its leading words as a crop, with the count read from a
  `Counter:` line. It now scans the heading and the four lines under it, accepts the count either on
  a `Counter:` line or as the trailing number beside the crop, and only ever takes a crop name from
  a **closed list of the eleven Garden crops**. A closed list because a pattern that accepts "the
  leading words" accepts a heading too, and then the coins figure prices whatever was written there.
- **Nether Wart has no bazaar id called `NETHER_WART`.** `Bazaar.idFor` upcases the display name,
  which is exact for most farming items and wrong for exactly the crops Diego farms: the ids are
  Minecraft's old ones (`NETHER_STALK`, `CARROT_ITEM`, `POTATO_ITEM`, `INK_SACK:3`). The price came
  back 0, the coins figure went to -1, and the profit line hid itself. There is now an alias table,
  which fixes the **Visitor Helper** at the same time - it could not price half of what a visitor asks.
- **The pest kill line was anchored at the start** (`^You received ...`), and Hypixel prefixes it
  with its own banner, exactly like the spawn line the field above it already allows for. Searched
  rather than matched now, and there is a **second source**: the Pests widget's alive count falling
  is a pest that died. Chat wins where it works, because it carries the drop as well; the two are
  never added, and the first chat kill takes the widget's count with it.
- **The clock paused while farming.** Idle was measured off copper and the milestone counter, and
  copper only moves on a visitor while the counter needs a widget that may be off. Activity is now
  the action bar's **farming XP** - the thing gained on the crop itself. The segment's text has to
  *change* to count, not merely be present: SkyBlock leaves the last one drawn for a few seconds.

The card no longer hides a number it cannot produce - a missing line reads as a broken mod, which is
how this stayed broken. Crops says "enable the Crop Milestones widget", profit says whether it is
waiting for prices or has no price for this crop, and pests is drawn at 0.

- [ ] **Farm for two minutes and watch the clock keep running** past the 2-minute pause without
      touching a visitor. That is the XP signal working.
- [ ] **Stand still for two minutes** and it should say "(paused)". If it never pauses, the action
      bar keeps its last segment up and presence has to stop counting.
- [ ] **Crops, profit and pests should all carry a number.** Nether Wart is the one to test - it is
      the crop the alias table exists for.
- [ ] **Kill a pest** and check the count moves once, not twice. Twice means chat and the widget are
      both being counted.
- [ ] With **Debug scan (log)** on, the log prints the whole tab list every 15s plus the parse, and
      any chat line with "killing" in it that did not match. **If crops still reads as missing, that
      dump is the answer** - paste it back and the widget shapes come from it rather than a guess.

### 8. The equipped panel was never being found at all (2.5.5-b-4)
Diego: "why does scanning the loadouts screen still not work, it only shows the new pet and equipment
on my 2 farming loadouts." That shape - some loadouts and not others - is the tooltip fallback, which
can only draw gear seen elsewhere this session. So the panel route was not running **at all**, and
had not been since the price mod was installed.

`categoryOf` read an item's **last non-blank lore line** and asked whether it ended in a category
word. Diego's screenshot of a panel cloak shows why that never matched: under the rarity line
`✦ MYTHIC DUNGEON CLOAK ✦` sit `[NF] Lowest BIN: 5,589,000` and `[NF] Created: Saturday 2/8/25`. So
the line tested was a date, every piece scored -1, `col` stayed -1, and the panel was declared
missing on every menu. **Two independent faults in one test**, either enough on its own: the extra
lines below, and the decorative glyph *after* the category word that `endsWith` could not see past.

The category is now read off the **rarity line**, found by searching the lore bottom-up for a line
carrying a rarity word, and matched with a whole-word `contains` instead of `endsWith`. Bottom-up
because the rarity line is the last thing SkyBlock itself writes - anything under it came from
something else. The preset icons stay immune for a better reason than before: they name their
contents (`Necklace: Peony Necklace`) but have no rarity line, and a category is only ever read off
one. That is why this looks for a rarity rather than just scanning every line for a category word -
scanning every line would make each preset look like a panel piece.

- [ ] **Swap between loadouts that are not the farming ones.** Both HUDs should follow every one of
      them now, including gear this session has never opened a menu for.
- [ ] **A loadout with no pet** should clear the pet rather than leave the last one up.
- [ ] With **Debug scan (log)** on, the log should say `loadout read by panel`, never `tooltip`.
- [ ] Worth a look with the price mod **off** too - the fix should be indifferent to it either way.

### 7. Inventory Buttons, ported from afranz29's mod (2.5.5-b-4)
Diego: "port it, meaning exact same looks for the inventory buttons and editor." So this is a port
and not a rework - which is what the old §5 entry below asked for and was never done. The source is
[Inventory-Buttons](https://github.com/afranz29/Inventory-Buttons) (LGPLv3, itself NEU's feature
carried forward); the geometry, colours and layout numbers are its own and are **not** to be restyled
into the mod's configlib look. The ported files carry its copyright header.

Nothing of the 2.2.2 implementation was reused - it was removed for RenderLib and the upstream mod
is a different, better thing. New files: `util/InvButtons` (list, icons, profiles, clipboard),
`util/HypixelSkulls` (the SkyBlock head catalogue), `gui/InvButtonsOverlay` (drawn on real menus),
`gui/InvButtonEditor` (the editor), `module/modules/InventoryButtonsModule`, `config/InvButton`.

**What changed against upstream**, all of it deliberate:
- **Its two screens are gone.** The settings are rows on the module card and the profile list is a
  picker there, because that is where every other setting in this mod lives. The buttons and the
  editor are pixel-identical; only the config surface moved.
- **The layout is in the mod's config**, not `config/inventorybuttons/invbuttons.json`, so it is
  carried with everything else. Profiles are still files, under `config/diegoaddons/invbuttons/`.
- **Drawn from the screen events rather than a mixin**, like the storage sheet and the search box -
  after the menu's own pass, before its tooltips, with the click vetoed so it never reaches a slot.

- [ ] **Open your inventory with a button or two placed.** They should sit exactly where the editor
      showed them, tooltip on hover, and the command should fire on a click.
- [ ] **Open a SkyBlock menu that is taller than the inventory** (a bazaar page). A button above the
      midline should stay put; one below it should move down with the menu's extra rows.
- [ ] **Drag with snapping off, then press S.** Off: free everywhere except over the slots, where it
      magnets to the nearest gap. On: the thirteen free slots inside, a 20px tiling outside.
- [ ] **Pick a SkyBlock head in the icon list.** It needs the Hypixel item list, which is fetched on
      enable - if the heads are missing, that request failed and the log says so.
- [ ] **Export, then Import.** The clipboard blob should also accept one from the upstream mod, and
      an old 1.8-era layout should come back with real items rather than question marks.

### 6. The keybind swap now closes the menu when it has finished reloading (2.5.5-b-4)
Diego: "just make it that it can just close the menu when it was already fully reloaded." That
replaces a bet with an observation, and it also closes the hole left in §5 — a keybind swap shuts the
menu, so the panel re-read had nothing on screen to read.

**"Wait before closing" was always a guess at how long SkyBlock takes to rewrite the menu**, on a
number that depends on the ping. The rewrite is something the client can simply watch happen: the
panel changes, then stops changing. New row on the Loadout Keybinds card, **"Close once the menu has
reloaded", on by default**; the delay stays as the floor, so a swap never closes sooner than it did.

- **Finished = changed since the click, then held still for 300 ms.**
- **Two signals, whichever came last.** The chat line lands first and the panel rewrite after it, so
  the message alone would settle too early - but a swap between two loadouts with the same gear
  changes nothing in the panel, and then the message is the only evidence there is.
- **`panelChangedAt` moves only on a real change**, not on every forced re-read inside §5's window,
  or "held still" could never become true while the window was open.
- **Three seconds and it closes anyway.** Both signals can be missing - a menu whose layout the panel
  scan does not recognise produces neither - and waiting forever on a signal that is not coming
  would leave the menu open, which is worse than closing late.
- [ ] **Press a loadout key and watch the Player HUD and Pet HUD**. They should be on the new loadout
      by the time the menu disappears. This is the case §5 could not reach.
- [ ] **Swap between two loadouts with identical gear** — it should still close, ~300 ms after the
      chat line, rather than sitting there for the full three seconds.
- [ ] Does the menu ever now feel slow to shut? The floor is still your "Wait before closing"; if it
      lingers, the settle is waiting on a panel that keeps being rewritten.

### 5. The swap announces itself in chat — Diego's fix (2.5.5-b-4)
Diego: "lwk just scan the menu when the player has the `(X/3) Loadouts` menu open and theres a chat
message saying `You equipped XXX`." **He is right and it is the better design**, so it is now the
trigger rather than another guess layered on the old one.

Everything before this treated a swap as something to be *inferred*: the menu does not close when
you switch, so the only evidence was the panel's contents changing under you, and the fingerprint,
the freshly-opened-screen reset and the name-not-identity comparison all exist to turn that into an
event. **The server was saying so in chat the whole time.**

- On `You equipped ...`, the panel is re-read every tick for **1.5 s, ignoring the fingerprint**. A
  window rather than one re-read, because the message arrives *before* the menu has been rewritten -
  SkyBlock sends the line and then repopulates the slots, so a single scan on the message would read
  the loadout you just left. Whatever the panel settles on is what lands.
- The fingerprint is still written during the window, so the normal "has anything moved" test picks
  up from what was last read rather than from what was there before the swap.
- **Nothing is parsed out of the message.** What was equipped is read from the menu; the line only
  says when to look. So a wording that varies with what you equipped cannot stop it firing.
- It is only reachable from the Loadouts menu - the forced re-read lives in the panel scan, which
  nothing else calls - so this cannot fire against some other menu that happens to be open.
- [ ] **Switch loadouts with `/loadout` open** and watch the Player HUD and Pet HUD follow, for a
      loadout that used to work and one that did not. This is the one that should just work now.
- [ ] Is 1.5 s long enough on a bad connection? "Debug scan (log)" prints the trigger and the window,
      and the route line after it says what was read.
- [ ] **A keybind swap closes the menu**, so this does nothing for those - the panel is not on screen
      to be re-read. Worth deciding whether the message should also drive a swap made that way, which
      would need the gear from somewhere other than a menu.

### 4. Loadout swaps only updated the HUD for two of Diego's loadouts (2.5.5-b-4)
Diego: "updating the pet hud and equipment in player hud when using another loadout somehow only
works with my 2 farming loadouts." **Three real defects found, and the cause is not confirmed** -
"some loadouts but not others" is the symptom of the *tooltip* route running instead of the panel
one, because that route can only draw a name it already has a model for, so the loadouts that work
are whichever happen to be made of things seen this session.

- **A pet skin broke the loadout's pet line** - the same bug as the Autopet lines, and this pattern
  was simply left behind when those were fixed in b-1. `Pet: [Lvl 90] Rabbit ✦` and
  `Pet: [Lvl 100] [122✦] Golden Dragon` had the decoration taken *into* the name, which then matched
  nothing. **This alone fits the report exactly**: a loadout whose pet has a skin cannot draw it
  while a plain one can.
- **The key the three name sources meet in still carried the decoration.** b-1 taught the messages to
  strip `[122✦]` and a trailing `✦`; `petKey` went on storing the pet under "rabbit ✦" while every
  lookup asked for "rabbit". Both ends now normalise the same way, so the menu, the chat line and the
  tooltip agree.
- **The panel scan gave up too easily.** It bailed the moment a second column held anything that
  looked like an equipment piece - and giving up is the expensive answer, since it drops the whole
  menu onto the tooltip route. One preset icon whose bottom lore line ends in a category word is
  enough to do that to *every* loadout at once. It now takes the **leftmost** column with a piece,
  which is what "the panel" means, and still remembers pieces found anywhere in the menu.
- **The tooltip route left the previous loadout's gear on the HUD** when it could not resolve a
  single piece of the new one. That is a confident wrong answer, and the same mistake the pet slot
  was fixed for in b-1: a named-but-unknown piece now shows empty, which is at least true.
- Patterns checked with a harness over 17 cases - the plain form, both skin decorations together and
  apart, varied spacing, the key normalisation from all three sources, and the lines that must *not*
  parse as a pet. All pass. The `✦` literal survives the build: its UTF-8 bytes appear 7 times in
  the compiled class, up from the 3 b-1 verified.
- [ ] **This is the measurement that settles it.** Inventory HUD -> **"Debug scan (log)"** on, open
      `/loadout`, switch between a loadout that works and one that does not. The log now says
      `loadout read by panel -> ...` or `loadout read by tooltip (N candidate(s), panel not found)`,
      with the gear and pet it came out with and how many pieces/pets it knows.
      **panel for both** - the read is fine and the bug is downstream, in the HUD elements.
      **tooltip** - the panel is not being found at all, and the slot dump above it says why.
- [ ] If it is the tooltip route, the fix after this one is **remembering pets and gear between
      sessions** rather than only for as long as the client has been open. That is the standing
      question from b-1 about seeding the pet cache, and this is the second thing to run into it.

### 3. Chat peek — hold a key to read the chat while still playing (2.5.5-b-4)
Diego: "while you have it pressed the chat is opened but like you can still play its just that the
chat is visible without being able to type." On the **Chat** card, at his call, rather than as a
module of its own — it is "how my chat behaves", which is what that card already is.

**Unbound by default.** Same rule as Ctrl+F and the Inventory Search box: the Chat module is on for
anyone who wants unlimited history, so a default key would quietly take that key off everyone who
never asked for this. Bound is on, unbound is off — no second switch, because a hold key with
nothing to hold is already the off state. `KeybindSetting` gained an `isDown()` and a
default-key constructor (unused so far, and documented as being for the case where the key *is* the
feature).

- **It is not an approximation of "open the chat", it is the same code path.** 26.1 hands the chat a
  `DisplayMode`, and that one argument is the whole difference between the HUD's chat and the chat
  screen's: `BACKGROUND` fades each line on a timer (hence the ten seconds you can see while
  playing), `FOREGROUND` uses `AlphaCalculator.FULLY_VISIBLE`. The mixin swaps that one argument in
  `Gui.extractChat` while the key is held — nothing is opened, no input is captured.
- **The second half is the height**, or the peek would un-fade the lines and then show the same
  handful of them. `ChatComponent.getHeight()` picks between two saved heights on whether the chat
  screen is up, and `getLinesPerPage()` is built on it, so one inject covers both.
- **The obvious way is a trap and is written down in the mixin**: making `isChatFocused()` lie would
  *stop the chat drawing at all*, because `Gui.extractChat` returns early when it is true — the chat
  screen is supposed to be drawing it instead, and during a peek there is no chat screen.
- **No state is kept.** The key is polled where the answer is needed, so releasing it lands on the
  next frame rather than the next tick, and no flag can be left set by a screen change, a disconnect
  or the module being switched off mid-hold. A screen being open rules a peek out, which covers both
  the chat screen (already open, HUD does not draw it) and any other menu (you are not playing).
- [ ] **Bind it and hold it.** Lines older than ten seconds should come back and stay while held.
- [ ] **Check you can still walk and look around** with it held - that is the whole ask.
- [ ] **Watch for a hovered-line highlight.** The foreground path is handed the mouse position, which
      while the cursor is grabbed is wherever it was last, so a chat line may light up. Harmless, but
      it is the one visible difference from just "the chat, showing".
- [ ] Scrolling while peeking is **not** in: it would mean taking the mouse wheel off the hotbar
      while held. Worth adding as an off-by-default row if reading one page back is not enough.

### 2. Your own PNGs on portals and on the Hub's big map (2.5.5-b-4) — Diego's ask this session
"Add a feature that lets me display .png files on portals. oh and make a feature for custom hub map.
Yk the big ass map in the hub." Two modules, both in **Render**, both reading the same folder:
**`<config>/diegoaddons/images/`**. Drop a `.png` in, name it on the card or with a command.

Neither replaces anything the game draws. A picture is hung **2 cm off the surface** it covers
(`WorldRender.PICTURE_OFFSET`), so a translucent PNG shows the portal swirl through it, nothing has
to be mixed into block or map rendering, and switching a module off puts the world straight back.
Drawn on the engine's translucent-entity type, which is **not back-face culled**, at full brightness
— a portal is a light source and the map wall is read from across the courtyard, so a picture that
went dark at night would be the one time you could not see it.

- **Fit / Fill / Stretch** (Diego's follow-up: "add options for filling and stretching the image to
  fit"), on both cards, defaulting to **Fill**. The three differ in what they give up, and the
  difference is where the work happens: **Stretch** distorts, **Fill** keeps the shape and crops the
  overflow **in the UVs** (so nothing is drawn that is not seen), **Fit** keeps the shape and shrinks
  the **geometry**, leaving the rest of the surface bare rather than painting over it. All of it is
  `util/ImageQuad.fit`, checked by hand against a 4×3 surface with 16:9 and 1:1 images.
- **Portal Images.** Portal blocks are flood-filled into **panes** first — a portal is a rectangle of
  blocks, and drawing per block would tile the image once per square metre. Which image goes where is
  stored **per portal position** (`/da portal <file>` while facing one), so every portal in the Hub
  can have its own; anything unassigned shows the card's default (`portal.png`).
  - Keyed on the **position only, not the island name**: the island comes from the tab list and is
    blank for the first seconds after a warp, and a briefly-blank key is a picture that briefly falls
    off every time you change lobby.
  - "Which portal am I looking at" is **not a ray trace** — a nether portal has no collision box, so
    the crosshair passes through it and the game's own hit result never names one. It asks which
    nearby pane is closest to the middle of the screen instead.
  - The scan is on a timer (1.5 s, or at once after moving 6 blocks) like the Floor Drops one, and
    **shorter than it is wide** (±8 blocks vertically): the third axis is what multiplies the cost.
  - The block is a **text setting**, defaulting to `minecraft:nether_portal`, so an end portal or
    whatever else Hypixel uses somewhere is one line to fix rather than a new build.
- **Hub Map.** The wall is **found, not hard-coded**: it looks for the biggest group of framed maps
  facing one way in one plane and takes the rectangle from the frames' own bounding boxes. Coordinates
  would have been shorter to write and wrong the day a wall moves; this way it also covers a map wall
  anywhere else. "Only in the Hub" is on by default.
- [ ] **The one assumption: the Hub's map is filled maps in item frames.** If it is not, the module
      finds nothing. **"Count frames (log)" on the card is the measurement that settles it** — it says
      how many item frames are nearby and how many hold a map. Frames but no maps → try "Any framed
      item". No frames at all → the wall is built from something else and I need to know what.
- [ ] **Do the SkyBlock portals turn out to be `minecraft:nether_portal`?** Same shape of question,
      same answer: if nothing is papered over, the block id is the first thing to change.
- [ ] **Check the picture is the right way round on all four walls**, and not mirrored. The "right"
      edge is worked out as `forward × up` rather than listed per direction, which is the part that
      would show up as text reading backwards on half of them.
- [ ] **`/da images`** lists what is in the folder and re-reads it, so an edited PNG takes effect
      without a restart. Both cards also have a "Reload images" button.
- **Not yet run in game.** Built and deployed to the test instance only.

### 0. The menu search box was too small and sat over tooltips (2.5.5-b-3)
Diego: "die search bar ist sehr klein und die font scaled nicht richtig. Außerdem overlapt sie
tooltips."

Both are `InventorySearch`, the box under a container menu — not the storage sheet's.

- **Size.** It passed `BOX_H = 22` to configlib's `SearchBox`, which is built around **34**. The
  widget squashes its frame to whatever height it is handed (`Math.min(height, BOX_H)`) but scales
  nothing inside: the magnifier column is a fixed 34 wide and the field's font is one of the
  pre-baked sizes. So the frame ended up half the height of its own contents, which reads exactly as
  "font doesn't scale". The sheet has passed 34 since 2.5.3 and looks right; same number now.
  `BOX_W` already matched `preferredWidth()`.
- **Order.** It drew on `afterExtract`, i.e. after the screen's tooltips. Moved to `afterBackground`
  where the rarity backing, the slot locks and its own match highlights already sit. Nothing is lost
  by going early: the box is **below the menu's rectangle**, so the menu has nothing down there left
  to paint over it — which is the only thing `afterExtract` was buying. `Toasts` stays behind on
  `afterExtract` and still lands on top of everything.

**Not yet run in game.** Folded into b-3 rather than shipped as its own build — b-3 had not been
released, only deployed, so both instances were simply overwritten with the rebuilt jar and no
2.5.5-b-4 exists.

### 0. The HUD editor crashed the game from the main menu (2.5.5-b-3)
Opening the HUD editor on the title screen killed the client outright:
`NullPointerException: Components not bound yet`, thrown inside `new ItemStack(...)` in
`HarvestFeast.icon` while `FeastHudModule` drew its preview.

An item's component map is bound when the server's data arrives, not at startup, so **every**
`new ItemStack` outside a world dies in the constructor. `HarvestFeast` is the only place in this
repo that builds stacks from `Items.*` — the other HUD elements show stacks read out of the
player's inventory, which is simply empty on the title screen — so the guard sits there: no level,
no stack. `HudSlots.item` already skips an empty stack, so the card keeps its size and its
countdown and just shows no icons until you are in a world.

Not a b-2 regression, only reached because the HUD editor is reachable from the custom title
screen. **Worth considering:** `HudRegistry.draw` in configlib lets an element's exception escape
into the render loop, so one bad element takes the whole game down. Catching there — log once per
element, then stop drawing it — would make this class of bug a missing card instead of a crash.

### 0a. configlib draws rounded shapes with a shader (2.5.5-b-2)
Done over in `diegos-config-lib`, not here: rounded rectangles, outlines and circles now go through
a signed-distance-field pipeline submitted into the vanilla GUI batcher, so a corner is smooth at
any GUI scale and a shape costs one quad instead of three fills per corner scanline. It carries its
own `configlib.accesswidener` (two `GuiGraphicsExtractor` fields plus `ScissorStack.peek`) and its
own `assets/configlib/shaders/core/round_rect.{vsh,fsh}`, both of which ride along inside the
nested jar — nothing had to be declared on this side, and the CPU scanline rasteriser stays as the
fallback if the pipeline will not compile.

The one thing this repo had to change: **`Ui.shadow` is gone.** configlib dropped panel drop
shadows with the rewrite, so `GameScreen` lost its shadow call too rather than growing a private
copy — the minigame panel now sits on surface + outline, matching the config GUI instead of being
the one screen still wearing a shadow.

**Not yet run in game.** Built, bundled and deployed only; the shader path itself is unverified at
runtime.

### 0b. Auto Update kept the old jar in the mods folder (2.5.5-b-1)
Diego: the leftover `.jar.bak` "hat letztens meine instanz so gefickt dass ich meinen pc neu starten
musste, weil ich die bak datei nicht disabled habe bevor ich gestarted hab."

The old jar was renamed to `diegoaddonsv2-previous.jar.bak` **beside the new one**, on the reasoning
that a backup is the way back from a bad update. That was wrong twice: the way back is the GitHub
release it came from, which is still there and still downloadable, and a folder the loader scans is
no place to keep something not meant to be loaded. The old jar is now **deleted**.

- **Ordering was reversed on purpose**, and it is a choice between failures. Moving the new jar in
  first and then failing to remove the old leaves **two jars with the same mod id**, which does not
  start - the exact thing being fixed. Deleting first and failing to move leaves **no** mod, which
  starts fine and is fixed by dragging one file out of the staging folder. Missing beats broken, so
  it deletes first and says loudly where the new jar is if the move then fails.
- The Windows batch fallback does the same: `del /f /q` instead of `move` to a backup.
- **Leftovers are cleaned up on start** (`Updater.removeStaleBackup`) - one exact filename, one that
  only this mod writes, in the folder this mod runs from. Everyone who ever auto-updated already has
  one of these sitting there, and leaving them to find out the way Diego did is not a fix. Runs
  regardless of the module's setting: the file is no longer the feature's doing, just rubbish.
- Verified by running the **real batch template against real files** in a temp folder: old jar gone,
  new jar in place with the right contents, staging emptied, script self-deleted, and exactly one
  jar left in the folder.

### 0. The storage sheet was throwing items on the floor (2.5.5-b-1) — **the important one**
Diego, on b-9: "ab und zu ist es noch so dass im storage overlay einfach sachen gedropt werden...
bisher hab ich noch nichts gevoided aber das ist auch nur eine frage der zeit."

The 2.5.4-b-3 fix — the sheet taking every **key** — was real and still holds: `keyPressed` ends in
`return true`, so Q never reaches vanilla. What it did not cover is the **mouse release**. The sheet
vetoed the press and nothing else, and a press that never reaches vanilla does not stop the release
from arriving.

`AbstractContainerScreen.mouseReleased` is not a formality: it calls `slotClicked` **seven times**,
one of them with slot id **-999**, which is the id meaning "outside the window" and whose effect is
to throw the carried stack on the ground. So picking an item up in the sheet and letting go anywhere
the real menu has no slot handed it to the floor. Occasional rather than constant, because it needs
something on the cursor *and* a release over the wrong place — exactly as described.
`mouseDragged` is the same shape: three more item-moving calls, driving quick-craft distribution.

Both are now refused outright while the sheet is up (`allowMouseRelease`, `allowMouseDrag` — neither
was registered at all). **Refused wholesale rather than by identifying which of those paths fired**:
the sheet has no release or drag behaviour of its own beyond the search box's text selection, so
there is nothing to preserve, and a narrower fix would be a guess about which call was doing it
where being wrong costs somebody their items.

- The one thing given up: **drag-to-distribute inside the sheet**. Against items on the floor, that
  is the right trade. Picking up and putting down still work — those are presses.
- [ ] **Test it deliberately**: pick an item up in the sheet, then release over the panel, over the
      grey outside it, and over another page. Nothing should ever leave your inventory.
- [ ] **This has not reached the main instance.** It is in a beta, and the daily driver runs with
      pre-releases off — so until 2.5.5 is a real release, the sheet on that instance can still drop
      things. Worth deciding quickly, since the whole point is that it loses items.

### 1. Loadout re-scan (2.5.5-b-1) — Diego's first ask for this cycle
"Immer wenn man /loadout öffnet oder sein loadout im Menü wechselt" the Player HUD and Pet HUD
should be read again. The scan already ran every tick the menu was open, so the missing half was not
frequency — it was that **nothing counted as an event**, and one branch could not report a change at
all.

- **A loadout with no pet left the old pet on the HUD.** `scanPanelPet` returned early on an empty
  slot, so "no pet" was indistinguishable from "did not read one". An empty slot in a located,
  freshly-changed panel is the menu *stating* that nothing is out, and it is now believed.
- **Opening the menu now always produces a fresh read.** The panel fingerprint is dropped when the
  loadout screen changes identity, so reopening `/loadout` — which is how you ask the mod to look
  again — is never answered from a cache. That is the case that matters after an Autopet swap.
- **A swap while the menu stays open is detected by the panel's contents changing**, since the menu
  does not close when you switch. The fingerprint is built from item **names**, not stack identity:
  the server hands out fresh stack objects for slots that did not change, so identity would report a
  swap on every refresh and the pet clear would fire against a panel still filling in.
- [ ] **Switch loadouts with the menu open** and watch the Player HUD and Pet HUD follow.
- [ ] **Switch to a loadout with no pet out** — the Pet HUD should go empty rather than keeping the
      last pet. This is the specific bug being fixed.
- [ ] **Autopet swap, then open `/loadout`** — the HUD should correct itself on opening.
- [ ] Watch for a **flicker**: if the pet blinks empty for a tick when the menu opens, the panel is
      being read before the server has filled it and the fingerprint needs a settle delay.

### 1e. Inventory Search (2.5.5-b-1) — a search box over every menu, with a calculator in it
A Misc module. The storage sheet has had a search since 2.5.3 and it only ever worked there, which
is the wrong way round: the sheet is the one place your items are already laid out for you. The
menus where finding something is genuinely hard have no search at all — a bazaar page, a sack, an
auction browser, somebody's 54-slot trade window.

- **Matches are highlighted, non-matches are not veiled** — the opposite of the storage sheet, on
  purpose. The sheet is a map of everything you own, so dimming the rest keeps its shape; a server
  menu is somebody else's layout you are hunting through, and there the useful thing is one slot
  lighting up.
- **Nothing steals a key.** Ctrl+F belongs to the chat search (Diego), and a container menu already
  spends the inventory key, 1-9 and Q. So the box is focused by **clicking it**, and the keybind on
  the card is **unbound by default** for whoever knows which key is free for them.
- Keys only reach the box while it has focus, and the check runs **before** the slot-lock handling —
  so a hotbar number typed into a focused box is text, not a swap. That is the same trap the storage
  sheet had to be fixed for in 2.5.4-b-3.
- The box sits **under** the menu, not over it: a container's own area is the server's layout, and a
  box across the middle of a bazaar page would cover the thing being searched for.
- The query is dropped when the menu changes, so a search does not follow you into the next one.
- **The calculator** is `util/Calc`, shunting-yard, `+ - * x /` with brackets. `x` multiplies
  because that is what people actually type (`2x2`, `32x64`), and numbers take SkyBlock's own
  shorthand (`1.4m`, `60k`). The sum is drawn **beside** the box as an annotation - the text stays
  what you typed and the search goes on matching it, so `2x2` still finds an item called that.
- Verified with a harness over 30 cases: the `2x2` forms, precedence and brackets, the k/m/b/t
  suffixes, and - the ones that matter most - that plain words like `dragon`, `aspect of the end`
  and `Lvl 100` evaluate to nothing and stay ordinary searches. Malformed input (`2+`, `(2+3`, `5/0`)
  returns nothing rather than throwing.
- [ ] **Click the box in a few different menus** and check it lands under each one - the position
      comes from the menu's own reported size, which not every SkyBlock menu is honest about.
- [ ] **Check it stays out of the storage sheet's way** — the sheet has its own box and takes every
      key while it is up; this one stands down entirely there.
- [ ] Does the highlight colour read well over a rarity backing? Both draw with the background.

### 1d. Loadout swap delays (2.5.5-b-1)
Two waits, on the **Loadout Keybinds** card rather than the Pest Timer one - that card owns the
command and the clicking, so the keybind swaps get them too and there is one place to tune.

- **"Wait before clicking"** (default 150 ms) is new behaviour, not just a knob. It used to click on
  the first tick the menu was open and the preset was found; that is fine when the menu arrives
  complete and is exactly what breaks when SkyBlock is still filling it in, since a click into a
  half-built menu lands on whatever is at that slot number.
- **"Wait before closing"** (default 100 ms) was the hard-coded `CLOSE_DELAY_MS`. Same reasoning as
  before - closing into SkyBlock's rewrite of the menu leaves client and server disagreeing about
  what is open - but the right number depends on ping.
- **"Vary the waits" / "Vary by (±%)"** (off, 15%). Worth being straight about what this is for: it
  stops a fixed wait that happens to land just short of the menu being ready from failing *every*
  swap identically. It does **not** make the sequence look hand-driven - three menu actions a tenth
  of a second apart are machine-timed whatever the numbers are.
- The timeout no longer fires while a click is scheduled. It exists for "the menu never opened", and
  without that guard a long wait plus a slow menu would abandon a swap that was going fine.
- Jitter bounds checked with a standalone harness over 200k rolls at four base/percent combinations:
  the observed range matches `base ± percent` exactly, the mean stays on the base, off returns the
  base untouched, and a zero base can never go negative.
- [ ] **Watch a swap at the defaults** and confirm it still lands, then try a low wait to see the
      half-built-menu failure it is guarding against.

### 1c. Mod version on the custom scoreboard (2.5.5-b-1)
Diego's ask, and it answers a question this session kept running into — he thought he was on
`2.5.4-b-9` while the test instance had moved on. A grey `v<version>` at the very bottom, under the
custom bottom text, **off by default**: it is screen space that says nothing about the game, and it
earns its place while testing a build or reporting a bug.
Read from the loader rather than a constant, and cached: a version typed into the source is one that
can disagree with the jar it is in, which is exactly the confusion the line exists to end.

### 1b. Autopet from chat (2.5.5-b-1) — it existed, and two thirds of it never worked
Diego asked whether pet rules could be read off the chat message. They already were — `SkyblockHud`
has matched Autopet, summon and despawn lines since before 2.5.4. **But the patterns were wrong**,
and checking them against SkyHanni's own `REGEX-TEST` samples proved it rather than guessing:

- **Summoning by hand never matched once.** The pattern required `[Lvl n]` and the real line has no
  level at all — Hypixel writes `You summoned your Golden Dragon!`. So the entire manual-summon path
  has been dead for as long as it has existed. The level is now optional, in case it comes back.
- **A pet skin broke the name, twice over.** A skinned pet is announced as `... Rabbit ✦!` and a
  skin-numbered dragon as `... [122✦] Golden Dragon!`. The old pattern took everything up to the
  `!`, so the name came out as `Rabbit ✦` or `[122✦] Golden Dragon`, matched nothing in the
  seen-pets map, and the HUD dropped the icon — for exactly the pets most worth showing.
- Verified with a standalone harness over all six real Autopet lines, all four summon forms and the
  despawn line: **all pass**, and the old patterns visibly fail the same cases. The harness is in
  the scratchpad, not the repo.
- The `✦` is a literal in the source, like the `⏣` in `SkyblockLocation`. Confirmed it survives the
  build by finding its UTF-8 bytes (`E2 9C A6`) three times in the compiled class — twice for
  Autopet, once for summon.
- [ ] **Summon a pet by hand** and watch the Pet HUD change without opening a menu. This is the path
      that has never once worked.
- [ ] **Let an Autopet rule fire on a pet with a skin** — the icon should appear rather than vanish.
- [ ] **A pet never opened in the pets menu** still has no icon: the message gives a name, not an
      item, so the HUD falls back to text. Worth deciding whether that is good enough or whether the
      pet cache should be seeded from `/pets` once. RenderLib is gone; the mod runs on
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

### 2b. Scoreboard symbols — cause found (2.5.5-b-1), the earlier fixes aimed at the wrong thing
**Diego's screenshot settled it**: the remaining boxes sit on the **pest icon** in the Garden, next
to the `x2`. That is a Hypixel glyph, not a Unicode one — and it is why "der fix funktioniert nur so
halb" was the exactly right description.

All ten of the mod's fonts fell back to `minecraft:include/default`, `include/unifont` and
`include/space`. 2.5.3 chose those deliberately, to **equal vanilla's coverage** — and that is the
bug, stated as the goal. A server resource pack adds its glyphs by overriding
**`minecraft:default`**; the three includes are precisely the part of the chain a pack does not
touch. So the symbols vanilla itself has (⏣ ✦ ❤ ☠) were fixed, and the ones only Hypixel's pack
carries had nothing to fall through to and stayed boxed. Matching vanilla exactly is what guaranteed
the pack's glyphs would be missing.

Every font now falls back to **`minecraft:default`** itself, so it inherits whatever that resolves
to at load time: vanilla's glyphs with no pack, and the pack's additions with one. Vanilla's own
uniform/unifont filtering comes along with it rather than being reimplemented.

- [ ] **Look at the Garden sidebar.** The pest icon beside `x2` is the test case; it is the one that
      has never worked.
- [ ] **If a symbol is still boxed**, the Symbol report is still on the card, and its two columns
      still mean opposite things: **mod=MISSING, vanilla=ok** is ours; **both MISSING** is a glyph
      the client genuinely does not have, which now also means the pack did not supply it.
- [ ] **Watch the mixed look.** Pack glyphs will draw in the pack's own pixel style beside Poppins,
      which is the usual look for a SkyBlock mod but is a visible mix.
- [ ] Worth checking on an instance **without** a server resource pack too, that nothing regressed
      where the old chain was sufficient.

### 2c. Old notes on the scoreboard symbols (2.5.3/2.5.4 attempts)
Diego, on 2.5.4: "der fix für symbole im scoreboard funktioniert nur so halb" — some symbols still
draw as a box. The 2.5.3 fix (vanilla fallbacks in every `assets/diegoaddonsv2/font/*.json`) was
checked against the game's own assets afterwards, and the mod's coverage now **equals vanilla's**:
`include/default` + `include/unifont` is exactly what `minecraft:default` references, unifont's
`unifont_all_no_pua-17.0.01.hex` holds all 114,432 codepoints including every SkyBlock symbol
looked up (⏣ ✦ ❤ ☠ ♲ ⸎ ⚔ 🎣 🔮 …), and nothing in vanilla reaches the PUA at all. So a remaining box
should not be possible from the font definitions alone — which is why 2.5.4 measures instead of
guessing again.

- [ ] **Run the report**: Custom Scoreboard card → **"Symbol report (log)"**, standing on SkyBlock
      with the sidebar visible. It logs every non-ASCII character as
      `U+XXXX '<char>' mod=ok|MISSING vanilla=ok|MISSING`, and the two columns mean opposite things:
      **mod=MISSING, vanilla=ok** is ours — a fallback that did not take, fixable here.
      **both MISSING** is a character the game itself does not have; only the resource pack that
      ships it can draw it, and no font definition can conjure it. The chat line gives the counts.
- [ ] **Cross-check by eye**: switch the module off and look at the vanilla sidebar. A symbol that
      boxes there too was never ours to fix.
- 2.5.4 also added `minecraft:include/space` as a last fallback to all ten definitions — the one
      provider vanilla has and the mod did not (U+0020, already covered by Poppins, and U+200C).
- The probe is `util/GlyphProbe` on top of `mixin/FontGlyphAccessor`, which opens the private
      `Font.getGlyphSource`. A baked missing glyph keeps `SpecialGlyphs.MISSING` as its `GlyphInfo`,
      so the answer is the game's own, not a guess about pixels.

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

### 3b. Beta releases — the switch exists, the ordering did not
Diego's idea (2.5.4): ship betas so he can test without copying jars by hand. **The toggle was
already there** — "Include pre-releases" on the Auto Update card, off by default, and `Updater`
skips drafts always and pre-releases unless it is on. What was missing was the comparison.

`compare` split on every non-digit, so `2.5.5-beta.1` parsed as `2.5.5.1` and outranked the finished
`2.5.5`. Two silent failures fell out of that: a tester on the beta would **never** be offered the
release, and anyone on the release with pre-releases on would be offered the beta as an upgrade
forever. Fixed in 2.5.4 — a pre-release now ranks below the release it leads to, `beta.2` after
`beta.1`, and `rc` after `beta` (the word decides before the number). Checked against nine cases
including the two that were broken.

The scheme to follow, once Diego agrees:
- Beta: tag `vX.Y.Z-beta.N`, **tick "This is a pre-release"** on GitHub, attach
  `diegoaddonsv2-X.Y.Z-beta.N.jar`, and set `mod_version` in `gradle.properties` to match.
- Final: tag `vX.Y.Z` as a normal release. A beta tester is then offered it, because the ordering
  now says so.
- Instances split by the toggle: pre-releases **on** in the test instance, **off** on the daily
  driver. That is per-instance config, so one switch per instance and no jar copying either way.
- [ ] **Never cut a release without asking** (standing rule) — the scheme above is written down, not
      acted on.

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
- [x] **Inventory Buttons** — ported from afranz29's mod in 2.5.5-b-4, see §7.
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

### Garden (2.5.4) — six modules, all written without a Garden to stand in
A new **Garden** category holds them: Pest ESP, Pest Timer, Plot Borders, Sprayonator Timer,
Pest Traps, Jacob's Contest. Four of the six read the tab list, so **the widgets have to be on in
`/widget`** — Pests, Pest Traps and Jacob's Contest. Every one of them says so on the HUD when the
widget is missing rather than showing a zero.

- [ ] **Plot Borders.** The grid is arithmetic, not discovery: 5×5 plots of 96 blocks from -240 to
      240, Barn in the middle, ids spiralling out (`Garden.PLOT_MAP`). Check a border actually lands
      on the plot edge — if the whole grid is off by half a plot, that map or the ±48 offset is why.
      Bands at player height rather than full-height walls, infested plots marked through walls with
      a beam on the nearest one.
- [ ] **Sprayonator Timer.** 30 minutes, started from the chat line `SPRAYONATOR! You sprayed Plot -
      6 with Compost!` and corrected by the tab list's `Spray: Compost (12m)` — which is only ever
      about the plot you are standing on, so every other plot's timer is ours to keep. A **renamed
      plot is skipped entirely** rather than filed under a guess. Check a spray on a far plot still
      counts down, and that walking onto it does not jump the timer.
- [ ] **Pest Traps.** `Pest Traps: 2/3`, `Full Traps: #1, #2`, `No Bait: #3`. Both bad states are
      silent in game, so each is announced once when it starts and re-armed when it clears. Watch for
      a warning repeating.
- [ ] **Jacob's Contest.** Deliberately the widget's own lines, header plus the three crops. The
      crop lines are how the widget's extent is found at all — the tab list arrives here flat with
      the blank separators already dropped, so a line under the header that names one of the ten
      contest crops is part of it and the first that does not ends it. If the crops are missing or a
      foreign line is picked up, that rule is the place to look.
### Feast HUD (2.5.4) — testable right now, it is Autumn
Seasoning towards the next milestone plus the four in-season crops, shown **only while the event is
running**. Diego is on Autumn 19th, so this is the one Garden feature that can be checked today.

- [x] **"Show even when the event is over" was the only way to see the card** — Diego, from a real
      game: with it off the HUD was simply absent, while everything on it was correct with it on. The
      detection keyed on a tab line naming the event, and that line is evidently not there. It is now
      three signals, any of which is enough: **the season on the scoreboard** (the feast runs Early
      through Late Autumn, and the date is written every second of the game — this is the one that
      cannot go missing), the tab line if it does appear, and a **Grand Feast reading from Ted**,
      which is the case the calendar gets wrong since Finnegan's version runs all year.
- [ ] **Open Feast Chef Ted once**, with "Debug scan (log)" on. The seasoning total and the
      milestone ladder are read from that menu and nowhere else — the tab list only says whether the
      event is on. The dump is what the two patterns (`HarvestFeast.PROGRESS` / `TOTAL`) have to be
      tuned against; until then the HUD says "open Feast Chef Ted" rather than showing a zero.
- [ ] **Are the crops in slots 11, 12, 14 and 15?** That is SkyHanni's reading of Ted's menu. All
      four or none are taken — a partial read means the layout is different, and half a season list
      is worse than none.
- [ ] **Does the chat line for a seasoning drop match?** It is a 1-in-2,500 drop that never becomes
      an item, so chat is the only way the total moves between visits to Ted. `HarvestFeast.GAINED`
      is a guess; if the number never climbs while farming, that is why.
- [ ] **Milestone thresholds.** Only the last tier of each ladder is on the wiki (250 normal, 750
      Grand), so the intermediate ones are read from the menu when it spells them out and fall back
      to a guessed ladder otherwise. A wrong "next milestone" with a right seasoning count means the
      fallback is in use.
- [ ] **Stale crops are labelled, not hidden** — they rotate every SkyBlock month (10h20m real), and
      the row says "old - reopen Ted" instead of a countdown rather than presenting last month's
      four as now.
- [ ] **The crops are item icons with a countdown beside them** (SkyHanni's shape, Diego's ask), so
      the element draws its own widget rather than being a text chip. Vanilla items stand in for the
      crops — they are what you break and every client has them; an unrecognised crop still gets a
      slot (wheat seeds) so four icons stay four.
- [ ] **Where the countdown comes from, in order:** Ted's own lore if it states the remaining season
      (`SEASON_LEFT`, a guess — the debug dump will say), otherwise the SkyBlock calendar off the
      scoreboard: a day is 20 real minutes, a month is 31 of them, so the time to the month's end is
      exact arithmetic. **What is assumed is that the crops rotate on that boundary.** If the
      countdown is consistently off by a fixed amount, that assumption is the cause and the lore
      pattern is the fix — not the maths.

### Garden, third pass (2.5.4) — Sacks, auto swap, session tracker
- [ ] **Sacks feed the Visitor Helper.** Open any sack once (title ends in "Sack"); each item's lore
      carries `Stored: 28,183/60.5k`, and that is the whole reading. The tooltip then says
      "18 in sacks, 6 short" under the price. Counts are a **floor** — they only improve when you
      open a sack again — and are keyed per profile, since the other profile's sacks are somebody
      else's. Shortened numbers ("60.5k") are rounded by Hypixel, so this is "roughly enough",
      not an inventory.
- [ ] **Auto swap** on the Pest Timer card, off by default, with **its own "Swap at (seconds
      before)"** slider (default 30). It used to hang off the warning's lead time, which meant
      turning warnings off turned the swap off with them and both had to fire at the same instant.
      Needs the **Loadout Keybinds** module on, since it owns the command and the clicking.
- [ ] **Swapping back watches two signals**, either of which is enough: the spawn chat line (fast,
      but matched by a guessed pattern that a chat filter can also swallow) and the pest count in the
      tab widget going up (Hypixel's own number, about a second slower). Either alone is a way to get
      stuck in pest gear, which is farming fortune quietly bleeding away.
- [ ] **The safety rule that matters:** farming means holding the attack button, and a menu opening
      under a held button takes that as a click on a real slot. The swap therefore waits for both
      mouse buttons to be up, no other screen to be open, no swap already in flight, and the Garden.
      **Test it while holding left-click through a whole warning** — that is the case it exists for.
- [ ] **Farming Session.** Crops, coins, copper, pests, seasoning, with per-hour rates. Measured as
      deltas of Hypixel's own tab counters rather than counted here — a client-side tally misses
      Sprayonator breaks and drifts over an hour. The counter re-bases when it resets or the crop
      changes, so a milestone should never read as a negative harvest. Rates are withheld under a
      minute of session, where a single crop reads as thousands an hour. Reset button on the card.
- [ ] **Check `Counter:` and `Copper:` really are those tab lines** — both are visible in Diego's
      screenshot, but the crop-name line under "Crop Milestones" is the shakiest parse here, and a
      wrong crop name means the coins figure prices the wrong thing.

### Garden, second pass (2.5.4) — Visitor Helper, Bazaar, Loadout keybinds
- [x] **Spray durations are no longer 30 minutes for everything** — 30 / 45 / 60 for the plain,
      Juicy and Salty Sprayonator. The chat line names the *spray*, not the sprayer, so the duration
      comes from the item in hand at the moment the message arrives. Unreadable falls back to 30,
      the one that under-counts: an early timer sends you to a plot that is still sprayed, a late
      one tells you a plot is covered when it is not.
- [ ] **Bazaar prices** (`util/Bazaar.java`) — `api.hypixel.net/v2/skyblock/bazaar`, keyless, no
      player data in the request. Refreshed every 5 min on a daemon thread, only while a feature is
      reading it. Check the log says "Bazaar prices loaded (N products)" once on entering the Garden
      with the helper on. Item ids are the display name upcased (`Enchanted Carrot` →
      `ENCHANTED_CARROT`), which is exact for farming items and a miss - not a wrong price -
      elsewhere.
- [ ] **Visitor Helper.** Menu found by shape, not title: the middle item's lore ends with "Offers
      Accepted:", accept is slot 29, refuse 33. **The lore parsing is the guess** - turn on "Debug
      scan (log)" at the first visitor and tune `Visitors.ITEM` / `COPPER` against what it dumps.
- [ ] **Auto-decline, the part that cannot be taken back.** Three guards are code, not settings:
      never decline a profitable offer, never decline one that cannot be priced, never act on prices
      older than 20 minutes. Test with the mode Off first and read the tooltip numbers for a few
      visitors before letting it click anything.
- [ ] **Is copper valued right?** One copper = Green Thumb I price / 1500, the community's proxy.
      If the numbers look wrong by a constant factor, that is the line to check.
- [ ] **Loadout Keybinds** (Misc). `/loadout` is a **guess** and is a text box for that reason — if
      it is wrong, the key sends a bad command and nothing opens. Tell me the real one and it
      becomes the default. Presets are matched by name, never by slot, since the grid reorders.
- [ ] **configlib gained key fields in list rows** (`ListOption.KeyField`), which is what makes
      "name plus keybind" possible in the shared editor. **This also fixes Command Hotkeys**: its
      key was never settable from the menu, so every hotkey added there sat unbound and did nothing.
      Worth checking both lists: click the key button, press a key, and Escape to unbind.

- [ ] **The Pests widget has to be on** (`/widget`). The cooldown is Hypixel's own number, read from
      the tab list rather than calculated — the sum depends on a reforge, an attribute and a perk,
      and Hypixel moves all three. With the widget off the HUD says "Enable the Pests widget"
      instead of showing a wrong number. Check that line actually appears when it is off.
- [ ] **The tab lines are guesses at strings**, matched on `Pests:` / `Alive: N` / `Cooldown: 1m 58s`
      / `Plots: 4, 12` after colour stripping. If the HUD stays on "Unknown" with the widget on,
      those four prefixes are the first thing to print.
- [ ] **The swap warning.** Slider 0–240s (default 60), because the point is to swap into pest gear
      *before* the cooldown ends — pest gear shortens the cooldown itself, so the lead time depends
      on the gap between your two setups. Title + sound, chat optional, and a second warning at
      expiry. Check it fires once per cooldown and not once per second.
- [ ] **Expiry is read from READY, not from the countdown hitting zero** — the widget jumps straight
      from a number to READY, and a tick-boundary miss would otherwise swallow the warning. Watch
      that the "cooldown is up" warning fires exactly once.
- [ ] **Pest ESP** boxes any name plate carrying a pest name (all 15), Garden only, from the mob's
      own bounding box rather than the fixed drop below the plate — pests range from a mite to a
      field mouse. If a box floats or is the wrong size, the plate had no vehicle and the fallback
      box was used. "Only with a vacuum in hand" matches the item name (vacuum / hooverius / lasso).
- [ ] **Is the plate even there?** If nothing is ever boxed, the pest may carry its name on the mob
      rather than on a separate armour stand — that is the one assumption the whole ESP rests on.
- [x] **Pets were boxed as pests** — Diego, from a real game: his Slug pet was outlined. Six pests
      share their name with a pet (Slug, Mosquito, Rat, Beetle, Moth, Cricket), so matching the name
      alone catches your own pet. Pets carry `[Lvl 189]` on the plate and mobs never do, which is the
      same prefix `SkyblockHud` has read pets by since the pet HUD was written — so no new assumption.
      Deliberately not "skip whatever your active pet is called": that would hide a real Slug pest
      for as long as a Slug is out, and the pest is the thing being looked for. Other players' pets
      are covered by the same check. Ten cases checked, including `[Lv5] Slug 20/20` still counting
      as a pest and "Ratatouille" not.

### Safari (2.5.4-b-5) — SkyBlock 0.27, written the week the island shipped
A **Safari** category for the Critter Safari on Torrhus Canyon: **Critter ESP**, **Sparkling
Critters** and **Floor Drops ESP**. Everything here was written off the wiki, four days after an
island that did not exist when the last version shipped — so more of this is guesswork than usual,
and the guesses are named below rather than buried.

- [x] **Critter ESP boxed nothing** (b-8) — Diego: "fix die critter ESPs". The cause is the
      assumption named in the next item: it matched **only** name plates, and the Galatea critters
      were already proof that assumption is unsafe, since a Cinderbat carries no nametag at all.
      It now also matches the **vanilla entity type**, which covers 28 of the 37 with no plate
      needed. The plate is still preferred where there is one, because it is the only thing that says
      *which* critter a shared type is — a Bat is a Flitter or a Bloodbat, all three parrots are
      parrots. Where the type is shared the filter is applied to the whole candidate set (drawn if
      any of them would be) and the rarity colour is only used when the candidates agree on one.
      Hideyho deliberately has **no** type: it is a skinned player entity, and matching that would
      box every player in the lobby.
- [ ] **Did that fix it?** If critters are still not boxed with "Match by entity type too" on, then
      the entities are not the vanilla types the wiki lists either, and the next step is a debug that
      logs the type of everything nearby rather than another guess.
- [ ] **Is it now boxing scenery?** The risk of the type match is the opposite failure - a decorative
      parrot or an ambient bat getting a box. Pets are excluded by the `[Lvl n]` plate; anything else
      over-boxing means the toggle comes back off.
- [ ] **The island gate got a second reading too** — the tab list's island name *or* the scoreboard
      area naming one of the four biomes ("Icy Biome"), since the whole category is dead if that one
      tab string is wrong.
- [ ] **Everything rests on one thing: do critters carry name plates?** All thirty-seven are matched
      by reading the plate, not by entity type, and that is a deliberate choice — about a third of
      them are not vanilla animals at all (Duplico disguises itself as a block, Gazer only appears
      while you sleep, Hideyho is a skinned player entity), so type matching would cover two thirds
      and silently miss the rest, including most of the rare ones. The wiki's own statement that a
      sparkling critter shows a `SPARKLING` **prefix** is the evidence there is a name to prefix.
      **If nothing is ever boxed, turn on "Debug plates (log)" on the Critter ESP card**: it prints
      every plate it did *not* match, which is the one measurement that settles this.
- [ ] **The thirty-seven names are the wiki's spelling**, in `util/Safari.java`, with a biome and a
      rarity each (9 Cavern / 9 Forest / 10 Haunted / 9 Icy). A name Hypixel spells differently is
      the one failure the loose matching cannot absorb — the debug log is again the way to find it.
- [ ] **One card, not thirty-seven.** A critter is a thing you catch once, so the filters are the
      two that get used: four biome toggles and an "At least <rarity>" floor, plus "Colour by
      rarity" (on by default). Check the rarity colours actually read at distance in a dark cave.
- [ ] **The biome toggles filter by which critter it is, not where you stand** — the four biomes meet
      in the middle of the island, and filtering by location would blink boxes on and off at the
      borders.
- [ ] **Sparkling is its own module and ignores those filters on purpose** — a sparkling Cavernfish
      is still a Rainbow Feather even with every common hidden. 1 in 8,192 (1 in 4,096 with the
      Sparkling Amulet), so **this may take a very long time to see even once**. Worth testing the
      notification path by temporarily matching a common name instead.
- [ ] **The notification fires once per critter, with a re-announce cooldown** (default 60s) rather
      than once ever: a critter can leave view and come back, and once-ever risks the second sighting
      being the silent one. Watch it does not repeat every tick.
- [x] **Floor Drops were looking for the wrong thing** (fixed in b-7). The first cut hunted for a
      tripwire, on the wiki's word that a drop is "String on the ground". **Diego's screenshot
      settles it**: a Floor Drop is an ordinary block with pale bits on its top face and a stream of
      green four-pointed sparkles rising off it. The block is by far the weaker signal — whatever it
      is, the island is presumably covered in it, so boxing every one marks the ground rather than
      the drop. **Particles are the detector now** (`minecraft:happy_villager` by default, a text box
      as usual, 4 in one spot before marking), and the block scan is kept as a fallback, **off by
      default**, with its id defaulted to `minecraft:rooted_dirt` as a better guess than tripwire.
- [x] **It is tripwire after all** (b-8). Diego: "floor drops erkennt man daran, dass da strings in
      dem block stecken". The pale squiggles on the screenshot's top face *are* the string, so the
      wiki's "String on the ground" and the screenshot are the same thing seen twice, and the first
      guess was right before I talked myself out of it on the strength of a picture. The scan is the
      main detector again (**on** by default, `minecraft:tripwire`), particles are the second one.
- [ ] **Is the box on the right block?** A tripwire occupies the air space *above* a solid block and
      renders its strings just off that block's top face, which is why they look embedded in it — so
      the box is drawn one block down from where the scan finds the string. That is the
      **"String sits above the block"** switch; if the boxes come out a block low, turn it off.
- [ ] **The look now matches Diego's screenshot** — the block boxed in the module's style plus its
      **top face painted** in a second colour (magenta by default, box gold). The face is not
      decoration: the string is *on* that face, so it is the surface you have to click. Lifted 0.02
      above the block so it does not fight the block's own face for depth.
- [ ] **"Block I'm looking at → Log"** is still on the card and still worth one press, to confirm the
      block really is `minecraft:tripwire` and not something that merely looks like it.
- [ ] **Is the sparkle really `happy_villager`?** It is the green four-pointed particle in the
      screenshot, which is the villager-happy one. "Debug particles (log)" lists every type arriving
      with counts every 5s if it is not.
- [ ] **Is "Block is below by" right?** The particles come off the top face and drift up, so the
      cluster settles above the block; the slider (default 1.0) is what puts the box back on it. If
      the boxes float a block high, that is the row to move.
- [ ] **The scan is on a timer, not per tick** — a 24-block radius is about eighty thousand block
      reads, so it runs every 2s and again at once when you have moved 8 blocks. **A picked-up drop
      therefore stays boxed for up to 2 seconds.** That is the right way round, but check the scan
      does not stutter the game at radius 64.
- [ ] **"Only on the Foraging islands"** (default on) gates it to Moonglade Marsh, Torrhus Canyon and
      the Critter Safari. Turn it off to test the scan anywhere.
- [ ] **The new "Highlight (no x-ray)" style is on every ESP in the mod**, not just these three: a
      filled mark that terrain hides, which is what makes a busy island readable when forty boxes
      through a hillside is not. It is a **new render path** (`EspRenderTypes.QUADS_DEPTH`, the
      engine's own depth-tested debug-box pipeline, drawn in a second batch) and has never been on
      screen. Two things to check: that it is actually hidden behind terrain, and that it composites
      at all — unlike the see-through types it does not set `ITEM_ENTITY_TARGET`, because the depth
      test has to run against the main framebuffer. **If it draws nothing, that output target is the
      first thing to try.**
- [ ] It is last in the style list on purpose — the indices are what gets saved, so inserting it
      anywhere else would have moved every ESP already set to "Player outline".

### Torrhus ESP (2.5.4-b-8) — one card, nine switches
Diego's ask: the Torrhus Canyon hunting mobs as **one** module in Hunting, with every mob switchable.
One card suits them — unlike the Galatea critters, which are ordinary animals you might want boxed
one at a time in different colours, these are all the same job on the same island, and what changes
between runs is which of them you are still after.

- [ ] **Seven by entity type, two by plate.** Firefox = Fox, Mountain Goat = Goat, Drybark =
      Creaking, Groundhog = **Hoglin**, Honeybuzz = Bee, Pangolin = Armadillo, Blue Jay = Parrot —
      all off the wiki's own table. **Grizzly Bear and Tiki are not vanilla mobs**: the bear is a
      custom level-101 mob (1% chance when breaking a tree) and a Tiki is a totem of three rotating
      heads, so both are matched by name plate only. If either never boxes, its plate is spelled
      something else.
- [ ] **Honeybuzz boxes every bee**, and the row says so. Beeheemoth and Pollendart are also bees and
      nothing on the entity separates them; a plate naming one is the only thing that can. Silently
      missing the Honeybuzz would be the worse failure, so it over-boxes on purpose.
- [x] **Tiki found nothing** (b-9) — Diego, from a real game: "tiki hat kein nametag wenn es unsolved
      is". Confirmed the diagnosis: a Tiki is a sleeping totem of three heads until you turn them all
      to face the same way, and only the mob that wakes out of it carries a name. So the plate was
      the wrong half of the problem entirely — by the time it exists the thing is awake and hitting
      you, which is not when you needed to find it.
      It now marks the **24 documented totem spots** instead (all three Tikis share one set — the
      Cheeky page lists the same coordinates as the Sneaky one), with a range slider and optional
      distance labels. The plate match is kept for the awakened mob.
- [x] **Blue Jay and Pangolin were never plate-dependent** — Diego thought they were the other
      nametag-less ones. They are matched by **entity type** (Parrot, Armadillo) and always were, so
      a missing plate cannot affect them. Only two of the nine were ever plate-only: Tiki, now fixed
      with waypoints, and the Grizzly Bear.
- [x] **Grizzly Bear wears a player model** — Diego, from a real game. That rules out a type match
      outright: the class is the same one every real player in the lobby has. What separates them is
      that a SkyBlock mob wearing a player model is a **fake player** — client-side, version-2 UUID,
      no tab-list entry — and `EntityEsp.isRealPlayer` has known that difference since Player ESP was
      written, so it was reused rather than rediscovered.
- [ ] **With no skin id set it boxes every player-model mob on the island**, NPCs included, and the
      row says so. Over-boxing you can see beats a switch that silently does nothing. The fix is one
      paste: "Debug entities (log)" now prints **the skin texture of every fake player nearby**, and
      dropping that id into "Grizzly skin id" makes it exact. Real players' skins are deliberately
      left out of the log — they are never the mob and it is nobody's business.
- [ ] **Does the bear carry a plate at all?** The plate match on "Grizzly Bear" is still there as the
      second route; if it never fires, the bear is nameless like the Tiki and the skin id is the only
      way to be exact.
- [ ] **"Debug entities (log)" is the thing that ends this.** Every entity type within 32 blocks,
      with counts, plus every name plate in full. **This module has now been wrong twice for the same
      reason** - the wiki says what a mob *is*, not what the client is handed - so one walk around
      Torrhus with it on maps all nine definitively and beats a fourth guess. Plates are listed
      separately because they fix a different half of the problem than the types do.
- [ ] **Are the 24 coordinates the base or a head?** Not stated, so the box runs from y-1 to y+3 -
      deliberately generous enough to contain the totem either way. If they all sit a little high or
      low, that offset is why.
- [ ] **Nothing is struck off as you visit it**, unlike the Hideyho spots: a totem is a fixture that
      respawns, not one hidden thing in one of eleven places, so "already checked" means nothing here.
- [ ] **Tiki still matches the word "Tiki"** on a plate, which also catches Wiki Tiki — a sea
      creature, so only the island gate keeps them apart. Fine on Torrhus, worth remembering if the
      gate is ever turned off.
- [ ] **The island gate is just "Torrhus"**, so it covers the Heights too — Sneaky Tiki is documented
      in both, and one name beats two that have to track wherever Hypixel draws that line.
- [ ] **Shared types across islands are handled by the gate, not by the match**: a Fox is a Firefox
      here and a Foxtrot on the Safari, an Armadillo is a Pangolin here and a Scrappy there. Both
      modules would claim the entity; only one of them is on the right island at a time.

### Safari, second pass (2.5.4-b-6) — four more, off Diego's picks
Diego took Hideyho, the cold warning, floor-drop waypoints and quest tooltips off the list below, and
replaced the sparkling radar with **an ESP for the particles themselves**. All four are still written
blind.

- [ ] **Sparkling: found by particles now, not only by the plate.** A plate is a rendered entity, so
      it does not exist until the mob is loaded and roughly in front of you; a particle arrives as a
      packet from much further out and through terrain. For a 1-in-8,192 spawn that difference *is*
      the feature — the failure being designed against is walking past one.
- [ ] **Which particle it is, is not known**, so "Particle ids" is a **comma-separated text box**
      watching three likely ones at once (`end_rod`, `firework`, `crit`). Two things make a wrong
      guess survivable: several ids at once, and a marker needs a **cluster** — 8 particles in one
      spot by default — so a stray crit from hitting a mob never becomes a waypoint.
      **"Debug particles (log)" is the measurement that ends the guessing**: it logs every particle
      type arriving on the island with counts every 5s. Stand next to a sparkling critter with it on
      and the answer is whichever type suddenly appears.
- [ ] **The two routes must not both shout.** The plate stays quiet while the particle route is
      marking something; if the ids turn out wrong, that is false and the plate becomes the
      announcement again. Watch for a double notification — that would mean the guard is inverted.
- [ ] **Hideyho Finder.** Not an ESP — there is nothing to box, it teleports out of view the moment
      it agrees to play. It draws the **eleven documented hiding spots** and strikes each off as you
      get within 8 blocks, so what is left on screen means "still to check". Driven by three chat
      lines matched on fragments (`no peeking` / `come find me` / `you found me`) rather than whole
      sentences, because the real lines carry a time and a shard count.
      **Check the state does not get stuck on** — if it never clears, the "you found me" fragment is
      the thing to print. Six start positions are behind a toggle, off by default.
- [ ] **Are the eleven spots right?** Wiki coordinates, community-collected. If it is repeatedly
      found somewhere unmarked, that list needs the extra spot.
- [ ] **Cold Warning — and it is not gated to the Safari.** The Icy biome borrowed the Glacite
      Tunnels' mechanic wholesale and Hypixel writes the same sidebar line in both, so this covers
      the Dwarven Mines for free. Read from `Cold: -14❄` (Hypixel writes it **negative**; the sign is
      dropped here). Two tiers — 60 and 85 by default — with different sounds, because the two have
      to be tellable apart without looking. **Check the sidebar line actually reads that way**; the
      pattern is SkyHanni's, which is good evidence but not a measurement.
- [ ] **Cold re-arms on the campfire line** (`reduced your ... Cold`) and on freezing to death, so
      the warning fires once per approach rather than once per tick. Watch that a campfire visit
      actually silences it.
- [ ] **Floor Drop waypoints — 113 known Safari spots**, drawn as a quieter second layer under the
      live block scan. **These are preset spots, not drops**: the wiki says a drop has "a chance to
      spawn in preset locations", so a marker means "something can be here", never "something is
      here". A spot the scan has already found is skipped, so the two layers never draw on top of
      each other. If they are systematically off by a block, the wiki's numbers are somebody's F3
      readout and the ±2 tolerance in the overlap check is where to widen.
- [ ] **Safari Item Tooltips.** Eleven items, and the point is the ones that gate a critter — a
      Shining Coin describes itself as a shiny coin, not as the only way a Gimmiegold appears.
      Matched on the display name by `contains`; a name that stops matching costs a missing line,
      not a wrong one. Plain foods (bamboo, lily pads) are behind a toggle, off by default, because
      a Safari note on every stack of bamboo you ever pick up is noise.

### Safari — features not built, worth considering
Written down rather than built, so the list survives the session. Roughly in the order they would
pay off:

1. **Critterdex tracker / "still to catch" filter.** The single most useful thing on the island: box
   only what you have *not* caught. Needs the Critterdex menu scanned once (the same shape as the
   Storage Overlay's scan) and the caught set cached per profile. Everything else here is smaller.
2. **Capsule counter on the HUD.** Capsules are the run's limiting resource and regular hunting tools
   get sent to the Stash, so "how many left" is the number that decides whether to keep going.
3. **Safari Essence session tracker** — essence, shards and Hunting XP per hour, on the shape
   `FarmingSession` already has. Ties into whether a run is worth finishing.
4. **The seven bells.** Waypoints for the ones found, and a list of which are missing; the reward is
   an attribute and an achievement, and they are one-time, so a checklist is the whole feature.
5. **Biome waypoints from the centre** — the four biome entrances named at distance, which is
   SkyHanni's `namesInCenter`. Cheap, and the middle of the island is genuinely disorienting.
6. **Birdfeeder helper** — three of the Forest critters (Bluebird, Parakeet, Macaw) come from
   feeding a specific bait, and Macaw is legendary. A reminder of which bait is loaded. Partly
   covered by the item tooltips now; the missing half is knowing what is currently in the feeder.
7. **Campfire waypoints in the Icy biome** — the Cold warning says "go to a campfire" and does not
   say where one is. The natural follow-up once the warning has been seen working.

**Declined:** *FarmingSession* — Diego's call, leave it. *Sparkling radar* — replaced by the particle
ESP above, which solves the same problem (finding one before it is in view) without a HUD compass.

### Hunting (2.5.4-b-4) — a new category, ten ESPs, none of them seen
A **Hunting** category holds the ten ESPs from Diego's screenshot. Nine are ordinary boxes around a
kind of mob; the tenth finds something that cannot be seen at all. All ten default **on-island only**
(a toggle per card), because eight of them box plain vanilla animals — a dolphin is a dolphin, and
with the gate off this lights up the whole of Backwater Bayou while you are fishing.

- [ ] **Is the island name right?** The gate matches the tab list's `Area:` line *or* the
      scoreboard's `⏣` area against `Galatea` / `Moonglade Marsh`, and `Crimson Isle` /
      `Blazing Volcano`. Both readings because the tab gives the island and the scoreboard gives the
      sub-area. **If nothing is ever boxed on Galatea, those two strings are the first thing to
      print** — turn the module's "Only on Galatea" off and see whether boxes appear.
- [ ] **The critters are matched by entity type, not by name plate**, which is the opposite of every
      other ESP in the mod: they are real vanilla animals wearing a SkyBlock name, so Puffer =
      pufferfish (Spike), Turtle = Shellwise, Dolphin = Joydive, Axolotl = Coralot, Frog = Mossybit
      **and** Birries (tadpole, on the same card since it is the same animal younger), Panda =
      Bambuleaf and Mochibear. **Feesh is "a fish that is not one of those"** — cod and salmon today,
      and deliberately open-ended so the next fish critter is covered without a code change. If it
      boxes something it should not, that rule is why.
- [ ] **Cinderbats are just bats.** No plate, no distinguishing anything — the wiki says they carry
      nothing above their heads and are found by their fire particles. So the island gate is the
      *only* thing keeping this off dungeon secret bats, and turning it off means two ESPs on the
      same bat in two colours. Check they show hanging from cave ceilings under the Blazing Volcano.
- [ ] **Matcho is the one plated mob** here, boxed off `contains("matcho")` on the plate, which
      should read something like `[Lv100] Matcho 750k/750k`. It only spawns after the volcano
      erupts, so this cannot be checked on demand.
- [ ] **Invisibug — the whole feature rests on one assumption.** It has no model, no plate and no
      hit box: it is a marker armour stand that Hypixel sprays crit particles around. So every crit
      particle packet is checked, and the nearest *plain* armour stand within 5 blocks — no custom
      name, no equipment, so not a name plate or a prop — is taken to be the bug and remembered by
      entity id. **If nothing is ever boxed while standing in a cloud of crit particles, there is no
      marker stand there and the whole approach is wrong**, not a threshold to tune.
- [ ] **A bug is only found after it throws a particle**, so expect a moment on approach where it is
      not boxed yet. That is by design; a bug that stays unboxed for many seconds is not.
- [ ] **The box geometry is a guess** and is two sliders for that reason: "Box size" (0.8) and "Box
      height offset" (0.4), built around the marker's position because a marker has no size to
      inherit. If the box sits beside the particles rather than on them, move those two rather than
      anything in the code.
- [ ] **The tracer** is off by default and has its own colour — a line from your eye to each bug.
      Worth turning on once to confirm the bugs are where the boxes say they are.
- [ ] **Particles are read off the packet, not the renderer** (`ClientPacketListenerMixin`,
      `handleParticleEvent` at RETURN rather than HEAD — a packet handler is entered on the network
      thread first and vanilla throws there to reschedule, so only a return is on the client thread).
      Crit particles arrive by the hundred from anyone hitting anything, so the type check and the
      already-known check come before any search of the world; watch for a frame-rate cost anyway.
- [ ] **Not implemented from the screenshot:** only the ESP block was. The Hunting / Mining / Slayer
      rows under it (Disable Huntaxe ability, Fusion Delay, Endstone Sell Delay, Mining Ability
      Alert, Auto Blaze 3) are a different kind of feature and were not asked for.
- [ ] **Pets are excluded by the same rule the Garden uses.** A Dolphin, a Turtle, an Axolotl and a
      Bat are all real SkyBlock pets, so four of these ten would box whatever is following you
      around. A mob's plate rides the mob, so the passengers are checked for the `[Lvl n]` prefix
      that pet plates carry and mob plates never do — `Pests.isPetPlate`, now shared rather than
      copied. **Stand next to someone with a Dolphin pet and check it stays unboxed.**
- [ ] **Squid critters have no card** — Azure, Verdant and Lumisquid are glow squids and are not in
      the screenshot, so they were left out. One more module if they turn out to be wanted.

### Item Rarity in the pets menu (2.5.4-b-3) — written blind
- [ ] **Open `/pets` and check every pet is tinted its own rarity**, and that the buttons along the
      bottom row (sort, filter, autopet, back, close) are *not*. Toggle "Pets menu" on the Item
      Rarity card. The menu is found by its title **starting with** "pets", which covers the paged
      "Pets (1/2)"; if nothing is coloured at all, that string is the first thing to print.
- [ ] **The rarity read changed for every item, not only pets.** It used to take the bottom-most
      non-blank lore line's colour, which in a menu is a "Click to summon!" line rather than the
      rarity — so the whole pets menu would have come out yellow. It now walks the lore bottom-up
      for a line **containing a rarity word** (COMMON … DIVINE, word-boundary matched so
      "LEGENDARY DUNGEON HELMET" and the recombobulated form still count) and falls back to the old
      bottom-line behaviour when none is found. Watch for an item that used to be coloured right and
      now is not — that would mean its rarity word is spelled something this list does not have.

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
- [ ] **Items could be thrown out of a slot you could not see** (2.5.4-b-3). The sheet only claimed
      the keys its search box wanted, and everything else fell through to the menu underneath —
      whose key handling works off `hoveredSlot`, assigned in `extractContents`, the one method
      `StorageOverlayMixin` cancels. So that field stayed frozen at whatever the cursor was over in
      the frame before the sheet came up, and **Q threw that item**, a hotbar number swapped it, all
      at a slot nothing on screen was pointing at. The same leak made "e" close the menu mid-word,
      since the inventory key is checked on the key press before the character is typed.
      The sheet now takes **every** key while it is up: Escape closes, the inventory key closes once
      the search box is not focused, and nothing reaches the menu. Screenshots and fullscreen are
      unaffected — `KeyboardHandler` handles those before the screen is asked (checked in the 26.1.2
      bytecode). Clicking outside the panel while carrying something is refused with a toast now too,
      the same way navigating to another page already was.
      **To check:** pick an item up onto the cursor, press Q, press 1-9, then click outside the
      panel — nothing should move and nothing should close. Then type a word with an "e" in it.
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
- [ ] **Only enabled elements are editable** (2.5.4, configlib) — Diego, twice: "du kannst nur die
      elemente bearbeiten, die du auch enabled hast. Die anderen sollen da gar nicht erst editbar
      sein". 2.5.1 had answered the first telling by *labelling* the disabled ones "(hidden)" in red,
      which was not what was being asked. They are now left out of the drawing **and** the hit test,
      so a hidden element cannot take a click from the one drawn under it. A "Hidden: Off / Shown"
      button in the toolbar brings them back for the one case that needs it — placing an element
      before switching it on — and right-clicking an element off now drops the selection with it.
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

- **The updater trusted GitHub's ordering, and GitHub does not order how we assumed** (2.5.4-b-11) —
  found while publishing b-10, by looking at the live feed rather than assuming it. With b-9 and b-10
  both published, `/releases` came back **b-9, b-10, b-8, b-6, b-2, b-1, 2.5.3, …** — neither newest
  first nor alphabetical, and I could not work out the rule from five samples. `fetchLatest` returned
  the **first** entry it accepted, so it would have picked b-9.
  The way that fails is the bad way: a client on b-9 asks, is handed b-9, compares it with itself and
  is told it is up to date — **forever**, with the newer build simply not existing as far as it is
  concerned. No error, nothing in the log, exactly the quiet failure §4 warns about.
  It now walks every candidate and keeps the **highest version by `compare`**, which is correct under
  any ordering — the point being that the order was never ours to rely on. `per_page` raised from 10
  to 30 for the same reason: with the highest-wins rule, a longer list can only help.
  Verified against the live feed by replaying the comparison over the real tag list: the old rule
  picks `2.5.4-b-9`, the new one picks `2.5.4-b-10`, and every older tag ranks below b-9 as it should.
  - [ ] **Everyone on b-9 or b-10 has to update by hand once.** Both were built before this fix, so
        their own updater still picks b-9 off the feed and reports "up to date". After one manual jar
        swap to b-11 they are fine permanently. Worth remembering before assuming a future beta is
        not being offered.

- **We crashed somebody else's game over a chat constant** (2.5.4-b-10) — a friend of Diego's, on
  b-9 alongside SkyHanni and Skysoft: `InjectionError: Critical injection failure: Constant modifier
  method skysoftVisibleLineLimit(I)I ... failed injection check, (0/1) succeeded`, with
  `@ModifyConstant conflict. Skipping skysoft ... already redirected by diegoaddonsv2` earlier in the
  log. **Ours was the mod holding the constant**, and Skysoft's failure to get it was fatal.
  `@ModifyConstant` is a redirect, and a redirect *owns* the instruction it lands on: the first mod
  to claim vanilla's 100-entry chat cap wins, every other mod's is skipped, and Mixin turns a failed
  *required* injection into a hard crash. Both mods raise the same cap for the same reason, so the
  collision was inevitable the moment they were installed together — nothing was wrong with either
  feature.
  Now `@ModifyExpressionValue` (MixinExtras, already bundled with the loader — no new dependency).
  It does not replace the instruction; it takes the value after it is produced and returns a new one,
  so nothing is claimed and any number of mods can stack on the same constant. The knock-on is worth
  having: with our unlimited history **off** we now return whatever we were handed, which is the
  other mod's number rather than vanilla's, so the two features compose instead of one silently
  undoing the other.
  Checked before shipping: all three target methods (`addMessageToDisplayQueue`,
  `addMessageToQueue`, `addRecentChat`) hold **exactly one** `100` in the 26.1.2 bytecode, so there
  is no ordinal ambiguity and each injection is 1/1. An audit of the whole mixin package found this
  was the **only** exclusive injector in the mod — no other `@ModifyConstant`, `@Redirect` or
  `@Overwrite` anywhere — so no second mod can be crashed the same way.
  - [ ] **Unproven until his friend launches it.** The mechanism and the constants are verified; that
        the two mods now coexist is not, because it cannot be reproduced without Skysoft installed.

- **Blackjack deadlocked with nobody to move** (2.5.4) — Diego, from a real game: "hatte eben ein
  issue dass keiner am Zug war". Two faults, and the second is why it could never recover.
  **The turn was guessed rather than stated.** A hit was sent as a bare "h", and the receiver worked
  out whether that card had finished the sender by adding up *its own copy* of their hand. That hand
  is only known through our own copy of the deck, so a single card of disagreement meant one side
  decided the other had busted and took the turn, while the other was still playing. Their next line
  then arrived at a client that thought it was its own turn — and the guard there dropped it as out
  of turn, silently. Both sides ended up waiting for the other, with no move that could ever arrive.
  Whose turn it is now is therefore **read from the message**: the player whose turn it just was says
  whether that ends it. The hand is still tracked for the display, so a disagreement there is now
  cosmetic rather than fatal.
  **A lost line was unrecoverable.** Chat is not a reliable transport — a filter, a rate limit or a
  client that was not listening yet swallows a whisper and a one-message-per-move game has no way to
  notice. Every line now carries a number, a line already applied is ignored, and that is what makes
  saying it again safe: after fifteen seconds of silence on someone else's turn the board says so and
  offers **Send again**, which either lands or was already there. This applies to all four games, not
  just blackjack.
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
