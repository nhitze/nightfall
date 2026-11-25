package app.nightfall.playback

import app.nightfall.domain.AudioVolume
import app.nightfall.domain.MixComponent
import app.nightfall.domain.MixFactory
import app.nightfall.domain.SoundMix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AudioOutputAdapter {
    fun play(
        component: MixComponent,
        gain: Float,
    )

    fun pause(componentId: String)

    fun resume(
        component: MixComponent,
        gain: Float,
    )

    fun setGain(
        componentId: String,
        gain: Float,
    )

    fun stop(componentId: String)
}

data class PlaybackSnapshot(
    val activeMix: SoundMix? = null,
    val playingComponentIds: Set<String> = emptySet(),
    val masterVolume: Float = 0.65f,
    val fadeMultiplier: Float = 1f,
)

class PlaybackController(
    private val output: AudioOutputAdapter,
    initialMasterVolume: Float = 0.65f,
) {
    private val components = linkedMapOf<String, MixComponent>()
    private val playingIds = linkedSetOf<String>()
    private var interruptedIds = emptySet<String>()
    private var masterVolume = AudioVolume.output(initialMasterVolume, 1f)
    private var fadeMultiplier = 1f
    private val mutableState = MutableStateFlow(snapshot())

    var activeMix: SoundMix? = null
        private set

    val state: StateFlow<PlaybackSnapshot> = mutableState.asStateFlow()

    val playingComponentIds: Set<String>
        get() = playingIds.toSet()

    val isPlaying: Boolean
        get() = playingIds.isNotEmpty()

    fun selectMix(mix: SoundMix) {
        if (activeMix?.id != mix.id) {
            stopAll(clearMix = true)
            activeMix = mix
        } else {
            activeMix = mix
        }
        publish()
    }

    fun play(mix: SoundMix) {
        stopAll(clearMix = true)
        activeMix = mix
        mix.components.forEach { component ->
            components[component.id] = component
            output.play(component, gainFor(component))
            playingIds += component.id
        }
        publish()
    }

    fun pauseAll() {
        pause(ids = playingIds.toSet(), clearInterruptionIntent = true)
    }

    fun resumeAll() {
        resume(ids = components.keys.toSet())
    }

    fun startOrResume() {
        val mix = activeMix ?: return
        if (components.isEmpty()) {
            play(mix)
        } else {
            resumeAll()
        }
    }

    fun togglePlayback() {
        if (isPlaying) pauseAll() else resumeAll()
    }

    fun toggleComponent(component: MixComponent) {
        if (component.id in playingIds) {
            pause(setOf(component.id), clearInterruptionIntent = true)
            return
        }

        val wasLoaded = components.containsKey(component.id)
        components[component.id] = component
        activeMix =
            activeMix?.let { mix ->
                if (mix.components.any { it.id == component.id }) {
                    mix.copy(components = mix.components.map { if (it.id == component.id) component else it })
                } else if (mix.components.size < MixFactory.MAXIMUM_SOUNDS) {
                    mix.copy(components = mix.components + component, name = "New Mix")
                } else {
                    mix
                }
            }
        if (wasLoaded) {
            output.resume(component, gainFor(component))
        } else {
            output.play(component, gainFor(component))
        }
        playingIds += component.id
        publish()
    }

    fun removeComponent(componentId: String) {
        if (components.remove(componentId) == null) return
        output.stop(componentId)
        playingIds -= componentId
        interruptedIds -= componentId
        activeMix =
            activeMix?.let { mix ->
                mix.copy(components = mix.components.filterNot { it.id == componentId })
            }
        publish()
    }

    fun setMasterVolume(volume: Float) {
        masterVolume = AudioVolume.output(volume, 1f)
        updateGains()
        publish()
    }

    fun setComponentVolume(
        componentId: String,
        volume: Float,
    ) {
        val component = components[componentId] ?: return
        val updated = component.copy(volume = volume.coerceIn(0f, 1f))
        components[componentId] = updated
        activeMix =
            activeMix?.let { mix ->
                mix.copy(components = mix.components.map { if (it.id == componentId) updated else it })
            }
        output.setGain(componentId, gainFor(updated))
        publish()
    }

    fun setFadeMultiplier(multiplier: Float) {
        fadeMultiplier = AudioVolume.output(multiplier, 1f)
        updateGains()
        publish()
    }

    fun onInterruptionStarted() {
        interruptedIds = playingIds.toSet()
        pause(ids = interruptedIds, clearInterruptionIntent = false)
    }

    fun onInterruptionEnded(shouldResume: Boolean) {
        val idsToResume = if (shouldResume) interruptedIds else emptySet()
        interruptedIds = emptySet()
        resume(idsToResume)
        publish()
    }

    fun onBecomingNoisy() {
        pauseAll()
    }

    fun stopAll(clearMix: Boolean = false) {
        components.keys.toList().forEach(output::stop)
        components.clear()
        playingIds.clear()
        interruptedIds = emptySet()
        if (clearMix) activeMix = null
        publish()
    }

    private fun pause(
        ids: Set<String>,
        clearInterruptionIntent: Boolean,
    ) {
        ids.forEach { id ->
            if (playingIds.remove(id)) output.pause(id)
        }
        if (clearInterruptionIntent) interruptedIds = emptySet()
        publish()
    }

    private fun resume(ids: Set<String>) {
        ids.forEach { id ->
            val component = components[id] ?: return@forEach
            if (playingIds.add(id)) {
                output.resume(component, gainFor(component))
            }
        }
        publish()
    }

    private fun updateGains() {
        components.forEach { (id, component) -> output.setGain(id, gainFor(component)) }
    }

    private fun gainFor(component: MixComponent): Float = AudioVolume.output(masterVolume, component.volume, fadeMultiplier)

    private fun snapshot() =
        PlaybackSnapshot(
            activeMix = activeMix,
            playingComponentIds = playingIds.toSet(),
            masterVolume = masterVolume,
            fadeMultiplier = fadeMultiplier,
        )

    private fun publish() {
        mutableState.value = snapshot()
    }
}
