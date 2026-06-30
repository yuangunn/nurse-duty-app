import SwiftUI
import SwiftData
import UIKit
import NurseDutyModel

struct TodayView: View {
    @Environment(\.modelContext) private var ctx
    @Environment(\.scenePhase) private var scenePhase
    @Query private var assignments: [ShiftAssignment]
    @Query(sort: \DutyProfile.sortOrder) private var profiles: [DutyProfile]
    @Query private var checks: [ChecklistCheck]
    @State private var dayToken = 0   // bumped at the date boundary so "today" rolls over while foregrounded
    @State private var showSettings = false

    private let cal = Calendar.current
    private var today: Date { cal.startOfDay(for: Date()) }
    private var todayKey: Int { DayKey.from(Date(), cal) }

    private var todaysProfile: DutyProfile? {
        guard let a = assignments.first(where: { $0.dayKey == todayKey }) else { return nil }
        return profiles.first { $0.id == a.dutyProfileId }
    }
    private var checkedToday: Set<UUID> {
        Set(checks.filter { $0.dayKey == todayKey }.map(\.checklistItemId))
    }

    var body: some View {
        NavigationStack {
            Group {
                if let profile = todaysProfile {
                    dutyList(profile)
                } else {
                    ContentUnavailableView("오늘 근무가 없습니다",
                                           systemImage: "calendar",
                                           description: Text("근무표 탭에서 오늘 듀티를 배정하세요."))
                }
            }
            .navigationTitle(todayTitle)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { showSettings = true } label: { Image(systemName: "gearshape") }
                }
            }
            .sheet(isPresented: $showSettings) { SettingsView() }
            .onAppear {
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("--open-settings") { showSettings = true }
                #endif
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
                dayToken &+= 1
            }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { dayToken &+= 1 }   // returning to the app re-reads the current day
            }
        }
    }

    private func dutyList(_ profile: DutyProfile) -> some View {
        let items = profile.checklistItems.filter { !$0.isArchived }.sorted { $0.sortOrder < $1.sortOrder }
        let alarms = profile.alarms.filter { $0.isEnabled }.sorted { $0.sortOrder < $1.sortOrder }
        let done = items.filter { checkedToday.contains($0.id) }.count

        return List {
            Section {
                HStack {
                    Circle().fill(Color(hex: profile.colorHex)).frame(width: 18, height: 18)
                    Text(profile.name).font(.headline)
                    Spacer()
                    if !items.isEmpty {
                        Text("\(done)/\(items.count)").foregroundStyle(.secondary).monospacedDigit()
                    }
                }
            }

            if items.isEmpty {
                Section { Text("이 듀티엔 체크리스트가 없습니다.").foregroundStyle(.secondary) }
            } else {
                Section("체크리스트") {
                    ForEach(items) { item in
                        Button { toggle(item) } label: { checkRow(item) }
                            .buttonStyle(.plain)
                    }
                }
            }

            if !alarms.isEmpty {
                Section("오늘 알람") {
                    ForEach(alarms) { a in
                        HStack {
                            Text(a.label)
                            Spacer()
                            Text(timeText(a)).foregroundStyle(.secondary).monospacedDigit()
                        }
                    }
                }
            }
        }
    }

    private func checkRow(_ item: ChecklistItem) -> some View {
        let on = checkedToday.contains(item.id)
        return HStack {
            Image(systemName: on ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(on ? Color.accentColor : Color.secondary)
            Text(item.text)
                .strikethrough(on, color: .secondary)
                .foregroundStyle(on ? .secondary : .primary)
        }
    }

    private func toggle(_ item: ChecklistItem) {
        Checklist.toggle(item.id, on: today, in: ctx)
        try? ctx.save()
    }
    private func timeText(_ a: AlarmItem) -> String {
        let s = String(format: "%02d:%02d", a.hour, a.minute)
        return a.dayOffset > 0 ? "익일 \(s)" : s
    }
    private var todayTitle: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일 (EEE)"
        return f.string(from: Date())
    }
}
