import Foundation
import SwiftData

/// Cross-platform backup — byte-compatible with the Android app's Backup.kt JSON (flat tables,
/// string ids, epoch-millis timestamps, `version` key). iOS now exports this shape and imports
/// either this or the legacy iOS v1 (`schemaVersion`) format, so a device move works both ways.
///
/// Android omits fields at their default values (kotlinx encodeDefaults=false), so everything
/// with an Android-side default is optional here and falls back on decode.
public struct BackupV2: Codable {
    public var version: Int
    public var profiles: [Profile]?
    public var alarms: [Alarm]?
    public var checklist: [Item]?
    public var checks: [Check]?
    public var assignments: [Assignment]?
    public var memos: [Memo]?

    public struct Profile: Codable {
        public var id: String
        public var name: String
        public var colorHex: String
        public var kind: String?
        public var timeText: String?
        public var isPreset: Bool?
        public var isArchived: Bool?
        public var sortOrder: Int?
        public var createdAt: Int64?
    }
    public struct Alarm: Codable {
        public var id: String
        public var dutyProfileId: String
        public var label: String
        public var hour: Int
        public var minute: Int
        public var dayOffset: Int?
        public var enabled: Bool?
        public var sortOrder: Int?
    }
    public struct Item: Codable {
        public var id: String
        public var dutyProfileId: String
        public var text: String
        public var isArchived: Bool?
        public var sortOrder: Int?
    }
    public struct Check: Codable {
        public var id: String
        public var checklistItemId: String
        public var dayKey: Int
        public var checkedAt: Int64?
    }
    public struct Assignment: Codable {
        public var dayKey: Int
        public var dutyProfileId: String
        public var charge: Bool?
        public var note: String?
    }
    public struct Memo: Codable {
        public var id: String
        public var bedTag: String
        public var text: String
        public var isDone: Bool?
        public var createdAt: Int64?
    }

    /// Android writes the charge virtual checklist id as a plain string; iOS uses a fixed UUID.
    static let androidChargeID = "charge:handover"

    private static func ms(_ d: Date) -> Int64 { Int64(d.timeIntervalSince1970 * 1000) }
    private static func date(_ ms: Int64?) -> Date { ms.map { Date(timeIntervalSince1970: Double($0) / 1000) } ?? Date() }

    /// Same guard as Android Backup.valid — a wipe-everything or planner-crashing file must not import.
    public static func valid(_ b: BackupV2) -> Bool {
        guard b.version == 1, let profiles = b.profiles, !profiles.isEmpty else { return false }
        for a in b.alarms ?? [] where !(0...23).contains(a.hour) || !(0...59).contains(a.minute)
            || !(-1...1).contains(a.dayOffset ?? 0) { return false }
        let keys = (b.assignments ?? []).map(\.dayKey) + (b.checks ?? []).map(\.dayKey)
        for k in keys {
            let m = (k / 100) % 100, d = k % 100
            if !(1...12).contains(m) || !(1...31).contains(d) { return false }
        }
        return true
    }

    public static func export(from ctx: ModelContext) -> BackupV2 {
        let profiles = (try? ctx.fetch(FetchDescriptor<DutyProfile>())) ?? []
        let assignments = (try? ctx.fetch(FetchDescriptor<ShiftAssignment>())) ?? []
        let checks = (try? ctx.fetch(FetchDescriptor<ChecklistCheck>())) ?? []
        let memos = (try? ctx.fetch(FetchDescriptor<QuickMemo>())) ?? []
        return BackupV2(
            version: 1,
            profiles: profiles.map {
                Profile(id: $0.id.uuidString.lowercased(), name: $0.name, colorHex: $0.colorHex,
                        kind: $0.kind, timeText: $0.timeText, isPreset: $0.isPreset,
                        isArchived: $0.isArchived, sortOrder: $0.sortOrder, createdAt: ms($0.createdAt))
            },
            alarms: profiles.flatMap { p in
                p.alarms.map {
                    Alarm(id: $0.id.uuidString.lowercased(), dutyProfileId: p.id.uuidString.lowercased(),
                          label: $0.label, hour: $0.hour, minute: $0.minute,
                          dayOffset: $0.dayOffset, enabled: $0.isEnabled, sortOrder: $0.sortOrder)
                }
            },
            checklist: profiles.flatMap { p in
                p.checklistItems.map {
                    Item(id: $0.id.uuidString.lowercased(), dutyProfileId: p.id.uuidString.lowercased(),
                         text: $0.text, isArchived: $0.isArchived, sortOrder: $0.sortOrder)
                }
            },
            checks: checks.map {
                Check(id: $0.id.uuidString.lowercased(),
                      checklistItemId: $0.checklistItemId == ChargeRules.itemID
                          ? androidChargeID : $0.checklistItemId.uuidString.lowercased(),
                      dayKey: $0.dayKey, checkedAt: ms($0.checkedAt))
            },
            assignments: assignments.map {
                Assignment(dayKey: $0.dayKey, dutyProfileId: $0.dutyProfileId.uuidString.lowercased(),
                           charge: $0.charge, note: $0.note)
            },
            memos: memos.map {
                Memo(id: $0.id.uuidString.lowercased(), bedTag: $0.bedTag, text: $0.text,
                     isDone: $0.isDone, createdAt: ms($0.createdAt))
            }
        )
    }

