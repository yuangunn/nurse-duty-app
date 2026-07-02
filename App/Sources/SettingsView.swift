import SwiftUI
import SwiftData
import UniformTypeIdentifiers
import NurseDutyModel

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

struct SettingsView: View {
    @Environment(\.modelContext) private var ctx
    @Environment(\.dismiss) private var dismiss
    @Query private var assignments: [ShiftAssignment]
    @Query(sort: \DutyProfile.sortOrder) private var profiles: [DutyProfile]

    @AppStorage("userName") private var userName = ""
    @State private var exporting = false
    @State private var importing = false
    @State private var exportDoc = BackupDocument(data: Data())
    @State private var alert: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("프로필") {
                    TextField("이름 (홈 인사말에 표시)", text: $userName)
                }
                statsSection
                backupSection
            }
            .navigationTitle("설정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("완료") { dismiss() } } }
            .fileExporter(isPresented: $exporting, document: exportDoc, contentType: .json,
                          defaultFilename: "NurseDuty-backup") { result in
                if case .failure(let e) = result { alert = "내보내기 실패: \(e.localizedDescription)" }
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { handleImport($0) }
            .alert(alert ?? "", isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } })) {
                Button("확인", role: .cancel) {}
            }
        }
    }

    @ViewBuilder private var statsSection: some View {
        Section("이번 달") {
            if monthCounts.isEmpty {
                Text("이번 달 배정 없음").foregroundStyle(.secondary)
            } else {
                ForEach(monthCounts, id: \.name) { row in
                    HStack {
                        Circle().fill(Color(hex: row.color)).frame(width: 12, height: 12)
                        Text(row.name)
                        Spacer()
                        Text("\(row.count)일").foregroundStyle(.secondary).monospacedDigit()
                    }
                }
            }
        }
    }

    @ViewBuilder private var backupSection: some View {
        Section {
            Button { exportDoc = BackupDocument(data: backupData()); exporting = true } label: {
                Label("내보내기 (JSON)", systemImage: "square.and.arrow.up")
            }
            Button { importing = true } label: {
                Label("불러오기 (복원)", systemImage: "square.and.arrow.down")
            }
        } header: {
            Text("백업")
        } footer: {
            Text("계정이 없으니 기기 교체·재설치 전에 내보내 두세요. 불러오면 현재 데이터를 덮어씁니다.")
        }
    }

    private func backupData() -> Data {
        // v2 = Android-compatible flat format; a DENO backup now restores on either platform
        (try? JSONEncoder().encode(BackupV2.export(from: ctx))) ?? Data()
    }

    private func handleImport(_ result: Result<URL, Error>) {
        guard case .success(let url) = result else { return }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else {
            alert = "불러오기 실패: 파일을 읽을 수 없습니다."
            return
        }
        if let v2 = try? JSONDecoder().decode(BackupV2.self, from: data) {   // Android/DENO 공통 포맷
            guard BackupV2.valid(v2) else { alert = "불러오기 실패: 백업 파일이 아니거나 손상됐습니다."; return }
            BackupV2.restore(v2, into: ctx)
            try? ctx.save()
            rearm(ctx)
            alert = "복원 완료 (근무 \((v2.assignments ?? []).count) · 메모 \((v2.memos ?? []).count))"
        } else if let legacy = try? JSONDecoder().decode(Backup.self, from: data) {   // 구 iOS 포맷
            Backup.restore(legacy, into: ctx)
            try? ctx.save()
            rearm(ctx)
            alert = "복원 완료 (근무 \(legacy.assignments.count) · 메모 \(legacy.memos.count))"
        } else {
            alert = "불러오기 실패: 백업 파일이 아니거나 손상됐습니다."
        }
    }

    private struct CountRow { let name: String; let color: String; let count: Int }
    private var monthCounts: [CountRow] {
        let cal = Calendar.current
        let c = cal.dateComponents([.year, .month], from: Date())
        let ym = (c.year ?? 0) * 10000 + (c.month ?? 0) * 100
        let inMonth = assignments.filter { $0.dayKey >= ym && $0.dayKey < ym + 100 }
        let byID = Dictionary(grouping: inMonth, by: { $0.dutyProfileId }).mapValues(\.count)
        return profiles.compactMap { p in
            guard let n = byID[p.id], n > 0 else { return nil }
            return CountRow(name: p.name, color: p.colorHex, count: n)
        }
    }
}
