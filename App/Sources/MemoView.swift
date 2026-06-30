import SwiftUI
import SwiftData
import NurseDutyModel

struct MemoView: View {
    @Environment(\.modelContext) private var ctx
    @Query(sort: \QuickMemo.createdAt, order: .reverse) private var memos: [QuickMemo]
    @State private var capturing = false

    private var pending: [QuickMemo] { memos.filter { !$0.isDone } }
    private var done: [QuickMemo] { memos.filter { $0.isDone } }

    var body: some View {
        NavigationStack {
            Group {
                if memos.isEmpty {
                    ContentUnavailableView("메모 없음", systemImage: "note.text",
                                           description: Text("라운딩 중 환자 요청을 빠르게 적어두세요."))
                } else {
                    List {
                        Section("대기 \(pending.count)") {
                            ForEach(pending) { memo in memoRow(memo) }
                                .onDelete { delete($0, from: pending) }
                        }
                        if !done.isEmpty {
                            Section("완료") {
                                ForEach(done) { memo in memoRow(memo).foregroundStyle(.secondary) }
                                    .onDelete { delete($0, from: done) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("빠른 메모")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { capturing = true } label: { Image(systemName: "square.and.pencil") }
                }
            }
            .sheet(isPresented: $capturing) { MemoCaptureSheet() }
        }
    }

    private func memoRow(_ memo: QuickMemo) -> some View {
        HStack(alignment: .top, spacing: 10) {
            if !memo.bedTag.isEmpty {
                Text(memo.bedTag).font(.caption.weight(.bold)).monospacedDigit()
                    .padding(.horizontal, 6).padding(.vertical, 3)
                    .background(Color.accentColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 6))
                    .foregroundStyle(Color.accentColor)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(memo.text)
                Text(timeText(memo.createdAt)).font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
        }
        .swipeActions(edge: .leading) {
            if memo.isDone {
                Button { memo.isDone = false; try? ctx.save() } label: { Label("되돌리기", systemImage: "arrow.uturn.left") }
            } else {
                Button { memo.isDone = true; try? ctx.save() } label: { Label("완료", systemImage: "checkmark") }.tint(.green)
            }
        }
    }

    private func delete(_ idx: IndexSet, from list: [QuickMemo]) {
        for i in idx { ctx.delete(list[i]) }
        try? ctx.save()
    }
    private func timeText(_ d: Date) -> String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M/d HH:mm"
        return f.string(from: d)
    }
}

struct MemoCaptureSheet: View {
    @Environment(\.modelContext) private var ctx
    @Environment(\.dismiss) private var dismiss

    private enum Field { case bed, note }
    @FocusState private var focused: Field?
    @State private var bedTag = ""
    @State private var note = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("병상") {
                    TextField("1001:01", text: $bedTag)
                        .keyboardType(.numbersAndPunctuation)
                        .focused($focused, equals: .bed)
                        .submitLabel(.next)
                        .onSubmit { focused = .note }
                }
                Section("메모") {
                    TextField("예: 진통제 호소", text: $note, axis: .vertical)
                        .focused($focused, equals: .note)
                        .lineLimit(3...6)
                }
            }
            .navigationTitle("빠른 메모")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") { save() }
                        .disabled(note.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            // Defer focus: on first sheet present the field isn't in the responder chain yet at
            // onAppear, so setting focus there is silently dropped and the keyboard won't raise.
            .task { try? await Task.sleep(for: .milliseconds(50)); focused = .bed }
        }
    }

    private func save() {
        ctx.insert(QuickMemo(bedTag: bedTag.trimmingCharacters(in: .whitespaces),
                             text: note.trimmingCharacters(in: .whitespaces)))
        try? ctx.save()
        dismiss()
    }
}
