package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.routes.RouteFollower
import io.github.developergr3y.shaftutils.routes.RoutePoint
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Compat
import io.github.developergr3y.shaftutils.util.Projection
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.hypot

/**
 * The route: the current point as an outlined block with its number and distance, a line from your crosshair to it,
 * and the next few points as dimmer outlines joined by a path line.
 */
object RouteRender {
    private const val DEFAULT_COLOUR = 0x55FFFF

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val config = ShaftUtils.config.routes
        val route = RouteFollower.route ?: return
        if (!config.enabled || !Mineshaft.inShaft || Compat.screen != null || RouteFollower.finished) return
        val player = Minecraft.getInstance().player ?: return
        val index = RouteFollower.index

        // Upcoming points first, so the current one is drawn on top.
        val ahead = (1..config.pointsAhead).mapNotNull { route.getOrNull(index + it)?.let { p -> index + it to p } }
        if (config.showPath) {
            val chain = listOf(route[index]) + ahead.map { it.second }
            chain.zipWithNext { a, b -> Draw.worldLine(graphics, centre(a), centre(b), dim(colour(b)), 1) }
        }
        for ((i, point) in ahead.asReversed()) {
            Draw.box(graphics, box(point), dim(colour(point)), 1)
            label(graphics, point, "§7${i + 1}", player.position(), dim = true)
        }

        val current = route[index]
        val colour = 0xFF000000.toInt() or colour(current)
        Draw.box(graphics, box(current), colour, 2)
        val name = current.name?.takeIf { it.toIntOrNull() != index + 1 }?.let { " §f$it" }.orEmpty()
        label(graphics, current, "§b§l${index + 1}§7/${route.size}$name", player.position(), dim = false)
        if (config.showTracer) tracer(graphics, centre(current), colour)
    }

    private fun label(graphics: GuiGraphicsExtractor, point: RoutePoint, text: String, player: Vec3, dim: Boolean) {
        val at = Vec3.atBottomCenterOf(point.pos).add(0.0, 1.5, 0.0)
        val (x, y) = Projection.toScreen(at) ?: return
        val full = "$text §8${player.distanceTo(at).toInt()}m"
        val font = Minecraft.getInstance().font
        val w = font.width(full)
        graphics.fill(x - w / 2 - 2, y - 10, x + w / 2 + 2, y + 1, if (dim) 0x50000000 else 0x90000000.toInt())
        graphics.text(font, full, x - w / 2, y - 8, -1, true)
    }

    /** A line from the crosshair to the point, or to the screen edge in its direction if it's behind you. */
    private fun tracer(graphics: GuiGraphicsExtractor, target: Vec3, colour: Int) {
        val mc = Minecraft.getInstance()
        val cx = mc.window.guiScaledWidth / 2.0
        val cy = mc.window.guiScaledHeight / 2.0
        val cam = Projection.toCamera(target)
        val onScreen = Projection.toScreen(cam)
        val end = if (onScreen != null && cam.depth > 0.5) {
            onScreen
        } else {
            // Behind you: point towards it from the centre, stopping near the edge.
            val len = hypot(cam.right, cam.up).coerceAtLeast(1e-6)
            val reach = minOf(cx, cy) * 0.9
            cx + cam.right / len * reach to cy - cam.up / len * reach
        }
        Draw.line(graphics, cx, cy, end.first, end.second, colour, 1)
    }

    private fun centre(p: RoutePoint): Vec3 = Vec3.atCenterOf(p.pos)
    private fun box(p: RoutePoint) = AABB(p.pos)
    private fun colour(p: RoutePoint) = p.colour ?: DEFAULT_COLOUR
    private fun dim(rgb: Int) = 0x90000000.toInt() or (rgb and 0xFFFFFF)
}
