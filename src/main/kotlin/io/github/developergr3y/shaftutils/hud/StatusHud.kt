package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.corpse.CorpseFinder
import io.github.developergr3y.shaftutils.corpse.CorpseFinder.SpotState
import io.github.developergr3y.shaftutils.corpse.OrganDonor
import io.github.developergr3y.shaftutils.corpse.SpawnData
import io.github.developergr3y.shaftutils.routes.RouteFollower
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2

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
            RouteFollower.route?.let { route ->
                add(if (RouteFollower.finished) "§7Route: §aFinished" else "§7Route: §f${RouteFollower.index + 1}§7/${route.size}")
            }
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
        if (!OrganDonor.heardThisShaft) return "§7Organ Donor: §8no ding yet"
        if (!OrganDonor.dinging) return "§7Organ Donor: §7quiet §8(no unlooted corpse within 20)"
        val distance = OrganDonor.lastDistance ?: return "§7Organ Donor: §aDING"
        val target = CorpseFinder.likelySpot?.centre ?: CorpseFinder.estimate
        val direction = target?.let { " ${arrowTo(it)}" } ?: " §8(move around to get a direction)"
        return "§7Organ Donor: §aCorpse §f~${distance.toInt()}m$direction"
    }

    private val arrows = listOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")

    /** An arrow for which way [target] is relative to where you're facing. */
    private fun arrowTo(target: Vec3): String {
        val player = Minecraft.getInstance().player ?: return ""
        val dx = target.x - player.x
        val dz = target.z - player.z
        // Minecraft yaw: 0 = south (+z), 90 = west (-x).
        val targetYaw = Math.toDegrees(atan2(-dx, dz))
        var relative = (targetYaw - player.yRot) % 360
        if (relative < 0) relative += 360
        val arrow = arrows[((relative + 22.5) / 45).toInt() % 8]
        val dy = target.y - player.y
        val vertical = when {
            dy > 3 -> " §7(above)"
            dy < -3 -> " §7(below)"
            else -> ""
        }
        return "§e§l$arrow$vertical"
    }
}
