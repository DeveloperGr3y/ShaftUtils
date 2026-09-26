package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.util.Projection
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lines and boxes drawn on the HUD over the world, using [Projection].
 *
 * The GUI has no line primitive, so a line is drawn as rectangles. To keep that cheap, every line is first cut to the
 * screen (a nearby block's edges can project thousands of pixels off screen), then drawn as runs: one rectangle per
 * row or column it crosses rather than one per pixel.
 */
object Draw {
    fun line(graphics: GuiGraphicsExtractor, x0: Double, y0: Double, x1: Double, y1: Double, colour: Int, thickness: Int = 1) {
        val window = Minecraft.getInstance().window
        val clipped = clip(x0, y0, x1, y1, window.guiScaledWidth.toDouble(), window.guiScaledHeight.toDouble()) ?: return
        val ax = clipped[0].roundToInt()
        val ay = clipped[1].roundToInt()
        val bx = clipped[2].roundToInt()
        val by = clipped[3].roundToInt()
        val dx = bx - ax
        val dy = by - ay
        if (abs(dx) >= abs(dy)) {
            // Mostly horizontal: one rectangle per stretch at the same height.
            if (dx == 0) {
                graphics.fill(ax, ay, ax + thickness, ay + thickness, colour)
                return
            }
            val step = if (dx > 0) 1 else -1
            var runStart = ax
            var runY = ay
            var x = ax
            while (true) {
                val y = ay + ((x - ax).toDouble() * dy / dx).roundToInt()
                if (y != runY) {
                    rect(graphics, runStart, runY, x - step, runY, thickness, colour)
                    runStart = x
                    runY = y
                }
                if (x == bx) break
                x += step
            }
            rect(graphics, runStart, runY, bx, runY, thickness, colour)
        } else {
            // Mostly vertical: one rectangle per stretch at the same x.
            val step = if (dy > 0) 1 else -1
            var runStart = ay
            var runX = ax
            var y = ay
            while (true) {
                val x = ax + ((y - ay).toDouble() * dx / dy).roundToInt()
                if (x != runX) {
                    rect(graphics, runX, runStart, runX, y - step, thickness, colour)
                    runStart = y
                    runX = x
                }
                if (y == by) break
                y += step
            }
            rect(graphics, runX, runStart, runX, by, thickness, colour)
        }
    }

    /** Fill from (x0, y0) to (x1, y1) inclusive, in either order, [thickness] pixels wide. */
    private fun rect(graphics: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, thickness: Int, colour: Int) {
        graphics.fill(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1) + thickness, maxOf(y0, y1) + thickness, colour)
    }

    /** Liang-Barsky: the part of the line inside [0, w] x [0, h], or null if none of it is. */
    private fun clip(x0: Double, y0: Double, x1: Double, y1: Double, w: Double, h: Double): DoubleArray? {
        val dx = x1 - x0
        val dy = y1 - y0
        var t0 = 0.0
        var t1 = 1.0
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(x0, w - x0, y0, h - y0)
        for (i in 0..3) {
            if (p[i] == 0.0) {
                if (q[i] < 0) return null
            } else {
                val t = q[i] / p[i]
                if (p[i] < 0) {
                    if (t > t1) return null
                    if (t > t0) t0 = t
                } else {
                    if (t < t0) return null
                    if (t < t1) t1 = t
                }
            }
        }
        return doubleArrayOf(x0 + t0 * dx, y0 + t0 * dy, x0 + t1 * dx, y0 + t1 * dy)
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