    /// Replace the entire store with this backup (wipe-then-insert). Call valid() first.
    public static func restore(_ b: BackupV2, into ctx: ModelContext) {
        for p in (try? ctx.fetch(FetchDescriptor<DutyProfile>())) ?? [] { ctx.delete(p) }
        for a in (try? ctx.fetch(FetchDescriptor<ShiftAssignment>())) ?? [] { ctx.delete(a) }
        for c in (try? ctx.fetch(FetchDescriptor<ChecklistCheck>())) ?? [] { ctx.delete(c) }
        for m in (try? ctx.fetch(FetchDescriptor<QuickMemo>())) ?? [] { ctx.delete(m) }

        var byID: [String: DutyProfile] = [:]
        for p in b.profiles ?? [] {
            let profile = DutyProfile(id: UUID(uuidString: p.id) ?? UUID(), name: p.name,
                                      colorHex: p.colorHex, kind: p.kind ?? "Custom",
                                      timeText: p.timeText ?? "", isPreset: p.isPreset ?? false,
                                      isArchived: p.isArchived ?? false, sortOrder: p.sortOrder ?? 0,
                                      createdAt: date(p.createdAt))
            ctx.insert(profile)
            byID[p.id] = profile
        }
        for a in b.alarms ?? [] {
            guard let profile = byID[a.dutyProfileId] else { continue }
            ctx.insert(AlarmItem(id: UUID(uuidString: a.id) ?? UUID(), label: a.label,
                                 hour: a.hour, minute: a.minute, dayOffset: a.dayOffset ?? 0,
                                 isEnabled: a.enabled ?? true, sortOrder: a.sortOrder ?? 0,
                                 profile: profile))
        }
        for i in b.checklist ?? [] {
            guard let profile = byID[i.dutyProfileId] else { continue }
            ctx.insert(ChecklistItem(id: UUID(uuidString: i.id) ?? UUID(), text: i.text,
                                     isArchived: i.isArchived ?? false, sortOrder: i.sortOrder ?? 0,
                                     profile: profile))
        }
        for c in b.checks ?? [] {
            let itemID = c.checklistItemId == androidChargeID
                ? ChargeRules.itemID : UUID(uuidString: c.checklistItemId)
            guard let itemID else { continue }
            ctx.insert(ChecklistCheck(id: UUID(uuidString: c.id) ?? UUID(), checklistItemId: itemID,
                                      date: DayKey.date(c.dayKey), checkedAt: date(c.checkedAt)))
        }
        for a in b.assignments ?? [] {
            guard let pid = UUID(uuidString: a.dutyProfileId) else { continue }
            ctx.insert(ShiftAssignment(date: DayKey.date(a.dayKey), dutyProfileId: pid,
                                       charge: a.charge ?? false, note: a.note))
        }
        for m in b.memos ?? [] {
            ctx.insert(QuickMemo(id: UUID(uuidString: m.id) ?? UUID(), bedTag: m.bedTag, text: m.text,
                                 isDone: m.isDone ?? false, createdAt: date(m.createdAt)))
        }
    }
}
