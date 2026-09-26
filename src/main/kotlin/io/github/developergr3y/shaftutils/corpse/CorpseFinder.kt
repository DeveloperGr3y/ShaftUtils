package io.github.developergr3y.shaftutils.corpse

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.shaft.CorpseType
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Projection
import io.github.developergr3y.shaftutils.util.stripFormatting
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Finds Frozen Corpses without seeing through walls.
 *
 *  1. On entering a shaft, every known spawn spot for that shaft type/variant becomes a "to check" waypoint.
 *  2. A spot is checked once you've had a clear line of sight to it for a moment (or stood right by it): if a corpse
 *     is there it becomes a corpse waypoint, otherwise it's cleared.
 *  3. Corpses (armour stands wearing a corpse helmet) only count once you can see them, the same rule as the spots.
 *  4. Once you've found as many corpses as the tab list says the shaft has, the remaining spots are cleared.
 *
 * In debug mode, a corpse seen somewhere that isn't a known spot is saved as a new spawn spot.
 */
object CorpseFinder {
    enum class SpotState { TO_CHECK, CLEARED, CORPSE }

    class Spot(val pos: BlockPos) {
        var state = SpotState.TO_CHECK
        /** Why it was cleared ("sight", "silence", "all found"), so a wrong call can be undone. */
        var clearedBy: String? = null
        var visibleChecks = 0
        val centre: Vec3 = Vec3.atBottomCenterOf(pos).add(0.0, 0.5, 0.0)
    }

    class Corpse(val uuid: UUID, val type: CorpseType, var pos: Vec3) {
        var looted = false
    }

    private const val CHECK_EVERY_TICKS = 5
    private const val SPOT_RANGE = 40.0
    /** Checks in a row (at 4 a second) a spot must be on screen and in sight before it counts as looked at. */
    private const val VISIBLE_CHECKS_NEEDED = 2
    /** Standing this close counts as having checked a spot / seen a corpse (buried ones included). */
    private const val CLOSE_ENOUGH = 4.0
    /** A corpse this close to a spot belongs to it. */
    private const val CORPSE_AT_SPOT = 3.0
    /** When the Organ Donor is silent, no unlooted corpse is within 20 blocks; clear spots comfortably inside that. */
    private const val SILENT_CLEAR_RANGE = 17.0
    /** Silence has to last longer than the slowest ding gap seen (~1.15s) before it means anything. */
    private const val QUIET_WINDOW_MS = 1_600L
    /** Right after looting, the ding may pause or switch corpse; don't read anything into silence then. */
    private const val AFTER_LOOT_MS = 3_000L
    /** A cleared spot is reopened if the dings fit it at least this well (and there are enough of them). */
    private const val REOPEN_FIT = 1.2
    /** A spot whose distances match the ding readings this well (blocks, RMS) is the one being dinged for. */
    private const val SPOT_FIT = 1.8

    var spots: List<Spot> = emptyList()
        private set
    val corpses = linkedMapOf<UUID, Corpse>()
    var allFound = false
        private set

    /** The spawn spot the Organ Donor readings point to, if one fits. */
    var likelySpot: Spot? = null
        private set
    /** Where the readings alone put the corpse, when no known spot fits. */
    var estimate: Vec3? = null
        private set

    private var session = -1
    private var ticks = 0
    private var lastLootAt = 0L
    /** The corpse you last right-clicked, until the tab list confirms it was looted (or it needed a key). */
    private var pendingLoot: Pair<Corpse, Long>? = null
    /** How many of each corpse type the tab list showed as looted last check. */
    private var lootedByType: Map<CorpseType, Int> = emptyMap()
    private const val LOOT_CONFIRM_MS = 8_000L
    /** Where you've been recently (time, feet), to know where the talisman was actually silent. */
    private val recent = ArrayDeque<Pair<Long, Vec3>>()

