# DiegoAddons V2 — work list

`[ ]` open · `[~]` in progress · `[x]` done. Batches are the order we work in; each one ends with a
build, a deploy and a run in game before the next starts.

---

## Batch 1 — bugs that are just wrong

- [x] **CustomF5** — camera clip does nothing
- [x] **Fullbright** — does nothing
- [x] **Performance HUD** — renamed; the ping row is gone (see the note below)
- [x] **Music Display** — drop the "Centered" toggle (does nothing on the custom layout)
- [x] **Puzzle Solvers** — Blaze guessing options removed ("show whole order" already existed)
- [x] **Armor Hider** — stops hiding armour on mobs that use a player model

## Batch 2 — bugs that need real work

- [x] **Dungeon Map** — stray lines across rooms that span several tiles
- [x] **Etherwarp Helper** — zoom gone; the sound replaces the teleport's own; the highlight is a
      setting of its own
- [x] **Slot Locking** — a locked hotbar slot cannot be dropped with the drop key while the
      inventory is closed either
- [x] **Pet Display** — the pet icon is centred by padding now, not by asking the layout

## Batch 3 — ESP and item drawing (shared groundwork first)

- [x] **Setting groundwork** — `ColorSetting` (single / gradient / rainbow) with a mode pill, live
      swatches and RGB channel sliders in the ClickGUI
- [x] **All ESP modules** — a shared `EspModule` base carries style + colour; Starred Mob, Bat,
      Dungeon Miniboss, Player, Custom, Slayer Boss and Slayer Miniboss are on it
- [ ] **Door & Key ESP / Voidgloom beacon + nukekebi** — left on their semantic colours (a wither
      door is black, a blood key is red); they should get the style setting without the colour one
- [x] **Item Rarity** — display: outline, filled, circle

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
  1 ms. **Decided: the row is gone** - better no number than a confident wrong one.
- **Auto GFS custom items** - whether "custom item" means any item id, or SkyBlock items by name.
