import Foundation
import SwiftData
import NurseDutyModel

/// First-launch presets: D / E / N / Off / Charge. Night carries a +1 dayOffset "아침 인계"
/// alarm so the night-shift-crosses-midnight path is exercised out of the box.
enum PresetSeeder {
    static func seedIfEmpty(_ ctx: ModelContext) {
        let count = (try? ctx.fetchCount(FetchDescriptor<DutyProfile>())) ?? 0
        guard count == 0 else { return }

        for (i, preset) in presets.enumerated() {
            let profile = DutyProfile(name: preset.name, colorHex: preset.color, isPreset: true, sortOrder: i)
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

    private struct Preset {
        let name: String
        let color: String
        let alarms: [(label: String, h: Int, m: Int, off: Int)]
        let checklist: [String]
    }

    private static let presets: [Preset] = [
        Preset(name: "Day", color: "#4F86C6",
               alarms: [("기상", 6, 0, 0), ("인계", 7, 0, 0)],
               checklist: ["활력징후 측정", "오전 투약 확인", "인계 준비"]),
        Preset(name: "Evening", color: "#E8A33D",
               alarms: [("출근 준비", 14, 0, 0), ("인계", 15, 0, 0)],
               checklist: ["활력징후 측정", "저녁 투약 확인", "인계 준비"]),
        Preset(name: "Night", color: "#3B4A6B",
               alarms: [("출근 준비", 21, 0, 0), ("인계", 22, 0, 0), ("아침 인계", 6, 0, 1)],
               checklist: ["활력징후 측정", "야간 투약 확인", "낙상 위험 점검", "아침 인계 준비"]),
        Preset(name: "Off", color: "#9AA0A6", alarms: [], checklist: []),
        Preset(name: "Charge", color: "#C0504D",
               alarms: [("인계", 7, 0, 0), ("팀 브리핑", 8, 0, 0)],
               checklist: ["인력 배치 확인", "중환자 파악", "입퇴원 조율", "팀 인계"]),
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
        try? ctx.save()
    }
}
#endif
