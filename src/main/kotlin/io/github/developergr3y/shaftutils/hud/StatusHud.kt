package io.github.developergr3y.shaftutils.hud

import com.google.gson.annotations.Expose
import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.corpse.CorpseFinder
import io.github.developergr3y.shaftutils.corpse.CorpseFinder.SpotState
import io.github.developergr3y.shaftutils.corpse.OrganDonor
import io.github.developergr3y.shaftutils.corpse.SpawnData
import io.github.developergr3y.shaftutils.routes.RouteFollower
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Compat
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2

/** Where the status panel sits (GUI pixels) and how big it is. Set in /shaftutils gui. */
class HudPosition(
    @Expose @JvmField var x: Int = 5,
    @Expose @JvmField var y: Int = 60,
    @Expose @JvmField var scale: Float = 1f,
)

/** A small panel while you're in a shaft: which shaft, its corpses, spots left, the Organ Donor, and the route. */
object StatusHud {
    private const val LINE_HEIGHT = 10
    private const val PADDING = 3

    private val position get() = ShaftUtils.config.corpses.statusPosition

    /** Size of the panel as last drawn (unscaled), for the editor. */
    var width = 0
        private set
    var height = 0
        private set

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val config = ShaftUtils.config.corpses
        if (!config.enabled || !config.showStatus || !Mineshaft.inShaft || Compat.screen is HudEditScreen) return
        draw(graphics, lines())
    }

    /** Draws [lines] at the saved position and scale. */
    fun draw(graphics: GuiGraphicsExtractor, lines: List<String>) {
        val font = Minecraft.getInstance().font
        width = lines.maxOf { font.width(it) } + PADDING * 2
        height = lines.size * LINE_HEIGHT + PADDING * 2 - 1
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(position.x.toFloat(), position.y.toFloat())
        pose.scale(position.scale)
        graphics.fill(0, 0, width, height, 0x80000000.toInt())
        lines.forEachIndexed { i, line -> graphics.text(font, line, PADDING, PADDING + i * LINE_HEIGHT, -1, true) }
        pose.popMatrix()
    }

    /** What the panel says right now. */
    fun lines(): List<String> = buildList {
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
            val by = if (RouteFollower.builtIn) " §8(Mining Cult)" else ""
            add(if (RouteFollower.finished) "§7Route: §aFinished$by" else "§7Route: §f${RouteFollower.index + 1}§7/${route.size}$by")
        }
    }

    /** Example lines for the editor when you're not in a shaft. */
    fun previewLines() = listOf(
        "§b§lShaftUtils §7TOPA_1",
        "§7Corpses: §9Lapis §a✔ §6Umber §c✖",
        "§7Spots: §f2 §7to check §8/ 5  §7Found: §f1",
        "§7Organ Donor: §aCorpse §f~8m §e§l↗",
        "§7Route: §f3§7/24",
    )

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

    fun contains(mouseX: Double, mouseY: Double) =
        mouseX >= position.x && mouseY >= position.y &&
            mouseX <= position.x + width * position.scale && mouseY <= position.y + height * position.scale
}
