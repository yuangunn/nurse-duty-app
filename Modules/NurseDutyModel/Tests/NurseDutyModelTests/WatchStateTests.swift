import Testing
import Foundation
import SwiftData
@testable import NurseDutyModel

@Suite struct WatchStateTests {

    private var seoul: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }

    @Test func stateReflectsTodaysDutyAndChecks() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let cal = seoul
        let p = DutyProfile(name: "Night", colorHex: "#3B4A6B")
        p.checklistItems = [ChecklistItem(text: "활력징후", sortOrder: 0),
                            ChecklistItem(text: "투약", sortOrder: 1)]
        ctx.insert(p)
        let now = cal.date(from: DateComponents(year: 2026, month: 6, day: 30, hour: 22))!
        try Assignments.upsert(in: ctx, date: now, dutyProfileId: p.id, calendar: cal)
        Checklist.toggle(p.checklistItems[0].id, on: now, in: ctx, calendar: cal)
        try ctx.save()

        let s = WatchSync.state(from: ctx, now: now, calendar: cal)
        #expect(s.dutyName == "Night")
        #expect(s.checklist.count == 2)
        #expect(s.checklist.first { $0.text == "활력징후" }?.checked == true)
        #expect(s.checklist.first { $0.text == "투약" }?.checked == false)
    }

    @Test func applyToggleCheckFlipsState() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let cal = seoul
        let item = UUID()
        let dayKey = 20260630
        #expect(Checklist.isChecked(item, on: DayKey.date(dayKey, cal), in: ctx, calendar: cal) == false)
        WatchSync.apply(.toggleCheck(itemID: item, dayKey: dayKey), to: ctx, calendar: cal)
        #expect(Checklist.isChecked(item, on: DayKey.date(dayKey, cal), in: ctx, calendar: cal) == true)
    }

    @Test func applyAddMemoIsIdempotent() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let id = UUID()
        let cmd = WatchCommand.addMemo(id: id, bedTag: "1001:01", text: "진통제",
                                       createdAt: Date(timeIntervalSinceReferenceDate: 0))
        WatchSync.apply(cmd, to: ctx)
        WatchSync.apply(cmd, to: ctx)   // re-delivered -> must not duplicate
        #expect(try ctx.fetch(FetchDescriptor<QuickMemo>()).count == 1)
    }

    @Test func watchCommandCodableRoundTrips() throws {
        let cmd = WatchCommand.addMemo(id: UUID(), bedTag: "305:02", text: "오심",
                                       createdAt: Date(timeIntervalSinceReferenceDate: 123))
        let data = try JSONEncoder().encode(cmd)
        let back = try JSONDecoder().decode(WatchCommand.self, from: data)
        if case let .addMemo(_, bedTag, text, _) = back {
            #expect(bedTag == "305:02"); #expect(text == "오심")
        } else { Issue.record("decoded wrong case") }
    }
}
