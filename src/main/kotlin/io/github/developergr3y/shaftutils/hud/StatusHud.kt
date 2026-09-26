package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.corpse.CorpseFinder
import io.github.developergr3y.shaftutils.corpse.CorpseFinder.SpotState
import io.github.developergr3y.shaftutils.corpse.OrganDonor
import io.github.developergr3y.shaftutils.corpse.SpawnData
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** A small panel while you're in a shaft: which shaft, the corpses it has, spots left to check, and the ding. */
object StatusHud {
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val config = ShaftUtils.config.corpses
        if (!config.enabled || !config.showStatus || !Mineshaft.inShaft) return
        val lines = buildList {
            add("§b§lShaftUtils §7${Mineshaft.code}")
            val corpses = Mineshaft.corpses
            add(
                if (corpses.isEmpty()) "§7Corpses: §8(tab widget not found)"
                else "§7Corpses: " + corpses.joinToString(" ") { "${it.type.formatted} ${if (it.looted) "§a✔" else "§c✖"}" },
            )
            val spots = CorpseFinder.spots
            val left = spots.count { it.state == SpotState.TO_CHECK }
            add(
                when {
                    CorpseFinder.allFound -> "§aAll corpses found"
                    spots.isEmpty() -> "§7Spots: §8none known for this shaft"
                    else -> "§7Spots: §f$left §7to check §8/ ${spots.size}  §7Found: §f${CorpseFinder.corpses.size}"
                },
            )
            add(dingLine())
            if (ShaftUtils.config.debug.probe || ShaftUtils.config.debug.recordSpots) {
                add("§8Debug: probe ${if (ShaftUtils.config.debug.probe) "on" else "off"} · ${SpawnData.learnedCount()} learned spots")
            }
        }
        val font = Minecraft.getInstance().font
        val x = config.statusX
        var y = config.statusY
        val width = lines.maxOf { font.width(it) }
        graphics.fill(x - 3, y - 3, x + width + 3, y + lines.size * 10 + 1, 0x80000000.toInt())
        for (line in lines) {
            graphics.text(font, line, x, y, -1, true)
            y += 10
        }
    }

    private fun dingLine(): String {
        val last = OrganDonor.lastDingAt
        if (last == 0L) return "§7Organ Donor: §8no ding yet"
        val ago = (System.currentTimeMillis() - last) / 1000.0
        val recent = ago < 3
        val gap = OrganDonor.lastGapMs?.let { " §8every ${"%.1f".format(it / 1000.0)}s" }.orEmpty()
        return "§7Organ Donor: ${if (recent) "§a§lDING" else "§7quiet"} §8(${"%.1f".format(ago)}s ago, ${OrganDonor.dingsThisShaft})$gap"
    }
}
