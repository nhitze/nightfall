package app.nightfall.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioVolumeTest {
    @Test
    fun outputVolumeCombinesMasterTrackAndFadeLevels() {
        assertEquals(0.1f, AudioVolume.output(master = 0.5f, sound = 0.4f, fade = 0.5f), 0.0001f)
    }

    @Test
    fun outputVolumeClampsEachInput() {
        assertEquals(0f, AudioVolume.output(master = -1f, sound = 0.5f), 0f)
        assertEquals(1f, AudioVolume.output(master = 2f, sound = 2f), 0f)
    }
}
