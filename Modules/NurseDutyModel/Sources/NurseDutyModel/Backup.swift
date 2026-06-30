import Foundation
import SwiftData

/// Whole-store snapshot as plain Codable structs. JSON export/import (not raw SQLite file swapping)
/// — robust under SwiftData, human-readable, and round-trip testable. Standalone app has no account,
/// so this file export is the only path off-device / across a reinstall.
public struct Backup: Codable {
    public static let currentVersion = 1

    public var schemaVersion: Int
    public var profiles: [Profile]
    public var assignments: [Assignment]
    public var checks: [Check]
    public var memos: [Memo]

    public struct Profile: Codable {
        public var id: UUID; public var name: String; public var colorHex: String
        public var isPreset: Bool; public var isArchived: Bool; public var sortOrder: Int; public var createdAt: Date
        public var alarms: [Alarm]; public var checklist: [ChecklistItemDTO]
    }
    public struct Alarm: Codable {
        public var id: UUID; public var label: String; public var hour: Int; public var minute: Int
        public var dayOffset: Int; public var soundName: String?; public var isEnabled: Bool
        public var sortOrder: Int; public var createdAt: Date
    }
    public struct ChecklistItemDTO: Codable {
        public var id: UUID; public var text: String; public var isArchived: Bool
        public var sortOrder: Int; public var createdAt: Date
    }
    public struct Assignment: Codable {
        public var id: UUID; public var dayKey: Int; public var dutyProfileId: UUID
        public var note: String?; public var createdAt: Date
    }
    public struct Check: Codable {
        public var id: UUID; public var checklistItemId: UUID; public var dayKey: Int
        public var checkedAt: Date; public var createdAt: Date
    }
    public struct Memo: Codable {
        public var id: UUID; public var bedTag: String; public var text: String
        public var voicePath: String?; public var isDone: Bool; public var createdAt: Date
    }

    public static func export(from ctx: ModelContext) -> Backup {
        let profiles = (try? ctx.fetch(FetchDescriptor<DutyProfile>())) ?? []
        let assignments = (try? ctx.fetch(FetchDescriptor<ShiftAssignment>())) ?? []
        let checks = (try? ctx.fetch(FetchDescriptor<ChecklistCheck>())) ?? []
        let memos = (try? ctx.fetch(FetchDescriptor<QuickMemo>())) ?? []
        return Backup(
            schemaVersion: currentVersion,
            profiles: profiles.map { p in
                Profile(id: p.id, name: p.name, colorHex: p.colorHex, isPreset: p.isPreset,
                        isArchived: p.isArchived, sortOrder: p.sortOrder, createdAt: p.createdAt,
                        alarms: p.alarms.map {
                            Alarm(id: $0.id, label: $0.label, hour: $0.hour, minute: $0.minute,
                                  dayOffset: $0.dayOffset, soundName: $0.soundName, isEnabled: $0.isEnabled,
                                  sortOrder: $0.sortOrder, createdAt: $0.createdAt)
                        },
                        checklist: p.checklistItems.map {
                            ChecklistItemDTO(id: $0.id, text: $0.text, isArchived: $0.isArchived,
                                             sortOrder: $0.sortOrder, createdAt: $0.createdAt)
                        })
            },
            assignments: assignments.map {
                Assignment(id: $0.id, dayKey: $0.dayKey, dutyProfileId: $0.dutyProfileId,
                           note: $0.note, createdAt: $0.createdAt)
            },
            checks: checks.map {
                Check(id: $0.id, checklistItemId: $0.checklistItemId, dayKey: $0.dayKey,
                      checkedAt: $0.checkedAt, createdAt: $0.createdAt)
            },
            memos: memos.map {
                Memo(id: $0.id, bedTag: $0.bedTag, text: $0.text, voicePath: $0.voicePath,
                     isDone: $0.isDone, createdAt: $0.createdAt)
            }
        )
    }

    /// Replace the entire store with this backup (wipe-then-insert).
    public static func restore(_ backup: Backup, into ctx: ModelContext) {
        for p in (try? ctx.fetch(FetchDescriptor<DutyProfile>())) ?? [] { ctx.delete(p) }   // cascades alarms + checklist
        for a in (try? ctx.fetch(FetchDescriptor<ShiftAssignment>())) ?? [] { ctx.delete(a) }
        for c in (try? ctx.fetch(FetchDescriptor<ChecklistCheck>())) ?? [] { ctx.delete(c) }
        for m in (try? ctx.fetch(FetchDescriptor<QuickMemo>())) ?? [] { ctx.delete(m) }

        for p in backup.profiles {
            let profile = DutyProfile(id: p.id, name: p.name, colorHex: p.colorHex, isPreset: p.isPreset,
                                      isArchived: p.isArchived, sortOrder: p.sortOrder, createdAt: p.createdAt)
            ctx.insert(profile)
            for al in p.alarms {
                ctx.insert(AlarmItem(id: al.id, label: al.label, hour: al.hour, minute: al.minute,
                                     dayOffset: al.dayOffset, soundName: al.soundName, isEnabled: al.isEnabled,
                                     sortOrder: al.sortOrder, createdAt: al.createdAt, profile: profile))
            }
            for cl in p.checklist {
                ctx.insert(ChecklistItem(id: cl.id, text: cl.text, isArchived: cl.isArchived,
                                         sortOrder: cl.sortOrder, createdAt: cl.createdAt, profile: profile))
            }
        }
        for a in backup.assignments {
            ctx.insert(ShiftAssignment(id: a.id, date: DayKey.date(a.dayKey), dutyProfileId: a.dutyProfileId,
                                       note: a.note, createdAt: a.createdAt))
        }
        for c in backup.checks {
            ctx.insert(ChecklistCheck(id: c.id, checklistItemId: c.checklistItemId, date: DayKey.date(c.dayKey),
                                      checkedAt: c.checkedAt, createdAt: c.createdAt))
        }
        for m in backup.memos {
            ctx.insert(QuickMemo(id: m.id, bedTag: m.bedTag, text: m.text, voicePath: m.voicePath,
                                 isDone: m.isDone, createdAt: m.createdAt))
        }
    }
}
