import SwiftUI
import UIKit

struct NightfallView: View {
    @ObservedObject private var viewModel: NightfallViewModel
    @ObservedObject private var timerService: SleepTimerService
    @Binding private var useLightAppearance: Bool
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    @State private var isShowingTimer = false

    init(viewModel: NightfallViewModel, useLightAppearance: Binding<Bool>) {
        self.viewModel = viewModel
        self.timerService = viewModel.timerService
        self._useLightAppearance = useLightAppearance
    }

    private var palette: NightfallPalette {
        NightfallPalette(nightMode: viewModel.nightModeEnabled, light: colorScheme == .light)
    }

    var body: some View {
        ZStack {
            palette.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    header

                    if !viewModel.hasSeenOnboarding {
                        onboardingCard
                    }

                    nowPlayingCard
                    activeSoundsSection
                    favoritesSection
                    presetsSection
                    librarySection
                    footer
                }
                .frame(maxWidth: 680)
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 36)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)
        }
        .tint(palette.accent)
        .sheet(isPresented: $isShowingTimer) {
            timerSheet
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
        }
        .alert("Audio unavailable", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { viewModel.errorMessage = nil }
        } message: {
            Text(viewModel.errorMessage ?? "Please try again.")
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                viewModel.refreshTimer()
            }
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: "moon.stars.fill")
                .font(.title2)
                .foregroundStyle(palette.accent)
                .accessibilityHidden(true)

            Text("Nightfall")
                .font(.title2.weight(.semibold))
                .foregroundStyle(palette.primaryText)

            Spacer()

            Button {
                viewModel.setNightMode(!viewModel.nightModeEnabled)
                haptic()
            } label: {
                Image(systemName: viewModel.nightModeEnabled ? "moon.zzz.fill" : "moon.zzz")
                    .font(.body.weight(.medium))
                    .frame(width: 46, height: 46)
                    .foregroundStyle(palette.secondaryText)
                    .background(palette.card, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(viewModel.nightModeEnabled ? "Turn off night mode" : "Turn on night mode")
            .accessibilityHint("Makes the screen extremely dim and removes bright colors.")

            Button {
                useLightAppearance.toggle()
                haptic()
            } label: {
                Image(systemName: useLightAppearance ? "sun.max.fill" : "sun.max")
                    .font(.body.weight(.medium))
                    .frame(width: 46, height: 46)
                    .foregroundStyle(palette.secondaryText)
                    .background(palette.card, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(useLightAppearance ? "Use dark appearance" : "Use light appearance")
        }
        .accessibilityElement(children: .contain)
    }

    private var onboardingCard: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: "lock.shield")
                .font(.title3)
                .foregroundStyle(palette.accent)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 6) {
                Text("Your sounds stay here.")
                    .font(.headline)
                    .foregroundStyle(palette.primaryText)
                Text("Nightfall plays sounds stored on this iPhone. No account, ads, tracking, or data collection.")
                    .font(.subheadline)
                    .foregroundStyle(palette.secondaryText)
                    .fixedSize(horizontal: false, vertical: true)
                Button("Sounds good") {
                    viewModel.finishOnboarding()
                    haptic()
                }
                .font(.subheadline.weight(.semibold))
                .padding(.top, 4)
                .accessibilityLabel("Dismiss privacy introduction")
            }
        }
        .padding(18)
        .background(palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var nowPlayingCard: some View {
        VStack(spacing: 20) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 7) {
                    Text(viewModel.isPlaying ? "NOW PLAYING" : "READY WHEN YOU ARE")
                        .font(.caption.weight(.semibold))
                        .tracking(1.8)
                        .foregroundStyle(palette.secondaryText)
                    Text(viewModel.activeMix.displayName)
                        .font(.system(.largeTitle, design: .rounded).weight(.medium))
                        .foregroundStyle(palette.primaryText)
                        .lineLimit(2)
                        .minimumScaleFactor(0.8)
                        .accessibilityAddTraits(.isHeader)
                    Text(viewModel.activeMix.components.map(\.sound.name).joined(separator: "  ·  "))
                        .font(.subheadline)
                        .foregroundStyle(palette.secondaryText)
                        .lineLimit(2)
                }
                Spacer(minLength: 8)
                Button {
                    viewModel.toggleFavorite(viewModel.activeMix)
                    haptic()
                } label: {
                    Image(systemName: viewModel.isFavorite(viewModel.activeMix) ? "heart.fill" : "heart")
                        .font(.title3)
                        .foregroundStyle(viewModel.isFavorite(viewModel.activeMix) ? palette.accent : palette.secondaryText)
                        .frame(width: 48, height: 48)
                        .background(palette.card, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(viewModel.isFavorite(viewModel.activeMix) ? "Remove mix from favorites" : "Favorite this mix")
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                viewModel.togglePlayback()
                haptic()
            } label: {
                ZStack {
                    Circle()
                        .stroke(palette.outline, lineWidth: 1)
                        .frame(width: 118, height: 118)
                    Circle()
                        .fill(palette.playButton)
                        .frame(width: 92, height: 92)
                    Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 31, weight: .semibold))
                        .foregroundStyle(palette.playGlyph)
                        .offset(x: viewModel.isPlaying ? 0 : 2)
                }
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(viewModel.isPlaying ? "Pause sounds" : "Play sounds")
            .accessibilityHint("Playback continues when Nightfall is in the background.")

            VStack(spacing: 8) {
                HStack {
                    Label("Master volume", systemImage: "speaker.wave.2")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(palette.secondaryText)
                    Spacer()
                    Text("\(Int(viewModel.masterVolume * 100))%")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(palette.secondaryText)
                        .accessibilityHidden(true)
                }
                Slider(
                    value: Binding(
                        get: { Double(viewModel.masterVolume) },
                        set: { viewModel.setMasterVolume(Float($0)) }
                    ),
                    in: 0...1
                )
                .accessibilityLabel("Master volume")
                .accessibilityValue("\(Int(viewModel.masterVolume * 100)) percent")
            }

            Button {
                isShowingTimer = true
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "moon.zzz")
                        .foregroundStyle(palette.accent)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(timerService.remaining.map { "Sounds stop in \(formatTime($0))" } ?? "Sleep timer")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(palette.primaryText)
                        Text(timerService.remaining == nil ? viewModel.timerOption.title : "Fades out over the final 5 minutes")
                            .font(.caption)
                            .foregroundStyle(palette.secondaryText)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(palette.secondaryText)
                }
                .padding(14)
                .background(palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(timerService.remaining.map { "Sleep timer, \(formatTime($0)) remaining" } ?? "Set sleep timer, \(viewModel.timerOption.title)")
        }
        .padding(20)
        .background(
            LinearGradient(
                colors: [palette.heroTop, palette.heroBottom],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(palette.outline.opacity(0.7), lineWidth: 1)
        }
    }

    private var activeSoundsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("In this mix", subtitle: "\(viewModel.activeMix.components.count) of \(MixFactory.maximumSounds) sounds")
            VStack(spacing: 0) {
                ForEach(viewModel.activeMix.components) { component in
                    ActiveSoundRow(
                        component: component,
                        volume: viewModel.soundVolume(for: component.sound),
                        isPlaying: viewModel.isComponentPlaying(component),
                        palette: palette,
                        onVolumeChange: { viewModel.setSoundVolume($0, for: component) },
                        onToggle: { viewModel.toggle(component) },
                        onRemove: { viewModel.remove(component) }
                    )
                    if component.id != viewModel.activeMix.components.last?.id {
                        Divider().overlay(palette.outline).padding(.leading, 58)
                    }
                }
            }
            .padding(.horizontal, 14)
            .background(palette.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    private var favoritesSection: some View {
        let favoriteSounds = SoundKind.all.filter(viewModel.isFavorite)
        let favoriteMixes = viewModel.preferences.favoriteMixes
        return Group {
            if !favoriteSounds.isEmpty || !favoriteMixes.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    sectionTitle("Favorites", subtitle: "Saved on this iPhone")
                    ScrollView(.horizontal) {
                        HStack(spacing: 10) {
                            ForEach(favoriteSounds) { sound in
                                Button {
                                    viewModel.select(sound)
                                    haptic()
                                } label: {
                                    Label(sound.name, systemImage: sound.symbol)
                                        .font(.subheadline.weight(.medium))
                                        .padding(.horizontal, 15)
                                        .padding(.vertical, 12)
                                        .foregroundStyle(palette.primaryText)
                                        .background(palette.card, in: Capsule())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Play favorite sound, \(sound.name)")
                            }
                            ForEach(favoriteMixes) { mix in
                                Button {
                                    viewModel.activate(mix)
                                    haptic()
                                } label: {
                                    Label(mix.displayName, systemImage: "square.stack.3d.up")
                                        .font(.subheadline.weight(.medium))
                                        .padding(.horizontal, 15)
                                        .padding(.vertical, 12)
                                        .foregroundStyle(palette.primaryText)
                                        .background(palette.card, in: Capsule())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel("Play favorite mix, \(mix.displayName)")
                            }
                        }
                    }
                    .scrollIndicators(.hidden)
                }
            }
        }
    }

    private var presetsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("A little inspiration", subtitle: "One-tap mixes")
            VStack(spacing: 10) {
                ForEach(SoundMix.starterPresets) { mix in
                    HStack(spacing: 4) {
                        Button {
                            viewModel.activate(mix)
                            haptic()
                        } label: {
                            HStack(spacing: 13) {
                                Image(systemName: presetSymbol(for: mix))
                                    .font(.title3)
                                    .foregroundStyle(palette.accent)
                                    .frame(width: 40, height: 40)
                                    .background(palette.heroBottom, in: RoundedRectangle(cornerRadius: 13))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(mix.name)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(palette.primaryText)
                                    Text(mix.components.map(\.sound.name).joined(separator: " + "))
                                        .font(.caption)
                                        .foregroundStyle(palette.secondaryText)
                                }
                                Spacer()
                                Image(systemName: "play.fill")
                                    .font(.caption)
                                    .foregroundStyle(palette.secondaryText)
                            }
                            .padding(.leading, 14)
                            .padding(.vertical, 9)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Play preset \(mix.name), \(mix.components.map(\.sound.name).joined(separator: " and "))")

                        Button {
                            viewModel.toggleFavorite(mix)
                            haptic()
                        } label: {
                            Image(systemName: viewModel.isFavorite(mix) ? "heart.fill" : "heart")
                                .foregroundStyle(viewModel.isFavorite(mix) ? palette.accent : palette.secondaryText)
                                .frame(width: 52, height: 52)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(viewModel.isFavorite(mix) ? "Remove \(mix.name) from favorites" : "Favorite \(mix.name)")
                    }
                    .background(palette.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }
            }
        }
    }

    private var librarySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionTitle("Sound library", subtitle: "Tap to play · + to add to your mix")
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(SoundKind.all) { sound in
                    SoundLibraryCard(
                        sound: sound,
                        isSelected: viewModel.activeMix.components.contains(where: { $0.sound == sound }),
                        isFavorite: viewModel.isFavorite(sound),
                        palette: palette,
                        onSelect: {
                            viewModel.select(sound)
                            haptic()
                        },
                        onAdd: {
                            viewModel.addToMix(sound)
                            haptic()
                        },
                        onFavorite: {
                            viewModel.toggleFavorite(sound)
                            haptic()
                        }
                    )
                }
            }
        }
    }

    private var footer: some View {
        VStack(spacing: 5) {
            Text("Made for quiet nights.")
                .font(.caption.weight(.medium))
                .foregroundStyle(palette.secondaryText)
            Text("All playback stays on this iPhone.")
                .font(.caption2)
                .foregroundStyle(palette.secondaryText.opacity(0.8))
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 6)
    }

    private var timerSheet: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(SleepTimerOption.allCases) { option in
                        Button {
                            viewModel.setTimerOption(option)
                        } label: {
                            HStack {
                                Text(option.title)
                                    .foregroundStyle(.primary)
                                Spacer()
                                if option == viewModel.timerOption {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(.tint)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .accessibilityLabel("Timer duration, \(option.title)")
                        .accessibilityAddTraits(option == viewModel.timerOption ? .isSelected : [])
                    }
                } header: {
                    Text("Fade out over the final 5 minutes")
                } footer: {
                    Text("Until morning ends at 7:00 AM. The timer is saved if you close Nightfall.")
                }

                if timerService.remaining != nil {
                    Section {
                        Button("Start this timer") {
                            viewModel.startTimer()
                            isShowingTimer = false
                        }
                        .fontWeight(.semibold)
                        Button("Turn off timer", role: .destructive) {
                            viewModel.cancelTimer()
                            isShowingTimer = false
                        }
                    }
                } else {
                    Section {
                        Button("Start \(viewModel.timerOption.title) timer") {
                            viewModel.startTimer()
                            isShowingTimer = false
                        }
                        .fontWeight(.semibold)
                    }
                }
            }
            .navigationTitle("Sleep timer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { isShowingTimer = false }
                }
            }
        }
    }

    private func sectionTitle(_ title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.title3.weight(.semibold))
                .foregroundStyle(palette.primaryText)
            Text(subtitle)
                .font(.caption)
                .foregroundStyle(palette.secondaryText)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private func presetSymbol(for mix: SoundMix) -> String {
        switch mix.name {
        case "Rainy Night": "cloud.moon.rain.fill"
        case "Quiet Room": "fanblades.fill"
        default: "flame.fill"
        }
    }

    private func formatTime(_ interval: TimeInterval) -> String {
        let seconds = max(0, Int(interval))
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }

    private func haptic() {
        guard !viewModel.nightModeEnabled else { return }
        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
    }
}

