import AVFoundation
import MediaPlayer
import Foundation
import Combine

@MainActor
final class AudioPlayerService: NSObject, ObservableObject {
    @Published private(set) var isPlaying = false

    private struct PlayerState {
        let player: AVAudioPlayer
        let sound: SoundKind
        var volume: Float
        var isPlaying: Bool
    }

    private var players: [UUID: PlayerState] = [:]
    private var masterVolume: Float = 0.65
    private var fadeMultiplier: Float = 1
    private var currentTitle = "Nightfall"
    private var shouldResumeAfterInterruption = false
    private var interruptedComponentIDs = Set<UUID>()
    private var notificationTokens: [NSObjectProtocol] = []

    override init() {
        super.init()
        observeAudioSession()
        configureRemoteCommands()
    }

    func play(_ mix: SoundMix) throws {
        stop()
        currentTitle = mix.displayName
        try activateAudioSession()

        do {
            for component in mix.components {
                let data = try audioData(for: component.sound)
                let player = try AVAudioPlayer(data: data)
                player.numberOfLoops = -1
                player.volume = AudioVolume.output(
                    master: masterVolume,
                    sound: component.volume,
                    fade: fadeMultiplier
                )
                guard player.prepareToPlay(), player.play() else {
                    throw AudioPlayerError.couldNotStart(component.sound.name)
                }
                players[component.id] = PlayerState(
                    player: player,
                    sound: component.sound,
                    volume: component.volume,
                    isPlaying: true
                )
            }
        } catch {
            stop()
            throw error
        }

        updatePlaybackState()
        updateNowPlaying()
    }

    func togglePlayback(_ mix: SoundMix) {
        if isPlaying {
            pauseAll()
        } else if players.isEmpty {
            do {
                try play(mix)
            } catch {
                NSLog("Nightfall could not start playback: %@", error.localizedDescription)
            }
        } else {
            resumeAll()
        }
    }

    func toggleComponent(_ component: MixComponent, mixTitle: String) throws {
        if players[component.id] == nil {
            try activateAudioSession()
            let player = try AVAudioPlayer(data: audioData(for: component.sound))
            player.numberOfLoops = -1
            player.volume = AudioVolume.output(
                master: masterVolume,
                sound: component.volume,
                fade: fadeMultiplier
            )
            guard player.prepareToPlay() else {
                throw AudioPlayerError.couldNotStart(component.sound.name)
            }
            players[component.id] = PlayerState(
                player: player,
                sound: component.sound,
                volume: component.volume,
                isPlaying: false
            )
        }

        guard var state = players[component.id] else { return }
        if state.isPlaying {
            state.player.pause()
            state.isPlaying = false
        } else {
            try activateAudioSession()
            state.player.volume = outputVolume(for: state)
            state.isPlaying = state.player.play()
        }
        players[component.id] = state
        currentTitle = mixTitle
        updatePlaybackState()
    }

    func removeComponent(_ component: MixComponent) {
        players.removeValue(forKey: component.id)?.player.stop()
        updatePlaybackState()
    }

    func pauseAll() {
        for id in Array(players.keys) {
            guard var state = players[id] else { continue }
            state.player.pause()
            state.isPlaying = false
            players[id] = state
        }
        shouldResumeAfterInterruption = false
        interruptedComponentIDs.removeAll()
        updatePlaybackState()
    }

    func resumeAll() {
        do {
            try activateAudioSession()
            for id in Array(players.keys) {
                guard var state = players[id] else { continue }
                state.player.volume = outputVolume(for: state)
                state.isPlaying = state.player.play()
                players[id] = state
            }
            interruptedComponentIDs.removeAll()
            updatePlaybackState()
        } catch {
            NSLog("Nightfall could not resume playback: %@", error.localizedDescription)
        }
    }

