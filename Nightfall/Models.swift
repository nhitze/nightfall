import Foundation

enum SoundKind: String, Codable, CaseIterable, Identifiable {
    case whiteNoise
    case pinkNoise
    case brownNoise
    case rain
    case ocean
    case fan
    case fireplace
    case train
    case wind
    case forest

    var id: String { rawValue }

    var name: String {
        switch self {
        case .whiteNoise: "White noise"
        case .pinkNoise: "Pink noise"
        case .brownNoise: "Brown noise"
        case .rain: "Rain"
        case .ocean: "Ocean waves"
        case .fan: "Fan"
        case .fireplace: "Fireplace"
        case .train: "Train"
        case .wind: "Wind"
        case .forest: "Forest"
        }
    }

    var symbol: String {
        switch self {
        case .whiteNoise: "waveform"
        case .pinkNoise: "waveform.path"
        case .brownNoise: "waveform.path.ecg"
        case .rain: "cloud.rain.fill"
        case .ocean: "water.waves"
        case .fan: "fanblades.fill"
        case .fireplace: "flame.fill"
        case .train: "tram.fill"
        case .wind: "wind"
        case .forest: "tree.fill"
        }
    }

    var bundledFilename: String? {
        switch self {
        case .whiteNoise, .pinkNoise, .brownNoise:
            nil
        case .rain: "Rain.wav"
        case .ocean: "Ocean.wav"
        case .fan: "Fan.wav"
        case .fireplace: "Fireplace.wav"
        case .train: "Train.wav"
        case .wind: "Wind.wav"
        case .forest: "Forest.wav"
        }
    }

    static let all: [SoundKind] = [
        .whiteNoise, .pinkNoise, .brownNoise, .rain, .ocean,
        .fan, .fireplace, .train, .wind, .forest
    ]
}

struct MixComponent: Codable, Equatable, Identifiable {
    var id: UUID
    var sound: SoundKind
    var volume: Float

    init(id: UUID = UUID(), sound: SoundKind, volume: Float = 0.65) {
        self.id = id
        self.sound = sound
        self.volume = min(max(volume, 0), 1)
    }
}

struct SoundMix: Codable, Equatable, Identifiable {
    var id: UUID
    var name: String
    var components: [MixComponent]
    var isStarterPreset: Bool

    init(id: UUID = UUID(), name: String, components: [MixComponent], isStarterPreset: Bool = false) {
        self.id = id
        self.name = name
        self.components = components
        self.isStarterPreset = isStarterPreset
    }

    static func single(_ sound: SoundKind) -> SoundMix {
        SoundMix(name: sound.name, components: [MixComponent(sound: sound)])
    }

    static let starterPresets: [SoundMix] = [
        SoundMix(
            id: UUID(uuidString: "00000000-0000-4000-8000-000000000001")!,
            name: "Rainy Night",
            components: [MixComponent(sound: .rain), MixComponent(sound: .brownNoise, volume: 0.4)],
            isStarterPreset: true
        ),
        SoundMix(
            id: UUID(uuidString: "00000000-0000-4000-8000-000000000002")!,
            name: "Quiet Room",
            components: [MixComponent(sound: .fan), MixComponent(sound: .whiteNoise, volume: 0.35)],
            isStarterPreset: true
        ),
        SoundMix(
            id: UUID(uuidString: "00000000-0000-4000-8000-000000000003")!,
            name: "Campfire",
            components: [MixComponent(sound: .fireplace), MixComponent(sound: .wind, volume: 0.35)],
            isStarterPreset: true
        )
    ]

    var displayName: String {
        components.count == 1 ? (components.first?.sound.name ?? name) : name
    }
}

enum MixValidationError: Error, Equatable {
    case empty
    case tooManySounds
    case duplicateSound
}

enum MixFactory {
    static let maximumSounds = 3

    static func make(name: String, components: [MixComponent]) throws -> SoundMix {
        guard !components.isEmpty else { throw MixValidationError.empty }
        guard components.count <= maximumSounds else { throw MixValidationError.tooManySounds }
        guard Set(components.map(\.sound)).count == components.count else {
            throw MixValidationError.duplicateSound
        }

        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return SoundMix(name: trimmedName.isEmpty ? "My Mix" : trimmedName, components: components)
    }
}

enum SleepTimerOption: String, Codable, CaseIterable, Identifiable {
    case fifteenMinutes
    case thirtyMinutes
    case fortyFiveMinutes
    case oneHour
    case ninetyMinutes
    case untilMorning

    var id: String { rawValue }

    var title: String {
        switch self {
        case .fifteenMinutes: "15 minutes"
        case .thirtyMinutes: "30 minutes"
        case .fortyFiveMinutes: "45 minutes"
        case .oneHour: "60 minutes"
        case .ninetyMinutes: "90 minutes"
        case .untilMorning: "Until morning"
        }
    }

    func deadline(from date: Date, calendar: Calendar = .current) -> Date {
        if self == .untilMorning {
            let tomorrowAtSeven = calendar.nextDate(
                after: date,
                matching: DateComponents(hour: 7),
                matchingPolicy: .nextTime
            ) ?? date.addingTimeInterval(8 * 60 * 60)
            return tomorrowAtSeven > date ? tomorrowAtSeven : tomorrowAtSeven.addingTimeInterval(24 * 60 * 60)
        }

        return date.addingTimeInterval(TimeInterval(minutes * 60))
    }

    var minutes: Int {
        switch self {
        case .fifteenMinutes: 15
        case .thirtyMinutes: 30
        case .fortyFiveMinutes: 45
        case .oneHour: 60
        case .ninetyMinutes: 90
        case .untilMorning: 0
        }
    }
}

struct TimerSnapshot: Equatable {
    var deadline: Date?

    func remaining(at date: Date) -> TimeInterval? {
        guard let deadline else { return nil }
        return max(0, deadline.timeIntervalSince(date))
    }

    func fadeMultiplier(at date: Date, fadeDuration: TimeInterval = 5 * 60) -> Float {
        guard let remaining = remaining(at: date) else { return 1 }
        guard remaining > 0 else { return 0 }
        guard remaining < fadeDuration else { return 1 }
        return Float(remaining / fadeDuration)
    }
}

struct NightfallPreferences: Codable, Equatable {
    var activeMix: SoundMix
    var masterVolume: Float
    var soundVolumes: [String: Float]
    var favoriteSoundIDs: Set<String>
    var favoriteMixes: [SoundMix]
    var recentMixes: [SoundMix]
    var timerOption: SleepTimerOption
    var timerDeadline: Date?
    var hasSeenOnboarding: Bool
    var nightModeEnabled: Bool

    static let initial = NightfallPreferences(
        activeMix: SoundMix.single(.rain),
        masterVolume: 0.65,
        soundVolumes: [:],
        favoriteSoundIDs: [],
        favoriteMixes: [],
        recentMixes: [],
        timerOption: .thirtyMinutes,
        timerDeadline: nil,
        hasSeenOnboarding: false,
        nightModeEnabled: false
    )
}

enum AudioVolume {
    static func output(master: Float, sound: Float, fade: Float = 1) -> Float {
        min(max(master, 0), 1) * min(max(sound, 0), 1) * min(max(fade, 0), 1)
    }
}