private struct ActiveSoundRow: View {
    let component: MixComponent
    let volume: Float
    let isPlaying: Bool
    let palette: NightfallPalette
    let onVolumeChange: (Float) -> Void
    let onToggle: () -> Void
    let onRemove: () -> Void

    var body: some View {
        VStack(spacing: 7) {
            HStack(spacing: 10) {
                Image(systemName: component.sound.symbol)
                    .font(.body)
                    .foregroundStyle(palette.accent)
                    .frame(width: 34, height: 34)
                    .accessibilityHidden(true)
                Text(component.sound.name)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(palette.primaryText)
                    .lineLimit(1)
                Spacer(minLength: 4)
                Button(action: onToggle) {
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.caption.weight(.semibold))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(isPlaying ? "Pause \(component.sound.name)" : "Play \(component.sound.name)")
                Button(action: onRemove) {
                    Image(systemName: "xmark")
                        .font(.caption.weight(.semibold))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove \(component.sound.name) from mix")
            }
            HStack(spacing: 8) {
                Image(systemName: "speaker")
                    .font(.caption2)
                    .foregroundStyle(palette.secondaryText)
                    .accessibilityHidden(true)
                Slider(
                    value: Binding(get: { Double(volume) }, set: { onVolumeChange(Float($0)) }),
                    in: 0...1
                )
                .accessibilityLabel("\(component.sound.name) volume")
                .accessibilityValue("\(Int(volume * 100)) percent")
            }
            .padding(.leading, 40)
        }
        .padding(.vertical, 8)
    }
}

private struct SoundLibraryCard: View {
    let sound: SoundKind
    let isSelected: Bool
    let isFavorite: Bool
    let palette: NightfallPalette
    let onSelect: () -> Void
    let onAdd: () -> Void
    let onFavorite: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Button(action: onSelect) {
                    Image(systemName: sound.symbol)
                        .font(.title3)
                        .foregroundStyle(palette.accent)
                        .frame(width: 38, height: 38)
                        .background(palette.heroBottom, in: RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Play \(sound.name)")

                Spacer(minLength: 0)

                Button(action: onFavorite) {
                    Image(systemName: isFavorite ? "heart.fill" : "heart")
                        .font(.caption)
                        .foregroundStyle(isFavorite ? palette.accent : palette.secondaryText)
                        .frame(width: 40, height: 40)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(isFavorite ? "Remove \(sound.name) from favorites" : "Favorite \(sound.name)")

                Button(action: onAdd) {
                    Image(systemName: "plus")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(palette.primaryText)
                        .frame(width: 40, height: 40)
                        .background(palette.heroBottom, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Add \(sound.name) to current mix")
            }

            Button(action: onSelect) {
                HStack(spacing: 5) {
                    Text(sound.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.primaryText)
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)
                    Spacer(minLength: 0)
                    if isSelected {
                        Circle()
                            .fill(palette.accent)
                            .frame(width: 7, height: 7)
                            .accessibilityHidden(true)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Play \(sound.name)")
            .accessibilityAddTraits(isSelected ? .isSelected : [])
        }
        .padding(13)
        .background(palette.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(isSelected ? palette.accent.opacity(0.65) : palette.outline, lineWidth: 1)
        }
    }
}

private struct NightfallPalette {
    let background: Color
    let card: Color
    let heroTop: Color
    let heroBottom: Color
    let primaryText: Color
    let secondaryText: Color
    let accent: Color
    let outline: Color
    let playButton: Color
    let playGlyph: Color

    init(nightMode: Bool, light: Bool) {
        if nightMode {
            background = Color(red: 0.012, green: 0.012, blue: 0.014)
            card = Color(red: 0.035, green: 0.035, blue: 0.038)
            heroTop = card
            heroBottom = Color(red: 0.02, green: 0.02, blue: 0.022)
            primaryText = Color(white: 0.47)
            secondaryText = Color(white: 0.31)
            accent = Color(white: 0.42)
            outline = Color(white: 0.1)
            playButton = Color(white: 0.3)
            playGlyph = Color(white: 0.06)
        } else if light {
            background = Color(red: 0.95, green: 0.95, blue: 0.94)
            card = Color.white.opacity(0.9)
            heroTop = Color(red: 0.91, green: 0.92, blue: 0.92)
            heroBottom = Color(red: 0.86, green: 0.88, blue: 0.89)
            primaryText = Color(red: 0.12, green: 0.15, blue: 0.17)
            secondaryText = Color(red: 0.38, green: 0.42, blue: 0.44)
            accent = Color(red: 0.32, green: 0.45, blue: 0.48)
            outline = Color.black.opacity(0.08)
            playButton = Color(red: 0.28, green: 0.41, blue: 0.44)
            playGlyph = .white
        } else {
            background = Color(red: 0.035, green: 0.047, blue: 0.057)
            card = Color(red: 0.075, green: 0.09, blue: 0.10)
            heroTop = Color(red: 0.10, green: 0.13, blue: 0.14)
            heroBottom = Color(red: 0.07, green: 0.09, blue: 0.10)
            primaryText = Color(red: 0.91, green: 0.93, blue: 0.91)
            secondaryText = Color(red: 0.61, green: 0.67, blue: 0.66)
            accent = Color(red: 0.66, green: 0.78, blue: 0.73)
            outline = Color.white.opacity(0.08)
            playButton = Color(red: 0.72, green: 0.82, blue: 0.77)
            playGlyph = Color(red: 0.08, green: 0.12, blue: 0.12)
        }
    }
}
