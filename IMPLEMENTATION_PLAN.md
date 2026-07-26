# DiegoAddons V2 — Implementation Plan (remaining TODO features)

This plan turns the open items in `TODO.md` into concrete, codebase-shaped work. It is grounded in
the existing infrastructure (module system, `WorldRender`, `ScreenEvents` hooks, `DungeonState` /
`SkyblockLocation`) and references **NoammAddons** (cloned in scratchpad, MC 26.1.2) for any solver
logic worth porting rather than reinventing.

> Standing project rules to honour on **every** feature (from memory):
> - Settings live only in our own ClickGUI, never Minecraft's settings screen.
> - On every module add/update/remove: post an **embed** to Diego's Discord webhook (English).
> - Mod + all dev logs (commits, webhook) are **English only**.
> - After each change: `./gradlew build -x test` (JDK 25), then copy the jar into the Prism
>   **DiegoAddonsV2 Test** instance — but never overwrite the jar while `javaw` (Minecraft) runs.

---

## 0. How a feature is wired (the recipe every item below follows)

1. **Logic** → a class in `util/` (world scanning, solving, state). Static `tick(mc)` / `onMessage`.
2. **Module** → a class in `module/modules/` extending `Module` (world/tick feature) or `HudModule`
   (draws a chip). Holds `public static X INSTANCE;`, its `BooleanSetting`/`NumberSetting`s, and
   forwards `onClientTick` to the util.
3. **Register** in `ModuleManager.init()` via `register(new XModule(), false)`.
4. **Dispatch**, if it needs more than `onClientTick`:
   - Chat: add a `static onMessage(String plain)` and call it from the `ClientReceiveMessageEvents.GAME`
     block in `ModuleManager` (recolour needs `MODIFY_GAME`, already wired for the quiz).
   - World render: call `WorldRender.box/thickBox/line/text/blockBox` from the tick; the shared
     `flip()` already promotes it. Gate on `DungeonState.inDungeons()` / `SkyblockLocation`.
   - Container GUI: use the existing `ScreenEvents.AFTER_INIT` block — add an `afterBackground`
     (highlight behind items) or `afterExtract` (on top) callback, and
     `ScreenMouseEvents.allowMouseClick` to swallow clicks. Slot geometry from
     `AbstractContainerScreenAccessor` (`diego$leftPos/topPos`) + `screen.getMenu().slots`.
5. **Webhook + build + deploy.**

**Sending chat/commands:** `mc.player.connection.sendCommand("pc <msg>")` (party chat) — mirror
`PartyCommands`. **Sounds:** `mc.player.playSound(SoundEvents.X.value(), vol, pitch)` — mirror
`EtherwarpModule`.

---

## 1. Shared infrastructure to build first (unblocks several features)

### 1a. New categories — **required** — ✅ DONE
`module/Category.java`: add `FORAGING("Foraging")`, `FISHING("Fishing")`, `SLAYER("Slayer")`.
Enum order = menu order; put them after `MINING`, before `MISC`. Empty categories are auto-hidden
by `ModuleManager.categories()`, so adding them early is safe. **Effort: XS.**
Added after `MINING` / before `MISC`; confirmed hidden until a module uses them.

### 1b. `CycleSetting` (enum/multi-choice option) — **needed by 3 features** — ✅ DONE
Built `module/CycleSetting.java` (index stored in `numbers`, `get()/label()/cycle()/reset()`, clamped)
and wired a `cycleChip` + `CycleSetting` branch into `ClickGuiScreen` at both the draw and click sites.
No module uses it yet, so nothing is visible until one does.

There are only Boolean/Number/Keybind/Action settings today; several features need a small
"pick one of N" (Etherwarp sound choice, Trophy Fish HUD mode). Add:
- `module/CycleSetting.java` — stores an `int index` in `ModuleConfig.numbers` (reuse the existing
  map so no config-schema change), `String[] options`, `get()/label()/cycle()`.
- Wire one `else if (s instanceof CycleSetting cs)` branch into `ClickGuiScreen` at the two dispatch
  sites (draw row ~L384, click ~L574) — draw the label + current option, click cycles.

**Effort: S.** Do this before Etherwarp-sound and Trophy-Fish.

