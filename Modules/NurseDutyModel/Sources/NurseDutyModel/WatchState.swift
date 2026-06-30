import Foundation
import SwiftData

/// Snapshot the phone sends to the watch (latest-state-wins via WatchConnectivity applicationContext).
/// App Groups do NOT cross devices, so this Codable payload is the wire format.
public struct WatchState: Codable, Hashable, Sendable {
    public var dayKey: Int                 // today, so the watch can toggle the right day
    public var dutyName: String?
    public var dutyColorHex: String?
    public var nextAlarmLabel: String?
    public var nextAlarmDate: Date?
    public var pendingMemoCount: Int
    public var checklist: [Item]

    public struct Item: Codable, Hashable, Sendable, Identifiable {
        public var id: UUID
        public var text: String
        public var checked: Bool
        public init(id: UUID, text: String, checked: Bool) {
            self.id = id; self.text = text; self.checked = checked
        }
    }

    public init(dayKey: Int, dutyName: String?, dutyColorHex: String?,
                nextAlarmLabel: String?, nextAlarmDate: Date?, pendingMemoCount: Int, checklist: [Item]) {
        self.dayKey = dayKey; self.dutyName = dutyName; self.dutyColorHex = dutyColorHex
        self.nextAlarmLabel = nextAlarmLabel; self.nextAlarmDate = nextAlarmDate
        self.pendingMemoCount = pendingMemoCount; self.checklist = checklist
    }

    public static let placeholder = WatchState(
        dayKey: 20260630, dutyName: "Day", dutyColorHex: "#4F86C6",
        nextAlarmLabel: "인계", nextAlarmDate: Date(timeIntervalSinceReferenceDate: 800_010_000),
        pendingMemoCount: 2,
        checklist: [.init(id: UUID(), text: "활력징후 측정", checked: true),
                    .init(id: UUID(), text: "오전 투약 확인", checked: false)])
}

/// Commands the watch sends back to the phone (transferUserInfo — guaranteed background delivery).
public enum WatchCommand: Codable, Sendable {
    case toggleCheck(itemID: UUID, dayKey: Int)
    case addMemo(id: UUID, bedTag: String, text: String, createdAt: Date)
}

public enum WatchSync {
    public static func state(from ctx: ModelContext, now: Date = Date(),
                             calendar: Calendar = .current) -> WatchState {
        let todayKey = DayKey.from(now, calendar)
        let assignments = (try? ctx.fetch(FetchDescriptor<ShiftAssignment>())) ?? []
        let profiles = (try? ctx.fetch(FetchDescriptor<DutyProfile>())) ?? []
        let byID = Dictionary(profiles.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let profile = assignments.first { $0.dayKey == todayKey }.flatMap { byID[$0.dutyProfileId] }

        let items = (profile?.checklistItems.filter { !$0.isArchived }.sorted { $0.sortOrder < $1.sortOrder }) ?? []
        let checked = Checklist.checkedIDs(on: now, in: ctx, calendar: calendar)
        let next = AlarmPlanner.plan(assignments: assignments, profilesByID: byID,
                                     from: now, windowDays: 2, budget: 5, calendar: calendar).first
        let pending = (try? ctx.fetchCount(
            FetchDescriptor<QuickMemo>(predicate: #Predicate { $0.isDone == false }))) ?? 0

        return WatchState(
            dayKey: todayKey, dutyName: profile?.name, dutyColorHex: profile?.colorHex,
            nextAlarmLabel: next?.title, nextAlarmDate: next?.fireDate, pendingMemoCount: pending,
            checklist: items.map { .init(id: $0.id, text: $0.text, checked: checked.contains($0.id)) })
    }

    /// Apply a watch-originated command to the phone store. Idempotent for memos so a re-delivered
    /// transferUserInfo can't create a duplicate.
    public static func apply(_ command: WatchCommand, to ctx: ModelContext, calendar: Calendar = .current) {
        switch command {
        case let .toggleCheck(itemID, dayKey):
            Checklist.toggle(itemID, on: DayKey.date(dayKey, calendar), in: ctx, calendar: calendar)
        case let .addMemo(id, bedTag, text, createdAt):
            let exists = (try? ctx.fetchCount(
                FetchDescriptor<QuickMemo>(predicate: #Predicate { $0.id == id }))) ?? 0
            if exists == 0 {
                ctx.insert(QuickMemo(id: id, bedTag: bedTag, text: text, createdAt: createdAt))
            }
        }
        try? ctx.save()
    }
}
