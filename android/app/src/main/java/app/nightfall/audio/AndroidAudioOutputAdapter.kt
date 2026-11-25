package app.nightfall.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import app.nightfall.domain.MixComponent
import app.nightfall.domain.SoundKind
import app.nightfall.playback.AudioOutputAdapter
import java.io.File

class AndroidAudioOutputAdapter(
    private val context: Context,
) : AudioOutputAdapter {
    private val players = mutableMapOf<String, MediaPlayer>()

    override fun play(
        component: MixComponent,
        gain: Float,
    ) {
        stop(component.id)
        val player =
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                setVolume(gain, gain)
            }

        try {
            setDataSource(player, component.sound)
            player.prepare()
            player.start()
            players[component.id] = player
        } catch (exception: Exception) {
            player.release()
            throw exception
        }
    }

    override fun pause(componentId: String) {
        runCatching {
            players[componentId]?.takeIf(MediaPlayer::isPlaying)?.pause()
        }
    }

    override fun resume(
        component: MixComponent,
        gain: Float,
    ) {
        val player = players[component.id]
        if (player == null) {
            play(component, gain)
        } else {
            player.setVolume(gain, gain)
            player.start()
        }
    }

    override fun setGain(
        componentId: String,
        gain: Float,
    ) {
        runCatching { players[componentId]?.setVolume(gain, gain) }
    }

    override fun stop(componentId: String) {
        val player = players.remove(componentId) ?: return
        try {
            if (player.isPlaying) player.stop()
        } catch (_: IllegalStateException) {
            // Release below even if the platform player has already changed state.
        } finally {
            player.release()
        }
    }

    fun release() {
        players.keys.toList().forEach(::stop)
    }

    private fun setDataSource(
        player: MediaPlayer,
        sound: SoundKind,
    ) {
        if (sound.assetFilename == null) {
            val file = generatedNoiseFile(sound)
            player.setDataSource(file.absolutePath)
            return
        }

        context.assets.openFd(sound.assetFilename).use { asset ->
            player.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
        }
    }

    private fun generatedNoiseFile(sound: SoundKind): File {
        val file = File(context.cacheDir, "${sound.name.lowercase()}-noise.wav")
        if (!file.exists()) {
            file.writeBytes(NoiseWavGenerator.createWav(sound))
        }
        return file
    }
}
