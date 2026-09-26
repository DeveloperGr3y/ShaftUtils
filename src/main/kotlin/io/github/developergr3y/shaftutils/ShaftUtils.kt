package io.github.developergr3y.shaftutils

import io.github.developergr3y.shaftutils.config.ModConfig
import io.github.developergr3y.shaftutils.corpse.CorpseFinder
import io.github.developergr3y.shaftutils.corpse.CorpseFinder.SpotState
import io.github.developergr3y.shaftutils.corpse.OrganDonor
import io.github.developergr3y.shaftutils.corpse.SpawnData
import io.github.developergr3y.shaftutils.hud.HudEditScreen
import io.github.developergr3y.shaftutils.hud.HudPosition
import io.github.developergr3y.shaftutils.hud.RouteRender
import io.github.developergr3y.shaftutils.routes.RouteKeys
import io.github.developergr3y.shaftutils.profit.ShaftProfit
import io.github.developergr3y.shaftutils.hud.StatusHud
import io.github.developergr3y.shaftutils.routes.RouteFollower
import io.github.developergr3y.shaftutils.routes.Routes
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.developergr3y.shaftutils.hud.Waypoints
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Compat
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory
import java.io.File

object ShaftUtils : ClientModInitializer {
    const val MOD_ID = "shaftutils"

    val logger = LoggerFactory.getLogger("ShaftUtils")
    val configDir = File(FabricLoader.getInstance().configDir.toFile(), MOD_ID)
    val version: String = FabricLoader.getInstance().getModContainer(MOD_ID)
        .map { it.metadata.version.friendlyString }.orElse("dev")

    private lateinit var managedConfig: ManagedConfig<ModConfig>
    val config: ModConfig get() = managedConfig.instance

    /** Opening a screen straight from a chat command gets undone when the chat closes, so wait a tick. */
    private var nextTick: (() -> Unit)? = null

    override fun onInitializeClient() {
        configDir.mkdirs()
        managedConfig = ManagedConfig.create(File(configDir, "config.json"), ModConfig::class.java) {
            checkExpose = false
        }
        SpawnData.load()

        ClientLifecycleEvents.CLIENT_STARTED.register { OrganDonor.register() }
        CorpseFinder.register()
        ShaftProfit.register()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            nextTick?.let {
                nextTick = null
                it()
            }
            Mineshaft.tick(client)
            CorpseFinder.tick(client)
            RouteFollower.tick(client)
            RouteKeys.tick(client)
            ShaftProfit.tick(client)
        }

        // MoulConfig's openConfigGui() doesn't pass on the close event it saves on, so save when our screen closes.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is MoulConfigScreenComponent) ScreenEvents.remove(screen).register { saveConfig() }
        }

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("route"), RouteRender::render)
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("waypoints"), Waypoints::render)
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("status"), StatusHud::render)

        registerCommands()
    }

    private fun registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("shaftutils")
                    .executes {
                        nextTick = { managedConfig.openConfigGui() }
                        1
                    }
                    .then(ClientCommands.literal("gui").executes {
                        openHudEditor()
                        1
                    })
                    .then(ClientCommands.literal("export").executes {
                        exportSpots()
                        1
                    })
                    .then(ClientCommands.literal("status").executes {
                        status()
                        1
                    })
                    .then(ClientCommands.literal("addspot").executes {
                        addSpotHere()
                        1
                    })
                    .then(routeCommand())
