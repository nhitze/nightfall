package app.nightfall.playback

import app.nightfall.domain.MixComponent
import app.nightfall.domain.MixFactory
import app.nightfall.domain.SoundKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControllerTest {
    @Test
    fun newControllerDoesNotAutoplay() {
        val output = InMemoryAudioOutput()
        val controller = PlaybackController(output)

        assertFalse(controller.isPlaying)
        assertTrue(output.playingIds.isEmpty())
    }

    @Test
    fun playingMixCombinesMasterAndPerSoundVolumes() {
        val output = InMemoryAudioOutput()
        val controller = PlaybackController(output)
        val mix =
            MixFactory.create(
                name = "Rainy Night",
                components =
                    listOf(
                        MixComponent(SoundKind.RAIN, volume = 0.8f),
                        MixComponent(SoundKind.BROWN_NOISE, volume = 0.4f),
                    ),
            )

        controller.play(mix)

        assertEquals(mix.components.map { it.id }.toSet(), output.playingIds)
        assertEquals(0.52f, output.gainFor(mix.components[0].id), 0.0001f)
        assertEquals(0.26f, output.gainFor(mix.components[1].id), 0.0001f)
    }

    @Test
    fun interruptionResumesPreviouslyPlayingSoundsWhenAllowed() {
        val output = InMemoryAudioOutput()
        val controller = PlaybackController(output)
        val mix = MixFactory.create("Rainy Night", listOf(MixComponent(SoundKind.RAIN)))
        controller.play(mix)

        controller.onInterruptionStarted()
        controller.onInterruptionEnded(shouldResume = true)

        assertTrue(controller.isPlaying)
        assertEquals(setOf(mix.components.single().id), output.playingIds)
    }

    @Test
    fun userPauseDuringInterruptionPreventsAutomaticResume() {
        val output = InMemoryAudioOutput()
        val controller = PlaybackController(output)
        val mix = MixFactory.create("Rainy Night", listOf(MixComponent(SoundKind.RAIN)))
        controller.play(mix)

        controller.onInterruptionStarted()
        controller.pauseAll()
        controller.onInterruptionEnded(shouldResume = true)

        assertEquals(emptySet<String>(), output.playingIds)
    }

    @Test
    fun pausingOneMixComponentLeavesOtherComponentsPlaying() {
        val output = InMemoryAudioOutput()
        val controller = PlaybackController(output)
        val mix =
            MixFactory.create(
                "Rainy Night",
                listOf(
                    MixComponent(SoundKind.RAIN),
                    MixComponent(SoundKind.BROWN_NOISE),
                ),
            )
        val rain = mix.components[0]
        val brown = mix.components[1]
        controller.play(mix)

        controller.toggleComponent(rain)

        assertEquals(setOf(brown.id), output.playingIds)

        controller.toggleComponent(rain)

        assertEquals(setOf(rain.id, brown.id), output.playingIds)
    }

    @Test
    fun masterVolumeChangesEveryPlayingSound() {
        val output = InMemoryAudioOutput()
        val controller = PlaybackController(output)
        val mix =
            MixFactory.create(
                "Rainy Night",
                listOf(
                    MixComponent(SoundKind.RAIN, volume = 0.8f),
                    MixComponent(SoundKind.BROWN_NOISE, volume = 0.4f),
                ),
            )
        controller.play(mix)

        controller.setMasterVolume(0.5f)

        assertEquals(0.4f, output.gainFor(mix.components[0].id), 0.0001f)
        assertEquals(0.2f, output.gainFor(mix.components[1].id), 0.0001f)
    }

    @Test
    fun stoppingMixStopsEverySound() {
        val output = InMemoryAudioOutput()
        val controller = PlaybackController(output)
        val mix =
            MixFactory.create(
                "Rainy Night",
                listOf(
                    MixComponent(SoundKind.RAIN),
                    MixComponent(SoundKind.BROWN_NOISE),
                ),
            )
        controller.play(mix)

        controller.stopAll(clearMix = true)

        assertTrue(output.playingIds.isEmpty())
        assertFalse(controller.isPlaying)
        assertEquals(null, controller.activeMix)
    }

    @Test
    fun selectingMixDoesNotAutoplayAndComponentCanStartIndependently() {
        val output = InMemoryAudioOutput()
        val controller = PlaybackController(output)
        val mix =
            MixFactory.create(
                "Rainy Night",
                listOf(
                    MixComponent(SoundKind.RAIN),
                    MixComponent(SoundKind.BROWN_NOISE),
                ),
            )
        val rain = mix.components.first()

        controller.selectMix(mix)

        assertFalse(controller.isPlaying)
        assertTrue(output.playingIds.isEmpty())

        controller.toggleComponent(rain)

        assertEquals(setOf(rain.id), output.playingIds)
    }
}

private class InMemoryAudioOutput : AudioOutputAdapter {
    val playingIds = mutableSetOf<String>()
    private val gains = mutableMapOf<String, Float>()

    override fun play(
        component: MixComponent,
        gain: Float,
    ) {
        playingIds += component.id
        gains[component.id] = gain
    }

    override fun pause(componentId: String) {
        playingIds.remove(componentId)
    }

    override fun resume(
        component: MixComponent,
        gain: Float,
    ) {
        play(component, gain)
    }

    override fun setGain(
        componentId: String,
        gain: Float,
    ) {
        gains[componentId] = gain
    }

    override fun stop(componentId: String) {
        playingIds.remove(componentId)
        gains.remove(componentId)
    }

    fun gainFor(componentId: String): Float = gains.getValue(componentId)
}