### 1c. `SlayerState` util — **needed by all Slayer features** — ✅ DONE
Built `util/SlayerState.java` (sidebar → `Type` enum + `tier`; world → own boss via the
"Spawned by: &lt;name&gt;" stand). Exposes `inSlayer()`, `activeType()`, `tier()`, `bossAlive()`,
`bossEntity()`. Ticked + reset alongside `DungeonState` in `ModuleManager`. Needs in-game verification
of the sidebar quest-line wording and the boss-locate heuristic once a Slayer feature uses it.

Mirror `DungeonState`: read the sidebar to know the active slayer type/tier and whether a boss is
alive, and find the player's own boss armour-stand (nametag contains the owner's name + "Spawned by").
Expose `activeType()`, `bossEntity()`, `inSlayer()`. **Effort: M.** Reference: `DungeonListener` /
slayer detection in NoammAddons.

### 1d. `EspDraw` convenience layer — **shared by Slayer beacon/nukekebi + miniboss ESPs** — ✅ DONE
Built `util/EspDraw.java`: `highlight` (thickBox through walls), `tracer` (camera→point line, nudged
off the near plane), `timerLabel` (WorldRender.text wrapper), and `arrow2d` — a 2D HUD off-screen
arrow using a yaw/pitch bearing projection (no view-matrix handedness), queued per tick and drawn from
the HUD pass. Wired `flip()` (after WorldRender.flip), `renderHud()` (after ItemRarity.renderHotbar),
and `clear()` (on disconnect) in `ModuleManager`. **Milestone 1 (infra 1a–1d) complete.** The 2D arrow
placement wants an in-game sanity check once a Slayer feature uses it.
The Slayer items all want the same primitives. Add small helpers on top of `WorldRender`:
- `tracer(Vec3 target, int argb)` — line from the camera/crosshair to a point (camera pos is already
  available; draw `line(camera, target)`).
- `arrow2d(...)` — a **2D HUD** arrow pointing toward an off-screen target (new; draw in the HUD
  layer like `ItemRarity.renderHotbar`, project the target to screen edge).
- `timerLabel(Vec3 pos, String text)` — thin wrapper over `WorldRender.text`.
`highlight` is just `thickBox`. **Effort: M** (the 2D arrow is the only real work).

---

## 2. TODO corrections (already done — update the file)

- **Wither/Blood Key ESP** → already implemented: `DoorKeyEspModule` + `util/DoorKeyEsp` (doors *and*
  keys, per-toggle, key tracer). Mark `[x]`. (Verify key tracer still works after the map rewrite.)
- **Force Nametag** → `[x]` (done). Item Rarity, Auto Sprint, Announce Kick, Fullbright, Auto Requeue,
  Dungeon Map, Mimic/Prince messages → already `[x]`.

---

## 3. Dungeon features

### Leap Overlay — **M** — ✅ DONE
`util/LeapOverlay.java` + `LeapOverlayModule`: in the Spirit Leap menu, overlays each head with the
teammate's class tag (HEA/MAG/BER/ARC/TAN) in class colour. Class read from tab list, matched by name,
degrades to nothing if unreadable. Hooked in `afterExtract`. Needs tab-format verification in-game.
When the "Spirit Leap" chest opens, overlay each teammate's **class + name in class colour** on their
head slot for fast leaping.
- New: `util/LeapOverlay.java` (map slots→teammates by matching head-slot display name to tab-list
  dungeon teammates + their class/colour), `module/modules/LeapOverlayModule.java`.
- Hook: `ScreenEvents.afterExtract` when `screen.getMenu()` title ≈ "Spirit Leap"; draw big centred
  name via `UiRender.text`. Slot geometry via the accessor.
- Reference: `dungeon/LeapMenu.kt`. Gotcha: title/format varies — match loosely.

### Livid Solver (+ hide wrong Livids) — **M–L**
On F5/M5, box the **correct** Livid (its nametag colour matches the "real" Livid; NoammAddons matches
the boss-room wool/name colour). Option to hide the decoys.
- New: `util/LividSolver.java` (find correct livid by colour match → `WorldRender.thickBox` green),
  `module/modules/LividSolverModule.java` (+ `hideWrong` BooleanSetting).
- "Hide wrong": add a **render mixin** on the living-entity renderer (see existing `AvatarRendererMixin`
  pattern) that cancels rendering for the non-correct Livids when the option is on.
