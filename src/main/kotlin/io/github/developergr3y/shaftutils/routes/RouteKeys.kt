package io.github.developergr3y.shaftutils.routes

import com.mojang.blaze3d.platform.InputConstants
import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.util.Compat
import net.minecraft.client.Minecraft

/** The route's next/previous keys (set in Routes settings). Only while playing, not in menus or chat. */
object RouteKeys {
    private var nextWasDown = false
    private var backWasDown = false

    fun tick(client: Minecraft) {
        if (RouteFollower.route == null || Compat.screen != null) {
            nextWasDown = false
            backWasDown = false
            return
        }
        val config = ShaftUtils.config.routes
        val nextDown = isDown(client, config.nextKey)
        val backDown = isDown(client, config.backKey)
        if (nextDown && !nextWasDown) RouteFollower.advance()
        if (backDown && !backWasDown) RouteFollower.back()
        nextWasDown = nextDown
        backWasDown = backDown
    }

    private fun isDown(client: Minecraft, key: Int) = key > 0 && InputConstants.isKeyDown(client.window, key)
}
