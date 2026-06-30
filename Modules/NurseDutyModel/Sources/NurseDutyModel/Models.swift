import Foundation
import SwiftData

// MARK: - Duty (== Profile)
// A duty is a profile that OWNS an alarm set + a checklist template.
// D/E/N/Off + Charge are just preset profiles; "Charge Day" is its own profile.
@Model
public final class DutyProfile {
    public var id: UUID
    public var name: String
    public var colorHex: String
    public var isPreset: Bool
    public var isArchived: Bool          // soft-delete: hide from pickers, keep for history. Never cascade.
    public var sortOrder: Int
    public var createdAt: Date

    @Relationship(deleteRule: .cascade, inverse: \AlarmItem.profile)
    public var alarms: [AlarmItem]
    @Relationship(deleteRule: .cascade, inverse: \ChecklistItem.profile)
    public var checklistItems: [ChecklistItem]

    public init(id: UUID = UUID(), name: String, colorHex: String,
                isPreset: Bool = false, isArchived: Bool = false,
                sortOrder: Int = 0, createdAt: Date = Date(),
                alarms: [AlarmItem] = [], checklistItems: [ChecklistItem] = []) {
        self.id = id
        self.name = name
        self.colorHex = colorHex
        self.isPreset = isPreset
        self.isArchived = isArchived
        self.sortOrder = sortOrder
        self.createdAt = createdAt
        self.alarms = alarms
        self.checklistItems = checklistItems
    }
}

// MARK: - Alarm (template on the profile; never stored per-date)
// Fire instant is derived: (shift date + dayOffset) at hour:minute. See AlarmScheduling.
@Model
public final class AlarmItem {
    public var id: UUID
    public var label: String
    public var hour: Int
    public var minute: Int
    public var dayOffset: Int             // 0 = same day as shift date, +1 = next morning (night shift handover)
    public var soundName: String?
    public var isEnabled: Bool
    public var sortOrder: Int
    public var createdAt: Date
    public var profile: DutyProfile?

    public init(id: UUID = UUID(), label: String, hour: Int, minute: Int,
                dayOffset: Int = 0, soundName: String? = nil, isEnabled: Bool = true,
                sortOrder: Int = 0, createdAt: Date = Date(), profile: DutyProfile? = nil) {
        self.id = id
        self.label = label
        self.hour = hour
        self.minute = minute
        self.dayOffset = dayOffset
        self.soundName = soundName
        self.isEnabled = isEnabled
        self.sortOrder = sortOrder
        self.createdAt = createdAt
        self.profile = profile
    }
}

// MARK: - Checklist template (definition only; daily state lives in ChecklistCheck)
@Model
public final class ChecklistItem {
    public var id: UUID
    public var text: String
    public var isArchived: Bool          // soft-delete: edit/remove without breaking past check history
    public var sortOrder: Int
    public var createdAt: Date
    public var profile: DutyProfile?

    public init(id: UUID = UUID(), text: String, isArchived: Bool = false,
                sortOrder: Int = 0, createdAt: Date = Date(), profile: DutyProfile? = nil) {
        self.id = id
        self.text = text
        self.isArchived = isArchived
        self.sortOrder = sortOrder
        self.createdAt = createdAt
        self.profile = profile
    }
}

// MARK: - Checklist daily state (presence == checked)
// Plain UUID ref to the item (NOT a relationship): deleting/archiving an item must never wipe history.
@Model
public final class ChecklistCheck {
    #Unique<ChecklistCheck>([\.checklistItemId, \.date])   // one check per item per day

    public var id: UUID
    public var checklistItemId: UUID
    public var date: Date                 // normalized to startOfDay in init
    public var checkedAt: Date
    public var createdAt: Date

    public init(id: UUID = UUID(), checklistItemId: UUID, date: Date,
                checkedAt: Date = Date(), createdAt: Date = Date(),
                calendar: Calendar = .current) {
        self.id = id
        self.checklistItemId = checklistItemId
        self.date = calendar.startOfDay(for: date)   // guarantees the unique key is day-granular
        self.checkedAt = checkedAt
        self.createdAt = createdAt
    }
}

// MARK: - Shift assignment (one duty per calendar day)
// dutyProfileId is a plain UUID ref (no cascade): archiving a profile must not delete past assignments.
@Model
public final class ShiftAssignment {
    #Unique<ShiftAssignment>([\.date])     // one duty per day

    public var id: UUID
    public var date: Date                  // the shift START date, normalized to startOfDay
    public var dutyProfileId: UUID
    public var note: String?               // handover / shift note (shift-scoped, not bed-scoped)
    public var createdAt: Date

    public init(id: UUID = UUID(), date: Date, dutyProfileId: UUID,
                note: String? = nil, createdAt: Date = Date(),
                calendar: Calendar = .current) {
        self.id = id
        self.date = calendar.startOfDay(for: date)
        self.dutyProfileId = dutyProfileId
        self.note = note
        self.createdAt = createdAt
    }
}

// MARK: - Quick Memo (ad-hoc, bed-scoped, inbox → swipe-done)
@Model
public final class QuickMemo {
    public var id: UUID
    public var bedTag: String              // "room:bed", e.g. "1001:01"
    public var text: String
    public var voicePath: String?          // nullable; populated only once optional voice phase lands
    public var isDone: Bool
    public var createdAt: Date

    public init(id: UUID = UUID(), bedTag: String, text: String,
                voicePath: String? = nil, isDone: Bool = false, createdAt: Date = Date()) {
        self.id = id
        self.bedTag = bedTag
        self.text = text
        self.voicePath = voicePath
        self.isDone = isDone
        self.createdAt = createdAt
    }
}
