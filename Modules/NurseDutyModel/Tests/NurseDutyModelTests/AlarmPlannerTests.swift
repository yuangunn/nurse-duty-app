import Testing
import Foundation
@testable import NurseDutyModel

@Suite struct AlarmPlannerTests {

    private var seoul: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }

    // The Phase-2 acceptance check: a full pre-filled month must NOT exceed the budget
    // (which keeps us under iOS's 64-pending cap), and the kept ones are the soonest.
    @Test func planCapsAtBudgetAndSortsSoonestFirst() throws {
        let cal = seoul
        let profile = DutyProfile(name: "Day", colorHex: "#4F86C6", alarms: [
            AlarmItem(label: "기상", hour: 6, minute: 0),
            AlarmItem(label: "인계", hour: 7, minute: 0),
            AlarmItem(label: "점심 라운딩", hour: 12, minute: 0),
        ])
        let byID = [profile.id: profile]
        let start = cal.date(from: DateComponents(year: 2026, month: 6, day: 1))!
        let assignments = (0..<30).map {
            ShiftAssignment(date: cal.date(byAdding: .day, value: $0, to: start)!,
                            dutyProfileId: profile.id, calendar: cal)
        }

        let plan = AlarmPlanner.plan(assignments: assignments, profilesByID: byID,
                                     from: start, windowDays: 28, budget: 50, calendar: cal)

        #expect(plan.count == 50)                                  // 90 possible -> capped to 50
        #expect(plan == plan.sorted { $0.fireDate < $1.fireDate })  // soonest-first
        #expect(Set(plan.map(\.id)).count == 50)                   // ids are unique
    }

    @Test func planPlacesNightHandoverNextMorning() throws {
        let cal = seoul
        let night = DutyProfile(name: "Night", colorHex: "#3B4A6B",
                                alarms: [AlarmItem(label: "아침 인계", hour: 6, minute: 0, dayOffset: 1)])
        let byID = [night.id: night]
        let jun30 = cal.date(from: DateComponents(year: 2026, month: 6, day: 30))!

        let plan = AlarmPlanner.plan(
            assignments: [ShiftAssignment(date: jun30, dutyProfileId: night.id, calendar: cal)],
            profilesByID: byID, from: jun30, windowDays: 7, budget: 50, calendar: cal)

        #expect(plan.count == 1)
        #expect(plan.first?.components.month == 7)
        #expect(plan.first?.components.day == 1)
        #expect(plan.first?.components.hour == 6)
        #expect(plan.first?.components.timeZone == nil)            // wall-clock, not pinned
    }

    @Test func planSkipsDisabledAndPastAlarms() throws {
        let cal = seoul
        let profile = DutyProfile(name: "Day", colorHex: "#4F86C6", alarms: [
            AlarmItem(label: "켜짐", hour: 23, minute: 0, isEnabled: true),
            AlarmItem(label: "꺼짐", hour: 23, minute: 30, isEnabled: false),
        ])
        let byID = [profile.id: profile]
        let day = cal.date(from: DateComponents(year: 2026, month: 6, day: 30, hour: 22))!  // "now" = 22:00
        let plan = AlarmPlanner.plan(
            assignments: [ShiftAssignment(date: day, dutyProfileId: profile.id, calendar: cal)],
            profilesByID: byID, from: day, windowDays: 7, budget: 50, calendar: cal)

        #expect(plan.map(\.title) == ["켜짐"])   // disabled dropped; 23:00 > 22:00 kept
    }

    @Test func reconcileReemitsDesiredAndRemovesStale() throws {
        func pa(_ id: String) -> PlannedAlarm {
            PlannedAlarm(id: id, fireDate: Date(timeIntervalSinceReferenceDate: 0),
                         components: DateComponents(), title: "", body: "")
        }
        let (add, remove) = AlarmPlanner.reconcile(pending: ["A", "B", "C"],
                                                   desired: [pa("B"), pa("C"), pa("D")])
        // re-emit ALL desired so edited alarms (same id, new time/label) get replaced;
        // add(_:) replaces same-id requests, so this is idempotent.
        #expect(Set(add.map(\.id)) == ["B", "C", "D"])
        #expect(remove == ["A"])             // A no longer desired
    }

    @Test func planSkipsArchivedProfiles() throws {
        let cal = seoul
        let archived = DutyProfile(name: "Day", colorHex: "#4F86C6", isArchived: true,
                                   alarms: [AlarmItem(label: "인계", hour: 23, minute: 0)])
        let byID = [archived.id: archived]
        let day = cal.date(from: DateComponents(year: 2026, month: 6, day: 30, hour: 22))!
        let plan = AlarmPlanner.plan(
            assignments: [ShiftAssignment(date: day, dutyProfileId: archived.id, calendar: cal)],
            profilesByID: byID, from: day, windowDays: 7, budget: 50, calendar: cal)
        #expect(plan.isEmpty)   // archived duty must not schedule alarms
    }
}
