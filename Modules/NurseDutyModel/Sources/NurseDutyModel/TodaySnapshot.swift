import Foundation

// Serialization boundary, locked now to avoid rework. SwiftData @Model objects can't cross
// the WatchConnectivity hop, so widgets (App Group) and watch (WCSession) both consume these
// Codable DTOs instead. Shape only in Phase 0; population logic lands with widgets/watch.

public struct AlarmDTO: Codable, Hashable, Sendable {
    public let id: UUID
    public let label: String
    public let fireDate: Date
    public init(id: UUID, label: String, fireDate: Date) {
        self.id = id; self.label = label; self.fireDate = fireDate
    }
}

public struct ChecklistEntryDTO: Codable, Hashable, Sendable {
    public let id: UUID
    public let text: String
    public let isChecked: Bool
    public init(id: UUID, text: String, isChecked: Bool) {
        self.id = id; self.text = text; self.isChecked = isChecked
    }
}

public struct TodaySnapshot: Codable, Hashable, Sendable {
    public let date: Date
    public let dutyName: String?
    public let dutyColorHex: String?
    public let alarms: [AlarmDTO]
    public let checklist: [ChecklistEntryDTO]
    public let pendingMemoCount: Int
    public init(date: Date, dutyName: String?, dutyColorHex: String?,
                alarms: [AlarmDTO], checklist: [ChecklistEntryDTO], pendingMemoCount: Int) {
        self.date = date
        self.dutyName = dutyName
        self.dutyColorHex = dutyColorHex
        self.alarms = alarms
        self.checklist = checklist
        self.pendingMemoCount = pendingMemoCount
    }
}
