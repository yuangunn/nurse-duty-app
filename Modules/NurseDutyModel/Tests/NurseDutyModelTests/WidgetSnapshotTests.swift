import Testing
import Foundation
import SwiftData
@testable import NurseDutyModel

@Suite struct WidgetSnapshotTests {

    private var seoul: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }

    @Test func snapshotReflectsTodaysDutyChecklistAlarmAndMemos() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let cal = seoul
        let p = DutyProfile(name: "Day", colorHex: "#4F86C6")
        p.alarms = [AlarmItem(label: "인계", hour: 23, minute: 0)]    // still upcoming at 22:00
        p.checklistItems = [ChecklistItem(text: "활력징후"), ChecklistItem(text: "투약")]
        ctx.insert(p)
        let now = cal.date(from: DateComponents(year: 2026, month: 6, day: 30, hour: 22))!
        try Assignments.upsert(in: ctx, date: now, dutyProfileId: p.id, calendar: cal)
        Checklist.toggle(p.checklistItems[0].id, on: now, in: ctx, calendar: cal)
        ctx.insert(QuickMemo(bedTag: "1001:01", text: "진통제"))
        ctx.insert(QuickMemo(bedTag: "1003:02", text: "오심", isDone: true))   // done -> not counted
        try ctx.save()

        let s = WidgetSnapshotBuilder.build(from: ctx, now: now, calendar: cal)
        #expect(s.dutyName == "Day")
        #expect(s.checklistDone == 1)
        #expect(s.checklistTotal == 2)
        #expect(s.nextAlarmLabel == "인계")
        #expect(s.pendingMemoCount == 1)
    }

    @Test func snapshotIsEmptyWhenNoDutyToday() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let cal = seoul
        let now = cal.date(from: DateComponents(year: 2026, month: 6, day: 30, hour: 9))!
        let s = WidgetSnapshotBuilder.build(from: ctx, now: now, calendar: cal)
        #expect(s.dutyName == nil)
        #expect(s.checklistTotal == 0)
        #expect(s.pendingMemoCount == 0)
    }
}
