package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.corpse.CorpseFinder
import io.github.developergr3y.shaftutils.corpse.CorpseFinder.SpotState
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Compat
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.Vec3
import io.github.developergr3y.shaftutils.util.Projection

/**
 * Waypoint labels drawn on the HUD at the point on screen where a spot/corpse is, worked out from the camera.
 * (Drawing on the HUD instead of in the world avoids per-version world rendering code.)
 *
 * Spots are the known spawn positions (public data, not a live corpse), and a corpse only gets a label once
 * you've seen it, so nothing here reveals anything through walls.
 */
object Waypoints {
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val config = ShaftUtils.config.corpses
        if (!config.enabled || !Mineshaft.inShaft || Compat.screen != null) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val eye = player.getEyePosition(1f)

        if (config.showSpots && !CorpseFinder.allFound) {
            for (spot in CorpseFinder.spots) {
                if (spot.state != SpotState.TO_CHECK) continue
                val distance = eye.distanceTo(spot.centre).toInt()
                if (spot === CorpseFinder.likelySpot) {
                    label(graphics, spot.centre, "§a§l◆ likely corpse §f${distance}m", 0xFF55FF55.toInt())
                } else {
                    label(graphics, spot.centre, "§e? §7corpse §f${distance}m", 0xFFFFFF55.toInt())
                }
            }
        }
        if (config.showEstimate) {
            CorpseFinder.estimate?.let { label(graphics, it, "§d≈ corpse (est.) §f${eye.distanceTo(it).toInt()}m", 0xFFFF55FF.toInt()) }
        }
        if (config.showCorpses) {
            for (corpse in CorpseFinder.corpses.values) {
                if (corpse.looted && config.hideLooted) continue
                val key = corpse.type.key?.let { if (CorpseFinder.hasKey(corpse.type)) " §a✔" else " §c✖ $it" }.orEmpty()
                val looted = if (corpse.looted) " §8(looted)" else ""
                val at = corpse.pos.add(0.0, 1.0, 0.0)
                label(graphics, at, "${corpse.type.formatted} §f${eye.distanceTo(at).toInt()}m$key$looted", corpse.type.rgb)
            }
        }
    }

    /** Draws a marker and text at [world]'s position on screen, if it's in front of the camera. */
    private fun label(graphics: GuiGraphicsExtractor, world: Vec3, text: String, colour: Int) {
        val (x, y) = Projection.toScreen(world) ?: return
        val font = Minecraft.getInstance().font
        graphics.fill(x - 2, y - 2, x + 2, y + 2, colour)
        val width = font.width(text)
        graphics.fill(x - width / 2 - 2, y - 14, x + width / 2 + 2, y - 3, 0x90000000.toInt())
        graphics.text(font, text, x - width / 2, y - 12, -1, true)
    }
}
