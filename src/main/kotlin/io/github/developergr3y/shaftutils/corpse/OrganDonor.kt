package io.github.developergr3y.shaftutils.corpse

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.debug.Probe
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundEventListener
import net.minecraft.client.sounds.WeighedSoundEvents

/**
 * The Organ Donor talisman "Sends out a Ding when within 20 blocks of an unlooted Frozen Corpse". The ding is a
 * note block harp. We only use *when* it dings (and its pitch/volume), never where the sound comes from, since that
 * could give away a corpse's exact position through walls.
 *
 * For now this records dings for the probe log and the status display; the search-area logic comes once the probe
 * has shown how the ding actually behaves (how often, whether it speeds up, what happens with two corpses nearby).
 */
object OrganDonor : SoundEventListener {
    private const val DING = "block.note_block.harp"

    /** When the last ding was heard (epoch millis), 0 if none this shaft. */
    var lastDingAt = 0L
        private set
    var dingsThisShaft = 0
        private set
    /** Gap between the last two dings, in ms. */
    var lastGapMs: Long? = null
        private set

    private var session = -1

    fun register() {
        Minecraft.getInstance().soundManager.addListener(this)
    }

    override fun onPlaySound(sound: SoundInstance, events: WeighedSoundEvents, range: Float) {
        if (!Mineshaft.inShaft) return
        if (session != Mineshaft.session) reset()
        val id = sound.identifier.toString()
        val isDing = id.endsWith(DING)
        if (!isDing && !(Probe.enabled && ShaftUtils.config.debug.logAllSounds)) return

        val player = Minecraft.getInstance().player
        val now = System.currentTimeMillis()
        if (isDing) {
            if (lastDingAt > 0) lastGapMs = now - lastDingAt
            lastDingAt = now
            dingsThisShaft++
        }
        Probe.log(
            if (isDing) "ding" else "sound",
            "id" to id,
            "source" to sound.source.name,
            "pitch" to sound.pitch.toDouble(),
            "volume" to sound.volume.toDouble(),
            "attenuation" to sound.attenuation.name,
            "soundPos" to listOf(sound.x, sound.y, sound.z).map { Math.round(it * 10) / 10.0 },
            "playerPos" to player?.let { listOf(it.x, it.y, it.z).map { v -> Math.round(v * 10) / 10.0 } },
            "gapMs" to if (isDing) lastGapMs else null,
            "code" to Mineshaft.code,
        )
    }

    private fun reset() {
        session = Mineshaft.session
        lastDingAt = 0L
        dingsThisShaft = 0
        lastGapMs = null
    }
}
