import Testing
import Foundation
import SwiftData
@testable import NurseDutyModel

@Suite struct ChecklistTests {

    private var seoul: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }

    // Phase-3 acceptance check: today's checks don't bleed into tomorrow, and toggling works.
    @Test func checksArePerDateAndToggle() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let cal = seoul
        let item = UUID()
        let today = cal.date(from: DateComponents(year: 2026, month: 6, day: 30))!
        let tomorrow = cal.date(from: DateComponents(year: 2026, month: 7, day: 1))!

        #expect(Checklist.isChecked(item, on: today, in: ctx, calendar: cal) == false)

        Checklist.toggle(item, on: today, in: ctx, calendar: cal); try ctx.save()
        #expect(Checklist.isChecked(item, on: today, in: ctx, calendar: cal) == true)
        #expect(Checklist.isChecked(item, on: tomorrow, in: ctx, calendar: cal) == false)  // no bleed

        Checklist.toggle(item, on: today, in: ctx, calendar: cal); try ctx.save()           // uncheck
        #expect(Checklist.isChecked(item, on: today, in: ctx, calendar: cal) == false)
    }

    // Editing an item's text must not disturb past check history (check is keyed by id, not text).
    @Test func editingItemTextKeepsCheckHistory() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let item = ChecklistItem(text: "활력징후 측정")
        ctx.insert(item)
        let day = Date()
        Checklist.toggle(item.id, on: day, in: ctx); try ctx.save()

        item.text = "V/S 측정 + 통증 사정"; try ctx.save()    // rename the task

        #expect(Checklist.isChecked(item.id, on: day, in: ctx) == true)
    }

    @Test func checkedIDsReturnsOnlyThatDay() throws {
        let ctx = ModelContext(try NurseDutyStore.makeContainer(inMemory: true))
        let cal = seoul
        let a = UUID(), b = UUID(), c = UUID()
        let today = cal.date(from: DateComponents(year: 2026, month: 6, day: 30))!
        let other = cal.date(from: DateComponents(year: 2026, month: 7, day: 1))!
        Checklist.toggle(a, on: today, in: ctx, calendar: cal)
        Checklist.toggle(b, on: today, in: ctx, calendar: cal)
        Checklist.toggle(c, on: other, in: ctx, calendar: cal)
        try ctx.save()

        #expect(Checklist.checkedIDs(on: today, in: ctx, calendar: cal) == [a, b])
    }
}