    fun register() {
        // Right-clicking doesn't mean it was looted (it may need a key you don't have): wait for the tab list.
        UseEntityCallback.EVENT.register { player, _, _, entity, _ ->
            if (player == Minecraft.getInstance().player) {
                corpses[entity.uuid]?.takeIf { !it.looted }?.let {
                    pendingLoot = it to System.currentTimeMillis()
                }
            }
            InteractionResult.PASS
        }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay && pendingLoot != null && message.string.stripFormatting().contains("Key to unlock this corpse")) {
                pendingLoot = null
            }
        }
    }

    /** A corpse counts as looted once the tab list shows one more of its type looted after you clicked it. */
    private fun confirmLoot(now: Long) {
        val looted = Mineshaft.corpses.filter { it.looted }.groupingBy { it.type }.eachCount()
        pendingLoot?.let { (corpse, at) ->
            when {
                (looted[corpse.type] ?: 0) > (lootedByType[corpse.type] ?: 0) -> {
                    corpse.looted = true
                    lastLootAt = now
                    OrganDonor.clearTarget()
                    pendingLoot = null
                }
                now - at > LOOT_CONFIRM_MS -> pendingLoot = null
            }
        }
        lootedByType = looted
    }

    fun tick(client: Minecraft) {
        if (!Mineshaft.inShaft || !ShaftUtils.config.corpses.enabled) {
            if (session != -1 && !Mineshaft.inShaft) clear()
            return
        }
        if (session != Mineshaft.session) startShaft()
        if (++ticks % CHECK_EVERY_TICKS != 0) return

        val player = client.player ?: return
        val level = client.level ?: return
        val eye = player.getEyePosition(1f)

        val now = System.currentTimeMillis()
        recent.addLast(now to player.position())
        while (recent.isNotEmpty() && now - recent.first().first > 5_000) recent.removeFirst()

        confirmLoot(now)
        scanCorpses(level, eye)
        checkSpots(level, eye)
        if (ShaftUtils.config.corpses.useOrganDonor) {
            clearOnSilence(now)
            locate()
        } else {
            likelySpot = null
            estimate = null
        }
        checkAllFound()
    }

    /**
     * Silence (once the talisman has been heard this shaft) means no unlooted corpse within 20 blocks, but only where
     * you actually were while it was silent. A spot is cleared only if you stayed within range of it for a whole quiet
     * window; teleporting next to a spot and clearing it before the next ding is exactly what we must not do.
     */
    private fun clearOnSilence(now: Long) {
        // After every corpse is found, other mods (and players) often mute the ding, so silence means nothing then.
        if (!OrganDonor.heardThisShaft || allFound) return
        val quietSince = maxOf(OrganDonor.lastDingAt, lastLootAt + AFTER_LOOT_MS)
        if (now - quietSince < QUIET_WINDOW_MS) return
        val windowStart = now - QUIET_WINDOW_MS
        val window = recent.filter { it.first >= windowStart }
        if (window.isEmpty() || recent.first().first > windowStart) return // not enough history yet
        for (spot in spots) {
            if (spot.state != SpotState.TO_CHECK) continue
            if (window.all { it.second.distanceTo(spot.centre) <= SILENT_CLEAR_RANGE }) clearSpot(spot, "silence")
        }
    }

    private fun clearSpot(spot: Spot, reason: String) {
        spot.state = SpotState.CLEARED
        spot.clearedBy = reason
    }

    /** Which spot the dings point to, or failing that an estimate from the readings alone. */
    private fun locate() {
        if (!OrganDonor.dinging || OrganDonor.samples.size < 3) {
            likelySpot = null
            estimate = null
            return
        }
        val enough = OrganDonor.samples.size >= 4 && OrganDonor.spread() >= 3.0
        val best = spots.filter { it.state == SpotState.TO_CHECK || (enough && it.state == SpotState.CLEARED && it.clearedBy != "all found") }
            .map { it to OrganDonor.fit(it.centre) }
            .filter { (spot, fit) -> fit <= if (spot.state == SpotState.TO_CHECK) SPOT_FIT else REOPEN_FIT }
            .minByOrNull { it.second }?.first
        if (best != null && best.state == SpotState.CLEARED) {
            // The dings say a corpse is here after all (we cleared it by mistake): bring it back.
            best.state = SpotState.TO_CHECK
            best.clearedBy = null
            best.visibleChecks = 0
        }
        likelySpot = best
        estimate = if (likelySpot == null) OrganDonor.estimate() else null
    }

    private fun startShaft() {
        clear()
        session = Mineshaft.session
        val type = Mineshaft.type
        val variant = Mineshaft.variant
        spots = if (type != null && variant != null) SpawnData.spots(type, variant).map { Spot(it) } else emptyList()
        if (spots.isEmpty() && ShaftUtils.config.debug.recordSpots) {
            ShaftUtils.chat("§eNo known corpse spots for §f${Mineshaft.code}§e yet. Any corpse you see will be recorded.")
        }
    }

    private fun clear() {
        session = -1
        spots = emptyList()
        corpses.clear()
        allFound = false
        recent.clear()
        lastLootAt = 0L
        pendingLoot = null
        lootedByType = emptyMap()
        likelySpot = null
        estimate = null
    }

    private fun scanCorpses(level: ClientLevel, eye: Vec3) {
        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand || entity.uuid in corpses) continue
            val helmet = entity.getItemBySlot(EquipmentSlot.HEAD)
            if (helmet.isEmpty) continue
            val id = helmet.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "").orEmpty()
            val type = CorpseType.fromHelmetId(id)
            val pos = entity.position()
            val distance = eye.distanceTo(pos)
            val seen = distance <= CLOSE_ENOUGH || Sight.canSeeAny(level, eye, Sight.spotSamples(pos.add(0.0, 0.3, 0.0)))
            if (!seen) continue

            if (type == null) {
                continue
            }
            addCorpse(entity, type, id, distance, "sight")
        }
    }

    /** A corpse-helmeted armour stand within [radius] of [centre], if there is one. */
    private fun corpseStandNear(level: ClientLevel, centre: Vec3, radius: Double): Pair<ArmorStand, CorpseType>? {
        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand || entity.position().distanceTo(centre) > radius) continue
            val id = entity.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "").orEmpty()
            CorpseType.fromHelmetId(id)?.let { return entity to it }
        }
        return null
    }

    private fun addCorpse(entity: ArmorStand, type: CorpseType, id: String, distance: Double, how: String) {
        if (entity.uuid in corpses) return
        val pos = entity.position()
        corpses[entity.uuid] = Corpse(entity.uuid, type, pos)
        if (ShaftUtils.config.corpses.announce) ShaftUtils.chat("Found a ${type.formatted}§r corpse §7(${distance.toInt()}m)")

        val shaftType = Mineshaft.type
        val variant = Mineshaft.variant
        if (ShaftUtils.config.debug.recordSpots && shaftType != null && variant != null &&
            SpawnData.learn(shaftType, variant, BlockPos.containing(pos))
        ) {
            ShaftUtils.chat("§aRecorded a new corpse spot for §f${Mineshaft.code}§a (${SpawnData.learnedCount()} learned)")
        }
    }

    private fun checkSpots(level: ClientLevel, eye: Vec3) {
        for (spot in spots) {
            if (spot.state != SpotState.TO_CHECK) continue
            val distance = eye.distanceTo(spot.centre)
            if (distance > SPOT_RANGE) {
                spot.visibleChecks = 0
                continue
            }
            // Looked at: on screen, and a clear line to it (or to its edges/top).
            val inView = distance <= CLOSE_ENOUGH ||
                (Projection.onScreen(spot.centre) && Sight.canSeeAny(level, eye, Sight.spotSamples(spot.centre)))
            spot.visibleChecks = if (inView) spot.visibleChecks + 1 else 0
            if (distance > CLOSE_ENOUGH && spot.visibleChecks < VISIBLE_CHECKS_NEEDED) continue

            // You've looked at the spot, so a corpse lying in it has been seen too (even if it's buried).
            corpseStandNear(level, spot.centre, CORPSE_AT_SPOT)?.let { (stand, type) ->
                addCorpse(stand, type, type.helmetIds.first(), distance, "spot")
            }
            if (corpses.values.any { it.pos.distanceTo(spot.centre) <= CORPSE_AT_SPOT }) {
                spot.state = SpotState.CORPSE
            } else {
                clearSpot(spot, "sight")
            }
        }
        // A corpse found anywhere near a spot settles that spot too.
        for (spot in spots) {
            if (spot.state == SpotState.TO_CHECK && corpses.values.any { it.pos.distanceTo(spot.centre) <= CORPSE_AT_SPOT }) {
                spot.state = SpotState.CORPSE
            }
        }
    }

    private fun checkAllFound() {
        val expected = Mineshaft.corpses.size
        if (allFound || expected == 0 || corpses.size < expected) return
        allFound = true
        spots.filter { it.state == SpotState.TO_CHECK }.forEach {
            it.state = SpotState.CLEARED
            it.clearedBy = "all found"
        }
        ShaftUtils.chat("§aAll $expected corpses found!")
    }

    /** Whether you have the key a corpse needs (Lapis needs none). */
    fun hasKey(type: CorpseType): Boolean {
        val key = type.key ?: return true
        val inventory = Minecraft.getInstance().player?.inventory ?: return false
        return (0 until inventory.containerSize).any { inventory.getItem(it).hoverName.string.stripFormatting().contains(key) }
    }

    private fun vec(v: Vec3) = listOf(v.x, v.y, v.z).map { Math.round(it * 10) / 10.0 }
}
