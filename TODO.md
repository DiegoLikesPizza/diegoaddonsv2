# DiegoAddons V2 — Feature TODO

Status of the planned feature list. `[x]` = implemented & registered, `[~]` = partial, `[ ]` = not started.

New categories still needed in `Category` enum: **Foraging**, **Fishing**, **Slayer**.

---

## Dungeon
- [x] Auto Requeue
- [ ] Wither/Blood Key ESP
- [ ] Leap Overlay
- [ ] Livid Solver — option to hide the wrong Livids
- [ ] F7 P3 Terminal Solvers
- [ ] F7 P3 Device Solvers
- [ ] F7 P3 Melody Terminal Message — option to also send progress to party chat
- [ ] Miniboss ESP
- [ ] Auto close secret chests
- [ ] Bat ESP

## Render
- [x] Fullbright
- [x] Force Nametag — show tags also when invisible/sneaking (already covered)

## Misc
- [ ] Experimentation Solvers
- [ ] Hoppity ESP
- [~] Etherwarp Helper — add a **custom sound** setting (currently has a ready-sound toggle + pitch slider, but no sound choice)
- [ ] Slot locking — keybind: press on a slot to lock it; hold on a hotbar slot and drag to an inventory slot to bind a quick shift-click swap (both directions)
- [ ] Gyro Helper
- [ ] Ability Cooldown — show an item's ability cooldown as a number on the hotbar item
- [x] Item Rarity — draw rarity behind the item (Skytils 1.8.9 / NoammAddons style)
- [ ] Terminator AutoClicker
- [ ] Player ESP — tricky: NPCs are often player models
- [ ] Custom Scoreboard
- [ ] Hide Vanilla Inventory — options for recipe book, off-hand, and the 2x2 craft grid
- [x] Auto Sprint
- [x] Announce SB Kick — announce in party chat when kicked to the Hypixel lobby
- [ ] Custom Hub Map

## Foraging (new category)
- [ ] Beacon Solver

## Fishing (new category)
- [ ] Rare Alert
- [ ] Placeable reminder (totem, umbrella)
- [ ] Hotspot Finder
- [ ] Hotspot Highlight
- [ ] Trophy Fish HUD — option: show all / show unfinished / show available (based on the region the player is in)

## Slayer (new category)
- [ ] Boss Highlight
- [ ] Miniboss ESP
- [ ] Beacon — options: highlight, tracer, arrow, show timer (above the beacon)
- [ ] Nukekebi — options: highlight, tracer, arrow

---

### Notes
- `ChestSolverModule` is the **Crystal Hollows treasure-chest** highlighter (Mining) — NOT the dungeon "Auto close secret chests".
- `StarredMobEspModule` boxes starred mobs generally — NOT the dedicated dungeon "Miniboss ESP" / "Bat ESP" or the Slayer ESPs.
