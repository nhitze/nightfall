package app.nightfall.domain

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SoundKind(
    val title: String,
    val assetFilename: String?,
    val glyph: String,
) {
    WHITE_NOISE("White noise", null, "≋"),
    PINK_NOISE("Pink noise", null, "∿"),
    BROWN_NOISE("Brown noise", null, "〰"),
    RAIN("Rain", "Rain.wav", "☂"),
    OCEAN("Ocean waves", "Ocean.wav", "≈"),
    FAN("Fan", "Fan.wav", "✺"),
    FIREPLACE("Fireplace", "Fireplace.wav", "♨"),
    TRAIN("Train", "Train.wav", "▰"),
    WIND("Wind", "Wind.wav", "≋"),
    FOREST("Forest", "Forest.wav", "♧"),
}

@Serializable
data class MixComponent(
    val sound: SoundKind,
    val volume: Float = DEFAULT_COMPONENT_VOLUME,
    val id: String = UUID.randomUUID().toString(),
) {
    init {
        require(volume.isFinite() && volume in 0f..1f) {
            "Sound volume must be between 0 and 1."
        }
    }

    companion object {
        const val DEFAULT_COMPONENT_VOLUME = 0.65f
    }
}

@Serializable
data class SoundMix(
    val name: String,
    val components: List<MixComponent>,
    val id: String = UUID.randomUUID().toString(),
    val isStarterPreset: Boolean = false,
) {
    val displayName: String
        get() = if (components.size == 1) components.first().sound.title else name

    companion object {
        fun single(sound: SoundKind): SoundMix = SoundMix(name = sound.title, components = listOf(MixComponent(sound)))

        val starterPresets =
            listOf(
                SoundMix(
                    name = "Rainy Night",
                    components =
                        listOf(
                            MixComponent(SoundKind.RAIN),
                            MixComponent(SoundKind.BROWN_NOISE, volume = 0.4f),
                        ),
                    id = "starter-rainy-night",
                    isStarterPreset = true,
                ),
                SoundMix(
                    name = "Quiet Room",
                    components =
                        listOf(
                            MixComponent(SoundKind.FAN),
                            MixComponent(SoundKind.WHITE_NOISE, volume = 0.35f),
                        ),
                    id = "starter-quiet-room",
                    isStarterPreset = true,
                ),
                SoundMix(
                    name = "Campfire",
                    components =
                        listOf(
                            MixComponent(SoundKind.FIREPLACE),
                            MixComponent(SoundKind.WIND, volume = 0.35f),
                        ),
                    id = "starter-campfire",
                    isStarterPreset = true,
                ),
            )
    }
}

@Serializable
data class NightfallPreferences(
    val activeMix: SoundMix = SoundMix.single(SoundKind.RAIN),
    val masterVolume: Float = 0.65f,
    val soundVolumes: Map<SoundKind, Float> = emptyMap(),
    val favoriteSoundIds: Set<SoundKind> = emptySet(),
    val favoriteMixes: List<SoundMix> = emptyList(),
    val recentMixes: List<SoundMix> = emptyList(),
    val timerOption: SleepTimerOption = SleepTimerOption.THIRTY_MINUTES,
    val timerDeadlineEpochMillis: Long? = null,
    val hasSeenOnboarding: Boolean = false,
    val nightModeEnabled: Boolean = false,
    val useLightAppearance: Boolean = false,
) {
    companion object {
        val initial = NightfallPreferences()
    }
}

enum class MixValidationFailure {
    EMPTY,
    TOO_MANY_SOUNDS,
    DUPLICATE_SOUND,
}

class MixValidationException(
    val failure: MixValidationFailure,
) : IllegalArgumentException(failure.name)

object MixFactory {
    const val MAXIMUM_SOUNDS = 3

    fun create(
        name: String,
        components: List<MixComponent>,
    ): SoundMix {
        if (components.isEmpty()) {
            throw MixValidationException(MixValidationFailure.EMPTY)
        }
        if (components.size > MAXIMUM_SOUNDS) {
            throw MixValidationException(MixValidationFailure.TOO_MANY_SOUNDS)
        }
        if (components.map(MixComponent::sound).distinct().size != components.size) {
            throw MixValidationException(MixValidationFailure.DUPLICATE_SOUND)
        }

        val cleanName = name.trim().ifEmpty { "My Mix" }
        return SoundMix(name = cleanName, components = components.toList())
    }
}
