import Foundation

protocol PreferencesPersisting {
    func load() -> NightfallPreferences
    func save(_ preferences: NightfallPreferences) throws
}

struct JSONPersistenceService: PreferencesPersisting {
    private let fileURL: URL

    init(fileURL: URL? = nil, fileManager: FileManager = .default) {
        if let fileURL {
            self.fileURL = fileURL
        } else {
            let supportDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            self.fileURL = supportDirectory.appendingPathComponent("Nightfall", isDirectory: true)
                .appendingPathComponent("preferences.json")
        }
    }

    func load() -> NightfallPreferences {
        guard let data = try? Data(contentsOf: fileURL),
              let preferences = try? JSONDecoder().decode(NightfallPreferences.self, from: data) else {
            return .initial
        }

        return preferences
    }

    func save(_ preferences: NightfallPreferences) throws {
        try FileManager.default.createDirectory(
            at: fileURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        let data = try JSONEncoder().encode(preferences)
        try data.write(to: fileURL, options: .atomic)
    }
}
