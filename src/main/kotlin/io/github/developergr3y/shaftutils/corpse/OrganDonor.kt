package io.github.developergr3y.shaftutils.corpse

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundEventListener
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The Organ Donor talisman "Sends out a Ding when within 20 blocks of an unlooted Frozen Corpse".
 *
 * What we measured (36 shafts, 1,143 dings; see docs/RESEARCH.md):
 *  - The ding is a note block harp played at *your* position, so the sound itself says nothing about where the corpse is.
 *  - Its pitch gives your distance to the corpse: distance ≈ 20 × √(2 − pitch), accurate to about half a block
 *    (1.0 at 20 blocks, 2.0 on top of it). It's silent beyond 20 blocks.
 *  - It follows the nearest unlooted corpse, but seems to stay on the first one in range until it's looted or you
 *    leave its range.
 *
 * So each ding is a distance reading. Readings from a few different spots pin the corpse down (like GPS), which gives
 * a direction and an estimated position. This only uses what the talisman tells you.
 */
object OrganDonor : SoundEventListener {
    private const val DING = "block.note_block.harp"
    /** The fit came out about 0.4 blocks long on average (measured from your feet). */
    private const val DISTANCE_CORRECTION = 0.4
    /** Dings come every ~0.55-0.95s in range; longer than this means you're out of range of every unlooted corpse. */
    const val SILENCE_MS = 2_500L
    private const val MAX_SAMPLES = 24
    private const val SAMPLE_MAX_AGE_MS = 45_000L

    class Sample(val pos: Vec3, val distance: Double, val at: Long)

    /** When the last ding was heard (epoch millis), 0 if none this shaft. */
    var lastDingAt = 0L
        private set
    var dingsThisShaft = 0
        private set
    /** Distance to the corpse from the last ding. */
    var lastDistance: Double? = null
        private set

    /** Readings for the corpse the talisman is currently following. */
    val samples = ArrayDeque<Sample>()

    private var session = -1

    fun register() {
        Minecraft.getInstance().soundManager.addListener(this)
    }

    val heardThisShaft get() = session == Mineshaft.session && dingsThisShaft > 0
    val dinging get() = heardThisShaft && System.currentTimeMillis() - lastDingAt < SILENCE_MS
    val silent get() = heardThisShaft && !dinging

    fun distanceFromPitch(pitch: Float) = (20 * sqrt((2.0 - pitch).coerceAtLeast(0.0)) - DISTANCE_CORRECTION).coerceAtLeast(0.0)

    override fun onPlaySound(sound: SoundInstance, events: WeighedSoundEvents, range: Float) {
        if (!Mineshaft.inShaft) return
        if (session != Mineshaft.session) reset()
        val id = sound.identifier.toString()
        val isDing = id.endsWith(DING)
        if (!isDing) return

        val player = Minecraft.getInstance().player
        val now = System.currentTimeMillis()
        var gap: Long? = null
        var distance: Double? = null
        if (isDing && player != null) {
            if (lastDingAt > 0) gap = now - lastDingAt
            distance = distanceFromPitch(sound.pitch)
            addSample(Sample(player.position(), distance, now), gap)
            lastDingAt = now
            lastDistance = distance
            dingsThisShaft++
        }
    }

    private fun addSample(sample: Sample, gap: Long?) {
        val last = samples.lastOrNull()
        // A new run of dings (after a silence), or a jump no amount of walking explains, means a different corpse.
        val newTarget = last == null || (gap ?: Long.MAX_VALUE) > SILENCE_MS ||
            abs(sample.distance - last.distance) > sample.pos.distanceTo(last.pos) + 2.5
        if (newTarget) samples.clear()
        // Standing still adds nothing new; keep the freshest reading from each spot.
        if (last != null && !newTarget && sample.pos.distanceTo(last.pos) < 0.5) samples.removeLast()
        samples.addLast(sample)
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
        samples.removeAll { sample.at - it.at > SAMPLE_MAX_AGE_MS }
    }

