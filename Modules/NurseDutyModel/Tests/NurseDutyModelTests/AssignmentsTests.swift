import Testing
import Foundation
import SwiftData
@testable import NurseDutyModel

@Suite struct AssignmentsTests {

    // Phase-1 acceptance check: reassigning a duty to a day that already has one
    // REPLACES it — no duplicate rows.
    @Test func reassigningSameDayReplacesNotDuplicates() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let day = Date()
        let dayShift = UUID(), nightShift = UUID()

        try Assignments.upsert(in: ctx, date: day, dutyProfileId: dayShift)
        try ctx.save()
        try Assignments.upsert(in: ctx, date: day, dutyProfileId: nightShift)   // reassign same day
        try ctx.save()

        let all = try ctx.fetch(FetchDescriptor<ShiftAssignment>())
        #expect(all.count == 1)
        #expect(all.first?.dutyProfileId == nightShift)
    }

    @Test func differentDaysCoexist() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Asia/Seoul")!
        let jun30 = cal.date(from: DateComponents(year: 2026, month: 6, day: 30))!
        let jul1 = cal.date(from: DateComponents(year: 2026, month: 7, day: 1))!

        try Assignments.upsert(in: ctx, date: jun30, dutyProfileId: UUID(), calendar: cal)
        try Assignments.upsert(in: ctx, date: jul1, dutyProfileId: UUID(), calendar: cal)
        try ctx.save()

        #expect(try ctx.fetch(FetchDescriptor<ShiftAssignment>()).count == 2)
    }

    @Test func clearRemovesTheDay() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let day = Date()
        try Assignments.upsert(in: ctx, date: day, dutyProfileId: UUID())
        try ctx.save()
        try Assignments.clear(in: ctx, date: day)
        try ctx.save()
        #expect(try ctx.fetch(FetchDescriptor<ShiftAssignment>()).isEmpty)
    }
}
