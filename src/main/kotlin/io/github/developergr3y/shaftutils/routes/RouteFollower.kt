package io.github.developergr3y.shaftutils.routes

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.debug.Probe
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

/**
 * Follows the route for the shaft you're in: loads it when you arrive, shows the current point (and a couple ahead),
 * and moves on once you're within a few blocks of it.
 */
object RouteFollower {
    var route: List<RoutePoint>? = null
        private set
    var builtIn = false
        private set
    var index = 0
        private set
    var finished = false
        private set

    private var session = -1

    val current get() = route?.getOrNull(index)

    fun tick(client: Minecraft) {
        val config = ShaftUtils.config.routes
        if (!config.enabled || !Mineshaft.inShaft) {
            if (session != -1 && !Mineshaft.inShaft) {
                route = null
                session = -1
            }
            return
        }
        if (session != Mineshaft.session) load()
        val point = current ?: return
        val player = client.player ?: return
        if (player.position().distanceTo(Vec3.atBottomCenterOf(point.pos).add(0.0, 0.5, 0.0)) <= config.advanceDistance) advance()
    }

    private fun load() {
        session = Mineshaft.session
        index = 0
        finished = false
        val code = Mineshaft.code ?: return
        val loaded = Routes.forShaft(code)
        route = loaded?.points
        builtIn = loaded?.builtIn ?: false
        loaded?.let {
            val source = if (it.builtIn) "Mining Cult route" else "your route"
            ShaftUtils.chat("Loaded $source for §f$code§r §7(${it.points.size} points)")
            Probe.log("route_loaded", "code" to code, "points" to it.points.size, "builtIn" to it.builtIn)
        }
    }

    fun advance() {
        val r = route ?: return
        if (index < r.size - 1) {
            index++
        } else if (!finished) {
            finished = true
            ShaftUtils.chat("§aRoute finished!")
        }
    }

    fun back() {
        if (route == null) return
        finished = false
        index = (index - 1).coerceAtLeast(0)
    }

    fun restart() {
        finished = false
        index = 0
    }

    /** Re-read the route file for this shaft (after importing or editing it). */
    fun reload() {
        Routes.reload()
        session = -1
    }
}
