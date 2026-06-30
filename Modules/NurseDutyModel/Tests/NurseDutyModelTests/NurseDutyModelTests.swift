import Testing
import Foundation
import SwiftData
@testable import NurseDutyModel

@Suite struct NurseDutyModelTests {

    private var seoul: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }

    // The Phase-0 acceptance check: a night shift's 06:00 handover (dayOffset = 1)
    // written on Jun 30 must fire Jul 1 06:00.
    @Test func nightShiftAlarmFiresNextMorning() throws {
        let cal = seoul
        let jun30 = cal.date(from: DateComponents(year: 2026, month: 6, day: 30))!
        let fire = AlarmScheduling.fireDate(shiftDate: jun30, hour: 6, minute: 0, dayOffset: 1, calendar: cal)
        let expected = cal.date(from: DateComponents(year: 2026, month: 7, day: 1, hour: 6, minute: 0))!
        #expect(fire == expected)
    }

    @Test func sameDayAlarmFiresSameDay() throws {
        let cal = seoul
        let jun30 = cal.date(from: DateComponents(year: 2026, month: 6, day: 30))!
        let fire = AlarmScheduling.fireDate(shiftDate: jun30, hour: 7, minute: 0, dayOffset: 0, calendar: cal)
        let expected = cal.date(from: DateComponents(year: 2026, month: 6, day: 30, hour: 7, minute: 0))!
        #expect(fire == expected)
    }

    @Test func notificationIDIsDeterministic() throws {
        let cal = seoul
        let jun30noon = cal.date(from: DateComponents(year: 2026, month: 6, day: 30, hour: 12))!
        let jun30nine = cal.date(from: DateComponents(year: 2026, month: 6, day: 30, hour: 9))!
        let duty = UUID(), alarm = UUID()
        // Same day -> same id regardless of time-of-day in the source date.
        #expect(AlarmScheduling.notificationID(shiftDate: jun30noon, dutyProfileID: duty, alarmID: alarm, calendar: cal)
                == AlarmScheduling.notificationID(shiftDate: jun30nine, dutyProfileID: duty, alarmID: alarm, calendar: cal))
    }

    // ChecklistCheck must be unique per (item, day) so yesterday's checks don't bleed
    // and a double-tap can't create a duplicate row.
    @Test func checklistCheckIsUniquePerItemPerDay() throws {
        let container = try NurseDutyStore.makeContainer(inMemory: true)
        let ctx = ModelContext(container)
        let itemID = UUID()
        let day = Date()

        ctx.insert(ChecklistCheck(checklistItemId: itemID, date: day))
        try ctx.save()
        ctx.insert(ChecklistCheck(checklistItemId: itemID, date: day))   // same item + same day
        try ctx.save()

        let rows = try ctx.fetch(FetchDescriptor<ChecklistCheck>())
        #expect(rows.filter { $0.checklistItemId == itemID }.count == 1)
    }

    @Test func shiftAssignmentIsUniquePerDate() throws {
        let container = try NurseDutyStore.makeContainer(inMemory: true)
        let ctx = ModelContext(container)
        let day = Date()

        ctx.insert(ShiftAssignment(date: day, dutyProfileId: UUID()))
        try ctx.save()
        ctx.insert(ShiftAssignment(date: day, dutyProfileId: UUID()))     // same day
        try ctx.save()

        #expect(try ctx.fetch(FetchDescriptor<ShiftAssignment>()).count == 1)
    }
}
