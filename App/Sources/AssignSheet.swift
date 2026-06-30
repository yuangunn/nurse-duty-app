import SwiftUI
import SwiftData
import NurseDutyModel

struct AssignSheet: View {
    let date: Date

    @Environment(\.modelContext) private var ctx
    @Environment(\.dismiss) private var dismiss
    @Query private var assignments: [ShiftAssignment]
    @Query(sort: \DutyProfile.sortOrder) private var profiles: [DutyProfile]

    private let cal = Calendar.current

    private var current: ShiftAssignment? {
        let day = cal.startOfDay(for: date)
        return assignments.first { cal.startOfDay(for: $0.date) == day }
    }
    private var currentProfile: DutyProfile? {
        current.flatMap { a in profiles.first { $0.id == a.dutyProfileId } }
    }
    private var activeProfiles: [DutyProfile] { profiles.filter { !$0.isArchived } }

    var body: some View {
        NavigationStack {
            List {
                Section("근무 선택") {
                    ForEach(activeProfiles) { p in
                        Button { assign(p) } label: {
                            HStack {
                                Circle().fill(Color(hex: p.colorHex)).frame(width: 14, height: 14)
                                Text(p.name).foregroundStyle(.primary)
                                Spacer()
                                if currentProfile?.id == p.id {
                                    Image(systemName: "checkmark").foregroundStyle(.tint)
                                }
                            }
                        }
                    }
                }

                if current != nil {
                    Section {
                        Button("근무 지우기", role: .destructive) { clear() }
                    }
                }

                if let p = currentProfile {
                    let alarms = p.alarms.sorted { $0.sortOrder < $1.sortOrder }
                    if !alarms.isEmpty {
                        Section("알람 미리보기") {
                            ForEach(alarms) { a in
                                HStack {
                                    Text(a.label)
                                    Spacer()
                                    Text(timeText(a)).foregroundStyle(.secondary).monospacedDigit()
                                }
                            }
                        }
                    }
                    let items = p.checklistItems.filter { !$0.isArchived }.sorted { $0.sortOrder < $1.sortOrder }
                    if !items.isEmpty {
                        Section("체크리스트 미리보기") {
                            ForEach(items) { c in
                                Label(c.text, systemImage: "circle")
                            }
                        }
                    }
                }
            }
            .navigationTitle(dateTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("완료") { dismiss() } }
            }
        }
    }

    private func assign(_ p: DutyProfile) {
        _ = try? Assignments.upsert(in: ctx, date: date, dutyProfileId: p.id)
        try? ctx.save()
    }
    private func clear() {
        try? Assignments.clear(in: ctx, date: date)
        try? ctx.save()
    }
    private func timeText(_ a: AlarmItem) -> String {
        let s = String(format: "%02d:%02d", a.hour, a.minute)
        return a.dayOffset > 0 ? "익일 \(s)" : s
    }
    private var dateTitle: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일 (EEE)"
        return f.string(from: date)
    }
}
