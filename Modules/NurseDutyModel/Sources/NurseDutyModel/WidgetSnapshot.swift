import Foundation
import SwiftData

/// Compact, Codable view of "right now" for widgets (and later watch complications): today's duty,
/// checklist progress, the next upcoming alarm, and the pending-memo count. Built by reading the
/// App Group store from the widget process.
public struct WidgetSnapshot: Codable, Hashable, Sendable {
    public var date: Date
    public var dutyName: String?
    public var dutyColorHex: String?
    public var checklistDone: Int
    public var checklistTotal: Int
    public var nextAlarmLabel: String?
    public var nextAlarmDate: Date?
    public var pendingMemoCount: Int

    public init(date: Date, dutyName: String?, dutyColorHex: String?,
                checklistDone: Int, checklistTotal: Int,
                nextAlarmLabel: String?, nextAlarmDate: Date?, pendingMemoCount: Int) {
        self.date = date
        self.dutyName = dutyName
        self.dutyColorHex = dutyColorHex
        self.checklistDone = checklistDone
        self.checklistTotal = checklistTotal
        self.nextAlarmLabel = nextAlarmLabel
        self.nextAlarmDate = nextAlarmDate
        self.pendingMemoCount = pendingMemoCount
    }

    /// Sample for widget galleries / previews.
    public static let placeholder = WidgetSnapshot(
        date: Date(timeIntervalSinceReferenceDate: 800_000_000),
        dutyName: "Day", dutyColorHex: "#4F86C6",
        checklistDone: 1, checklistTotal: 3,
        nextAlarmLabel: "인계",
        nextAlarmDate: Date(timeIntervalSinceReferenceDate: 800_010_000),
        pendingMemoCount: 2)
}

public enum WidgetSnapshotBuilder {
    public static func build(from ctx: ModelContext, now: Date = Date(),
                             calendar: Calendar = .current) -> WidgetSnapshot {
        let todayKey = DayKey.from(now, calendar)
        let assignments = (try? ctx.fetch(FetchDescriptor<ShiftAssignment>())) ?? []
        let profiles = (try? ctx.fetch(FetchDescriptor<DutyProfile>())) ?? []
        let byID = Dictionary(profiles.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let profile = assignments.first { $0.dayKey == todayKey }.flatMap { byID[$0.dutyProfileId] }

        let items = profile?.checklistItems.filter { !$0.isArchived } ?? []
        let checkedToday = Checklist.checkedIDs(on: now, in: ctx, calendar: calendar)
        let done = items.filter { checkedToday.contains($0.id) }.count

        // soonest upcoming alarm across the next couple of days (handles night next-morning offsets)
        let next = AlarmPlanner.plan(assignments: assignments, profilesByID: byID,
                                     from: now, windowDays: 2, budget: 5, calendar: calendar).first

        let pendingMemos = (try? ctx.fetchCount(
            FetchDescriptor<QuickMemo>(predicate: #Predicate { $0.isDone == false })
        )) ?? 0

        return WidgetSnapshot(
            date: now,
            dutyName: profile?.name, dutyColorHex: profile?.colorHex,
            checklistDone: done, checklistTotal: items.count,
            nextAlarmLabel: next?.title, nextAlarmDate: next?.fireDate,
            pendingMemoCount: pendingMemos)
    }
}
