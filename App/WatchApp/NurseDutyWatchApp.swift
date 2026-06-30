import SwiftUI
import WatchConnectivity
import NurseDutyModel

@main
struct NurseDutyWatchApp: App {
    @StateObject private var model = WatchModel()
    var body: some Scene {
        WindowGroup { WatchRootView(model: model) }
    }
}

final class WatchModel: NSObject, ObservableObject, WCSessionDelegate {
    @Published var state = WatchState(dayKey: 0, dutyName: nil, dutyColorHex: nil,
                                      nextAlarmLabel: nil, nextAlarmDate: nil, pendingMemoCount: 0, checklist: [])

    override init() {
        super.init()
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--watch-preview") { state = .placeholder }
        #endif
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    func toggle(_ id: UUID) {
        if let i = state.checklist.firstIndex(where: { $0.id == id }) {
            state.checklist[i].checked.toggle()      // optimistic; phone re-sends authoritative state
        }
        send(.toggleCheck(itemID: id, dayKey: state.dayKey))
    }

    func addMemo(bedTag: String, text: String) {
        state.pendingMemoCount += 1                  // optimistic
        send(.addMemo(id: UUID(), bedTag: bedTag, text: text, createdAt: Date()))
    }

    private func send(_ cmd: WatchCommand) {
        guard let data = try? JSONEncoder().encode(cmd) else { return }
        WCSession.default.transferUserInfo(["command": data])   // guaranteed FIFO background delivery
    }

    func session(_ s: WCSession, didReceiveApplicationContext ctx: [String: Any]) {
        guard let data = ctx["state"] as? Data,
              let st = try? JSONDecoder().decode(WatchState.self, from: data) else { return }
        DispatchQueue.main.async { self.state = st }
    }
    func session(_ s: WCSession, activationDidCompleteWith activationState: WCSessionActivationState,
                 error: Error?) {}
    #if os(iOS)   // required on iOS, unavailable on watchOS
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) {}
    #endif
}

struct WatchRootView: View {
    @ObservedObject var model: WatchModel
    var body: some View {
        TabView {
            WatchTodayView(model: model)
            WatchMemoView(model: model)
        }
    }
}

struct WatchTodayView: View {
    @ObservedObject var model: WatchModel
    var body: some View {
        NavigationStack {
            List {
                if let duty = model.state.dutyName {
                    Section {
                        HStack(spacing: 6) {
                            Circle().fill(Color(hex: model.state.dutyColorHex ?? "#9AA0A6")).frame(width: 10, height: 10)
                            Text(duty).font(.headline)
                        }
                        if let label = model.state.nextAlarmLabel, let date = model.state.nextAlarmDate {
                            HStack(spacing: 4) {
                                Image(systemName: "bell.fill")
                                Text(label)
                                Spacer()
                                Text(date, format: .dateTime.hour().minute())
                            }.font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    if !model.state.checklist.isEmpty {
                        Section("체크리스트") {
                            ForEach(model.state.checklist) { item in
                                Button { model.toggle(item.id) } label: {
                                    HStack {
                                        Image(systemName: item.checked ? "checkmark.circle.fill" : "circle")
                                            .foregroundStyle(item.checked ? .green : .secondary)
                                        Text(item.text).strikethrough(item.checked)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("오늘 근무 없음").foregroundStyle(.secondary)
                }
            }
            .navigationTitle("오늘")
        }
    }
}

struct WatchMemoView: View {
    @ObservedObject var model: WatchModel
    @State private var bedTag = ""
    @State private var text = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("병상 1001:01", text: $bedTag)
                TextField("메모 (받아쓰기)", text: $text)
                Button("저장") { save() }
                    .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
                if model.state.pendingMemoCount > 0 {
                    Text("대기 메모 \(model.state.pendingMemoCount)").font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("빠른 메모")
        }
    }

    private func save() {
        guard !text.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        model.addMemo(bedTag: bedTag.trimmingCharacters(in: .whitespaces),
                      text: text.trimmingCharacters(in: .whitespaces))
        bedTag = ""; text = ""
    }
}
