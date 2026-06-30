import SwiftUI
import SwiftData
import NurseDutyModel

struct ProfilesView: View {
    @Environment(\.modelContext) private var ctx
    @Query(sort: \DutyProfile.sortOrder) private var profiles: [DutyProfile]
    @State private var creatingNew = false

    private var active: [DutyProfile] { profiles.filter { !$0.isArchived } }

    var body: some View {
        NavigationStack {
            List {
                ForEach(active) { p in
                    NavigationLink { DutyDetailView(profile: p) } label: { row(p) }
                }
                .onDelete(perform: archive)
            }
            .navigationTitle("듀티")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { creatingNew = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $creatingNew) { ProfileEditor(profile: nil) }
        }
    }

    private func row(_ p: DutyProfile) -> some View {
        HStack {
            Circle().fill(Color(hex: p.colorHex)).frame(width: 16, height: 16)
            Text(p.name).foregroundStyle(.primary)
            Spacer()
            Text("알람 \(p.alarms.count) · 체크 \(p.checklistItems.filter { !$0.isArchived }.count)")
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    // soft-delete: archive (keeps past assignments/history intact), never hard-delete.
    private func archive(_ idx: IndexSet) {
        for i in idx { active[i].isArchived = true }
        try? ctx.save()
        rearm(ctx)   // archived duty's future alarms must be pulled
    }
}

struct ProfileEditor: View {
    @Environment(\.modelContext) private var ctx
    @Environment(\.dismiss) private var dismiss
    let profile: DutyProfile?

    @State private var name = ""
    @State private var colorHex = "#4F86C6"

    var body: some View {
        NavigationStack {
            Form {
                Section("이름") {
                    TextField("예: Charge Day", text: $name)
                }
                Section("색상") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 12) {
                        ForEach(dutySwatches, id: \.self) { hex in
                            Circle().fill(Color(hex: hex)).frame(width: 30, height: 30)
                                .overlay {
                                    if hex == colorHex {
                                        Image(systemName: "checkmark").font(.caption.bold()).foregroundStyle(.white)
                                    }
                                }
                                .onTapGesture { colorHex = hex }
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle(profile == nil ? "새 듀티" : "듀티 편집")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") { save() }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear {
                if let profile { name = profile.name; colorHex = profile.colorHex }
            }
        }
    }

    private func save() {
        if let profile {
            profile.name = name
            profile.colorHex = colorHex
        } else {
            let maxOrder = (try? ctx.fetch(FetchDescriptor<DutyProfile>()))?.map(\.sortOrder).max() ?? -1
            ctx.insert(DutyProfile(name: name, colorHex: colorHex, sortOrder: maxOrder + 1))
        }
        try? ctx.save()
        dismiss()
    }
}
