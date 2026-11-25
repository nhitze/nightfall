package app.nightfall.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MixFactoryTest {
    @Test
    fun createMixAllowsThreeDistinctSounds() {
        val mix =
            MixFactory.create(
                name = "Rainy Night",
                components =
                    listOf(
                        MixComponent(SoundKind.RAIN),
                        MixComponent(SoundKind.BROWN_NOISE),
                        MixComponent(SoundKind.WIND),
                    ),
            )

        assertEquals("Rainy Night", mix.name)
        assertEquals(3, mix.components.size)
    }

    @Test
    fun createMixRejectsMoreThanThreeSounds() {
        val error =
            assertThrows(MixValidationException::class.java) {
                MixFactory.create(
                    name = "Too many",
                    components =
                        listOf(
                            MixComponent(SoundKind.RAIN),
                            MixComponent(SoundKind.OCEAN),
                            MixComponent(SoundKind.FAN),
                            MixComponent(SoundKind.WIND),
                        ),
                )
            }

        assertEquals(MixValidationFailure.TOO_MANY_SOUNDS, error.failure)
    }

    @Test
    fun createMixRejectsEmptyAndDuplicateSoundSets() {
        val emptyError =
            assertThrows(MixValidationException::class.java) {
                MixFactory.create(name = "Empty", components = emptyList())
            }
        assertEquals(MixValidationFailure.EMPTY, emptyError.failure)

        val duplicateError =
            assertThrows(MixValidationException::class.java) {
                MixFactory.create(
                    name = "Duplicate",
                    components = listOf(MixComponent(SoundKind.RAIN), MixComponent(SoundKind.RAIN)),
                )
            }
        assertEquals(MixValidationFailure.DUPLICATE_SOUND, duplicateError.failure)
    }
}