,
            )
        }
    }

    /**
     * /shaftutils route: import (from clipboard), next, back, restart, reload, list, delete, folder.
     * Import and delete work on the shaft you're in, or name a code: /shaftutils route import TOPA_1
     */
    private fun routeCommand() = ClientCommands.literal("route")
        .executes {
            val route = RouteFollower.route
            chat(
                if (route == null) "No route for §f${Mineshaft.code ?: "this area"}§r. Copy one and run §e/shaftutils route import"
                else "Route §f${Mineshaft.code}§r: point ${RouteFollower.index + 1}/${route.size}",
            )
            chat("§7Routes: §f${Routes.list().joinToString().ifEmpty { "none yet" }}")
            1
        }
        .then(ClientCommands.literal("import")
            .executes { importRoute(Mineshaft.code); 1 }
            .then(ClientCommands.argument("code", StringArgumentType.greedyString()).executes {
                importRoute(codeArg(StringArgumentType.getString(it, "code")) ?: return@executes 0); 1
            }))
        .then(ClientCommands.literal("delete")
            .executes { deleteRoute(Mineshaft.code); 1 }
            .then(ClientCommands.argument("code", StringArgumentType.greedyString()).executes {
                deleteRoute(codeArg(StringArgumentType.getString(it, "code")) ?: return@executes 0); 1
            }))
        .then(ClientCommands.literal("next").executes { RouteFollower.advance(); 1 })
        .then(ClientCommands.literal("back").executes { RouteFollower.back(); 1 })
        .then(ClientCommands.literal("restart").executes { RouteFollower.restart(); 1 })
        .then(ClientCommands.literal("reload").executes {
            RouteFollower.reload()
            chat("Reloaded routes.")
            1
        })
        .then(ClientCommands.literal("list").executes {
            chat("Routes: §f${Routes.list().joinToString().ifEmpty { "none yet" }}")
            1
        })
        .then(ClientCommands.literal("folder").executes { openRoutesFolder(); 1 })

    /** A typed shaft code, tidied up ("amber_1" -> "AMBE_1"), or null with a message if it isn't one. */
    private fun codeArg(input: String): String? {
        Routes.normaliseCode(input)?.let { return it }
        chat("§c\"$input\" isn't a shaft code. Use the code from the scoreboard, e.g. §fTOPA_1§c, §fRUBY_C§c or §fCRYSTAL§c.")
        return null
    }

    private fun importRoute(code: String?) {
        if (code == null) {
            chat("§cEnter a shaft first, or name one: §e/shaftutils route import TOPA_1")
            return
        }
        val text = net.minecraft.client.Minecraft.getInstance().keyboardHandler.clipboard
        try {
            val count = Routes.import(code, text)
            chat("§aSaved a $count-point route for §f$code§a from your clipboard.")
            if (code == Mineshaft.code) RouteFollower.reload()
        } catch (e: Exception) {
            chat("§cCouldn't read a route from your clipboard: ${e.message}")
        }
    }

    private fun deleteRoute(code: String?) {
        if (code == null) {
            chat("§cName a shaft: §e/shaftutils route delete TOPA_1")
            return
        }
        chat(if (Routes.delete(code)) "Deleted the route for §f$code§r." else "§cNo route for §f$code§c.")
        if (code == Mineshaft.code) RouteFollower.reload()
    }

    fun openRoutesFolder() {
        Routes.dir.mkdirs()
        net.minecraft.util.Util.getPlatform().openFile(Routes.dir)
    }

    fun exportSpots() {
        val file = SpawnData.export()
        chat("§aExported spawn spots to §f${file.path}")
    }

    private fun status() {
        chat("Shaft: §f${Mineshaft.code ?: "not in a mineshaft"}")
        if (Mineshaft.inShaft) {
            chat("Corpses (tab): §f${Mineshaft.corpses.joinToString { "${it.type.label} ${if (it.looted) "looted" else "not looted"}" }.ifEmpty { "none found" }}")
            val spots = CorpseFinder.spots
            chat("Spots: §f${spots.count { it.state == SpotState.TO_CHECK }} to check, ${spots.count { it.state == SpotState.CLEARED }} cleared, ${spots.count { it.state == SpotState.CORPSE }} with a corpse")
        }
        chat("Learned spots: §f${SpawnData.learnedCount()}")
    }

    /** Debug: record where you're standing as a corpse spot for this shaft (for corpses the finder missed). */
    private fun addSpotHere() {
        val player = net.minecraft.client.Minecraft.getInstance().player ?: return
        val type = Mineshaft.type
        val variant = Mineshaft.variant
        if (type == null || variant == null) {
            chat("§cYou need to be in a mineshaft.")
            return
        }
        val pos = BlockPos.containing(player.position())
        if (SpawnData.learn(type, variant, pos)) {
            chat("§aAdded a corpse spot for §f${Mineshaft.code}§a at ${pos.x}, ${pos.y}, ${pos.z}")
        } else {
            chat("§eThere's already a known spot there.")
        }
    }

    fun openHudEditor() {
        nextTick = { Compat.setScreen(HudEditScreen()) }
    }

    fun resetHud() {
        config.corpses.statusPosition = HudPosition()
        saveConfig()
    }

    fun saveConfig() = managedConfig.saveToFile()

    fun chat(message: String) {
        Compat.chat.addClientSystemMessage(Component.literal("§b[ShaftUtils] §r$message"))
    }

    private fun id(path: String) = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