- Reference: `dungeon/solvers/LividSolver.kt`. Gate: `DungeonState.floor()` in {F5,M5} + Livid room.

### F7 P3 Terminal Solvers — **L**
The four/five chest terminals (Select all same colour, Click in order 1→N, Correct password/Rubix,
Starts with letter, Select the X). Read the menu items, compute the click set, **highlight** slots.
- New: `util/TerminalSolver.java` (detect type from title + items → ordered list of slots to click),
  `module/modules/TerminalSolverModule.java` (per-terminal toggles; auto-click **off by default** and
  clearly labelled — input automation).
- Hook: `afterBackground` highlight + optional `allowMouseClick` gating for click-order enforcement.
- Reference: `floor7/terminals/TerminalSolver.kt`, `TerminalType.kt`, `TerminalClick.kt`. Big but
  self-contained; ship highlight-only first, auto-click as a follow-up.

### F7 P3 Device Solvers — **L**
Simon Says (light sequence → next button), Arrow Align (rotations), I4/Ice-fill device.
- New: `util/DeviceSolvers.java` (world block/redstone reads → `WorldRender.blockBox` next step),
  `module/modules/DeviceSolverModule.java`.
- Reference: `floor7/devices/SimonSays.kt`, `ArrowAlign.kt`, `AutoI4.kt`/`I4Helper.kt`.

### F7 Melody Terminal Message (+ party chat option) — **M**
Show Melody's-Harp progress; option to also post progress to party chat.
- New: `util/MelodyProgress.java` (read the harp GUI columns → % done), `module/modules/MelodyMessageModule.java`
  (+ `toParty` BooleanSetting → `sendCommand("pc Melody: NN%")`, throttled).
- Reference: `floor7/MelodyDisplay.kt`, `MelodyAlert.kt`.

### Miniboss ESP (dungeon) — **S–M** — ✅ DONE
`DungeonMinibossEspModule` (name whitelist) + a branch in `EntityEsp` (pink-red), gated to dungeons.

### Auto close secret chests — **S** — ✅ DONE
`AutoCloseChestModule`: in dungeons, when a "Chest"-titled screen's own slots are all empty, closes it.
Off by default. Needs in-game check that dungeon secret chests use the plain "Chest" title.
After a dungeon secret chest is opened and looted, auto-close the screen.
- New: `module/modules/AutoCloseChestModule.java`: on tick, if a container screen is open whose title
  is a dungeon chest and its contents are gone/claimed, `mc.player.closeContainer()`.
- Gate strictly to dungeons; input side-effect, keep off by default.

### Bat ESP — **S** — ✅ DONE
Box dungeon secret bats. `BatEspModule` + a `Bat`-type branch in `EntityEsp` (cyan), gated to dungeons.

---

## 4. Render / Misc features

### Experimentation Solvers — **L**
Chronomatron (remember flashing sequence), Superpairs (memory match), Ultrasequencer (click ascending).
Track slot reveals across ticks; highlight the next correct slot.
- New: `util/ExperimentSolvers.java` (per-game state machine keyed off the menu title + item changes),
  `module/modules/ExperimentSolverModule.java` (per-game toggles).
- Hook: `afterBackground` highlight. Reference: SkyHanni experiments logic (behaviour), our own state.

### Hoppity ESP — **M (seasonal, low priority)**
Box Hoppity's chocolate-rabbit NPCs / event NPCs. Only live during the Easter event — defer unless in
season. Add to `EntityEsp` name-filter pass.

### Etherwarp custom sound — **S** (needs `CycleSetting`) — ✅ DONE (Pling/Bell/Harp/Anvil/Orb)
Replace the fixed `NOTE_BLOCK_PLING` with a `CycleSetting` sound choice (Pling / Bell / Note Harp /
Anvil land / Experience orb …). `EtherwarpModule` maps the index → `SoundEvents`. Reference: existing
`EtherwarpModule.onClientTick`.

### Slot locking — **L** — ✅ DONE (locking; bindings deferred)
`util/SlotLocks.java` + `SlotLockModule` + `AddonConfig.lockedSlots`. Keybind toggles the hovered
slot's lock; red overlay; blocks clicks/shift-clicks (`allowMouseClick`), hotbar-swap/drop/offhand
keys (`allowKeyPress` with `KeyMapping.matches(KeyEvent)`). No mixin needed - all client-side input
denial. **Deferred follow-ups:** the drag-to-bind shift-click swap, and world Q-drop of the held item
outside a menu.

