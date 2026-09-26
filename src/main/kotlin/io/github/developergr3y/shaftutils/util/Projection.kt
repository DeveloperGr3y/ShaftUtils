package io.github.developergr3y.shaftutils.util

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import kotlin.math.tan

/** Where a world point appears on screen, worked out from the camera (no per-version render code needed). */
object Projection {
    /** Screen position in GUI pixels, or null if the point is behind you or more than [margin] off screen. */
    fun toScreen(world: Vec3, margin: Double = 1.2): Pair<Int, Int>? {
        val mc = Minecraft.getInstance()
        val camera = Compat.camera
        val d = world.subtract(camera.position())
        val forward = camera.forwardVector()
        val up = camera.upVector()
        val left = camera.leftVector()
        val z = d.x * forward.x() + d.y * forward.y() + d.z * forward.z()
        if (z < 0.1) return null
        val right = -(d.x * left.x() + d.y * left.y() + d.z * left.z())
        val upward = d.x * up.x() + d.y * up.y() + d.z * up.z()

        val width = mc.window.guiScaledWidth
        val height = mc.window.guiScaledHeight
        val fov = camera.fov.takeIf { it > 1f } ?: mc.options.fov().get().toFloat()
        val scale = 1.0 / tan(Math.toRadians(fov / 2.0))
        val ndcX = right / z * scale / (width.toDouble() / height)
        val ndcY = upward / z * scale
        if (ndcX !in -margin..margin || ndcY !in -margin..margin) return null
        return ((ndcX + 1) / 2 * width).toInt() to ((1 - ndcY) / 2 * height).toInt()
    }

    /** On screen right now (not just in front of you). */
    fun onScreen(world: Vec3) = toScreen(world, margin = 0.95) != null
}
