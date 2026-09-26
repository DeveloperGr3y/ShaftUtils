package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.corpse.CorpseFinder
import io.github.developergr3y.shaftutils.corpse.CorpseFinder.SpotState
import io.github.developergr3y.shaftutils.corpse.OrganDonor
import io.github.developergr3y.shaftutils.profit.ItemIds
import io.github.developergr3y.shaftutils.shaft.CorpseType
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2

/**
 * Every corpse in the shaft you're in, from the tab list, and how far along each one is:
 *   Lapis     ✔ looted
 *   Umber     ● found 12m · ✔ key
 *   Tungsten  ✖ not found · ✖ Tungsten Key
 * plus the Organ Donor's distance and direction and how many spawn spots are left to check.
 * Can hide itself once everything is looted.
 */
object CorpseHelper : Panel("Corpse Helper") {
    private val config get() = ShaftUtils.config.corpses

    override var position: HudPosition
        get() = config.statusPosition
        set(value) {
            config.statusPosition = value
        }

    override fun defaultPosition() = HudPosition(x = 5, y = 60)

    override fun visible(): Boolean {
        if (!config.enabled || !config.showStatus || !Mineshaft.inShaft) return false
        val corpses = Mineshaft.corpses
        return !(config.hideWhenLooted && corpses.isNotEmpty() && corpses.all { it.looted })
    }

    override fun lines(): List<PanelLine> = buildList {
        val code = Mineshaft.code ?: return@buildList
        val crystal = if (code.endsWith("_C")) " Crystal" else ""
        add(PanelLine("§b§lCorpse Helper §7${ItemIds.shaftName(Mineshaft.type)}$crystal §8$code"))

        val tab = Mineshaft.corpses
        if (tab.isEmpty()) {
            add(PanelLine("§8No corpse list in the tab list yet"))
        } else {
            // Match the tab list's corpses to the ones we've seen, type by type.
            val player = Minecraft.getInstance().player
            val seen = CorpseFinder.corpses.values.filter { !it.looted }.groupBy { it.type }.mapValues { it.value.toMutableList() }
            val nameWidth = tab.maxOf { Minecraft.getInstance().font.width(it.type.label) }
            for (corpse in tab.sortedBy { it.looted }) {
                val name = pad(corpse.type.formatted, corpse.type.label, nameWidth)
                val status = when {
                    corpse.looted -> "§a✔ looted"
                    else -> {
                        val found = seen[corpse.type]?.removeFirstOrNull()
                        val where = if (found != null && player != null) "§e● found §f${player.position().distanceTo(found.pos).toInt()}m" else "§c✖ §7not found"
                        where + key(corpse.type)
                    }
                }
                add(PanelLine("$name $status"))
            }
        }

        val spots = CorpseFinder.spots.count { it.state == SpotState.TO_CHECK }
        if (!CorpseFinder.allFound && CorpseFinder.spots.isNotEmpty()) add(PanelLine("§7Spots to check: §f$spots"))
        dingLine()?.let { add(PanelLine(it)) }
    }

    override fun previewLines() = listOf(
        PanelLine("§b§lCorpse Helper §7Topaz §8TOPA_1"),
        PanelLine("§6Umber    §e● found §f12m §8· §a✔ key"),
        PanelLine("§7Tungsten §c✖ §7not found §8· §c✖ Tungsten Key"),
        PanelLine("§9Lapis     §a✔ looted"),
        PanelLine("§7Spots to check: §f2"),
        PanelLine("§7Organ Donor: §aCorpse §f~8m §e§l↗"),
    )

    private fun key(type: CorpseType): String {
        val key = type.key ?: return ""
        return if (CorpseFinder.hasKey(type)) " §8· §a✔ key" else " §8· §c✖ $key"
    }

    /** Pads a coloured name to line the status column up. */
    private fun pad(coloured: String, plain: String, width: Int): String {
        val font = Minecraft.getInstance().font
        var spaces = 0
        while (font.width(plain + " ".repeat(spaces + 1)) <= width + font.width(" ")) spaces++
        return coloured + " ".repeat(spaces)
    }

    private fun dingLine(): String? {
        if (!config.useOrganDonor || !OrganDonor.heardThisShaft) return null
        if (!OrganDonor.dinging) return "§7Organ Donor: §8quiet (nothing within 20)"
        val distance = OrganDonor.lastDistance ?: return "§7Organ Donor: §aDING"
        val target = CorpseFinder.likelySpot?.centre ?: CorpseFinder.estimate
        val direction = target?.let { " ${arrowTo(it)}" } ?: " §8(move to get a direction)"
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
