import SwiftUI
import SwiftData
import NurseDutyModel

struct DutyDetailView: View {
    @Environment(\.modelContext) private var ctx
    @Bindable var profile: DutyProfile

    @State private var editingMeta = false
    @State private var alarmToEdit: AlarmItem?
    @State private var addingAlarm = false

    private var alarms: [AlarmItem] { profile.alarms.sorted { $0.sortOrder < $1.sortOrder } }
    private var checklist: [ChecklistItem] {
        profile.checklistItems.filter { !$0.isArchived }.sorted { $0.sortOrder < $1.sortOrder }
    }

    var body: some View {
        List {
            Section {
                HStack {
                    Circle().fill(Color(hex: profile.colorHex)).frame(width: 18, height: 18)
                    Text(profile.name).font(.headline)
                    Spacer()
                    Button("편집") { editingMeta = true }
                }
            }

            Section("알람") {
                ForEach(alarms) { alarm in
                    Button { alarmToEdit = alarm } label: { alarmRow(alarm) }
                }
                .onDelete(perform: deleteAlarms)
                Button { addingAlarm = true } label: { Label("알람 추가", systemImage: "plus") }
            }

            if !checklist.isEmpty {
                Section("체크리스트 (미리보기)") {
                    ForEach(checklist) { c in Label(c.text, systemImage: "circle") }
                }
            }
        }
        .navigationTitle("듀티")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $editingMeta) { ProfileEditor(profile: profile) }
        .sheet(item: $alarmToEdit) { a in AlarmEditorView(profile: profile, alarm: a) }
        .sheet(isPresented: $addingAlarm) { AlarmEditorView(profile: profile, alarm: nil) }
    }

    private func alarmRow(_ a: AlarmItem) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(a.label).foregroundStyle(.primary)
                if a.dayOffset > 0 {
                    Text("익일").font(.caption2.weight(.semibold)).foregroundStyle(.orange)
                }
            }
            Spacer()
            Text(String(format: "%02d:%02d", a.hour, a.minute)).monospacedDigit().foregroundStyle(.secondary)
            if !a.isEnabled { Image(systemName: "bell.slash").foregroundStyle(.tertiary) }
        }
    }

    private func deleteAlarms(_ idx: IndexSet) {
        for i in idx { ctx.delete(alarms[i]) }
        try? ctx.save()
        rearm(ctx)
    }
}
