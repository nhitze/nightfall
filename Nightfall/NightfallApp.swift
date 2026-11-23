import SwiftUI

@main
struct NightfallApp: App {
    @StateObject private var viewModel = NightfallViewModel()
    @AppStorage("nightfall.useLightAppearance") private var useLightAppearance = false

    var body: some Scene {
        WindowGroup {
            NightfallView(
                viewModel: viewModel,
                useLightAppearance: $useLightAppearance
            )
            .preferredColorScheme(useLightAppearance ? .light : .dark)
        }
    }
}
