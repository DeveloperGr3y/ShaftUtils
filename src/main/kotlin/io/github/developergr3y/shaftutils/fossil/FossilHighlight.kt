package io.github.developergr3y.shaftutils.fossil

import com.mojang.blaze3d.vertex.PoseStack
import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Compat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/**
 * Fossils in a shaft are built from quartz (blocks, pillars, stairs, slabs...). This tints the outward faces of those
 * blocks purple, drawn in the world so the game's depth test hides anything behind walls:
 *  1. Every couple of seconds, the loaded chunks around you are scanned for quartz. Chunk sections whose palette has
 *     no quartz at all are skipped, so this is cheap.
 *  2. Twice a second, the faces to tint are worked out: each quartz block's faces that aren't pressed against another
 *     solid block, within range. Mined blocks drop out here.
 *  3. Every frame, those faces are sent to the game as one batch of translucent quads.
 */
object FossilHighlight {
    private const val SCAN_TICKS = 40
    private const val FACE_TICKS = 10
    private const val SCAN_HEIGHT = 48
    private const val PURPLE = 0xB040FF
    /** Lifts the tint just off the block, so it doesn't flicker against the block's own surface. */
    private const val LIFT = 0.003

    private var blocks: Set<BlockPos> = emptySet()
    /** Four corners per face, already lifted off the block. */
    private var faces: List<Array<Vec3>> = emptyList()
    private var ticks = 0
    private var lastLevel: Level? = null
    private val isQuartz = mutableMapOf<Block, Boolean>()

    private fun quartz(block: Block) = isQuartz.getOrPut(block) {
        val path = BuiltInRegistries.BLOCK.getKey(block).path
        "quartz" in path && "ore" !in path
    }

    fun tick(client: Minecraft) {
        val level = client.level
        val player = client.player
        if (!ShaftUtils.config.fossils.enabled || !Mineshaft.inShaft || level == null || player == null) {
            blocks = emptySet()
            faces = emptyList()
            return
        }
        if (level !== lastLevel) {
            lastLevel = level
            blocks = emptySet()
            ticks = 0
        }
        if (ticks % SCAN_TICKS == 0) blocks = scan(level, player.blockPosition())
        if (ticks % FACE_TICKS == 0) faces = faces(level, player.position())
        ticks++
    }

    private fun scan(level: Level, centre: BlockPos): Set<BlockPos> {
        val found = mutableSetOf<BlockPos>()
        val chunks = ceil(ShaftUtils.config.fossils.range / 16.0).toInt() + 1
        val cx = centre.x shr 4
        val cz = centre.z shr 4
        val minY = centre.y - SCAN_HEIGHT
        val maxY = centre.y + SCAN_HEIGHT
        for (x in cx - chunks..cx + chunks) for (z in cz - chunks..cz + chunks) {
            val chunk = level.getChunk(x, z)
            chunk.sections.forEachIndexed { index, section ->
                if (section.hasOnlyAir()) return@forEachIndexed
                val baseY = level.getSectionYFromSectionIndex(index) shl 4
                if (baseY + 15 < minY || baseY > maxY) return@forEachIndexed
                if (!section.maybeHas { quartz(it.block) }) return@forEachIndexed
                for (dy in 0..15) for (dz in 0..15) for (dx in 0..15) {
                    if (quartz(section.getBlockState(dx, dy, dz).block)) {
                        found += BlockPos((x shl 4) + dx, baseY + dy, (z shl 4) + dz)
                    }
                }
            }
        }
        return found
    }

    private fun faces(level: Level, player: Vec3): List<Array<Vec3>> {
        val range = ShaftUtils.config.fossils.range.toDouble()
        val out = mutableListOf<Array<Vec3>>()
        for (pos in blocks) {
            if (Vec3.atCenterOf(pos).distanceTo(player) > range) continue
            val state = level.getBlockState(pos)
            if (!quartz(state.block)) continue // mined since the last scan
            for (box in state.getShape(level, pos).toAabbs()) {
                val b = box.move(pos)
                for (direction in Direction.entries) {
                    if (onBoundary(b, pos, direction)) {
                        val neighbour = pos.relative(direction)
                        val n = level.getBlockState(neighbour)
                        if (n.canOcclude() && n.isCollisionShapeFullBlock(level, neighbour)) continue
                    }
                    out += corners(b, direction)
                }
            }
        }
        return out
    }

    /** Whether the box's face in [d] lies on the edge of its block (so a neighbouring block could cover it). */
    private fun onBoundary(b: AABB, pos: BlockPos, d: Direction) = when (d) {
        Direction.DOWN -> b.minY == pos.y.toDouble()
        Direction.UP -> b.maxY == pos.y + 1.0
        Direction.NORTH -> b.minZ == pos.z.toDouble()
        Direction.SOUTH -> b.maxZ == pos.z + 1.0
        Direction.WEST -> b.minX == pos.x.toDouble()
        Direction.EAST -> b.maxX == pos.x + 1.0
    }

    private fun corners(b: AABB, d: Direction): Array<Vec3> {
        val corners = when (d) {
            Direction.DOWN -> arrayOf(Vec3(b.minX, b.minY, b.minZ), Vec3(b.maxX, b.minY, b.minZ), Vec3(b.maxX, b.minY, b.maxZ), Vec3(b.minX, b.minY, b.maxZ))
            Direction.UP -> arrayOf(Vec3(b.minX, b.maxY, b.minZ), Vec3(b.minX, b.maxY, b.maxZ), Vec3(b.maxX, b.maxY, b.maxZ), Vec3(b.maxX, b.maxY, b.minZ))
            Direction.NORTH -> arrayOf(Vec3(b.minX, b.minY, b.minZ), Vec3(b.minX, b.maxY, b.minZ), Vec3(b.maxX, b.maxY, b.minZ), Vec3(b.maxX, b.minY, b.minZ))
            Direction.SOUTH -> arrayOf(Vec3(b.minX, b.minY, b.maxZ), Vec3(b.maxX, b.minY, b.maxZ), Vec3(b.maxX, b.maxY, b.maxZ), Vec3(b.minX, b.maxY, b.maxZ))
            Direction.WEST -> arrayOf(Vec3(b.minX, b.minY, b.minZ), Vec3(b.minX, b.minY, b.maxZ), Vec3(b.minX, b.maxY, b.maxZ), Vec3(b.minX, b.maxY, b.minZ))
            Direction.EAST -> arrayOf(Vec3(b.maxX, b.minY, b.minZ), Vec3(b.maxX, b.maxY, b.minZ), Vec3(b.maxX, b.maxY, b.maxZ), Vec3(b.maxX, b.minY, b.maxZ))
        }
        val lift = d.unitVec3.scale(LIFT)
        return Array(4) { corners[it].add(lift) }
    }

    /** Called while the world is drawn: every face as one batch of translucent, depth-tested quads. */
    fun renderWorld(poseStack: PoseStack, collector: SubmitNodeCollector) {
        val list = faces
        if (list.isEmpty()) return
        val camera = Compat.camera.position()
        val alpha = (ShaftUtils.config.fossils.opacity / 100f * 255).toInt().coerceIn(0, 255)
        val colour = (alpha shl 24) or PURPLE
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads()) { pose, buffer ->
            for (face in list) for (corner in face) {
                buffer.addVertex(pose, (corner.x - camera.x).toFloat(), (corner.y - camera.y).toFloat(), (corner.z - camera.z).toFloat())
                    .setColor(colour)
            }
        }
    }
}
