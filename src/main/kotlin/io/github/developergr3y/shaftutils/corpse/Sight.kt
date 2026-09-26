package io.github.developergr3y.shaftutils.corpse

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * "Could the player see this point?" Steps along the line from the eye and stops at the first block you can't see
 * through. See-through blocks (ice, glass, leaves, plants) don't block; the block the target is in doesn't count,
 * so a corpse half buried in ice or rock is visible once you can see the spot it's in.
 */
object Sight {
    private const val STEP = 0.2
    /** Blocks within this distance of the target are part of it (the ice/rock it's buried in). */
    private const val TARGET_RADIUS = 0.9

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
