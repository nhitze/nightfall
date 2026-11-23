import Combine
import Foundation

@MainActor
final class NightfallViewModel: ObservableObject {
    @Published private(set) var preferences: NightfallPreferences
    @Published private(set) var isPlaying = false
    @Published var errorMessage: String?

    let audioService: AudioPlayerService
    private let persistence: PreferencesPersisting
    private var cancellables = Set<AnyCancellable>()

    lazy var timerService = SleepTimerService(deadline: preferences.timerDeadline) { [weak self] snapshot, expired in
        guard let self else { return }
        if expired {
            self.audioService.stop()
            self.audioService.setFadeMultiplier(1)
            self.preferences.timerDeadline = nil
            self.persist()
        } else if let remaining = snapshot.remaining(at: Date()) {
            self.audioService.setFadeMultiplier(snapshot.fadeMultiplier(at: Date()))
            if remaining <= 0 {
                self.audioService.stop()
            }
        } else {
            self.audioService.setFadeMultiplier(1)
        }
    }

    init(
        persistence: PreferencesPersisting = JSONPersistenceService(),
        audioService: AudioPlayerService? = nil
    ) {
        let service = audioService ?? AudioPlayerService()
        self.persistence = persistence
        self.audioService = service
        self.preferences = persistence.load()
        service.setMasterVolume(preferences.masterVolume)
        service.$isPlaying
            .receive(on: DispatchQueue.main)
            .sink { [weak self] value in self?.isPlaying = value }
            .store(in: &cancellables)
    }

    var activeMix: SoundMix { preferences.activeMix }
    var masterVolume: Float { preferences.masterVolume }
    var timerOption: SleepTimerOption { preferences.timerOption }
    var hasSeenOnboarding: Bool { preferences.hasSeenOnboarding }
    var nightModeEnabled: Bool { preferences.nightModeEnabled }

    func togglePlayback() {
        if isPlaying {
            audioService.pauseAll()
        } else {
            do {
                try audioService.play(activeMix)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func select(_ sound: SoundKind) {
        let volume = preferences.soundVolumes[sound.id] ?? 0.65
        activate(SoundMix(name: sound.name, components: [MixComponent(sound: sound, volume: volume)]))
    }

    func activate(_ mix: SoundMix) {
        preferences.activeMix = applyingSavedVolumes(to: mix)
        remember(preferences.activeMix)
        if isPlaying {
            do {
                try audioService.play(preferences.activeMix)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
        persist()
    }

    func addToMix(_ sound: SoundKind) {
        guard !activeMix.components.contains(where: { $0.sound == sound }) else { return }
        let components = activeMix.components + [
            MixComponent(sound: sound, volume: preferences.soundVolumes[sound.id] ?? 0.45)
        ]
        do {
            let mix = try MixFactory.make(name: components.count > 1 ? "New Mix" : sound.name, components: components)
            activate(mix)
        } catch {
            errorMessage = "A mix can contain up to three different sounds."
        }
    }

    func remove(_ component: MixComponent) {
        audioService.removeComponent(component)
        let remaining = activeMix.components.filter { $0.id != component.id }
        if remaining.isEmpty {
            preferences.activeMix = .single(.rain)
        } else {
            preferences.activeMix.components = remaining
            if remaining.count == 1 {
                preferences.activeMix.name = remaining[0].sound.name
            }
        }
        persist()
    }

    func toggle(_ component: MixComponent) {
        do {
            try audioService.toggleComponent(component, mixTitle: activeMix.displayName)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func setSoundVolume(_ volume: Float, for component: MixComponent) {
        let normalized = min(max(volume, 0), 1)
        if let index = preferences.activeMix.components.firstIndex(where: { $0.id == component.id }) {
            preferences.activeMix.components[index].volume = normalized
        }
        preferences.soundVolumes[component.sound.id] = normalized
        updateStoredMixes(matching: preferences.activeMix)
        audioService.setComponentVolume(id: component.id, volume: normalized)
        persist()
    }

    func setMasterVolume(_ volume: Float) {
        preferences.masterVolume = min(max(volume, 0), 1)
        audioService.setMasterVolume(preferences.masterVolume)
        persist()
    }

    func setTimerOption(_ option: SleepTimerOption) {
        preferences.timerOption = option
        persist()
    }

    func startTimer() {
        let deadline = timerService.start(option: preferences.timerOption)
        preferences.timerDeadline = deadline
        persist()
    }

    func cancelTimer() {
        timerService.cancel()
        preferences.timerDeadline = nil
        audioService.setFadeMultiplier(1)
        persist()
    }

    func refreshTimer() {
        timerService.refresh()
    }

    func toggleFavorite(_ sound: SoundKind) {
        if preferences.favoriteSoundIDs.contains(sound.id) {
            preferences.favoriteSoundIDs.remove(sound.id)
        } else {
            preferences.favoriteSoundIDs.insert(sound.id)
        }
        persist()
    }

    func isFavorite(_ sound: SoundKind) -> Bool {
        preferences.favoriteSoundIDs.contains(sound.id)
    }

    func toggleFavorite(_ mix: SoundMix) {
        if let index = preferences.favoriteMixes.firstIndex(where: { sameMix($0, mix) }) {
            preferences.favoriteMixes.remove(at: index)
        } else {
            preferences.favoriteMixes.insert(mix, at: 0)
        }
        persist()
    }

    func isFavorite(_ mix: SoundMix) -> Bool {
        preferences.favoriteMixes.contains(where: { sameMix($0, mix) })
    }

    func setNightMode(_ enabled: Bool) {
        preferences.nightModeEnabled = enabled
        persist()
    }

    func finishOnboarding() {
        preferences.hasSeenOnboarding = true
        persist()
    }

    func soundVolume(for sound: SoundKind) -> Float {
        activeMix.components.first(where: { $0.sound == sound })?.volume
            ?? preferences.soundVolumes[sound.id]
            ?? 0.65
    }

    func isComponentPlaying(_ component: MixComponent) -> Bool {
        audioService.isComponentPlaying(component.id)
    }

    private func applyingSavedVolumes(to mix: SoundMix) -> SoundMix {
        var updated = mix
        for index in updated.components.indices {
            let sound = updated.components[index].sound
            if let volume = preferences.soundVolumes[sound.id] {
                updated.components[index].volume = volume
            }
        }
        return updated
    }

    private func remember(_ mix: SoundMix) {
        preferences.recentMixes.removeAll { $0.components.map(\.sound) == mix.components.map(\.sound) }
        preferences.recentMixes.insert(mix, at: 0)
        preferences.recentMixes = Array(preferences.recentMixes.prefix(8))
    }

    private func updateStoredMixes(matching mix: SoundMix) {
        for index in preferences.favoriteMixes.indices where preferences.favoriteMixes[index].id == mix.id {
            preferences.favoriteMixes[index] = mix
        }
        for index in preferences.recentMixes.indices where preferences.recentMixes[index].id == mix.id {
            preferences.recentMixes[index] = mix
        }
    }

    private func sameMix(_ lhs: SoundMix, _ rhs: SoundMix) -> Bool {
        lhs.id == rhs.id || (
            lhs.name == rhs.name &&
            lhs.components.map(\.sound) == rhs.components.map(\.sound)
        )
    }

    private func persist() {
        do {
            try persistence.save(preferences)
        } catch {
            errorMessage = "Your settings could not be saved. Please check available storage."
            NSLog("Nightfall could not save preferences: %@", error.localizedDescription)
        }
    }
}
