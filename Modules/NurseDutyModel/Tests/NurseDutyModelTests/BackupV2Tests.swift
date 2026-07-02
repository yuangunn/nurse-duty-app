import Testing
import Foundation
import SwiftData
@testable import NurseDutyModel

struct BackupV2Tests {
    private func makeContext() throws -> ModelContext {
        let container = try ModelContainer(
            for: DutyProfile.self, ShiftAssignment.self, ChecklistCheck.self, QuickMemo.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        return ModelContext(container)
    }

    @Test func roundTripsThroughAndroidShapedJSON() throws {
        let ctx = try makeContext()
        let p = DutyProfile(name: "Day", colorHex: "#3182F6", kind: "Day",
                            timeText: "06:00 – 14:00", isPreset: true)
        p.alarms = [AlarmItem(label: "인계 준비", hour: 5, minute: 30)]
        p.checklistItems = [ChecklistItem(text: "활력징후 측정")]
        ctx.insert(p)
        ctx.insert(ShiftAssignment(date: Date(), dutyProfileId: p.id, charge: true, note: "인계 메모"))
        ctx.insert(ChecklistCheck(checklistItemId: ChargeRules.itemID, date: Date()))
        ctx.insert(QuickMemo(bedTag: "1001:01", text: "진통제"))
        try ctx.save()

        let data = try JSONEncoder().encode(BackupV2.export(from: ctx))
        let decoded = try JSONDecoder().decode(BackupV2.self, from: data)
        #expect(BackupV2.valid(decoded))

        let ctx2 = try makeContext()
        BackupV2.restore(decoded, into: ctx2)
        try ctx2.save()

        let profiles = try ctx2.fetch(FetchDescriptor<DutyProfile>())
        #expect(profiles.count == 1 && profiles[0].kind == "Day" && profiles[0].timeText == "06:00 – 14:00")
        #expect(profiles[0].alarms.count == 1 && profiles[0].checklistItems.count == 1)
        let assigns = try ctx2.fetch(FetchDescriptor<ShiftAssignment>())
        #expect(assigns.count == 1 && assigns[0].charge && assigns[0].note == "인계 메모")
        let checks = try ctx2.fetch(FetchDescriptor<ChecklistCheck>())
        #expect(checks.count == 1 && checks[0].checklistItemId == ChargeRules.itemID)   // charge:handover 왕복
    }

    @Test func decodesAndroidExportWithOmittedDefaults() throws {
        // kotlinx encodeDefaults=false: default-valued fields are absent
        let json = """
        {"version":1,
         "profiles":[{"id":"a1","name":"Day","colorHex":"#3182F6","kind":"Day","timeText":"06:00 – 14:00","isPreset":true,"createdAt":1780000000000}],
         "alarms":[{"id":"al1","dutyProfileId":"a1","label":"인계","hour":6,"minute":0}],
         "checklist":[{"id":"c1","dutyProfileId":"a1","text":"활력징후"}],
         "checks":[{"id":"k1","checklistItemId":"charge:handover","dayKey":20260702,"checkedAt":1780000000000}],
         "assignments":[{"dayKey":20260702,"dutyProfileId":"a1","charge":true}],
         "memos":[{"id":"m1","bedTag":"1001:01","text":"진통제","createdAt":1780000000000}]}
        """
        let b = try JSONDecoder().decode(BackupV2.self, from: Data(json.utf8))
        #expect(BackupV2.valid(b))

        let ctx = try makeContext()
        BackupV2.restore(b, into: ctx)   // 비-UUID 문자열 id("a1")는 새 UUID로 매핑돼도 관계는 유지
        try ctx.save()
        let profiles = try ctx.fetch(FetchDescriptor<DutyProfile>())
        #expect(profiles.count == 1 && profiles[0].alarms.count == 1 && profiles[0].checklistItems.count == 1)
        let checks = try ctx.fetch(FetchDescriptor<ChecklistCheck>())
        #expect(checks.first?.checklistItemId == ChargeRules.itemID)
    }

    @Test func rejectsEmptyOrOutOfRange() throws {
        #expect(!BackupV2.valid(BackupV2(version: 1)))   // profiles 없음
        let bad = BackupV2(version: 1,
                           profiles: [.init(id: "p", name: "X", colorHex: "#000000", kind: nil, timeText: nil,
                                            isPreset: nil, isArchived: nil, sortOrder: nil, createdAt: nil)],
                           alarms: [.init(id: "a", dutyProfileId: "p", label: "x", hour: 24, minute: 0,
                                          dayOffset: nil, enabled: nil, sortOrder: nil)],
                           checklist: nil, checks: nil, assignments: nil, memos: nil)
        #expect(!BackupV2.valid(bad))
    }
}
