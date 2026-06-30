import Testing
import Foundation
import SwiftData
@testable import NurseDutyModel

@Suite struct BackupTests {

    private func seed(_ ctx: ModelContext) throws -> (profileID: UUID, itemID: UUID, date: Date) {
        let p = DutyProfile(name: "Night", colorHex: "#3B4A6B")
        p.alarms = [AlarmItem(label: "아침 인계", hour: 6, minute: 0, dayOffset: 1)]
        p.checklistItems = [ChecklistItem(text: "활력징후 측정")]
        ctx.insert(p)
        let date = Date()
        try Assignments.upsert(in: ctx, date: date, dutyProfileId: p.id)
        Checklist.toggle(p.checklistItems[0].id, on: date, in: ctx)
        ctx.insert(QuickMemo(bedTag: "1001:01", text: "진통제 호소"))
        try ctx.save()
        return (p.id, p.checklistItems[0].id, date)
    }

    @Test func exportImportRoundTrips() throws {
        let src = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let (_, itemID, date) = try seed(src)
        let data = try JSONEncoder().encode(Backup.export(from: src))

        let dst = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let backup = try JSONDecoder().decode(Backup.self, from: data)
        Backup.restore(backup, into: dst)
        try dst.save()

        #expect(try dst.fetch(FetchDescriptor<DutyProfile>()).count == 1)
        #expect(try dst.fetch(FetchDescriptor<ShiftAssignment>()).count == 1)
        #expect(try dst.fetch(FetchDescriptor<ChecklistCheck>()).count == 1)
        #expect(try dst.fetch(FetchDescriptor<QuickMemo>()).count == 1)

        let p = try #require(try dst.fetch(FetchDescriptor<DutyProfile>()).first)
        #expect(p.name == "Night")
        #expect(p.alarms.first?.dayOffset == 1)
        #expect(p.checklistItems.count == 1)
        #expect(Checklist.isChecked(itemID, on: date, in: dst) == true)   // restored check still resolves
    }

    @Test func restoreReplacesRatherThanAppends() throws {
        let src = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        _ = try seed(src)
        let backup = Backup.export(from: src)

        // restore twice into the same store -> wipe-then-insert means no duplication
        Backup.restore(backup, into: src); try src.save()
        Backup.restore(backup, into: src); try src.save()

        #expect(try src.fetch(FetchDescriptor<DutyProfile>()).count == 1)
        #expect(try src.fetch(FetchDescriptor<QuickMemo>()).count == 1)
    }
}
