# DiegoAddons V2

A client-side Fabric mod for **Hypixel SkyBlock** on **Minecraft 26.1.2**. 60 features across seven
categories, a settings menu that is searchable rather than a wall of switches, a HUD you place from
one screen, and dungeon and mining tooling that draws in the world.

Client-side only. Nothing here needs a server-side counterpart, and nothing is sent anywhere except
the two places noted under [Network](#network).

## Install

1. Install **Fabric Loader 0.19.3+** for **Minecraft 26.1.2**, and **Fabric API**.
2. Download `diegoaddonsv2-<version>.jar` from the [latest release][releases] and drop it in `mods/`.
3. Launch, and press `\` (backslash) to open the settings. Rebind under *Options › Controls*.

Requires **Java 25**, which is what MC 26.x runs on.

There is an **Auto Update** module on the *Client* page that watches this repository's releases. It
is off by default, and its three modes are separate decisions: notify only, download to
`diegoaddons-updates/` for you to move by hand, or download and install. See
[Updating itself](#updating-itself).

[releases]: https://github.com/DiegoLikesPizza/diegoaddonsv2/releases/latest

## What is in it

**Dungeons** — Dungeon Map, Puzzle Solvers (Creeper Beams, Boulder, Ice Fill, Water Board, Quiz),
Secret Chime, Bat / Door & Key / Miniboss / Starred Mob ESP, Leap Overlay, Auto Requeue, Auto Close
Chests, Mimic and Prince messages, Show Hidden Mobs.

**Mining** — Crystal Hollows Map, Chest Solver, Grotto Finder, Structure Finder, Mining Routes,
Mining Ability Reminder.

**Slayer** — Voidgloom Slayer, Boss Highlight, Miniboss ESP. **Fishing** — Rare Sea Creature Alert.

**HUD** — Player HUD (armour, your character, and your SkyBlock equipment), Inventory HUD, Pet HUD,
Custom Scoreboard, Music Display, Performance HUD, Real-Time Clock.

**Render** — Custom ESP, Player ESP, Fullbright, CustomF5, Animations, Armor Hider, Force Nametag,
Hide Effects, Skin Changer, Party Finder, Borderless Fullscreen, Title Screen.

**Misc** — Chat (compacting, filters, search), Replace Words, Better Ignore List, Command Hotkeys,
Party Commands, Auto GFS, Auto Sprint, Slot Lock, Item Rarity, Ability Cooldown, Etherwarp Helper,
Old Master Stars, Hydration Reminder, Announce SB Kick, Storage Overlay, Inventory Buttons,
No Cursor Reset, Minigames, Auto Update.

Every module's own settings live on its card. ESP modules all share a style and a colour; HUD
elements all share four appearance rows, plus a per-element override.

## The settings menu

Press `\`. Categories down the left, one card per module, and the switch is **on** the card — you
turn something on without opening it. Clicking a card opens its settings in a drawer beside it.
The search box at the top matches modules by name and description.

- **HUD editor** — drag every element where you want it. Elements that are switched off are drawn in
  red and labelled *(hidden)*, so you can place something before turning it on. Positions are a
  screen fraction plus an anchor, so an element pinned to an edge stays pinned at any window size.
- **Client page** — five themes (Galaxy, Midnight, Mint, Crimson, Light), an optional custom accent
  colour that the HUD, the menu and the toasts all follow live, smooth corners, and Auto Update.
- **List editors** — blocked players, replaced words, command hotkeys, Auto GFS items and mining
  routes each get the same add / reorder / delete screen.
- Both scroll bars can be dragged, or you can use the wheel.

Commands: `/da` for everything (`/da help`, `/da hud`, `/da update`, `/da esp`, `/da route`,
`/da words`, `/da hotkeys`, `/da blocked`).

## Files

Everything the mod owns is under `config/diegoaddons/`:

| Path | What it is |
| --- | --- |
| `config.json` | every setting, HUD placement and saved list |
| `sounds/` | your own `.mp3` / `.ogg` / `.wav` / `.aiff` / `.au` files, offered to the sound pickers |
| `skins/` | PNGs the Skin Changer reads, by name |
| `smtc.ps1` | the Windows media bridge the Music Display uses (rewritten each launch) |

Older versions kept the config loose in `config/`. Those files are moved in on the first start of
2.5.2 or later; nothing needs doing by hand and nothing is deleted.

## Updating itself

Auto Update asks GitHub for this repository's newest release, checks it, and can carry it all the
way in. What it will not do is surprise you: the default is off, and *Notify only* is a separate
mode from *Download* and *Download & install*.

A downloaded jar is staged in `diegoaddons-updates/` — outside `mods/`, because two jars with the
same mod id in there is a crash rather than a choice — and it is verified before anything else
happens: it must open as a zip, its `fabric.mod.json` must name this mod and a **newer** version,
and it must have come from a GitHub host. The install itself waits for the game to exit, since the
running jar is locked on Windows; the old jar becomes `diegoaddonsv2-previous.jar.bak` (Fabric only
scans `.jar`, so it is inert, and it is the way back). If any step fails you are still on the old
version, which is the right way for an updater to fail.

## Network

Three requests, all only when you have asked for them:

- **Auto Update** talks to `api.github.com` and `github.com` for the release list and the jar.
- **Music Display**, with *Album cover* switched on, sends the track title to the iTunes search API
  for cover art. That setting is off by default for exactly that reason.
- **Inventory Buttons**, once switched on, fetches Hypixel's public SkyBlock item list from
  `api.hypixel.net` so the icon picker can offer the game's own heads. Once per session, no key,
  and nothing about you goes with it.

Nothing else leaves your machine. The mod reads Hypixel's own menus, scoreboard and chat, and
everything it learns from them stays in your config.

## Build

configlib — the settings GUI, the HUD manager and the custom title screen — is consumed as a
**composite build**, so a clone needs `diegos-config-lib` checked out **beside** this repository.
It is not published yet, so building from a fresh clone is not possible for anyone but me at the
moment:

```
parent/
├── diegoaddonsv2/
└── diegos-config-lib/
```

Then, with **JDK 25** (MC 26.x ships de-obfuscated, so Loom needs no `mappings` line):

```bash
JAVA_HOME=/path/to/jdk-25 ./gradlew build
```

Output: `build/libs/diegoaddonsv2-<version>.jar`, with configlib and JLayer bundled inside it.

## Licensing and credits

The mod is **MIT**. Four things in the jar are not ours:

- **Poppins** (Indian Type Foundry & contributors), the mod's typeface, under the
  **SIL Open Font License 1.1** — free to use, embed and redistribute. The TTFs are at
  `assets/diegoaddonsv2/font/poppins_*.ttf`.
- **JLayer** (JavaZOOM), **LGPL 2.1**, bundled unmodified as a jar-in-jar. It decodes MP3, which
  neither Minecraft nor Java can: the game's sound engine reads OGG Vorbis out of resource packs and
  nothing else, so a file you drop into `sounds/` would otherwise be unplayable.
- **Inventory Buttons** is a port of
  [Inventory-Buttons](https://github.com/afranz29/Inventory-Buttons) (© 2026 Panda/afranz29), itself
  a port of the feature from [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates)
  (© Moulberry and contributors). Both are **LGPLv3**, so the ported files and the button textures
  stay LGPLv3 and carry that notice — the rest of the mod is unaffected. The files are
  `util/InvButtons`, `util/HypixelSkulls`, `gui/InvButtonsOverlay`, `gui/InvButtonEditor` and
  `assets/diegoaddonsv2/textures/invbuttons/`.
- **Puzzle Solvers** takes its quiz answer table and the Three Weirdos line lists from
  [Odin](https://github.com/odtheking/Odin) (BSD-3-Clause, © odtheking and contributors), whose
  licence permits reuse with attribution. The solver code itself is written for this mod.
