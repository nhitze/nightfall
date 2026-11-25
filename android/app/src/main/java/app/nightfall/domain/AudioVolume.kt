package app.nightfall.domain

object AudioVolume {
    fun output(
        master: Float,
        sound: Float,
        fade: Float = 1f,
    ): Float = normalize(master) * normalize(sound) * normalize(fade)

    private fun normalize(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}
