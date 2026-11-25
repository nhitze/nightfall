package app.nightfall.audio

import app.nightfall.domain.SoundKind
import kotlin.math.roundToInt

object NoiseWavGenerator {
    private const val SAMPLE_RATE = 22_050
    private const val DURATION_SECONDS = 8
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    fun createWav(sound: SoundKind): ByteArray {
        require(sound in GENERATED_SOUNDS) { "$sound is a bundled sound, not generated noise." }

        val sampleCount = SAMPLE_RATE * DURATION_SECONDS
        val samples = FloatArray(sampleCount)
        var random = 0xA341_316CL.toInt() xor (sound.ordinal + 1)
        var previous = 0f
        var brown = 0f

        for (index in samples.indices) {
            random = random xor (random shl 13)
            random = random xor (random ushr 17)
            random = random xor (random shl 5)
            val white = random.toFloat() / Int.MAX_VALUE.toFloat()
            samples[index] =
                when (sound) {
                    SoundKind.WHITE_NOISE -> white * 0.55f
                    SoundKind.PINK_NOISE -> {
                        previous = previous * 0.985f + white * 0.12f
                        previous * 0.9f
                    }
                    SoundKind.BROWN_NOISE -> {
                        brown = (brown + white * 0.018f).coerceIn(-0.8f, 0.8f)
                        brown * 0.85f
                    }
                    else -> error("Unexpected generated sound: $sound")
                }
        }

        makeBoundaryContinuous(samples)
        return toPcmWav(samples)
    }

    private fun makeBoundaryContinuous(samples: FloatArray) {
        if (samples.isEmpty()) return
        val boundary = (samples.first() + samples.last()) * 0.5f
        samples[0] = boundary
        samples[samples.lastIndex] = boundary
    }

    private fun toPcmWav(samples: FloatArray): ByteArray {
        val pcmBytes = samples.size * (BITS_PER_SAMPLE / 8)
        val wav = ByteArray(44 + pcmBytes)
        copyAscii(wav, 0, "RIFF")
        writeInt(wav, 4, 36 + pcmBytes)
        copyAscii(wav, 8, "WAVE")
        copyAscii(wav, 12, "fmt ")
        writeInt(wav, 16, 16)
        writeShort(wav, 20, 1)
        writeShort(wav, 22, CHANNELS)
        writeInt(wav, 24, SAMPLE_RATE)
        writeInt(wav, 28, SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8))
        writeShort(wav, 32, CHANNELS * (BITS_PER_SAMPLE / 8))
        writeShort(wav, 34, BITS_PER_SAMPLE)
        copyAscii(wav, 36, "data")
        writeInt(wav, 40, pcmBytes)

        samples.forEachIndexed { index, sample ->
            val pcm = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt()
            writeShort(wav, 44 + index * 2, pcm)
        }
        return wav
    }

    private fun copyAscii(
        target: ByteArray,
        offset: Int,
        value: String,
    ) {
        value.encodeToByteArray().copyInto(target, destinationOffset = offset)
    }

    private fun writeShort(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeInt(
        target: ByteArray,
        offset: Int,
        value: Int,
    ) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }

    private val GENERATED_SOUNDS =
        setOf(
            SoundKind.WHITE_NOISE,
            SoundKind.PINK_NOISE,
            SoundKind.BROWN_NOISE,
        )
}
