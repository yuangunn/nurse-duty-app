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
        let st = model.state
        let kind = st.dutyKind ?? "None"
        NavigationStack {
            ScrollView {
                VStack(spacing: 8) {
                    // 1b duty-gradient header (parity with the Wear OS card)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(st.dutyName.map { "\($0) · \(Duty.ko(kind))" } ?? "오늘 근무 없음")
                            .font(.system(size: 15, weight: .bold)).foregroundStyle(.white)
                        if st.charge == true {
                            HStack(spacing: 3) {
                                Image(systemName: "crown.fill").font(.system(size: 9))
                                    .foregroundStyle(Color(hex: "#FFE49B"))
                                Text("차지 · 팀 리더").font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(Color(hex: "#FFE49B"))
                            }
                        }
                        if kind != "None", let t = st.dutyTimeText, !t.isEmpty {
                            Text(t).font(.system(size: 11)).foregroundStyle(.white.opacity(0.9))
                        }
                        HStack(spacing: 8) {
                            let done = st.checklist.filter(\.checked).count
                            if !st.checklist.isEmpty {
                                Text("\(done)/\(st.checklist.count) 완료")
                                    .font(.system(size: 11, weight: .semibold)).foregroundStyle(.white)
                            }
                            if let label = st.nextAlarmLabel, let date = st.nextAlarmDate {
                                Text("⏰ \(label) \(date, format: .dateTime.hour().minute())")
                                    .font(.system(size: 10)).foregroundStyle(.white.opacity(0.9))
                            }
                        }
                        .padding(.top, 2)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 11)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Duty.gradient(kind), in: RoundedRectangle(cornerRadius: 16))

                    ForEach(st.checklist) { item in
                        Button { model.toggle(item.id) } label: {
                            HStack {
                                Image(systemName: item.checked ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(item.checked ? Duty.success : .secondary)
                                Text(item.text).font(.system(size: 13))
                                    .strikethrough(item.checked)
                                    .foregroundStyle(item.checked ? .secondary : .primary)
                                Spacer()
                            }
                            .padding(.horizontal, 12).padding(.vertical, 9)
                            .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 13))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 4)
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
