package io.github.developergr3y.shaftutils.corpse

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * "Could the player see this point?" Steps along the line from the eye and stops at the first block you can't see
 * through. See-through blocks (ice, glass, leaves, plants) don't block, and blocks right around the target don't
 * count, so a corpse half buried in ice or rock is visible once you can see the spot it's in.
 */
object Sight {
    private const val STEP = 0.2
    /** Blocks within this distance of the target are part of it (the ice/rock a corpse lies in). */
    private const val TARGET_RADIUS = 1.3

    /** Points around a spawn spot: its centre, just above it, and around its edges, so one blocked line isn't enough to miss it. */
    fun spotSamples(centre: Vec3): List<Vec3> = listOf(
        centre,
        centre.add(0.0, 0.9, 0.0),
        centre.add(0.45, 0.4, 0.0), centre.add(-0.45, 0.4, 0.0),
        centre.add(0.0, 0.4, 0.45), centre.add(0.0, 0.4, -0.45),
    )

    fun canSeeAny(level: Level, eye: Vec3, targets: List<Vec3>) = targets.any { canSee(level, eye, it) }

    fun canSee(level: Level, eye: Vec3, target: Vec3): Boolean {
        val direction = target.subtract(eye)
        val length = direction.length()
        if (length < 0.01) return true
        val step = direction.scale(STEP / length)
        var point = eye
        var travelled = 0.0
        var last: BlockPos? = null
        while (travelled < length - TARGET_RADIUS) {
            val pos = BlockPos.containing(point)
            if (pos != last) {
                last = pos
                if (level.getBlockState(pos).canOcclude()) return false
            }
            point = point.add(step)
            travelled += STEP
        }
        return true
    }
}