    func stop() {
        for state in players.values {
            state.player.stop()
        }
        players.removeAll()
        isPlaying = false
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func setMasterVolume(_ volume: Float) {
        masterVolume = min(max(volume, 0), 1)
        refreshPlayerVolumes()
    }

    func setComponentVolume(id: UUID, volume: Float) {
        guard var state = players[id] else { return }
        state.volume = min(max(volume, 0), 1)
        state.player.volume = outputVolume(for: state)
        players[id] = state
    }

    func isComponentPlaying(_ id: UUID) -> Bool {
        players[id]?.isPlaying ?? false
    }

    func setFadeMultiplier(_ multiplier: Float) {
        fadeMultiplier = min(max(multiplier, 0), 1)
        refreshPlayerVolumes()
    }

    func scheduleFadeAndStop(after duration: TimeInterval) {
        guard duration > 0 else {
            stop()
            return
        }
        for state in players.values where state.isPlaying {
            let target = AudioVolume.output(master: masterVolume, sound: state.volume, fade: 0)
            state.player.setVolume(target, fadeDuration: duration)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) { [weak self] in
            self?.stop()
        }
    }

    private func activateAudioSession() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .default, options: [.allowAirPlay, .allowBluetoothA2DP])
        try session.setActive(true)
    }

    private func audioData(for sound: SoundKind) throws -> Data {
        if sound == .whiteNoise || sound == .pinkNoise || sound == .brownNoise {
            return NoiseAudioFactory.makeWAV(for: sound)
        }
        guard let filename = sound.bundledFilename,
              let url = Bundle.main.url(forResource: filename.replacingOccurrences(of: ".wav", with: ""), withExtension: "wav") else {
            throw AudioPlayerError.missingAsset(sound.name)
        }
        return try Data(contentsOf: url)
    }

    private func refreshPlayerVolumes() {
        for id in Array(players.keys) {
            guard var state = players[id] else { continue }
            state.player.volume = outputVolume(for: state)
            players[id] = state
        }
    }

    private func outputVolume(for state: PlayerState) -> Float {
        AudioVolume.output(master: masterVolume, sound: state.volume, fade: fadeMultiplier)
    }

    private func updatePlaybackState() {
        isPlaying = players.values.contains(where: \.isPlaying)
        updateNowPlaying()
    }

    private func updateNowPlaying() {
        guard !players.isEmpty else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: currentTitle,
            MPMediaItemPropertyArtist: "Nightfall",
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0
        ]
    }

    private func configureRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.isEnabled = true
        commandCenter.pauseCommand.isEnabled = true
        commandCenter.togglePlayPauseCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resumeAll() }
            return .success
        }
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pauseAll() }
            return .success
        }
        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                self.isPlaying ? self.pauseAll() : self.resumeAll()
            }
            return .success
        }
    }

    private func observeAudioSession() {
        let center = NotificationCenter.default
        notificationTokens.append(center.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            Task { @MainActor in self?.handleInterruption(notification) }
        })
        notificationTokens.append(center.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            Task { @MainActor in self?.handleRouteChange(notification) }
        })
    }

    private func handleInterruption(_ notification: Notification) {
        guard let rawType = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: rawType) else { return }
        switch type {
        case .began:
            shouldResumeAfterInterruption = isPlaying
            interruptedComponentIDs = Set(players.compactMap { entry in
                entry.value.isPlaying ? entry.key : nil
            })
            for id in Array(players.keys) {
                guard var state = players[id] else { continue }
                state.player.pause()
                state.isPlaying = false
                players[id] = state
            }
            isPlaying = false
            updateNowPlaying()
        case .ended:
            let rawOptions = notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            let options = AVAudioSession.InterruptionOptions(rawValue: rawOptions)
            if shouldResumeAfterInterruption && options.contains(.shouldResume) {
                resumeInterruptedComponents()
            }
            shouldResumeAfterInterruption = false
            interruptedComponentIDs.removeAll()
        @unknown default:
            break
        }
    }

    private func resumeInterruptedComponents() {
        do {
            try activateAudioSession()
            for id in interruptedComponentIDs {
                guard var state = players[id] else { continue }
                state.player.volume = outputVolume(for: state)
                state.isPlaying = state.player.play()
                players[id] = state
            }
            updatePlaybackState()
        } catch {
            NSLog("Nightfall could not resume after interruption: %@", error.localizedDescription)
        }
    }

    private func handleRouteChange(_ notification: Notification) {
        guard let rawReason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: rawReason) else { return }
        if reason == .oldDeviceUnavailable {
            pauseAll()
        }
    }

    deinit {
        notificationTokens.forEach { NotificationCenter.default.removeObserver($0) }
    }
}

enum AudioPlayerError: LocalizedError {
    case missingAsset(String)
    case couldNotStart(String)

    var errorDescription: String? {
        switch self {
        case .missingAsset(let sound): "The bundled \(sound) sound is missing."
        case .couldNotStart(let sound): "The \(sound) sound could not be started."
        }
    }
}

enum NoiseAudioFactory {
    static let sampleRate = 22_050
    private static let duration: TimeInterval = 8

    static func makeWAV(for sound: SoundKind) -> Data {
        let count = Int(Double(sampleRate) * duration)
        var samples = [Float](repeating: 0, count: count)
        var random: UInt32 = 0xA341_316C ^ UInt32(truncatingIfNeeded: sound.rawValue.hashValue)
        var previous: Float = 0
        var brown: Float = 0

        for index in 0..<count {
            random ^= random << 13
            random ^= random >> 17
            random ^= random << 5
            let white = Float(Int32(bitPattern: random)) / Float(Int32.max)
            switch sound {
            case .whiteNoise:
                samples[index] = white * 0.55
            case .pinkNoise:
                previous = previous * 0.985 + white * 0.12
                samples[index] = previous * 0.9
            case .brownNoise:
                brown = min(max(brown + white * 0.018, -0.8), 0.8)
                samples[index] = brown * 0.85
            default:
                samples[index] = 0
            }
        }

        makeLoopSeamless(&samples)
        return wavData(samples: samples, sampleRate: sampleRate)
    }

    private static func makeLoopSeamless(_ samples: inout [Float]) {
        guard samples.count > 1 else { return }
        let boundary = (samples[0] + samples[samples.count - 1]) * 0.5
        samples[0] = boundary
        samples[samples.count - 1] = boundary
    }

    private static func wavData(samples: [Float], sampleRate: Int) -> Data {
        var data = Data()
        let pcmBytes = samples.count * MemoryLayout<Int16>.size
        data.append(contentsOf: Array("RIFF".utf8))
        data.appendLE(UInt32(36 + pcmBytes))
        data.append(contentsOf: Array("WAVEfmt ".utf8))
        data.appendLE(UInt32(16))
        data.appendLE(UInt16(1))
        data.appendLE(UInt16(1))
        data.appendLE(UInt32(sampleRate))
        data.appendLE(UInt32(sampleRate * 2))
        data.appendLE(UInt16(2))
        data.appendLE(UInt16(16))
        data.append(contentsOf: Array("data".utf8))
        data.appendLE(UInt32(pcmBytes))
        for sample in samples {
            let pcm = Int16(max(-1, min(1, sample)) * Float(Int16.max))
            data.appendLE(UInt16(bitPattern: pcm))
        }
        return data
    }
}

private extension Data {
    mutating func appendLE<T: FixedWidthInteger>(_ value: T) {
        var littleEndian = value.littleEndian
        let bytes = Swift.withUnsafeBytes(of: &littleEndian, Array.init)
        append(contentsOf: bytes)
    }
}
