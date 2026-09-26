# Ideas

Not built yet. Roughly in the order we'd tackle them.

## Organ Donor follow-ups
- Confirm from the position logs whether the ding stays on the first corpse in range until looted (seen once).
- Draw a line/arrow in the world towards the likely spot.

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
