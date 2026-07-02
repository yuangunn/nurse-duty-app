import SwiftUI
import SwiftData
import UIKit
import NurseDutyModel

struct TodayView: View {
    @Environment(\.modelContext) private var ctx
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.colorScheme) private var scheme
    @AppStorage("userName") private var userName = ""
    @Query private var assignments: [ShiftAssignment]
    @Query(sort: \DutyProfile.sortOrder) private var profiles: [DutyProfile]
    @Query private var checks: [ChecklistCheck]
    @Query(sort: \QuickMemo.createdAt, order: .reverse) private var memos: [QuickMemo]
    @StateObject private var weather = WeatherStore.shared
    @State private var dayToken = 0   // bumped at the date boundary so "today" rolls over while foregrounded
    @State private var showSettings = false
    var onMemoTab: () -> Void = {}

    private let cal = Calendar.current
    private var today: Date { cal.startOfDay(for: Date()) }
    private var todayKey: Int { DayKey.from(Date(), cal) }

    private var assignment: ShiftAssignment? { assignments.first { $0.dayKey == todayKey } }
    private var profile: DutyProfile? {
        assignment.flatMap { a in profiles.first { $0.id == a.dutyProfileId } }
    }
    private var kind: String { profile?.kind ?? "None" }
    private var isCharge: Bool { assignment?.charge == true && ChargeRules.chargeable(kind) }
    private var hasWork: Bool { profile != nil && kind != "Off" }
    private var checkedToday: Set<UUID> {
        Set(checks.filter { $0.dayKey == todayKey }.map(\.checklistItemId))
    }
    /// (id, text) with the charge handover item prepended on charge days.
    private var todayItems: [(id: UUID, text: String)] {
        let base = (profile?.checklistItems ?? []).filter { !$0.isArchived }
            .sorted { $0.sortOrder < $1.sortOrder }.map { (id: $0.id, text: $0.text) }
        return (isCharge ? [(id: ChargeRules.itemID, text: ChargeRules.itemText)] : []) + base
    }
    private var doneCount: Int { todayItems.filter { checkedToday.contains($0.id) }.count }
    private var nextAlarm: AlarmItem? {
        guard let p = profile else { return nil }
        let now = cal.component(.hour, from: Date()) * 60 + cal.component(.minute, from: Date())
        return p.alarms.filter(\.isEnabled)
            .sorted { ($0.dayOffset * 1440 + $0.hour * 60 + $0.minute) < ($1.dayOffset * 1440 + $1.hour * 60 + $1.minute) }
            .first { $0.dayOffset * 1440 + $0.hour * 60 + $0.minute >= now }
    }
    private var pendingMemos: [QuickMemo] { memos.filter { !$0.isDone } }

    var body: some View {
        let c = NurseColors.of(scheme)
        let sky = Duty.sky(kind)
        ScrollView {
            VStack(spacing: 0) {
                hero(sky: sky, c: c)
                checklistCard(c: c)
                    .padding(.init(top: 16, leading: 16, bottom: 0, trailing: 16))
                memoCard(c: c)
                    .padding(.init(top: 14, leading: 16, bottom: 24, trailing: 16))
            }
        }
        .background(c.bg)
        .ignoresSafeArea(edges: .top)
        .sheet(isPresented: $showSettings) { SettingsView() }
        .onAppear {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--open-settings") { showSettings = true }
            #endif
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
            dayToken &+= 1
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { dayToken &+= 1; weather.refresh() }
        }
        .task { weather.refresh() }
        .id(dayToken)
    }

    // MARK: hero
    private func hero(sky: Duty.Sky, c: NurseColors) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(todayTitle).font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.9))
                Spacer()
                HStack(spacing: 4) {
                    if weather.live { Circle().fill(Color(hex: "#7CFFB2")).frame(width: 6, height: 6) }
                    Image(systemName: "location.fill").font(.system(size: 10)).foregroundStyle(.white.opacity(0.9))
                    Text("서울").font(.system(size: 12, weight: .semibold)).foregroundStyle(.white)
                    Image(systemName: weather.symbol).font(.system(size: 12)).foregroundStyle(.white)
                    Text(weather.temp).font(.system(size: 13, weight: .heavy)).foregroundStyle(.white)
                }
                .padding(.horizontal, 10).padding(.vertical, 5)
                .background(.white.opacity(0.2), in: Capsule())
                .padding(.trailing, 7)
                Button { showSettings = true } label: {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 15)).foregroundStyle(.white)
                        .frame(width: 34, height: 34)
                        .background(.white.opacity(0.18), in: Circle())
                }
            }
            .padding(.top, 58)

            Text(userName.isEmpty ? sky.greeting : "\(userName)님, \(sky.greeting)")
                .font(.system(size: 19, weight: .heavy))
                .foregroundStyle(.white).padding(.top, 8)

            if isCharge {
                HStack(spacing: 5) {
                    Image(systemName: "crown.fill").font(.system(size: 11)).foregroundStyle(Color(hex: "#FFE49B"))
                    Text("차지 간호사 · 팀 리더").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                }
                .padding(.horizontal, 11).padding(.vertical, 4)
                .background(.white.opacity(0.2), in: Capsule())
                .overlay(Capsule().stroke(.white.opacity(0.36), lineWidth: 1))
                .padding(.top, 9)
            }

            HStack(spacing: 18) {
                if hasWork {
                    ProgressRing(done: doneCount, total: todayItems.count)
                } else {
                    Text(sky.restIcon).font(.system(size: 34))
                        .frame(width: 108, height: 108)
                        .background(.white.opacity(0.18), in: Circle())
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("오늘의 근무").font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.82))
                    Text(profile.map { dutyDisplay($0, charge: isCharge) } ?? "근무 미배정")
                        .font(.system(size: 23, weight: .heavy)).foregroundStyle(.white)
                    Text(profile.map { $0.kind == "Off" ? "휴무" : $0.timeText } ?? "미배정")
                        .font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(.white.opacity(0.2), in: Capsule())
                }
            }
            .padding(.top, 16)

            if hasWork, let a = nextAlarm {
                HStack(spacing: 11) {
                    Image(systemName: "bell.badge.fill").font(.system(size: 15)).foregroundStyle(.white)
                    Text("다음 알람 · \(a.label)").font(.system(size: 15, weight: .semibold)).foregroundStyle(.white)
                    Spacer()
                    Text((a.dayOffset > 0 ? "익일 " : "") + String(format: "%02d:%02d", a.hour, a.minute))
                        .font(.system(size: 15, weight: .heavy)).foregroundStyle(.white).monospacedDigit()
                }
                .padding(.horizontal, 15).padding(.vertical, 12)
                .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 15))
                .padding(.top, 16)
            }
        }
        .padding(.horizontal, 22).padding(.bottom, 24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(sky.grad)
        .clipShape(.rect(bottomLeadingRadius: 30, bottomTrailingRadius: 30))
    }

    // MARK: checklist
    @ViewBuilder private func checklistCard(c: NurseColors) -> some View {
        if hasWork {
            SoftCard {
                HStack(alignment: .bottom) {
                    Text("오늘 체크리스트").font(.system(size: 14.5, weight: .heavy)).foregroundStyle(c.text)
                    Spacer()
                    Text(progressNote).font(.system(size: 12, weight: .bold)).foregroundStyle(c.faint)
                }
                .padding(.bottom, 13)
                ForEach(todayItems, id: \.id) { item in
                    let on = checkedToday.contains(item.id)
                    Button { toggle(item.id) } label: {
                        HStack(spacing: 11) {
                            Image(systemName: on ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 21))
                                .foregroundStyle(on ? Duty.success : c.cardBorder)
                            Text(item.text)
                                .font(.system(size: 15, weight: .medium))
                                .foregroundStyle(on ? c.sub : c.text)
                                .strikethrough(on, color: c.sub)
                            Spacer()
                            if item.id == ChargeRules.itemID {
                                Text("차지").font(.system(size: 11, weight: .heavy))
                                    .foregroundStyle(Duty.goldInk)
                                    .padding(.horizontal, 7).padding(.vertical, 2)
                                    .background(Duty.gold.opacity(0.16), in: RoundedRectangle(cornerRadius: 6))
                            }
                        }
                        .padding(.vertical, 7)
                    }
                    .buttonStyle(.plain)
                }
            }
        } else {
            let sky = Duty.sky(kind)
            SoftCard(pad: 22) {
                VStack(spacing: 4) {
                    Text(sky.restTitle).font(.system(size: 14.5, weight: .heavy)).foregroundStyle(c.text)
                    Text(sky.restSub).font(.system(size: 13, weight: .semibold)).foregroundStyle(c.sub)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    // MARK: memo peek
    private func memoCard(c: NurseColors) -> some View {
        Button(action: onMemoTab) {
            SoftCard {
                HStack {
                    Text("대기 메모 ").font(.system(size: 13, weight: .bold)).foregroundStyle(c.text)
                    Text("\(pendingMemos.count)").font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color(hex: "#3182F6"))
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(c.faint)
                }
                ForEach(pendingMemos.prefix(2)) { m in
                    HStack(spacing: 10) {
                        Text(m.bedTag.isEmpty ? "병상" : m.bedTag)
                            .font(.system(size: 12, weight: .heavy)).foregroundStyle(.white)
                            .padding(.horizontal, 7).padding(.vertical, 3)
                            .background(Color(hex: "#3182F6"), in: RoundedRectangle(cornerRadius: 7))
                        Text(m.text).font(.system(size: 13, weight: .semibold)).foregroundStyle(c.sub).lineLimit(1)
                        Spacer()
                    }
                    .padding(.top, 10)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private func toggle(_ id: UUID) {
        Checklist.toggle(id, on: today, in: ctx)
        try? ctx.save()
        refreshWidgets()
    }
    private var progressNote: String {
        let rem = todayItems.count - doneCount
        return rem == 0 ? "모두 완료! 고생하셨어요 🎉" : (rem == 1 ? "하나만 더 하면 끝나요!" : "\(doneCount)/\(todayItems.count) 완료")
    }
    private var todayTitle: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일 (EEE)"
        return f.string(from: Date())
    }
}

struct ProgressRing: View {
    let done: Int
    let total: Int

    var body: some View {
        let pct = total > 0 ? Double(done) / Double(total) : 0
        ZStack {
            Circle().stroke(.white.opacity(0.3), lineWidth: 12)
            Circle().trim(from: 0, to: pct)
                .stroke(.white, style: StrokeStyle(lineWidth: 12, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                HStack(alignment: .bottom, spacing: 0) {
                    Text("\(done)").font(.system(size: 25, weight: .heavy)).foregroundStyle(.white)
                    Text("/\(total)").font(.system(size: 16, weight: .bold)).foregroundStyle(.white.opacity(0.6))
                }
                Text("완료").font(.system(size: 11, weight: .semibold)).foregroundStyle(.white.opacity(0.85))
            }
        }
        .frame(width: 108, height: 108)
    }
}

func dutyDisplay(_ p: DutyProfile, charge: Bool = false) -> String {
    p.kind == "Custom" ? p.name
        : "\(p.name) · \(Duty.ko(p.kind))\(charge && ChargeRules.chargeable(p.kind) ? "차지" : "")"
}
