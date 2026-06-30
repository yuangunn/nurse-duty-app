import SwiftUI
import SwiftData
import NurseDutyModel

struct DutyDetailView: View {
    @Environment(\.modelContext) private var ctx
    @Bindable var profile: DutyProfile

    @State private var editingMeta = false
    @State private var alarmToEdit: AlarmItem?
    @State private var addingAlarm = false
    @State private var checklistToEdit: ChecklistItem?
    @State private var addingChecklist = false

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

            Section("체크리스트") {
                ForEach(checklist) { item in
                    Button { checklistToEdit = item } label: {
                        Text(item.text).foregroundStyle(.primary)
                    }
                }
                .onDelete(perform: archiveChecklist)
                .onMove(perform: moveChecklist)
                Button { addingChecklist = true } label: { Label("체크리스트 추가", systemImage: "plus") }
            }
        }
        .navigationTitle("듀티")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { EditButton() }
        .sheet(isPresented: $editingMeta) { ProfileEditor(profile: profile) }
        .sheet(item: $alarmToEdit) { a in AlarmEditorView(profile: profile, alarm: a) }
        .sheet(isPresented: $addingAlarm) { AlarmEditorView(profile: profile, alarm: nil) }
        .sheet(item: $checklistToEdit) { c in ChecklistItemEditor(profile: profile, item: c) }
        .sheet(isPresented: $addingChecklist) { ChecklistItemEditor(profile: profile, item: nil) }
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

    // soft-delete: archive keeps past check history intact (checks reference the item id).
    private func archiveChecklist(_ idx: IndexSet) {
        for i in idx { checklist[i].isArchived = true }
        try? ctx.save()
    }

    private func moveChecklist(from: IndexSet, to: Int) {
        var arr = checklist
        arr.move(fromOffsets: from, toOffset: to)
        for (i, item) in arr.enumerated() { item.sortOrder = i }
        try? ctx.save()
    }
}

struct ChecklistItemEditor: View {
    @Environment(\.modelContext) private var ctx
    @Environment(\.dismiss) private var dismiss
    let profile: DutyProfile
    let item: ChecklistItem?

    @State private var text = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("할 일") {
                    TextField("예: 활력징후 측정", text: $text, axis: .vertical)
                }
            }
            .navigationTitle(item == nil ? "새 항목" : "항목 편집")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") { save() }
                        .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear { if let item { text = item.text } }
        }
    }

    private func save() {
        if let item {
            item.text = text
        } else {
            let order = (profile.checklistItems.map(\.sortOrder).max() ?? -1) + 1
            ctx.insert(ChecklistItem(text: text, sortOrder: order, profile: profile))
        }
        try? ctx.save()
        dismiss()
    }
}
