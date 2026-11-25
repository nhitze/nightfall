package app.nightfall.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.IBinder
import app.nightfall.MainActivity
import app.nightfall.R
import app.nightfall.audio.AndroidAudioOutputAdapter
import app.nightfall.data.PreferencesRepository
import app.nightfall.data.SharedPreferencesStorage
import app.nightfall.domain.MixComponent
import app.nightfall.domain.SoundMix
import app.nightfall.domain.TimerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class AudioPlaybackService : Service() {
    inner class LocalBinder : Binder() {
        val playbackState get() = controller.state

        fun play(mix: SoundMix): Boolean = playMix(mix)

        fun pause() = pausePlayback()

        fun resume(): Boolean = resumePlayback()

        fun stop() = stopPlayback()

        fun selectMix(mix: SoundMix) {
            controller.selectMix(mix)
            updateSessionAndNotification()
        }

        fun toggleComponent(component: MixComponent): Boolean = togglePlaybackComponent(component)

        fun removeComponent(componentId: String) {
            controller.removeComponent(componentId)
            updateSessionAndNotification()
        }

        fun setMasterVolume(volume: Float) {
            controller.setMasterVolume(volume)
            updateSessionAndNotification()
        }

        fun setComponentVolume(
            componentId: String,
            volume: Float,
        ) {
            controller.setComponentVolume(componentId, volume)
            updateSessionAndNotification()
        }

        fun setFadeMultiplier(multiplier: Float) {
            controller.setFadeMultiplier(multiplier)
        }

        fun setTimerDeadline(deadlineEpochMillis: Long?) {
            updateTimerDeadline(deadlineEpochMillis)
        }
    }

    private lateinit var controller: PlaybackController
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSession
    private lateinit var focusRequest: AudioFocusRequest
    private lateinit var preferencesRepository: PreferencesRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val localBinder = LocalBinder()
    private var timerJob: Job? = null
    private var hasAudioFocus = false
    private var interrupted = false
    private var noisyReceiverRegistered = false
    private var foregroundStarted = false

    private val noisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    controller.onBecomingNoisy()
                    abandonAudioFocus()
                    updateSessionAndNotification()
                }
            }
        }

    private val audioFocusListener =
        AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> {
                    hasAudioFocus = false
                    interrupted = controller.isPlaying
                    controller.onInterruptionStarted()
                    updateSessionAndNotification()
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    controller.onInterruptionEnded(shouldResume = interrupted)
                    interrupted = false
                    hasAudioFocus = true
                    updateSessionAndNotification()
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    controller.onInterruptionEnded(shouldResume = false)
                    controller.pauseAll()
                    interrupted = false
                    hasAudioFocus = false
                    updateSessionAndNotification()
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AudioManager::class.java)
        preferencesRepository = PreferencesRepository(SharedPreferencesStorage(this))
        val attributes = playbackAudioAttributes()
        focusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
        outputAdapter = AndroidAudioOutputAdapter(this)
        controller = PlaybackController(outputAdapter)
        controller.selectMix(preferencesRepository.load().activeMix)
        mediaSession =
            MediaSession(this, SESSION_TAG).apply {
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS,
                )
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() {
                            resumePlayback()
                        }

                        override fun onPause() {
                            pausePlayback()
                        }

                        override fun onStop() {
                            stopPlayback()
                        }
                    },
                )
            }
        registerNoisyReceiver()
    }

    override fun onBind(intent: Intent?): IBinder = localBinder

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                ensureForeground()
                pausePlayback()
            }
            ACTION_RESUME -> {
                ensureForeground()
                resumePlayback()
            }
            ACTION_STOP -> {
                ensureForeground()
                stopPlayback()
            }
            else -> ensureForeground()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterNoisyReceiver()
        timerJob?.cancel()
        serviceScope.cancel()
        abandonAudioFocus()
        controller.stopAll(clearMix = true)
        outputAdapter.release()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    private fun playMix(mix: SoundMix): Boolean {
        ensureForeground()
        if (!requestAudioFocus()) return false
        return runCatching {
            controller.play(mix)
            startStoredTimerIfNeeded()
            mediaSession.isActive = true
            updateSessionAndNotification()
            true
        }.getOrElse {
            controller.stopAll(clearMix = true)
            updateSessionAndNotification()
            false
        }
    }

    private fun pausePlayback() {
        controller.pauseAll()
        abandonAudioFocus()
        updateSessionAndNotification()
    }

    private fun resumePlayback(): Boolean {
        if (controller.activeMix == null || !requestAudioFocus()) return false
        return runCatching {
            ensureForeground()
            controller.startOrResume()
            mediaSession.isActive = true
            updateSessionAndNotification()
            true
        }.getOrElse {
            controller.stopAll(clearMix = true)
            abandonAudioFocus()
            updateSessionAndNotification()
            false
        }
    }

    private fun togglePlaybackComponent(component: MixComponent): Boolean {
        if (component.id !in controller.playingComponentIds && !requestAudioFocus()) return false
        return runCatching {
            ensureForeground()
            controller.toggleComponent(component)
            if (controller.isPlaying) {
                startStoredTimerIfNeeded()
            } else {
                abandonAudioFocus()
            }
            mediaSession.isActive = true
            updateSessionAndNotification()
            true
        }.getOrElse {
            controller.stopAll(clearMix = true)
            abandonAudioFocus()
            updateSessionAndNotification()
            false
        }
    }

    private fun stopPlayback() {
        controller.stopAll(clearMix = true)
        interrupted = false
        abandonAudioFocus()
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stopSelf()
    }

    private fun ensureForeground() {
        startForeground(NOTIFICATION_ID, createNotification())
        foregroundStarted = true
    }

    private fun updateTimerDeadline(deadlineEpochMillis: Long?) {
        timerJob?.cancel()
        timerJob = null
        if (deadlineEpochMillis == null) {
            controller.setFadeMultiplier(1f)
            return
        }

        val deadline = Instant.ofEpochMilli(deadlineEpochMillis)
        timerJob =
            serviceScope.launch {
                while (isActive) {
                    val now = Instant.now()
                    val timer = TimerSnapshot(deadline)
                    val remaining = timer.remainingAt(now) ?: Duration.ZERO
                    if (remaining.isZero) {
                        controller.setFadeMultiplier(0f)
                        controller.stopAll(clearMix = true)
                        controller.setFadeMultiplier(1f)
                        val preferences = preferencesRepository.load()
                        preferencesRepository.save(preferences.copy(timerDeadlineEpochMillis = null))
                        interrupted = false
                        abandonAudioFocus()
                        mediaSession.isActive = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        foregroundStarted = false
                        stopSelf()
                        break
                    }
                    controller.setFadeMultiplier(timer.fadeMultiplierAt(now))
                    delay(TIMER_TICK_MILLIS)
                }
            }
    }

    private fun startStoredTimerIfNeeded() {
        val deadline = preferencesRepository.load().timerDeadlineEpochMillis ?: return
        if (timerJob?.isActive != true) updateTimerDeadline(deadline)
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val result = audioManager.requestAudioFocus(focusRequest)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasAudioFocus = false
        }
    }

    private fun updateSessionAndNotification() {
        if (!foregroundStarted) return
        val playing = controller.isPlaying
        val title = controller.activeMix?.displayName ?: getString(R.string.app_name)
        mediaSession.setMetadata(
            MediaMetadata
                .Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.app_name))
                .build(),
        )
        mediaSession.setPlaybackState(
            PlaybackState
                .Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (playing) 1f else 0f,
                )
                .build(),
        )
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        val launchIntent =
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent =
            PendingIntent.getActivity(
                this,
                REQUEST_CONTENT,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val playing = controllerOrNull()?.isPlaying == true
        val action = if (playing) ACTION_PAUSE else ACTION_RESUME
        val actionTitle = if (playing) "Pause" else "Play"
        val actionIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val controlIntent =
            PendingIntent.getService(
                this,
                REQUEST_CONTROL,
                Intent(this, AudioPlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                REQUEST_STOP,
                Intent(this, AudioPlaybackService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return Notification
            .Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(controllerOrNull()?.activeMix?.displayName ?: getString(R.string.app_name))
            .setContentText(if (playing) "Playing sleep sounds" else "Paused")
            .setContentIntent(contentIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(Notification.Action.Builder(actionIcon, actionTitle, controlIntent).build())
            .addAction(
                Notification.Action
                    .Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
                    .build(),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSessionOrNull()?.sessionToken)
                    .setShowActionsInCompactView(0, 1),
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Sleep audio",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Playback controls for Nightfall sleep sounds."
                setSound(null, null)
                enableVibration(false)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisyReceiver, filter)
        }
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
    }

    private fun playbackAudioAttributes(): AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private fun controllerOrNull(): PlaybackController? = if (::controller.isInitialized) controller else null

    private fun mediaSessionOrNull(): MediaSession? = if (::mediaSession.isInitialized) mediaSession else null

    private lateinit var outputAdapter: AndroidAudioOutputAdapter

    private companion object {
        const val NOTIFICATION_CHANNEL = "nightfall-playback"
        const val NOTIFICATION_ID = 8301
        const val SESSION_TAG = "Nightfall"
        const val ACTION_PAUSE = "app.nightfall.action.PAUSE"
        const val ACTION_RESUME = "app.nightfall.action.RESUME"
        const val ACTION_STOP = "app.nightfall.action.STOP"
        const val REQUEST_CONTENT = 1
        const val REQUEST_CONTROL = 2
        const val REQUEST_STOP = 3
        const val TIMER_TICK_MILLIS = 1_000L
    }
}
