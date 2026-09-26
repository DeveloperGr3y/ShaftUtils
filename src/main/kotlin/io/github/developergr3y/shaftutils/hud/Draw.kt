package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.util.Projection
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Lines and boxes drawn on the HUD over the world, using [Projection]. */
object Draw {
    /** A straight line in GUI pixels, drawn as small squares (the GUI has no line primitive). */
    fun line(graphics: GuiGraphicsExtractor, x0: Double, y0: Double, x1: Double, y1: Double, colour: Int, thickness: Int = 1) {
        val steps = max(abs(x1 - x0), abs(y1 - y0)).roundToInt().coerceIn(1, 4000)
        for (i in 0..steps) {
            val x = (x0 + (x1 - x0) * i / steps).roundToInt()
            val y = (y0 + (y1 - y0) * i / steps).roundToInt()
            graphics.fill(x, y, x + thickness, y + thickness, colour)
        }
    }

    /** A line between two world points, as it appears on screen. */
    fun worldLine(graphics: GuiGraphicsExtractor, a: Vec3, b: Vec3, colour: Int, thickness: Int = 1) {
        val (sa, sb) = Projection.segment(a, b) ?: return
        line(graphics, sa.first, sa.second, sb.first, sb.second, colour, thickness)
    }

    /** The 12 edges of a box in the world. */
    fun box(graphics: GuiGraphicsExtractor, box: AABB, colour: Int, thickness: Int = 1) {
        val c = listOf(
            Vec3(box.minX, box.minY, box.minZ), Vec3(box.maxX, box.minY, box.minZ),
            Vec3(box.maxX, box.minY, box.maxZ), Vec3(box.minX, box.minY, box.maxZ),
            Vec3(box.minX, box.maxY, box.minZ), Vec3(box.maxX, box.maxY, box.minZ),
            Vec3(box.maxX, box.maxY, box.maxZ), Vec3(box.minX, box.maxY, box.maxZ),
        )
        val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        for ((i, j) in edges) worldLine(graphics, c[i], c[j], colour, thickness)
    }
}
