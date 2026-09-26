# Research notes

What we measured in game while building ShaftUtils (a temporary logging build, since removed). Recorded during the
Mining Fiesta: 36 shafts across 16 layouts, 1,143 Organ Donor dings, 92 corpse sightings, 52 corpses looted.

## Mineshafts
- The shaft code is on the scoreboard's first line, after the date and server: `09/26/26 m183BW RUBY_2`.
  Codes are `TYPE_VARIANT`: `TOPA_1`, `RUBY_2`, `PERI_C` (crystal), `LITT_L` (Littlefoot's Den).
- The tab list's Frozen Corpses widget has one line per corpse: `Lapis: NOT LOOTED` / `Umber: LOOTED`.
  Shafts had 1-4 corpses (1: 2 shafts, 2: 10, 3: 20, 4: 4). The line order can change between updates.
- Corpses are armour stands wearing the corpse's helmet (custom data `id`): `LAPIS_ARMOR_HELMET`,
  `ARMOR_OF_YOG_HELMET` (Umber), `MINERAL_HELMET` (Tungsten), `VANGUARD_HELMET`.
- Every corpse we saw was within about half a block of a fixed spawn spot for its layout; all crystal shafts share
  one layout.
- The tab list shows a corpse as LOOTED 1-4s after it's opened. Right-clicking without the key gives
  "You need an Umber Key to unlock this corpse!" and doesn't loot it.

## Organ Donor talisman
- The ding is `block.note_block.harp`, played at the player's own position (so it says nothing about where the corpse
  is), always volume 1.0.
- Pitch gives the distance to the corpse it's following: **distance ≈ 20 × √(2 − pitch) − 0.4**
  (1.0 at 20 blocks, 2.0 on top of it). Over 1,143 dings: median error 0.4 blocks, 90% within 1.4.
- It follows the nearest unlooted corpse 96% of the time; otherwise it seems to stay on the first corpse in range until
  that one is looted or you leave its range.
- Silent beyond 20 blocks. Gaps between dings: ~0.55s within 4 blocks, ~0.6s at 4-12, ~0.8s at 12-20.
- Other mods can mute it once every corpse is found, so silence after that means nothing.

## Loot and sacks
- Corpse loot is posted as a block of chat lines: a `▬▬▬` border, `  LAPIS CORPSE LOOT! `, blank, `  REWARDS`, then one
  indented line per item (`    Glacite Powder x4,238`, `    Enchanted Book (Ice Cold I)`), and a closing border.
  Milestone rewards can follow in the same block (`FROZEN CORPSE MILESTONE TIER V`, `25 SkyBlock XP`, ...), and
  `1 bonus drop!` lines appear; these aren't items.
- Gemstone names start with an icon glyph in the Unicode private use area, e.g. ` Fine Citrine Gemstone`.
- `[Sacks] +N items. (Last Ns.)` comes about every 30s; its hover text lists `Added items:` / `Removed items:`
  lines like `  +8,798  Rough Opal Gemstone (Gemstones Sack)`. The same hover is attached to more than one part of the
  message. A new server's first sack message only covers time since you arrived, and no sack message comes after
  leaving a shaft, so the last few seconds of sack gains are never reported.
- Raw blocks land in the inventory and Compact turns them into enchanted items that go to the sacks.
