package app.nightfall.audio

import app.nightfall.domain.SoundKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseWavGeneratorTest {
    @Test
    fun generatedNoiseIsMonoPcmWaveData() {
        val wav = NoiseWavGenerator.createWav(SoundKind.PINK_NOISE)

        assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", wav.copyOfRange(8, 12).decodeToString())
        assertEquals(1, wav.readLittleEndianShort(22))
        assertEquals(16, wav.readLittleEndianShort(34))
        assertTrue(wav.size > 44)
    }

    private fun ByteArray.readLittleEndianShort(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
}
