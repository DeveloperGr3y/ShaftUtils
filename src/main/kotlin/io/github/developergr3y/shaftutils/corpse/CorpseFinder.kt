package io.github.developergr3y.shaftutils.corpse

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.debug.Probe
import io.github.developergr3y.shaftutils.shaft.CorpseType
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Projection
import io.github.developergr3y.shaftutils.util.stripFormatting
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.Level
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
    /** A spot whose distances match the ding readings this well (blocks, RMS) is the one being dinged for. */
    private const val SPOT_FIT = 1.8
    private const val POSITION_LOG_MS = 500L

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

    fun register() {
        UseEntityCallback.EVENT.register { player, _, _, entity, _ ->
            if (player == Minecraft.getInstance().player) {
                corpses[entity.uuid]?.let {
                    if (!it.looted) {
                        it.looted = true
                        OrganDonor.clearTarget()
                        Probe.log("corpse_interact", "type" to it.type.label, "pos" to vec(it.pos), "code" to Mineshaft.code)
                    }
                }
            }
            InteractionResult.PASS
        }
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

        scanCorpses(level, eye)
        checkSpots(level, eye)
        if (ShaftUtils.config.corpses.useOrganDonor) {
            clearOnSilence(player.position())
            locate()
        } else {
            likelySpot = null
            estimate = null
        }
        checkAllFound()
        logPosition(player.position())
    }

    /** Silence (while the talisman has been heard this shaft) means no unlooted corpse within 20 blocks. */
    private fun clearOnSilence(feet: Vec3) {
        if (!OrganDonor.silent) return
        for (spot in spots) {
            if (spot.state == SpotState.TO_CHECK && spot.centre.distanceTo(feet) <= SILENT_CLEAR_RANGE) {
                spot.state = SpotState.CLEARED
                Probe.log("spot_checked", "code" to Mineshaft.code, "pos" to listOf(spot.pos.x, spot.pos.y, spot.pos.z), "result" to "CLEARED", "reason" to "silence")
            }
        }
    }

    /** Which spot the dings point to, or failing that an estimate from the readings alone. */
    private fun locate() {
        if (!OrganDonor.dinging || OrganDonor.samples.size < 3) {
            likelySpot = null
            estimate = null
            return
        }
        likelySpot = spots.filter { it.state == SpotState.TO_CHECK }
            .map { it to OrganDonor.fit(it.centre) }
            .filter { it.second <= SPOT_FIT }
            .minByOrNull { it.second }?.first
        estimate = if (likelySpot == null) OrganDonor.estimate() else null
    }

    private var lastPositionLog = 0L

    /** Probe: where you are twice a second, so dings and silences can be matched to positions afterwards. */
    private fun logPosition(feet: Vec3) {
        val now = System.currentTimeMillis()
        if (!Probe.enabled || now - lastPositionLog < POSITION_LOG_MS) return
        lastPositionLog = now
        Probe.log("pos", "p" to vec(feet), "dinging" to OrganDonor.dinging, "code" to Mineshaft.code)
    }

    private fun startShaft() {
        clear()
        session = Mineshaft.session
        val type = Mineshaft.type
        val variant = Mineshaft.variant
        spots = if (type != null && variant != null) SpawnData.spots(type, variant).map { Spot(it) } else emptyList()
        Probe.log("spots_loaded", "code" to Mineshaft.code, "count" to spots.size)
        if (spots.isEmpty() && ShaftUtils.config.debug.recordSpots) {
            ShaftUtils.chat("§eNo known corpse spots for §f${Mineshaft.code}§e yet. Any corpse you see will be recorded.")
        }
    }

    private fun clear() {
        session = -1
        spots = emptyList()
        corpses.clear()
        allFound = false
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
            val seen = distance <= CLOSE_ENOUGH || Sight.canSee(level, eye, pos.add(0.0, 0.3, 0.0)) ||
                Sight.canSee(level, eye, pos.add(0.0, 1.4, 0.0))
            if (!seen) continue

            if (type == null) {
                // Unknown helmet: log it (once seen) so we can spot corpse helmets we don't know about yet.
                Probe.log("armor_stand_seen", "helmetId" to id, "helmetName" to helmet.hoverName.string.stripFormatting(), "pos" to vec(pos))
                continue
            }
            corpses[entity.uuid] = Corpse(entity.uuid, type, pos)
            Probe.log("corpse_seen", "type" to type.label, "helmetId" to id, "pos" to vec(pos), "distance" to distance, "code" to Mineshaft.code)
            if (ShaftUtils.config.corpses.announce) ShaftUtils.chat("Found a ${type.formatted}§r corpse §7(${distance.toInt()}m)")

            val shaftType = Mineshaft.type
            val variant = Mineshaft.variant
            if (ShaftUtils.config.debug.recordSpots && shaftType != null && variant != null &&
                SpawnData.learn(shaftType, variant, BlockPos.containing(pos))
            ) {
                ShaftUtils.chat("§aRecorded a new corpse spot for §f${Mineshaft.code}§a (${SpawnData.learnedCount()} learned)")
                Probe.log("spot_learned", "code" to Mineshaft.code, "pos" to vec(pos))
            }
        }
    }

    private fun checkSpots(level: Level, eye: Vec3) {
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

            val corpse = corpses.values.any { it.pos.distanceTo(spot.centre) <= CORPSE_AT_SPOT }
            spot.state = if (corpse) SpotState.CORPSE else SpotState.CLEARED
            Probe.log("spot_checked", "code" to Mineshaft.code, "pos" to listOf(spot.pos.x, spot.pos.y, spot.pos.z), "result" to spot.state.name, "distance" to distance)
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
        spots.filter { it.state == SpotState.TO_CHECK }.forEach { it.state = SpotState.CLEARED }
        Probe.log("all_found", "code" to Mineshaft.code, "count" to expected)
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
