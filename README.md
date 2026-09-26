# ShaftUtils

Personal Glacite Mineshaft helpers for Hypixel SkyBlock (Fabric, Minecraft 26.1.x / 26.2). Not published.

## Corpse finder
- Entering a shaft puts a waypoint on every known corpse spawn spot for that shaft type and variant.
- A spot clears once you've had a clear look at it (or stood next to it). If a corpse is there, it becomes a corpse
  waypoint with its type and whether you have the key.
- Corpses are only marked once you can see them; nothing is shown through walls.
- When you've found as many corpses as the tab list says the shaft has, the remaining spots clear.
- A status panel shows the shaft code, the corpses from the tab list, spots left, and the Organ Donor ding.

## Organ Donor
The talisman's ding pitch gives your distance to the corpse it's following: distance ≈ 20 × √(2 − pitch)
(checked against 159 dings: about half a block out on average). ShaftUtils uses that to:
- show the distance and a direction arrow in the status panel;
- mark the spawn spot the readings point to as **likely corpse**;
- clear spots within 17 blocks when it's silent (no unlooted corpse within 20);
- where no known spot fits, estimate the position from readings taken at a few places (like GPS).

## Routes
Your own ordered mining routes, loaded automatically when you enter a shaft. None ship with the mod.
- One file per shaft code in `config/shaftutils/routes/` (`TOPA_1.json`, `RUBY_C.json`, ...). `CRYSTAL.json` covers
  any crystal shaft without its own file.
- Formats: the common ordered-waypoint list `[{"x":..,"y":..,"z":..,"r":..,"g":..,"b":..,"options":{"name":"1"}}]`,
  plain `[{"x":..,"y":..,"z":..}]` / `[[x,y,z]]`, or text with one `x y z` (or `x,y,z`) per line.
- Easiest: copy a route, stand in the shaft and run `/shaftutils route import` (or `import TOPA_1` from anywhere).
- Shows the current point as an outlined block with its number and distance, a line from your crosshair to it, and
  the next couple of points dimmer. Moves on when you're within 3 blocks (configurable).

## Debug / data gathering
- **Probe Logging** writes dings, corpse sightings and shaft info to `config/shaftutils/probe/<date>.jsonl`.
- **Record New Spots** saves corpses seen away from a known spot to `config/shaftutils/learned_spawns.json`.
- `/shaftutils export` merges known + learned spots into `config/shaftutils/corpse_spawns.export.json`, which can
  replace `src/main/resources/assets/shaftutils/corpse_spawns.json`.

## Commands
| Command | |
|---|---|
| `/shaftutils` | Settings |
| `/shaftutils status` | Shaft code, tab corpses, spot counts |
| `/shaftutils addspot` | Record where you're standing as a corpse spot |
| `/shaftutils export` | Export known + learned spots |
| `/shaftutils probe` | Where the probe logs are |
| `/shaftutils route` | Route status and list |
| `/shaftutils route import [code]` | Save the route on your clipboard for this shaft (or a named one) |
| `/shaftutils route next` / `back` / `restart` | Step through the route |
| `/shaftutils route reload` / `delete [code]` / `folder` | Manage route files |

## Data
The starting spawn spots are copied from meowdding's `mineshaft_corpses.json` (used by SkyOcean). That data isn't
openly licensed, so it's fine for personal use but needs their permission before this is shared.

## Building
`./gradlew build` (JDK 25). Jars end up in `build/libs/`.
