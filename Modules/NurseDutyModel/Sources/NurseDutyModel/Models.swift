import Foundation
import SwiftData

// MARK: - Duty (== Profile)
// A duty is a profile that OWNS an alarm set + a checklist template.
// 5 preset kinds (Day/Mid/Evening/Night/Off); charge is a per-assignment modifier, not a profile.
@Model
public final class DutyProfile {
    public var id: UUID
    public var name: String
    public var colorHex: String
    /// Day / Mid / Evening / Night / Off / Custom — drives hero gradients + charge rules (1b model).
    public var kind: String = "Custom"
    /// Display-only shift hours, e.g. "06:00 – 14:00".
    public var timeText: String = ""
    public var isPreset: Bool
    public var isArchived: Bool          // soft-delete: hide from pickers, keep for history. Never cascade.
    public var sortOrder: Int
    public var createdAt: Date

    @Relationship(deleteRule: .cascade, inverse: \AlarmItem.profile)
    public var alarms: [AlarmItem]
    @Relationship(deleteRule: .cascade, inverse: \ChecklistItem.profile)
    public var checklistItems: [ChecklistItem]

    public init(id: UUID = UUID(), name: String, colorHex: String,
                kind: String = "Custom", timeText: String = "",
                isPreset: Bool = false, isArchived: Bool = false,
                sortOrder: Int = 0, createdAt: Date = Date(),
                alarms: [AlarmItem] = [], checklistItems: [ChecklistItem] = []) {
        self.id = id
        self.name = name
        self.colorHex = colorHex
        self.kind = kind
        self.timeText = timeText
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
    #Unique<ChecklistCheck>([\.checklistItemId, \.dayKey])   // one check per item per day

    public var id: UUID
    public var checklistItemId: UUID
    public var dayKey: Int                // yyyymmdd, timezone-independent
    public var checkedAt: Date
    public var createdAt: Date

    public init(id: UUID = UUID(), checklistItemId: UUID, date: Date,
                checkedAt: Date = Date(), createdAt: Date = Date(),
                calendar: Calendar = .current) {
        self.id = id
        self.checklistItemId = checklistItemId
        self.dayKey = DayKey.from(date, calendar)
        self.checkedAt = checkedAt
        self.createdAt = createdAt
    }
}

// MARK: - Shift assignment (one duty per calendar day)
// dutyProfileId is a plain UUID ref (no cascade): archiving a profile must not delete past assignments.
@Model
public final class ShiftAssignment {
    #Unique<ShiftAssignment>([\.dayKey])   // one duty per day

    public var id: UUID
    public var dayKey: Int                  // yyyymmdd of the shift START day, timezone-independent
    public var dutyProfileId: UUID
    /// Charge nurse (team lead) modifier — not a separate duty (1b model).
    public var charge: Bool = false
    public var note: String?               // handover / shift note (shift-scoped, not bed-scoped)
    public var createdAt: Date

    public init(id: UUID = UUID(), date: Date, dutyProfileId: UUID,
                charge: Bool = false, note: String? = nil, createdAt: Date = Date(),
                calendar: Calendar = .current) {
        self.id = id
        self.dayKey = DayKey.from(date, calendar)
        self.dutyProfileId = dutyProfileId
        self.charge = charge
        self.note = note
        self.createdAt = createdAt
    }

    /// The shift's start day, reconstructed for display / fire-time math.
    public var day: Date { DayKey.date(dayKey) }
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
