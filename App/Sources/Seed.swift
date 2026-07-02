import Foundation
import SwiftData
import NurseDutyModel

/// First-launch presets — 5 shifts mirroring the Android 1b model (no 기상 alarm, charge is a
/// modifier not a profile). Night carries a +1 dayOffset "아침 인계" so the crosses-midnight
/// path is exercised out of the box.
enum PresetSeeder {
    static func seedIfEmpty(_ ctx: ModelContext) {
        migrateLegacy(ctx)   // idempotent cleanup for pre-1b installs
        let count = (try? ctx.fetchCount(FetchDescriptor<DutyProfile>())) ?? 0
        guard count == 0 else { return }

        for (i, preset) in presets.enumerated() {
            let profile = DutyProfile(name: preset.name, colorHex: preset.color,
                                      kind: preset.kind, timeText: preset.time,
                                      isPreset: true, sortOrder: i)
            profile.alarms = preset.alarms.enumerated().map { idx, a in
                AlarmItem(label: a.label, hour: a.h, minute: a.m, dayOffset: a.off, sortOrder: idx)
            }
            profile.checklistItems = preset.checklist.enumerated().map { idx, t in
                ChecklistItem(text: t, sortOrder: idx)
            }
            ctx.insert(profile)
        }
        try? ctx.save()
    }

    /// Pre-1b installs: drop 기상 alarms, archive the legacy Charge profile (charge is a modifier
    /// now), and backfill kind/timeText on the old presets by name. Safe to run every launch.
    static func migrateLegacy(_ ctx: ModelContext) {
        guard let profiles = try? ctx.fetch(FetchDescriptor<DutyProfile>()), !profiles.isEmpty else { return }
        var dirty = false
        for p in profiles {
            if p.isPreset && p.kind == "Custom" {
                if let preset = presets.first(where: { $0.name == p.name }) {
                    p.kind = preset.kind
                    if p.timeText.isEmpty { p.timeText = preset.time }
                    dirty = true
                } else if p.name == "Charge" && !p.isArchived {
                    p.isArchived = true   // legacy Charge profile → modifier world
                    dirty = true
                }
            }
            for a in p.alarms where a.label == "기상" {
                ctx.delete(a)
                dirty = true
            }
        }
        if dirty { try? ctx.save() }
    }

    private struct Preset {
        let name: String
        let kind: String
        let color: String
        let time: String
        let alarms: [(label: String, h: Int, m: Int, off: Int)]
        let checklist: [String]
    }

    // keep in lockstep with Android Repository.seedPresetsIfEmpty
    private static let presets: [Preset] = [
        Preset(name: "Day", kind: "Day", color: "#3182F6", time: "06:00 – 14:00",
               alarms: [("인계 준비", 5, 30, 0), ("인계 · 라운드", 6, 0, 0)],
               checklist: ["활력징후 측정", "오전 투약 확인", "인계 준비"]),
        Preset(name: "Mid", kind: "Mid", color: "#14B8A6", time: "11:00 – 19:00",
               alarms: [("인계 준비", 10, 30, 0), ("인계 · 라운드", 11, 0, 0)],
               checklist: ["활력징후 측정", "점심 투약 확인", "처치·검사 라운드", "인계 준비"]),
        Preset(name: "Evening", kind: "Evening", color: "#F59E0B", time: "14:00 – 22:00",
               alarms: [("인계 준비", 13, 30, 0), ("Day 인계", 14, 0, 0)],
               checklist: ["활력징후 측정", "저녁 투약 확인", "인계 준비"]),
        Preset(name: "Night", kind: "Night", color: "#5B5BD6", time: "22:00 – 06:00 익일",
               alarms: [("인계 준비", 21, 30, 0), ("인계", 22, 0, 0), ("아침 인계", 6, 0, 1)],
               checklist: ["활력징후 측정", "야간 투약 확인", "낙상 위험 점검", "아침 인계 준비"]),
        Preset(name: "Off", kind: "Off", color: "#94A3B8", time: "휴무", alarms: [], checklist: []),
    ]
}

#if DEBUG
/// Verification seam: `--seed-demo` fills the next 35 days with Day shifts so the rolling
/// scheduler can be exercised against iOS's 64-pending cap on the real OS. Dev-only.
enum DemoSeed {
    static func fillNext35Days(_ ctx: ModelContext) {
        guard let day = (try? ctx.fetch(FetchDescriptor<DutyProfile>()))?.first(where: { $0.name == "Day" })
        else { return }
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())
        for d in 0..<35 {
            if let date = cal.date(byAdding: .day, value: d, to: today) {
                _ = try? Assignments.upsert(in: ctx, date: date, dutyProfileId: day.id)
            }
        }
        if (try? ctx.fetchCount(FetchDescriptor<QuickMemo>())) == 0 {
            ctx.insert(QuickMemo(bedTag: "1001:01", text: "진통제 호소 — 처방 확인 필요"))
            ctx.insert(QuickMemo(bedTag: "1003:02", text: "오심 호소, 저녁 식사 거부"))
            ctx.insert(QuickMemo(bedTag: "1005:01", text: "수면제 요청", isDone: true))
        }
        try? ctx.save()
    }

    /// "--demo-today Night+charge" — force today's assignment to the given preset kind.
    static func assignToday(_ spec: String, _ ctx: ModelContext) {
        let parts = spec.split(separator: "+")
        guard let kind = parts.first.map(String.init),
              let p = (try? ctx.fetch(FetchDescriptor<DutyProfile>()))?.first(where: { $0.kind == kind })
        else { return }
        _ = try? Assignments.upsert(in: ctx, date: Date(), dutyProfileId: p.id,
                                    charge: parts.contains("charge"))
        try? ctx.save()
    }
}
#endif