    /** Forget the corpse being followed (e.g. it was just looted). */
    fun clearTarget() = samples.clear()

    /** How well a candidate position matches the readings: root mean square error in blocks. */
    fun fit(candidate: Vec3): Double {
        if (samples.isEmpty()) return Double.MAX_VALUE
        return sqrt(samples.sumOf { val e = candidate.distanceTo(it.pos) - it.distance; e * e } / samples.size)
    }

    /** How far apart the readings were taken (horizontally). Too little and the position can't be pinned down. */
    fun spread(): Double {
        var best = 0.0
        for (a in samples) for (b in samples) {
            val dx = a.pos.x - b.pos.x
            val dz = a.pos.z - b.pos.z
            best = maxOf(best, sqrt(dx * dx + dz * dz))
        }
        return best
    }

    /**
     * Best guess at where the followed corpse is, from the readings alone, or null if there isn't enough to go on.
     * Tries several starting points and refines each (least squares), keeping the best fit.
     */
    fun estimate(): Vec3? {
        if (samples.size < 4 || spread() < 3.0) return null
        val latest = samples.last()
        val centre = Vec3(samples.sumOf { it.pos.x } / samples.size, samples.sumOf { it.pos.y } / samples.size, samples.sumOf { it.pos.z } / samples.size)
        var best: Vec3? = null
        var bestFit = Double.MAX_VALUE
        for (i in 0 until 12) {
            val angle = i * Math.PI / 6
            val start = centre.add(Math.cos(angle) * latest.distance, 0.0, Math.sin(angle) * latest.distance)
            val result = refine(start)
            val f = fit(result)
            if (f < bestFit) {
                bestFit = f
                best = result
            }
        }
        // A poor fit, or two mirror-image answers equally good (readings all along one line), means we don't know yet.
        if (best == null || bestFit > 1.5) return null
        val mirrorFits = (0 until 12).map { i ->
            val angle = i * Math.PI / 6
            refine(centre.add(Math.cos(angle) * latest.distance, 0.0, Math.sin(angle) * latest.distance))
        }.filter { it.distanceTo(best) > 3.0 && fit(it) < bestFit + 0.3 }
        return if (mirrorFits.isEmpty()) best else null
    }

    /** Gauss-Newton with a little damping: nudges [start] until its distances to the readings match. */
    private fun refine(start: Vec3): Vec3 {
        var x = start.x
        var y = start.y
        var z = start.z
        repeat(25) {
            // Normal equations J^T J delta = -J^T r for residuals r_i = |p - s_i| - d_i.
            val a = Array(3) { DoubleArray(3) }
            val b = DoubleArray(3)
            for (s in samples) {
                val dx = x - s.pos.x
                val dy = y - s.pos.y
                val dz = z - s.pos.z
                val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6)
                val r = dist - s.distance
                val j = doubleArrayOf(dx / dist, dy / dist, dz / dist)
                for (p in 0..2) {
                    b[p] -= j[p] * r
                    for (q in 0..2) a[p][q] += j[p] * j[q]
                }
            }
            for (p in 0..2) a[p][p] += 0.05 // damping keeps it stable when readings are nearly level
            val delta = solve(a, b) ?: return Vec3(x, y, z)
            x += delta[0]
            y += delta[1]
            z += delta[2]
            if (abs(delta[0]) + abs(delta[1]) + abs(delta[2]) < 0.01) return Vec3(x, y, z)
        }
        return Vec3(x, y, z)
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val det = a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) -
            a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0]) +
            a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0])
        if (abs(det) < 1e-9) return null
        fun col(i: Int) = Array(3) { r -> DoubleArray(3) { c -> if (c == i) b[r] else a[r][c] } }
        fun det3(m: Array<DoubleArray>) = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
            m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
            m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
        return DoubleArray(3) { det3(col(it)) / det }
    }

    private fun reset() {
        session = Mineshaft.session
        lastDingAt = 0L
        dingsThisShaft = 0
        lastDistance = null
        samples.clear()
    }
}
