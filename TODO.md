# DiegoAddons V2 — work list

`[ ]` open · `[~]` in progress · `[x]` done. Batches are the order we work in; each one ends with a
build, a deploy and a run in game before the next starts.

---

## Batch 1 — bugs that are just wrong

- [x] **CustomF5** — camera clip does nothing
- [x] **Fullbright** — does nothing
- [~] **Performance HUD** — renamed; ping needs a decision (see below)
- [x] **Music Display** — drop the "Centered" toggle (does nothing on the custom layout)
- [x] **Puzzle Solvers** — Blaze guessing options removed ("show whole order" already existed)
- [x] **Armor Hider** — stops hiding armour on mobs that use a player model

## Batch 2 — bugs that need real work

- [ ] **Dungeon Map** — stray lines across rooms that span several tiles
- [ ] **Etherwarp Helper** — remove the zoom settings; the sound setting *replaces* the teleport
      sound instead of firing when ready; highlight the aimed block green when the warp would work
      and red when it would not, only while sneaking with an Etherwarp item
- [ ] **Slot Locking** — a locked hotbar slot cannot be dropped with the drop key while the
      inventory is closed either
- [ ] **Pet Display** — the pet icon still is not centred over its name

## Batch 3 — ESP and item drawing (shared groundwork first)

- [ ] **Setting groundwork** — a colour setting (single / gradient / rainbow) and its ClickGUI row;
      everything below depends on it
- [ ] **All ESP modules** — style: outline, box, 2D square · colour: single, gradient, rainbow
- [ ] **Item Rarity** — display: outline, filled, circle

## Batch 4 — chat, party and small additions

- [ ] **Chat** — rename Chat Search to "Chat" and fold in Unlimited Chat History and Compact Chat.
      Settings: unlimited history · compact chat · compact window (s, slider) · Ctrl+F to search ·
      Ctrl+click to copy
- [ ] **Party Commands** — !8ball, !coinflip / !cf, and more in the spirit of Odin's
- [ ] **Party Finder** — show a party's missing classes on hover; drop the per-class toggles
- [ ] **Force Nametag** — option to show your own nametag in F5
- [ ] **Hide Effects** — also hide the effect icons in the top-right corner

## Batch 5 — the bigger features

- [ ] **Custom Scoreboard** — customisation: hide the server id, hide the Hypixel URL, custom text
      top and bottom, show bank balance, and the rest of that family
- [ ] **Show Hidden Mobs** — reveal invisible dungeon mobs (shadow assassins, fels)
- [ ] **Secret Chime** — a browser for the game's sounds to pick from
- [ ] **Auto GFS** — custom items, sliders for all three defaults, likely its own GUI

## Batch 6 — the last three screens on the old drawing

- [ ] **Custom Ignore List** → RenderLib
- [ ] **Command Hotkeys** → RenderLib
- [ ] **Replace Words** → RenderLib

---

### Open questions

- **Ping**: the client has no way to time a round trip of its own - the play protocol has no
  client-initiated ping, so the only number available is the one the server hands out
  (`PlayerInfo.setLatency`). On Hypixel that is measured behind the proxy, which is why it reads
  1 ms. Options: leave it, drop the row, or read Hypixel's own `/ping` on a timer. Waiting on a call.
- **Auto GFS custom items** - whether "custom item" means any item id, or SkyBlock items by name.