### Gyro Helper — **M**
Aim helper for the gyrokinetic wand (smooth/lock look). Reference: `general/GyroHelper.kt`. Needs a
tick hook adjusting `mc.player` rotation — keep it a helper, off by default.

### Ability Cooldown — **M** — ✅ DONE
`util/AbilityCooldown.java` + `AbilityCooldownModule`: cooldown seconds parsed from lore, timer started
on use-key press while holding the item (keyed by display name, not NBT), drawn on the hotbar slot.
Best-effort (can't know if the ability actually fired). Right-click abilities covered.
Show an item's ability cooldown as a number on the hotbar item.
- New: `util/AbilityCooldown.java` (parse ability + cooldown seconds from lore; start a timer when the
  ability is used — detect via right-click / the "used" chat message), render like
  `ItemRarity.renderHotbar` (HUD layer, number bottom-right of the slot).
- `module/modules/AbilityCooldownModule.java`.

### Terminator AutoClicker — **M**
Auto right/left-click while holding a Terminator and conditions hold (e.g. targeting). Input
automation — gate to held item + SkyBlock, off by default, expose CPS `NumberSetting`.
- New: `util/TerminatorClicker.java`, `module/modules/TerminatorAutoClickerModule.java`.

### Player ESP — **M–L** — ✅ DONE
`PlayerEspModule` (Render) + a `Player` branch in `EntityEsp` (blue). NPC filter = version-4 UUID +
tab-list entry (same as the Dungeon Map markers). Works everywhere. Filter may need iteration.

### Custom Scoreboard — **M–L** — ✅ DONE
`util/CustomScoreboard.java` + `CustomScoreboardModule` + `ScoreboardSidebarMixin` (cancels
`Gui.extractScoreboardSidebar`). Themed rounded panel on the right, red numbers dropped, line colours
preserved, highest score on top. Background toggle. Needs in-game look for alignment / no double-draw.

### Hide Vanilla Inventory — **M**
Options for recipe book button, off-hand slot, and the 2×2 craft grid in the survival inventory.
- **Mixin** on `InventoryScreen` (hide recipe-book toggle/component) and on the inventory render to skip
  the craft grid + off-hand slot. Toggles on a `HideVanillaInvModule`. Reference existing
  `EffectsInInventoryMixin` for the mixin style.

### Custom Hub Map — **L (low priority)**
A drawn hub map like the dungeon map. Reference: `visual/HubMap.kt`. Large; schedule last.

---

## 5. Foraging (new category)

### Beacon Solver — **M (needs research)**
Intent unclear from the TODO alone (Galatea foraging "beacon" puzzle). **Action:** confirm the exact
puzzle with the user / find the reference before building. Likely a world-block sequence solver →
`WorldRender.blockBox` next step. Flag as needs-spec.

---

## 6. Fishing (new category)

Shared: a `FishingState` util (are we fishing / holding a rod, current region from `SkyblockLocation`).

### Rare Alert — **S** — ✅ DONE
`util/FishingAlerts.java` + `FishingRareAlertModule` (first Fishing-category module): matches rare
spawn chat fragments → title + sound. Starter list (Sea Emperor, Water Hydra, Yeti, Frozen Steve,
Thunder, Reindrake, Great White, Lord Jawbus); needs wording verification + expansion in-game.

### Placeable reminder (totem / umbrella) — **S–M**
Remind (title/chat) to place the fishing totem/umbrella when fishing in the relevant area and it isn't
placed. Needs the placeable's world presence check near the player.

### Hotspot Finder — **M**
Locate hotspot markers (the swirling-particle area) and waypoint them. Particle listen or nearby
armour-stand/area scan → `WorldRender.blockBox` + `text`.

### Hotspot Highlight — **S–M**
Highlight the hotspot radius in-world once found (shares the finder's data). `WorldRender` circle/box.

### Trophy Fish HUD — **L** (needs `CycleSetting`)
HUD listing trophy fish by region with a mode cycle: **show all / show unfinished / show available
(current region)**.
- New: `util/TrophyFish.java` (static table of trophy fish per region + tiers; track caught from the
  catch chat line / `/trophyfish` data), `module/modules/TrophyFishHudModule.java` (`HudModule`,
  `CycleSetting` mode). Reference: SkyHanni trophy-fish data for the table.

---

## 7. Slayer (new category) — build after `SlayerState` (1c) + `EspDraw` (1d)

### Boss Highlight — **M** — ✅ DONE
Box the player's own slayer boss (from `SlayerState.bossEntity()`), colour by tier. `thickBox`.
Built `module/modules/SlayerBossHighlightModule.java` (box always; `Colour by tier`, `Tracer to boss`,
`Off-screen arrow` toggles → exercises all of `EspDraw`). Registered; first module in the Slayer
category so the tab now shows. Webhook sent. **Needs in-game verification** of `SlayerState` (sidebar
wording + boss-locate) and the 2D arrow placement.

### Miniboss ESP — **S–M** — ✅ DONE
Box slayer minibosses by nametag name (per slayer type). Name-filter pass like `EntityEsp`.
Built `util/SlayerMinibossEsp.java` + `module/modules/SlayerMinibossEspModule.java`: matches unique
plate names (contains), finds the real mob under each plate and boxes its actual hitbox (yellow).
Decoupled from the quest parse on purpose. Zombie/Spider/Wolf/Enderman minis in; **Blaze + Vampire
miniboss names still TODO** (need in-game verification). Registered + webhook sent.

### Beacon — **M** — ✅ DONE (in Voidgloom Slayer module)
### Nukekebi — **M** — ✅ DONE (in Voidgloom Slayer module)
Both shipped together as `module/modules/VoidgloomSlayerModule.java` + `util/VoidgloomSlayer.java`.
Beacon: `Blocks.BEACON` scan near player (throttled, thin Y band) → highlight / tracer / arrow /
~5s countdown. Nukekebi: nametag "Nukekebi" match → highlight / tracer / arrow. Gated on
`SlayerState.activeType() == VOIDGLOOM`; reset on disconnect + onDisable. Registered + webhook sent.
**Needs in-game verification:** exact beacon lifetime (assumed 5s) and that Nukekebi heads carry a
"Nukekebi" nametag (else fall back to skull-texture matching).

---

## 8. Recommended sequencing (milestones)

1. **Infra:** 1a categories → 1b CycleSetting → 1c SlayerState → 1d EspDraw. Correct TODO (§2).
2. **Quick wins / high value:** Bat ESP, Dungeon Miniboss ESP, Etherwarp sound, Fishing Rare Alert,
   Auto close secret chests, Ability Cooldown.
3. **Slayer pack:** Boss Highlight → Miniboss ESP → Beacon → Nukekebi (reuse EspDraw).
4. **Dungeon solvers:** Livid → Melody message → Terminal Solvers (highlight-only) → Device Solvers →
   Leap Overlay.
5. **Misc heavier:** Custom Scoreboard, Hide Vanilla Inventory, Slot locking, Player ESP, Gyro Helper,
   Terminator AutoClicker.
6. **Fishing pack:** FishingState → Placeable reminder → Hotspot Finder/Highlight → Trophy Fish HUD.
7. **Experimentation Solvers**, then low-priority/seasonal: Custom Hub Map, Hoppity ESP,
   Foraging Beacon (after spec).

Each milestone is independently shippable + testable. Test with the user between packs (their pattern),
and send one webhook embed per module shipped.

## 9. Watch-outs

- **Input automation** (auto-click terminals, auto-close chests, Terminator clicker): off by default,
  clearly labelled, gated to the exact context. These are the riskiest for bans/misfires.
- **Mixins** needed for: Livid hide-wrong, Slot locking (hard block), Custom Scoreboard (cancel vanilla),
  Hide Vanilla Inventory. Follow the existing `mixin/` + `diegoaddonsv2.mixins.json` pattern; official
  namespace for any access widening.
- **NoammAddons is GPL-ish/attribution** — the user handles licensing/attribution; keep ports clean and
  note the source in commits.
- **Seasonal** (Hoppity) can't be tested off-event — defer.
- **Foraging Beacon Solver** is under-specified — get the spec first.
</content>
</invoke>
