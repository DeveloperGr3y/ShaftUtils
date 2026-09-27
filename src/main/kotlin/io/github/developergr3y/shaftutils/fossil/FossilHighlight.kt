package io.github.developergr3y.shaftutils.fossil

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Compat
import io.github.developergr3y.shaftutils.util.Projection
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Fossils in a shaft are built from quartz (blocks, pillars, stairs, slabs...). This tints the faces of those blocks
 * purple, drawn on the HUD like the other waypoints:
 *  1. Every couple of seconds, the loaded chunks around you are scanned for quartz. Chunk sections whose palette has
 *     no quartz at all are skipped, so this is cheap.
 *  2. A few times a second, each quartz block's outward faces (not ones pressed against another solid block) are
 *     checked for line of sight from your eyes. Nothing is shown through walls.
 *  3. Every frame, the visible faces that point towards you are projected to the screen and filled.
 */
object FossilHighlight {
    private const val SCAN_TICKS = 40
    private const val SIGHT_TICKS = 4
    private const val SCAN_HEIGHT = 48
    private const val PURPLE = 0xB040FF

    private class Face(val direction: Direction, val corners: List<Vec3>, val centre: Vec3)

    private var blocks: Set<BlockPos> = emptySet()
    private var visible: List<Face> = emptyList()
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
            visible = emptyList()
            return
        }
        if (level !== lastLevel) {
            lastLevel = level
            blocks = emptySet()
            ticks = 0
        }
        if (ticks % SCAN_TICKS == 0) blocks = scan(level, player.blockPosition())
        if (ticks % SIGHT_TICKS == 0) visible = visibleFaces(level, player.getEyePosition(1f))
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

    private fun visibleFaces(level: Level, eye: Vec3): List<Face> {
        val range = ShaftUtils.config.fossils.range.toDouble()
        val out = mutableListOf<Face>()
        val player = Minecraft.getInstance().player ?: return out
        for (pos in blocks) {
            if (Vec3.atCenterOf(pos).distanceTo(eye) > range) continue
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
                    val face = face(b, direction)
                    if (!facing(face, eye)) continue
                    if (samples(face).any { canSee(level, eye, it, player) }) out += face
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

    private fun face(b: AABB, d: Direction): Face {
        val corners = when (d) {
            Direction.DOWN -> listOf(Vec3(b.minX, b.minY, b.minZ), Vec3(b.maxX, b.minY, b.minZ), Vec3(b.maxX, b.minY, b.maxZ), Vec3(b.minX, b.minY, b.maxZ))
            Direction.UP -> listOf(Vec3(b.minX, b.maxY, b.minZ), Vec3(b.maxX, b.maxY, b.minZ), Vec3(b.maxX, b.maxY, b.maxZ), Vec3(b.minX, b.maxY, b.maxZ))
            Direction.NORTH -> listOf(Vec3(b.minX, b.minY, b.minZ), Vec3(b.maxX, b.minY, b.minZ), Vec3(b.maxX, b.maxY, b.minZ), Vec3(b.minX, b.maxY, b.minZ))
            Direction.SOUTH -> listOf(Vec3(b.minX, b.minY, b.maxZ), Vec3(b.maxX, b.minY, b.maxZ), Vec3(b.maxX, b.maxY, b.maxZ), Vec3(b.minX, b.maxY, b.maxZ))
            Direction.WEST -> listOf(Vec3(b.minX, b.minY, b.minZ), Vec3(b.minX, b.minY, b.maxZ), Vec3(b.minX, b.maxY, b.maxZ), Vec3(b.minX, b.maxY, b.minZ))
            Direction.EAST -> listOf(Vec3(b.maxX, b.minY, b.minZ), Vec3(b.maxX, b.minY, b.maxZ), Vec3(b.maxX, b.maxY, b.maxZ), Vec3(b.maxX, b.maxY, b.minZ))
        }
        val centre = corners.reduce(Vec3::add).scale(0.25)
        return Face(d, corners, centre)
    }

    /** The face points towards [eye] (you're on its outer side). */
    private fun facing(face: Face, eye: Vec3): Boolean {
        val n = face.direction.unitVec3
        return eye.subtract(face.centre).dot(n) > 0
    }

    /** The face's centre and points near its corners, nudged just off the surface. */
    private fun samples(face: Face): List<Vec3> {
        val out = face.direction.unitVec3.scale(0.02)
        return (listOf(face.centre) + face.corners.map { it.add(face.centre.subtract(it).scale(0.2)) }).map { it.add(out) }
    }

    private fun canSee(level: Level, eye: Vec3, target: Vec3, player: Entity): Boolean {
        val hit = level.clip(ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
        return hit.type == HitResult.Type.MISS || hit.location.distanceToSqr(target) < 0.01
    }

    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        if (visible.isEmpty() || Compat.screen != null) return
        val eye = Compat.camera.position()
        val alpha = (ShaftUtils.config.fossils.opacity / 100f * 255).toInt().coerceIn(0, 255)
        val colour = (alpha shl 24) or PURPLE
        for (face in visible) {
            if (!facing(face, eye)) continue
            fill(graphics, face.corners, colour)
        }
    }

    /** Fill a flat quad in the world: cut it at the camera, project it, then fill it row by row. */
    private fun fill(graphics: GuiGraphicsExtractor, corners: List<Vec3>, colour: Int) {
        var points = corners.map { Projection.toCamera(it) }
        points = clipNear(points)
        if (points.size < 3) return
        val screen = points.mapNotNull { Projection.toScreen(it) }
        if (screen.size < 3) return
        val window = Minecraft.getInstance().window
        val w = window.guiScaledWidth
        val h = window.guiScaledHeight
        val top = floor(screen.minOf { it.second }).toInt().coerceAtLeast(0)
        val bottom = ceil(screen.maxOf { it.second }).toInt().coerceAtMost(h)
        if (top >= bottom) return
        if (screen.maxOf { it.first } < 0 || screen.minOf { it.first } > w) return
        for (y in top until bottom) {
            val sy = y + 0.5
            var left = Double.MAX_VALUE
            var right = -Double.MAX_VALUE
            for (i in screen.indices) {
                val (x0, y0) = screen[i]
                val (x1, y1) = screen[(i + 1) % screen.size]
                if ((sy < y0) == (sy < y1)) continue
                val x = x0 + (sy - y0) / (y1 - y0) * (x1 - x0)
                if (x < left) left = x
                if (x > right) right = x
            }
            if (left > right) continue
            val l = Math.round(left).toInt().coerceAtLeast(0)
            val r = Math.round(right).toInt().coerceAtMost(w)
            if (r > l) graphics.fill(l, y, r, y + 1, colour)
        }
    }

    /** Sutherland-Hodgman against the plane just in front of the camera. */
    private fun clipNear(points: List<Projection.CameraPoint>): List<Projection.CameraPoint> {
        val near = 0.1
        val out = mutableListOf<Projection.CameraPoint>()
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val aIn = a.depth >= near
            val bIn = b.depth >= near
            if (aIn) out += a
            if (aIn != bIn) {
                val t = (a.depth - near) / (a.depth - b.depth)
                out += Projection.CameraPoint(a.right + (b.right - a.right) * t, a.up + (b.up - a.up) * t, near)
            }
        }
        return out
    }
}
