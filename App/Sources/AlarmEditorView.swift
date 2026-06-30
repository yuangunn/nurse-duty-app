import SwiftUI
import SwiftData
import NurseDutyModel

struct AlarmEditorView: View {
    @Environment(\.modelContext) private var ctx
    @Environment(\.dismiss) private var dismiss
    let profile: DutyProfile
    let alarm: AlarmItem?

    @State private var label = ""
    @State private var time = Date()
    @State private var nextDay = false
    @State private var enabled = true

    var body: some View {
        NavigationStack {
            Form {
                Section("이름") {
                    TextField("예: 인계", text: $label)
                }
                Section("시각") {
                    DatePicker("시각", selection: $time, displayedComponents: .hourAndMinute)
                        .datePickerStyle(.wheel)
                    Toggle("다음 날(익일)", isOn: $nextDay)
                }
                Section {
                    Toggle("알람 켜기", isOn: $enabled)
                } footer: {
                    Text(nextDay
                         ? "야간 근무처럼 자정을 넘겨 다음 날 아침에 울립니다."
                         : "근무를 배정한 날에 울립니다.")
                }
            }
            .navigationTitle(alarm == nil ? "새 알람" : "알람 편집")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") { save() }
                        .disabled(label.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard let alarm else { return }
        label = alarm.label
        nextDay = alarm.dayOffset > 0
        enabled = alarm.isEnabled
        time = Calendar.current.date(bySettingHour: alarm.hour, minute: alarm.minute, second: 0, of: Date()) ?? Date()
    }

    private func save() {
        let c = Calendar.current.dateComponents([.hour, .minute], from: time)
        let h = c.hour ?? 0, m = c.minute ?? 0
        if let alarm {
            alarm.label = label
            alarm.hour = h; alarm.minute = m
            alarm.dayOffset = nextDay ? 1 : 0
            alarm.isEnabled = enabled
        } else {
            let order = (profile.alarms.map(\.sortOrder).max() ?? -1) + 1
            let new = AlarmItem(label: label, hour: h, minute: m, dayOffset: nextDay ? 1 : 0,
                                isEnabled: enabled, sortOrder: order, profile: profile)
            ctx.insert(new)        // setting profile maintains the inverse relationship
        }
        try? ctx.save()
        rearm(ctx)
        dismiss()
    }
}
