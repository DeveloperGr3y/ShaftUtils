package io.github.developergr3y.shaftutils

import io.github.developergr3y.shaftutils.config.ModConfig
import io.github.developergr3y.shaftutils.corpse.CorpseFinder
import io.github.developergr3y.shaftutils.corpse.CorpseFinder.SpotState
import io.github.developergr3y.shaftutils.corpse.OrganDonor
import io.github.developergr3y.shaftutils.corpse.SpawnData
import io.github.developergr3y.shaftutils.debug.Probe
import io.github.developergr3y.shaftutils.hud.StatusHud
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

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            nextTick?.let {
                nextTick = null
                it()
            }
            Mineshaft.tick(client)
            CorpseFinder.tick(client)
        }

        // MoulConfig's openConfigGui() doesn't pass on the close event it saves on, so save when our screen closes.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is MoulConfigScreenComponent) ScreenEvents.remove(screen).register { saveConfig() }
        }

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
                    .then(ClientCommands.literal("probe").executes {
                        chat("Probe logs are in §f${Probe.folder.path}")
                        1
                    }),
            )
        }
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
            Probe.log("spot_added_manually", "code" to Mineshaft.code, "pos" to listOf(pos.x, pos.y, pos.z))
        } else {
            chat("§eThere's already a known spot there.")
        }
    }

    fun saveConfig() = managedConfig.saveToFile()

    fun chat(message: String) {
        Compat.chat.addClientSystemMessage(Component.literal("§b[ShaftUtils] §r$message"))
    }

    private fun id(path: String) = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
