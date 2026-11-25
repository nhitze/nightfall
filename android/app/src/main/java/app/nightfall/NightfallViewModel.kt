package app.nightfall

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nightfall.data.PreferencesRepository
import app.nightfall.data.SharedPreferencesStorage
import app.nightfall.domain.MixComponent
import app.nightfall.domain.MixFactory
import app.nightfall.domain.NightfallPreferences
import app.nightfall.domain.SleepTimerOption
import app.nightfall.domain.SoundKind
import app.nightfall.domain.SoundMix
import app.nightfall.playback.AudioPlaybackService
import app.nightfall.playback.PlaybackSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class NightfallViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val preferencesRepository =
        PreferencesRepository(SharedPreferencesStorage(application))
    private val mutablePreferences =
        MutableStateFlow(preferencesRepository.load())
    private val mutablePlayback =
        MutableStateFlow(
            PlaybackSnapshot(
                activeMix = mutablePreferences.value.activeMix,
                masterVolume = mutablePreferences.value.masterVolume,
            ),
        )
    private val mutableTimerRemainingMillis = MutableStateFlow<Long?>(null)
    private val mutableErrorMessage = MutableStateFlow<String?>(null)
    private val appContext = application.applicationContext

    private var audioBinder: AudioPlaybackService.LocalBinder? = null
    private var serviceBound = false
    private var pendingServiceAction: ((AudioPlaybackService.LocalBinder) -> Unit)? = null
    private var playbackStateJob: Job? = null

    val preferences: StateFlow<NightfallPreferences> = mutablePreferences.asStateFlow()
    val playback: StateFlow<PlaybackSnapshot> = mutablePlayback.asStateFlow()
    val timerRemainingMillis: StateFlow<Long?> = mutableTimerRemainingMillis.asStateFlow()
    val errorMessage: StateFlow<String?> = mutableErrorMessage.asStateFlow()

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val binder = service as? AudioPlaybackService.LocalBinder ?: return
                audioBinder = binder
                binder.setMasterVolume(mutablePreferences.value.masterVolume)
                binder.selectMix(mutablePreferences.value.activeMix)
                binder.setTimerDeadline(mutablePreferences.value.timerDeadlineEpochMillis)
                playbackStateJob?.cancel()
                playbackStateJob =
                    viewModelScope.launch {
                        binder.playbackState.collect { state ->
                            mutablePlayback.value = state
                        }
                    }
                pendingServiceAction?.also { action ->
                    pendingServiceAction = null
                    action(binder)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playbackStateJob?.cancel()
                playbackStateJob = null
                audioBinder = null
            }
        }

    init {
        connectToPlaybackService()
        refreshTimerRemaining()
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                refreshTimerRemaining()
            }
        }
    }

    fun togglePlayback() {
        val currentBinder = audioBinder
        if (mutablePlayback.value.playingComponentIds.isNotEmpty()) {
            currentBinder?.pause()
            return
        }

        val mix = mutablePreferences.value.activeMix
        if (currentBinder == null) {
            withAudioService { startMix(it, mix) }
        } else if (currentBinder.playbackState.value.activeMix == null) {
            startMix(currentBinder, mix)
        } else {
            if (!currentBinder.resume()) showAudioError()
        }
    }

    fun activate(mix: SoundMix) {
        val updated = applySavedVolumes(mix)
        updatePreferences { current ->
            current.copy(activeMix = updated, recentMixes = remember(current.recentMixes, updated))
        }

        val binder = audioBinder
        if (binder == null) return
        if (mutablePlayback.value.playingComponentIds.isNotEmpty()) {
            startMix(binder, updated)
        } else {
            binder.selectMix(updated)
        }
    }

    fun select(sound: SoundKind) {
        val volume = mutablePreferences.value.soundVolumes[sound] ?: MixComponent.DEFAULT_COMPONENT_VOLUME
        activate(SoundMix(name = sound.title, components = listOf(MixComponent(sound, volume))))
    }

    fun addToMix(sound: SoundKind) {
        val current = mutablePreferences.value.activeMix
        if (current.components.any { it.sound == sound }) return
        val volume = mutablePreferences.value.soundVolumes[sound] ?: 0.45f
        try {
            activate(
                MixFactory.create(
                    name = "New Mix",
                    components = current.components + MixComponent(sound, volume),
                ),
            )
        } catch (_: IllegalArgumentException) {
            mutableErrorMessage.value = "A mix can contain up to three different sounds."
        }
    }

    fun remove(component: MixComponent) {
        val remaining = mutablePreferences.value.activeMix.components.filterNot { it.id == component.id }
        val updated =
            if (remaining.isEmpty()) {
                SoundMix.single(SoundKind.RAIN)
            } else {
                mutablePreferences.value.activeMix.copy(
                    components = remaining,
                    name = if (remaining.size == 1) remaining.single().sound.title else mutablePreferences.value.activeMix.name,
                )
            }
        updatePreferences { it.copy(activeMix = updated) }
        audioBinder?.removeComponent(component.id)
        audioBinder?.selectMix(updated)
    }

    fun toggleComponent(component: MixComponent) {
        val mix = mutablePreferences.value.activeMix
        withAudioService { binder ->
            binder.selectMix(mix)
            if (!binder.toggleComponent(component)) showAudioError()
        }
    }

    fun setSoundVolume(
        component: MixComponent,
        volume: Float,
    ) {
        val normalized = volume.coerceIn(0f, 1f)
        val current = mutablePreferences.value
        val updatedComponents =
            current.activeMix.components.map {
                if (it.id == component.id) it.copy(volume = normalized) else it
            }
        val updatedMix = current.activeMix.copy(components = updatedComponents)
        updatePreferences {
            it.copy(
                activeMix = updatedMix,
                soundVolumes = it.soundVolumes + (component.sound to normalized),
                favoriteMixes = updateStoredMixes(it.favoriteMixes, updatedMix),
                recentMixes = updateStoredMixes(it.recentMixes, updatedMix),
            )
        }
        audioBinder?.setComponentVolume(component.id, normalized)
    }

    fun soundVolume(sound: SoundKind): Float =
        mutablePreferences.value.activeMix.components.firstOrNull { it.sound == sound }?.volume
            ?: mutablePreferences.value.soundVolumes[sound]
            ?: MixComponent.DEFAULT_COMPONENT_VOLUME

    fun setMasterVolume(volume: Float) {
        val normalized = volume.coerceIn(0f, 1f)
        updatePreferences { it.copy(masterVolume = normalized) }
        audioBinder?.setMasterVolume(normalized)
    }

    fun setTimerOption(option: SleepTimerOption) {
        updatePreferences { it.copy(timerOption = option) }
    }

    fun startTimer() {
        val deadline =
            mutablePreferences.value.timerOption.deadline(
                from = Instant.now(),
                zone = ZoneId.systemDefault(),
            ).toEpochMilli()
        updatePreferences { it.copy(timerDeadlineEpochMillis = deadline) }
        audioBinder?.setTimerDeadline(deadline)
    }

    fun cancelTimer() {
        updatePreferences { it.copy(timerDeadlineEpochMillis = null) }
        audioBinder?.setTimerDeadline(null)
    }

    fun toggleFavorite(sound: SoundKind) {
        updatePreferences { current ->
            val favorites =
                if (sound in current.favoriteSoundIds) {
                    current.favoriteSoundIds - sound
                } else {
                    current.favoriteSoundIds + sound
                }
            current.copy(favoriteSoundIds = favorites)
        }
    }

    fun isFavorite(sound: SoundKind): Boolean = sound in mutablePreferences.value.favoriteSoundIds

    fun toggleFavorite(mix: SoundMix) {
        updatePreferences { current ->
            val existing = current.favoriteMixes.indexOfFirst { sameMix(it, mix) }
            val updated =
                if (existing >= 0) {
                    current.favoriteMixes.toMutableList().also { it.removeAt(existing) }
                } else {
                    listOf(mix) + current.favoriteMixes
                }
            current.copy(favoriteMixes = updated)
        }
    }

    fun isFavorite(mix: SoundMix): Boolean = mutablePreferences.value.favoriteMixes.any { sameMix(it, mix) }

    fun setNightMode(enabled: Boolean) {
        updatePreferences { it.copy(nightModeEnabled = enabled) }
    }

    fun setLightAppearance(enabled: Boolean) {
        updatePreferences { it.copy(useLightAppearance = enabled) }
    }

    fun finishOnboarding() {
        updatePreferences { it.copy(hasSeenOnboarding = true) }
    }

    fun clearError() {
        mutableErrorMessage.value = null
    }

    private fun withAudioService(action: (AudioPlaybackService.LocalBinder) -> Unit) {
        val intent = Intent(appContext, AudioPlaybackService::class.java)
        pendingServiceAction = action
        try {
            ContextCompat.startForegroundService(appContext, intent)
            val binder = audioBinder
            if (binder != null) {
                pendingServiceAction = null
                action(binder)
            } else if (!serviceBound) {
                serviceBound =
                    appContext.bindService(
                        intent,
                        serviceConnection,
                        Context.BIND_AUTO_CREATE,
                    )
            }
        } catch (exception: Exception) {
            pendingServiceAction = null
            mutableErrorMessage.value = exception.localizedMessage ?: "Audio could not be started."
        }
    }

    private fun connectToPlaybackService() {
        if (serviceBound) return
        val intent = Intent(appContext, AudioPlaybackService::class.java)
        serviceBound =
            appContext.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
    }

    private fun updatePreferences(transform: (NightfallPreferences) -> NightfallPreferences) {
        val updated = transform(mutablePreferences.value)
        mutablePreferences.value = updated
        preferencesRepository.save(updated)
    }

    private fun startMix(
        binder: AudioPlaybackService.LocalBinder,
        mix: SoundMix,
    ) {
        if (!binder.play(mix)) showAudioError()
    }

    private fun showAudioError() {
        mutableErrorMessage.value = "Audio could not start. Check the audio route and try again."
    }

    private fun refreshTimerRemaining() {
        val deadline = mutablePreferences.value.timerDeadlineEpochMillis
        if (deadline == null) {
            mutableTimerRemainingMillis.value = null
            return
        }

        val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
        mutableTimerRemainingMillis.value = remaining
        if (remaining == 0L) {
            updatePreferences { it.copy(timerDeadlineEpochMillis = null) }
            audioBinder?.setTimerDeadline(null)
        }
    }

    private fun applySavedVolumes(mix: SoundMix): SoundMix =
        mix.copy(
            components =
                mix.components.map { component ->
                    val saved = mutablePreferences.value.soundVolumes[component.sound]
                    if (saved == null) component else component.copy(volume = saved)
                },
        )

    private fun remember(
        recent: List<SoundMix>,
        mix: SoundMix,
    ): List<SoundMix> =
        (
            listOf(mix) +
                recent.filterNot {
                    it.components.map(MixComponent::sound) == mix.components.map(MixComponent::sound)
                }
        ).take(8)

    private fun updateStoredMixes(
        stored: List<SoundMix>,
        current: SoundMix,
    ): List<SoundMix> = stored.map { if (sameMix(it, current)) current else it }

    private fun sameMix(
        first: SoundMix,
        second: SoundMix,
    ): Boolean =
        first.id == second.id ||
            (first.name == second.name && first.components.map(MixComponent::sound) == second.components.map(MixComponent::sound))

    override fun onCleared() {
        playbackStateJob?.cancel()
        if (serviceBound) appContext.unbindService(serviceConnection)
        super.onCleared()
    }
}
