<div align="center">

<img src="docs/images/icon.png" alt="ShaftUtils icon" width="96">

# ShaftUtils

Glacite Mineshaft helpers for Hypixel SkyBlock: find Frozen Corpses faster and follow mining routes.

[![Downloads](https://img.shields.io/github/downloads/DeveloperGr3y/ShaftUtils/total?logo=github&label=downloads)](https://github.com/DeveloperGr3y/ShaftUtils/releases)
[![Latest release](https://img.shields.io/github/v/release/DeveloperGr3y/ShaftUtils?label=release)](https://github.com/DeveloperGr3y/ShaftUtils/releases/latest)
[![Minecraft](https://img.shields.io/badge/minecraft-26.1.x%20%7C%2026.2-green)](#installing)
[![Licence: CC0](https://img.shields.io/badge/licence-CC0-lightgrey)](LICENSE)

</div>

## Installing
1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5+ and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the jar for your Minecraft version (`+mc26.1` or `+mc26.2`) from
   [Releases](https://github.com/DeveloperGr3y/ShaftUtils/releases) and drop it in your `mods` folder. Needs Java 25.
3. In game, `/shaftutils` opens the settings and `/shaftutils gui` moves the panels.

## Corpse finder
- Entering a shaft puts a waypoint on every known spot a corpse can spawn in that shaft type. It ships with the spots
  we've seen corpses at ourselves (26 so far, across 9 shaft types, more each release), and learns more as you play:
  every corpse you see that isn't known yet is saved as a spot for that shaft type (Debug > Record New Spots).
- A spot clears once you've looked at it (on screen, with a clear line to it). If a corpse is there it becomes a
  corpse waypoint with its type, distance and whether you have the key it needs.
- Corpses are only marked once you can see them. Nothing is shown through walls.
- When you've found as many corpses as the tab list says the shaft has, the rest of the spots clear.
- The **Corpse Helper** panel lists every corpse in the shaft (from the tab list): looted, found (with distance) or
  not found yet, and whether you have its key. It can hide itself once they're all looted.

### Entry title
Entering a shaft shows its type and what corpses it has, e.g. **Umber Shaft** / *3 Lapis · 1 Umber*. Crystal shafts
read *Jasper Crystal*, *Peridot Crystal* and so on. Jasper and Vanguard shafts, the good ones, get a bigger title and a
fanfare.

### With the Organ Donor talisman
The talisman's ding gets higher as you get closer: its pitch gives your distance to the corpse it's following
(distance ≈ 20 × √(2 − pitch), about half a block out on average; see [docs/RESEARCH.md](docs/RESEARCH.md)). ShaftUtils turns that into:
- the distance and a direction arrow in the Corpse Helper;
- a green **likely corpse** marker on the spawn spot the dings point to;
- clearing spots when it's been silent where you're standing (no unlooted corpse within 20 blocks);
- an estimated position when no known spot fits.

## Routes
Ordered mining routes, loaded automatically when you enter a shaft.
- **Built in:** routes from the **Mining Cult** community. Turn them off with Routes > Use Built-in Routes.
- **Your own:** copy a route and run `/shaftutils route import` in the shaft (or `/shaftutils route import TOPA_1`
  from anywhere). Yours replace the built-in one for that shaft. Files live in `config/shaftutils/routes/`, one per
  shaft code (`TOPA_1.json`, `RUBY_C.json`, ...); `CRYSTAL.json` covers crystal shafts without their own file.
- Formats: the common `[{"x":..,"y":..,"z":..,"r":..,"g":..,"b":..,"options":{"name":"1"}}]` list, plain
  `[{"x":..,"y":..,"z":..}]` / `[[x,y,z]]`, or text with one `x y z` (or `x,y,z`) per line.
- Shows the current point as an outlined block with its number and distance, a line from your crosshair to it, and the
  next couple of points dimmer. Moves on when you're within 3 blocks; you can also set next/previous keys.

## Shaft profit
The **Shaft Profit** panel shows what the shaft has made so far as a table: the most valuable items with counts and
values, corpse loot, keys, profit, profit per hour and time. With your inventory open, click **[Insta-buy]** to switch
prices and **[Shaft]** to switch to the whole session (every shaft since you started the game).

When you leave a shaft, ShaftUtils also posts what it made, with [Copy] [Party] [Guild] buttons (they only fill your
chat box):
```
[ShaftUtils] Profit from Jade shaft: 30.2m (12m 40s, 143m/h)
 Corpses: 12.4m (3 opened: 14.1m loot − 1.7m keys)
 Mining: 17.8m (Flawed Jade Gemstone ×212, ...)
```
- Mining: items added to your sacks (from the `[Sacks]` messages) and your inventory while in the shaft.
- Corpses: the corpse loot summary, minus the price of the keys used to open them.
- Prices from the bazaar (insta-buy or insta-sell; keys at instant buy or buy order). Items not on the bazaar are
  listed as not priced.

## Commands
| Command | |
|---|---|
| `/shaftutils` | Settings |
| `/shaftutils testtitles [shaft]` | Preview the entry titles with your settings (all, or e.g. `opal crystal`) |
| `/shaftutils resetprofit` | Reset the profit panel's session total |
| `/shaftutils gui` | Move and resize the Corpse Helper and Shaft Profit panels |
| `/shaftutils status` | Shaft code, tab corpses, spot counts |
| `/shaftutils route` | Route status and list |
| `/shaftutils route import [code]` | Save the route on your clipboard for this shaft (or a named one) |
| `/shaftutils route next` / `back` / `restart` | Step through the route |
| `/shaftutils route reload` / `delete [code]` / `folder` | Manage your route files |
| `/shaftutils addspot` / `export` | Record a corpse spot where you stand / export spots to share |

## Heads up
I wrote this with a lot of help from AI (Claude). I use it myself, but nobody else has reviewed it yet, so read the
source if that matters to you.

It only reads chat, the tab list, the scoreboard, sounds and what's on screen. It never mines, clicks or moves for you,
and it doesn't show anything through walls.

## Credits
- Built-in routes by the **Mining Cult** community, used with their permission (not covered by the CC0 licence).
- Licence: CC0 for ShaftUtils' own code. Third-party parts are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Sharing corpse spots
In the Debug tab:
- **Record New Spots** (on by default) saves corpses seen away from a known spot to `config/shaftutils/learned_spawns.json`.
- `/shaftutils export` merges known + learned spots into `config/shaftutils/corpse_spawns.export.json`.

## Building
`./gradlew build` (JDK 25). Jars end up in `build/libs/`.

`./gradlew build -Ppersonal` also bundles anything in a local, git-ignored `personal/` folder (e.g. extra spawn data in
`assets/shaftutils/corpse_spawns_personal.json`) and names the jars `...-personal.jar`.

To add spots for a release: run `/shaftutils export` and copy `config/shaftutils/corpse_spawns.export.json` over
`src/main/resources/assets/shaftutils/corpse_spawns.json` (it only includes our own data, never the personal file).

## Contributing
Open a PR against `main` with a [Conventional Commit](https://www.conventionalcommits.org/) title, e.g.
`feat: add a route for TOPA_2` or `fix: corpse spot cleared too early`. `feat` bumps the minor version, `fix` the
patch. PRs are squash-merged, and [release-please](https://github.com/googleapis/release-please) turns them into a
release PR with the changelog; merging that publishes the release with the jars attached.
