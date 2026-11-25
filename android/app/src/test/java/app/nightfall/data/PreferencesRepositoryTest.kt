package app.nightfall.data

import app.nightfall.domain.MixComponent
import app.nightfall.domain.MixFactory
import app.nightfall.domain.NightfallPreferences
import app.nightfall.domain.SleepTimerOption
import app.nightfall.domain.SoundKind
import app.nightfall.domain.SoundMix
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesRepositoryTest {
    @Test
    fun savedPreferencesRoundTripAcrossRepositoryInstances() {
        val storage = InMemoryPreferencesStorage()
        val preferences =
            NightfallPreferences.initial.copy(
                activeMix =
                    MixFactory.create(
                        name = "Rainy Night",
                        components =
                            listOf(
                                MixComponent(SoundKind.RAIN, volume = 0.8f),
                                MixComponent(SoundKind.BROWN_NOISE, volume = 0.3f),
                            ),
                    ),
                masterVolume = 0.42f,
                soundVolumes = mapOf(SoundKind.RAIN to 0.8f),
                favoriteSoundIds = setOf(SoundKind.FOREST),
                favoriteMixes = listOf(SoundMix.starterPresets[0]),
                timerOption = SleepTimerOption.NINETY_MINUTES,
                timerDeadlineEpochMillis = 1_800_000_300L,
                hasSeenOnboarding = true,
                nightModeEnabled = true,
            )
        val firstRepository = PreferencesRepository(storage)

        firstRepository.save(preferences)

        assertEquals(preferences, PreferencesRepository(storage).load())
    }

    @Test
    fun missingOrMalformedPreferencesUseDefaults() {
        val storage = InMemoryPreferencesStorage()
        val repository = PreferencesRepository(storage)

        assertEquals(NightfallPreferences.initial, repository.load())

        storage.write("{not-json")

        assertEquals(NightfallPreferences.initial, repository.load())
    }
}

private class InMemoryPreferencesStorage : PreferencesStorage {
    private var value: String? = null

    override fun read(): String? = value

    override fun write(value: String) {
        this.value = value
    }
}
