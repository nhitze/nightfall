import XCTest
@testable import Nightfall

final class MixCreationTests: XCTestCase {
    func testItCreatesMixesWithUpToThreeDistinctSounds() throws {
        let mix = try MixFactory.make(
            name: "  Soft rain  ",
            components: [
                MixComponent(sound: .rain),
                MixComponent(sound: .brownNoise),
                MixComponent(sound: .wind)
            ]
        )

        XCTAssertEqual(mix.name, "Soft rain")
        XCTAssertEqual(mix.components.count, 3)
    }

    func testItRejectsEmptyOversizedAndDuplicateMixes() {
        XCTAssertThrowsError(try MixFactory.make(name: "", components: [])) {
            XCTAssertEqual($0 as? MixValidationError, .empty)
        }
        XCTAssertThrowsError(try MixFactory.make(
            name: "Too much",
            components: [
                MixComponent(sound: .rain),
                MixComponent(sound: .wind),
                MixComponent(sound: .fan),
                MixComponent(sound: .forest)
            ]
        )) {
            XCTAssertEqual($0 as? MixValidationError, .tooManySounds)
        }
        XCTAssertThrowsError(try MixFactory.make(
            name: "Duplicate",
            components: [MixComponent(sound: .rain), MixComponent(sound: .rain)]
        )) {
            XCTAssertEqual($0 as? MixValidationError, .duplicateSound)
        }
    }
}

final class VolumeCalculationTests: XCTestCase {
    func testItCombinesMasterSoundAndFadeVolumesAndClampsInputs() {
        XCTAssertEqual(AudioVolume.output(master: 0.5, sound: 0.4, fade: 0.5), 0.1, accuracy: 0.0001)
        XCTAssertEqual(AudioVolume.output(master: 2, sound: -1), 0)
        XCTAssertEqual(AudioVolume.output(master: 2, sound: 2), 1)
    }
}

final class TimerCountdownTests: XCTestCase {
    func testItCountsDownAndFadesDuringTheFinalFiveMinutes() {
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        let deadline = start.addingTimeInterval(10 * 60)
        let timer = TimerSnapshot(deadline: deadline)

        XCTAssertEqual(timer.remaining(at: start) ?? -1, 600, accuracy: 0.001)
        XCTAssertEqual(timer.fadeMultiplier(at: deadline.addingTimeInterval(-150)), 0.5, accuracy: 0.001)
        XCTAssertEqual(timer.fadeMultiplier(at: deadline), 0)
        XCTAssertEqual(timer.remaining(at: deadline) ?? -1, 0, accuracy: 0.001)
    }
}

final class PersistenceRoundTripTests: XCTestCase {
    func testItPersistsPreferencesLocallyAndReadsThemBack() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("NightfallTests-\(UUID().uuidString)", isDirectory: true)
        let fileURL = directory.appendingPathComponent("preferences.json")
        defer { try? FileManager.default.removeItem(at: directory) }

        let persistence = JSONPersistenceService(fileURL: fileURL)
        var expected = NightfallPreferences.initial
        expected.activeMix = SoundMix(
            name: "Rainy Night",
            components: [MixComponent(sound: .rain), MixComponent(sound: .brownNoise)]
        )
        expected.masterVolume = 0.37
        expected.soundVolumes[SoundKind.rain.id] = 0.22
        expected.favoriteSoundIDs.insert(SoundKind.forest.id)
        expected.favoriteMixes = [SoundMix.starterPresets[0]]
        expected.timerOption = .ninetyMinutes
        expected.timerDeadline = Date(timeIntervalSince1970: 1_800_000_300)
        expected.hasSeenOnboarding = true
        expected.nightModeEnabled = true

        try persistence.save(expected)

        XCTAssertEqual(persistence.load(), expected)
    }
}
