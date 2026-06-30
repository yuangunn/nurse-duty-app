import Testing
import Foundation
import SwiftData
@testable import NurseDutyModel

@Suite struct DayKeyTests {

    private func cal(_ tz: String) -> Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: tz)!
        return c
    }

    // The dayKey of a duty assigned in KST must still resolve to the same calendar day after
    // the device moves to a far timezone — no day-shift, no duplicate assignment.
    @Test func dayKeyIsStableAcrossTimezones() throws {
        let kst = cal("Asia/Seoul")
        let pst = cal("America/Los_Angeles")
        let jun30KST = kst.date(from: DateComponents(year: 2026, month: 6, day: 30))!

        let assignment = ShiftAssignment(date: jun30KST, dutyProfileId: UUID(), calendar: kst)
        #expect(assignment.dayKey == 20260630)

        // A June-30 calendar cell rendered later in PST maps to the same key -> still matches.
        let jun30PST = pst.date(from: DateComponents(year: 2026, month: 6, day: 30))!
        #expect(DayKey.from(jun30PST, pst) == 20260630)
    }

    // Regression for the reviewed bug: re-assigning the same visible day after a TZ change must
    // UPDATE the existing row, not insert a duplicate.
    @Test func reassignAfterTimezoneChangeDoesNotDuplicate() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let kst = cal("Asia/Seoul")
        let pst = cal("America/Los_Angeles")
        let jun30 = DateComponents(year: 2026, month: 6, day: 30)
        let p1 = UUID(), p2 = UUID()

        try Assignments.upsert(in: ctx, date: kst.date(from: jun30)!, dutyProfileId: p1, calendar: kst)
        try ctx.save()
        try Assignments.upsert(in: ctx, date: pst.date(from: jun30)!, dutyProfileId: p2, calendar: pst)
        try ctx.save()

        let all = try ctx.fetch(FetchDescriptor<ShiftAssignment>())
        #expect(all.count == 1)                       // same calendar day -> replaced, not duplicated
        #expect(all.first?.dutyProfileId == p2)
    }
}
