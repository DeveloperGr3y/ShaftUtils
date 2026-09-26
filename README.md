# ShaftUtils

Glacite Mineshaft helpers for Hypixel SkyBlock: find Frozen Corpses faster and follow mining routes.
Fabric, Minecraft 26.1.x and 26.2.

## Installing
1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5+ and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Drop the jar for your Minecraft version (`+mc26.1` or `+mc26.2`) in your `mods` folder. Needs Java 25.
3. In game, `/shaftutils` opens the settings and `/shaftutils gui` moves the status panel.

## Corpse finder
- Entering a shaft puts a waypoint on every spot a corpse can spawn in that shaft type.
- A spot clears once you've looked at it (on screen, with a clear line to it). If a corpse is there it becomes a
  corpse waypoint with its type, distance and whether you have the key it needs.
- Corpses are only marked once you can see them. Nothing is shown through walls.
- When you've found as many corpses as the tab list says the shaft has, the rest of the spots clear.

### With the Organ Donor talisman
The talisman's ding gets higher as you get closer: its pitch gives your distance to the corpse it's following
(distance ≈ 20 × √(2 − pitch), about half a block out on average). ShaftUtils turns that into:
- the distance and a direction arrow in the status panel;
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

## Commands
| Command | |
|---|---|
| `/shaftutils` | Settings |
| `/shaftutils gui` | Move and resize the status panel |
| `/shaftutils status` | Shaft code, tab corpses, spot counts |
| `/shaftutils route` | Route status and list |
| `/shaftutils route import [code]` | Save the route on your clipboard for this shaft (or a named one) |
| `/shaftutils route next` / `back` / `restart` | Step through the route |
| `/shaftutils route reload` / `delete [code]` / `folder` | Manage your route files |
| `/shaftutils addspot` / `export` / `probe` | Debug: record a spot, export spots, find the probe logs |

## Heads up
I wrote this with a lot of help from AI (Claude). I use it myself, but nobody else has reviewed it yet, so read the
source if that matters to you.

It only reads chat, the tab list, the scoreboard, sounds and what's on screen. It never mines, clicks or moves for you,
and it doesn't show anything through walls.

## Credits
- Built-in routes by the **Mining Cult** community.
- Starting corpse spawn spots from meowdding's data (used by SkyOcean). **Pending their permission to redistribute;
  remove `assets/shaftutils/corpse_spawns.json` before publishing if they say no.**

## Debug / data gathering
Off by default (Debug tab):
- **Probe Logging** writes dings, positions, corpse sightings and shaft info to `config/shaftutils/probe/<date>.jsonl`.
- **Record New Spots** saves corpses seen away from a known spot to `config/shaftutils/learned_spawns.json`.
- `/shaftutils export` merges known + learned spots into `config/shaftutils/corpse_spawns.export.json`.

## Building
`./gradlew build` (JDK 25). Jars end up in `build/libs/`.
