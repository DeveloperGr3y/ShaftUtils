# Ideas

Not built yet. Roughly in the order we'd tackle them.

## Organ Donor search area
Once the probe logs show how the ding behaves (how often it plays, whether it stops the moment you leave 20 blocks,
what happens with two corpses in range), use it to narrow things down:
- Clear spawn spots within 20 blocks of you when it's been quiet long enough.
- Keep only spots within 20 blocks of you when it dings.
- Where the ding starts and stops as you walk, you're exactly 20 blocks from a corpse; a few of those points give an
  estimate of where it is.
- Never use the sound's position (it may be the corpse's exact spot, which would be seeing through walls).

## Mineshaft routes (user-provided)
Load an ordered waypoint route automatically when you enter a shaft, from routes people add themselves. No routes
ship with the mod.
- Config: one route per shaft type/variant (e.g. `TOPA_1`), pasted or imported from a file.
- Use a common ordered-waypoint format so routes from other sources work as-is, e.g. the widely used
  `[{"x":..,"y":..,"z":..,"r":..,"g":..,"b":..,"options":{"name":"1"}}]` list.
- Show the next point (and the line to it), advance when you reach it.

## Other
- Share a found corpse's location to party chat with a button.
- Mute the Organ Donor ding once every corpse is found.
