# ShaftUtils

Personal Glacite Mineshaft helpers for Hypixel SkyBlock (Fabric, Minecraft 26.1.x / 26.2). Not published.

## Corpse finder
- Entering a shaft puts a waypoint on every known corpse spawn spot for that shaft type and variant.
- A spot clears once you've had a clear look at it (or stood next to it). If a corpse is there, it becomes a corpse
  waypoint with its type and whether you have the key.
- Corpses are only marked once you can see them; nothing is shown through walls.
- When you've found as many corpses as the tab list says the shaft has, the remaining spots clear.
- A status panel shows the shaft code, the corpses from the tab list, spots left, and the Organ Donor ding.

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

## Data
The starting spawn spots are copied from meowdding's `mineshaft_corpses.json` (used by SkyOcean). That data isn't
openly licensed, so it's fine for personal use but needs their permission before this is shared.

## Building
`./gradlew build` (JDK 25). Jars end up in `build/libs/`.
