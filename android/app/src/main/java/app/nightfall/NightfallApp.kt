package app.nightfall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nightfall.domain.MixComponent
import app.nightfall.domain.MixFactory
import app.nightfall.domain.NightfallPreferences
import app.nightfall.domain.SleepTimerOption
import app.nightfall.domain.SoundKind
import app.nightfall.domain.SoundMix

@Composable
fun NightfallApp(viewModel: NightfallViewModel) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val remainingMillis by viewModel.timerRemainingMillis.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    var showTimer by remember { mutableStateOf(false) }

    val colors =
        when {
            preferences.nightModeEnabled -> nightModeColors()
            preferences.useLightAppearance -> lightAppearanceColors()
            else ->
                darkColorScheme(
                    primary = NightfallAccent,
                    onPrimary = NightfallInk,
                    background = NightfallBackground,
                    surface = NightfallSurface,
                    onSurface = NightfallText,
                    surfaceVariant = NightfallSurfaceVariant,
                    onSurfaceVariant = NightfallMuted,
                )
        }

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp)
                            .padding(insets),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    item {
                        Header(
                            nightMode = preferences.nightModeEnabled,
                            useLightAppearance = preferences.useLightAppearance,
                            onNightModeToggle = { viewModel.setNightMode(!preferences.nightModeEnabled) },
                            onAppearanceToggle = { viewModel.setLightAppearance(!preferences.useLightAppearance) },
                        )
                    }

                    if (!preferences.hasSeenOnboarding) {
                        item {
                            OnboardingCard(onDismiss = viewModel::finishOnboarding)
                        }
                    }

                    item {
                        NowPlayingCard(
                            preferences = preferences,
                            isPlaying = playback.playingComponentIds.isNotEmpty(),
                            remainingMillis = remainingMillis,
                            onTogglePlayback = viewModel::togglePlayback,
                            onMasterVolumeChange = viewModel::setMasterVolume,
                            onTimerClick = { showTimer = true },
                            onFavoriteMix = { viewModel.toggleFavorite(preferences.activeMix) },
                            isFavoriteMix = viewModel.isFavorite(preferences.activeMix),
                        )
                    }

                    item {
                        ActiveMixSection(
                            mix = preferences.activeMix,
                            playingIds = playback.playingComponentIds,
                            viewModel = viewModel,
                        )
                    }

                    if (preferences.favoriteSoundIds.isNotEmpty() || preferences.favoriteMixes.isNotEmpty()) {
                        item {
                            FavoritesSection(
                                preferences = preferences,
                                onSelectSound = viewModel::select,
                                onSelectMix = viewModel::activate,
                            )
                        }
                    }

                    item {
                        PresetsSection(
                            onSelect = viewModel::activate,
                            onFavorite = viewModel::toggleFavorite,
                            isFavorite = viewModel::isFavorite,
                        )
                    }

                    item {
                        SoundLibrarySection(
                            activeMix = preferences.activeMix,
                            favoriteSoundIds = preferences.favoriteSoundIds,
                            onSelect = viewModel::select,
                            onAdd = viewModel::addToMix,
                            onFavorite = viewModel::toggleFavorite,
                        )
                    }

                    item {
                        Text(
                            text = "Made for quiet nights · all playback stays on this device",
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }

    if (showTimer) {
        SleepTimerSheet(
            selected = preferences.timerOption,
            isRunning = remainingMillis != null,
            onSelect = viewModel::setTimerOption,
            onStart = {
                viewModel.startTimer()
                showTimer = false
            },
            onCancel = {
                viewModel.cancelTimer()
                showTimer = false
            },
            onDismiss = { showTimer = false },
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Nightfall") },
            text = { Text(errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun Header(
    nightMode: Boolean,
    useLightAppearance: Boolean,
    onNightModeToggle: () -> Unit,
    onAppearanceToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("☾", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
        Text(
            text = "Nightfall",
            modifier = Modifier.padding(start = 9.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onNightModeToggle) {
            Text(if (nightMode) "Night on" else "Night mode")
        }
        TextButton(onClick = onAppearanceToggle) {
            Text(if (useLightAppearance) "Dark" else "Light")
        }
    }
}

@Composable
private fun OnboardingCard(onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your sounds stay here.", fontWeight = FontWeight.SemiBold)
            Text(
                "Nightfall plays sounds stored on this device. No account, ads, tracking, or data collection.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            TextButton(onClick = onDismiss) {
                Text("Sounds good")
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    preferences: NightfallPreferences,
    isPlaying: Boolean,
    remainingMillis: Long?,
    onTogglePlayback: () -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onTimerClick: () -> Unit,
    onFavoriteMix: () -> Unit,
    isFavoriteMix: Boolean,
) {
    val mix = preferences.activeMix
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPlaying) "NOW PLAYING" else "READY WHEN YOU ARE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = mix.displayName,
                        modifier = Modifier.padding(top = 5.dp),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = mix.components.joinToString(" · ") { it.sound.title },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                TextButton(
                    onClick = onFavoriteMix,
                    modifier =
                        Modifier.semantics {
                            contentDescription = if (isFavoriteMix) "Remove mix from favorites" else "Favorite this mix"
                        },
                ) {
                    Text(if (isFavoriteMix) "♥" else "♡", fontSize = 23.sp)
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    onClick = onTogglePlayback,
                    modifier =
                        Modifier
                            .size(92.dp)
                            .semantics {
                                contentDescription = if (isPlaying) "Pause sounds" else "Play sounds"
                            },
                    shape = CircleShape,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Text(if (isPlaying) "Ⅱ" else "▶", fontSize = 28.sp)
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Master volume", fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${(preferences.masterVolume * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = preferences.masterVolume,
                    onValueChange = onMasterVolumeChange,
                    valueRange = 0f..1f,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Master volume"
                        },
                )
            }

            Button(
                onClick = onTimerClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(remainingMillis?.let { "Sounds stop in ${formatTime(it)}" } ?: "Sleep timer")
                    Text(
                        text =
                            if (remainingMillis == null) {
                                preferences.timerOption.title
                            } else {
                                "Fades out over the final 5 minutes"
                            },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveMixSection(
    mix: SoundMix,
    playingIds: Set<String>,
    viewModel: NightfallViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading("In this mix", "${mix.components.size} of ${MixFactory.MAXIMUM_SOUNDS} sounds")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                mix.components.forEachIndexed { index, component ->
                    ActiveSoundRow(
                        component = component,
                        isPlaying = component.id in playingIds,
                        volume = viewModel.soundVolume(component.sound),
                        onToggle = { viewModel.toggleComponent(component) },
                        onRemove = { viewModel.remove(component) },
                        onVolumeChange = { viewModel.setSoundVolume(component, it) },
                    )
                    if (index < mix.components.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ActiveSoundRow(
    component: MixComponent,
    isPlaying: Boolean,
    volume: Float,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(component.sound.glyph, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            Text(
                component.sound.title,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
                fontWeight = FontWeight.Medium,
            )
            IconButton(
                onClick = onToggle,
                modifier =
                    Modifier.semantics {
                        contentDescription = if (isPlaying) "Pause ${component.sound.title}" else "Play ${component.sound.title}"
                    },
            ) {
                Text(if (isPlaying) "Ⅱ" else "▶")
            }
            IconButton(
                onClick = onRemove,
                modifier =
                    Modifier.semantics {
                        contentDescription = "Remove ${component.sound.title} from mix"
                    },
            ) {
                Text("×")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("−", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { contentDescription = "${component.sound.title} volume" },
            )
            Text("＋", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FavoritesSection(
    preferences: NightfallPreferences,
    onSelectSound: (SoundKind) -> Unit,
    onSelectMix: (SoundMix) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading("Favorites", "Saved on this device")
        preferences.favoriteSoundIds.forEach { sound ->
            TextButton(onClick = { onSelectSound(sound) }) {
                Text("♡  ${sound.title}")
            }
        }
        preferences.favoriteMixes.forEach { mix ->
            TextButton(onClick = { onSelectMix(mix) }) {
                Text("▧  ${mix.displayName}")
            }
        }
    }
}

@Composable
private fun PresetsSection(
    onSelect: (SoundMix) -> Unit,
    onFavorite: (SoundMix) -> Unit,
    isFavorite: (SoundMix) -> Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading("A little inspiration", "One-tap mixes")
        SoundMix.starterPresets.forEach { mix ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("☾", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(mix.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            mix.components.joinToString(" + ") { it.sound.title },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = { onSelect(mix) }) { Text("Play") }
                    TextButton(
                        onClick = { onFavorite(mix) },
                        modifier =
                            Modifier.semantics {
                                contentDescription = if (isFavorite(mix)) "Remove ${mix.name} from favorites" else "Favorite ${mix.name}"
                            },
                    ) {
                        Text(if (isFavorite(mix)) "♥" else "♡")
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundLibrarySection(
    activeMix: SoundMix,
    favoriteSoundIds: Set<SoundKind>,
    onSelect: (SoundKind) -> Unit,
    onAdd: (SoundKind) -> Unit,
    onFavorite: (SoundKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading("Sound library", "Tap to play · + to add to your mix")
        SoundKind.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { sound ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(sound.glyph, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    onClick = { onFavorite(sound) },
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription =
                                                if (sound in favoriteSoundIds) {
                                                    "Remove ${sound.title} from favorites"
                                                } else {
                                                    "Favorite ${sound.title}"
                                                }
                                        },
                                ) {
                                    Text(if (sound in favoriteSoundIds) "♥" else "♡")
                                }
                            }
                            TextButton(
                                onClick = { onSelect(sound) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    sound.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(
                                onClick = { onAdd(sound) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (activeMix.components.any { it.sound == sound }) "Selected" else "+ Add to mix")
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String,
) {
    Column {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    selected: SleepTimerOption,
    isRunning: Boolean,
    onSelect: (SleepTimerOption) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sleep timer", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Fades out over the final 5 minutes. Until morning ends at 7:00 AM.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            SleepTimerOption.entries.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == option, onClick = { onSelect(option) })
                    Text(option.title, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text(if (isRunning) "Restart timer" else "Start timer")
                }
                if (isRunning) {
                    TextButton(onClick = onCancel) { Text("Turn off") }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private val NightfallAccent = androidx.compose.ui.graphics.Color(0xFFA8C8BC)
private val NightfallInk = androidx.compose.ui.graphics.Color(0xFF101917)
private val NightfallBackground = androidx.compose.ui.graphics.Color(0xFF090D10)
private val NightfallSurface = androidx.compose.ui.graphics.Color(0xFF141D20)
private val NightfallSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF172326)
private val NightfallText = androidx.compose.ui.graphics.Color(0xFFE5EBE7)
private val NightfallMuted = androidx.compose.ui.graphics.Color(0xFF99AAA5)

private fun nightModeColors() =
    darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF565B58),
        onPrimary = androidx.compose.ui.graphics.Color(0xFF050606),
        background = androidx.compose.ui.graphics.Color(0xFF030405),
        surface = androidx.compose.ui.graphics.Color(0xFF080A0B),
        onSurface = androidx.compose.ui.graphics.Color(0xFF777B79),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF080A0B),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF454B48),
    )

private fun lightAppearanceColors() =
    lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF506F63),
        onPrimary = androidx.compose.ui.graphics.Color(0xFFF3F7F4),
        background = androidx.compose.ui.graphics.Color(0xFFF4F5F2),
        surface = androidx.compose.ui.graphics.Color.White,
        onSurface = androidx.compose.ui.graphics.Color(0xFF17221F),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE5ECE8),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF63746E),
    )
