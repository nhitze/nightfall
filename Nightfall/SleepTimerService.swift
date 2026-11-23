import Combine
import Foundation

@MainActor
final class SleepTimerService: ObservableObject {
    @Published private(set) var deadline: Date?
    @Published private(set) var remaining: TimeInterval?

    private var ticker: Timer?
    private let onTick: (TimerSnapshot, Bool) -> Void

    init(deadline: Date? = nil, onTick: @escaping (TimerSnapshot, Bool) -> Void) {
        self.deadline = deadline
        self.onTick = onTick
        refresh()
        if self.deadline != nil {
            startTicker()
        }
    }

    func start(option: SleepTimerOption, from date: Date = Date()) -> Date {
        let newDeadline = option.deadline(from: date)
        deadline = newDeadline
        refresh(at: date)
        startTicker()
        return newDeadline
    }

    func cancel() {
        ticker?.invalidate()
        ticker = nil
        deadline = nil
        remaining = nil
        onTick(TimerSnapshot(deadline: nil), false)
    }

    func refresh(at date: Date = Date()) {
        let snapshot = TimerSnapshot(deadline: deadline)
        remaining = snapshot.remaining(at: date)
        if let remaining, remaining <= 0 {
            ticker?.invalidate()
            ticker = nil
            deadline = nil
            self.remaining = nil
            onTick(snapshot, true)
            return
        }
        onTick(snapshot, false)
    }

    private func startTicker() {
        ticker?.invalidate()
        ticker = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }
}
