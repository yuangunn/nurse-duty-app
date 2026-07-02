import SwiftUI
import SwiftData
import UIKit
import NurseDutyModel

private struct DayPick: Identifiable {
    let date: Date
    var id: Double { date.timeIntervalSinceReferenceDate }
}

struct CalendarView: View {
    @Environment(\.colorScheme) private var scheme
    @Query private var assignments: [ShiftAssignment]
    @Query(sort: \DutyProfile.sortOrder) private var profiles: [DutyProfile]

    @State private var month: Date = Calendar.current.startOfDay(for: Date())
    @State private var pick: DayPick?
    @State private var dayToken = 0   // bumped at the date boundary to refresh the "today" highlight

    private let cal = Calendar.current

    var body: some View {
        let c = NurseColors.of(scheme)
        ScrollView {
            VStack(spacing: 0) {
                header(c: c).padding(.init(top: 58, leading: 20, bottom: 0, trailing: 20))
                SoftCard(pad: 12) {
                    weekdayRow(c: c).padding(.bottom, 8)
                    grid(c: c)
                }
                .padding(.init(top: 16, leading: 16, bottom: 0, trailing: 16))
                statsCard(c: c).padding(.init(top: 14, leading: 16, bottom: 24, trailing: 16))
            }
        }
        .background(c.bg)
        .ignoresSafeArea(edges: .top)
        .sheet(item: $pick) { p in
            AssignSheet(date: p.date, onAdvance: { next in pick = DayPick(date: next) })
                .presentationDetents([.medium, .large])
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
            dayToken &+= 1
        }
        .id(dayToken)
    }

    // MARK: lookups
    private var profileByID: [UUID: DutyProfile] {
        Dictionary(profiles.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
    }
    private var assignmentByDay: [Int: ShiftAssignment] {
        Dictionary(assignments.map { ($0.dayKey, $0) }, uniquingKeysWith: { a, _ in a })
    }

    // MARK: header
    private func header(c: NurseColors) -> some View {
        HStack(spacing: 12) {
            Text("근무표").font(.system(size: 24, weight: .heavy)).foregroundStyle(c.text)
            Spacer()
            Button { shiftMonth(-1) } label: {
                Image(systemName: "chevron.left").font(.system(size: 16, weight: .semibold)).foregroundStyle(c.sub)
            }
            Text(monthTitle).font(.system(size: 16, weight: .bold)).foregroundStyle(c.text)
            Button { shiftMonth(1) } label: {
                Image(systemName: "chevron.right").font(.system(size: 16, weight: .semibold)).foregroundStyle(c.sub)
            }
        }
    }
    private func shiftMonth(_ n: Int) {
        if let m = cal.date(byAdding: .month, value: n, to: month) { month = m }
    }
    private var monthTitle: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "yyyy. M"
        return f.string(from: month)
    }

    // MARK: weekday row
    private func weekdayRow(c: NurseColors) -> some View {
        HStack {
            ForEach(["일", "월", "화", "수", "목", "금", "토"], id: \.self) { sym in
                Text(sym).font(.system(size: 11, weight: .bold)).foregroundStyle(c.faint)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    // MARK: grid
    private var cells: [Date?] {
        let comps = cal.dateComponents([.year, .month], from: month)
        guard let first = cal.date(from: comps),
              let range = cal.range(of: .day, in: .month, for: first) else { return [] }
        let weekday = cal.component(.weekday, from: first)
        let lead = (weekday - 1 + 7) % 7   // Sunday-first like the Android calendar
        var out: [Date?] = Array(repeating: nil, count: lead)
        for d in range { out.append(cal.date(byAdding: .day, value: d - 1, to: first)) }
        return out
    }
    private func grid(c: NurseColors) -> some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 4) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, day in
                if let day { dayCell(day, c: c) } else { Color.clear.frame(height: 48) }
            }
        }
    }
    private func dayCell(_ day: Date, c: NurseColors) -> some View {
        let dk = DayKey.from(day, cal)
        let assignment = assignmentByDay[dk]
        let profile = assignment.flatMap { profileByID[$0.dutyProfileId] }
        let col = profile.map { Color(hex: $0.colorHex) }
        let isToday = cal.isDateInToday(day)
        let cellBg: Color = if let col { isToday ? col : col.opacity(scheme == .dark ? 0.24 : 0.14) }
                            else { scheme == .dark ? Color(hex: "#1B160E") : Color(hex: "#F3EEE4") }
        let isCharge = assignment?.charge == true && profile.map { ChargeRules.chargeable($0.kind) } == true

        return Button { pick = DayPick(date: day) } label: {
            ZStack(alignment: .topTrailing) {
                VStack(spacing: 1) {
                    Text("\(cal.component(.day, from: day))")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(profile != nil ? (isToday ? .white : c.text) : (isToday ? Color(hex: "#3182F6") : c.faint))
                    if let profile {
                        Text(Duty.short(profile.kind))
                            .font(.system(size: 10, weight: .heavy))
                            .foregroundStyle(isToday ? .white.opacity(0.92) : (col ?? c.sub))
                    }
                }
                .frame(maxWidth: .infinity).frame(height: 48)
                if isCharge {
                    Circle().fill(Duty.gold).frame(width: 6, height: 6).padding(4)
                }
            }
            .background(cellBg, in: RoundedRectangle(cornerRadius: 11))
            .overlay {
                if isToday {
                    RoundedRectangle(cornerRadius: 11)
                        .stroke(profile != nil ? Color.white : Color(hex: "#3182F6"), lineWidth: 1.5)
                }
            }
        }
        .buttonStyle(.plain)
    }

    // MARK: month stats
    private func statsCard(c: NurseColors) -> some View {
        let ymPrefix = cal.component(.year, from: month) * 100 + cal.component(.month, from: month)
        let inMonth = assignments.filter { $0.dayKey / 100 == ymPrefix }
        let counts = Duty.kinds.map { k in (kind: k, n: inMonth.filter { profileByID[$0.dutyProfileId]?.kind == k }.count) }
        let total = counts.map(\.n).reduce(0, +)
        let chargeDays = inMonth.filter { a in a.charge && profileByID[a.dutyProfileId].map { ChargeRules.chargeable($0.kind) } == true }.count

        return SoftCard(pad: 18) {
            HStack(alignment: .lastTextBaseline) {
                Text("\(cal.component(.month, from: month))월 근무")
                    .font(.system(size: 14.5, weight: .heavy)).foregroundStyle(c.text)
                Spacer()
                Text("\(total)").font(.system(size: 21, weight: .heavy)).foregroundStyle(c.text)
                Text("일").font(.system(size: 13, weight: .bold)).foregroundStyle(c.sub)
            }
            .padding(.bottom, 14)
            GeometryReader { geo in
                HStack(spacing: 2) {
                    ForEach(counts.filter { $0.n > 0 }, id: \.kind) { row in
                        Duty.color(row.kind)
                            .frame(width: max(0, (geo.size.width - 8) * CGFloat(row.n) / CGFloat(max(total, 1))))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(c.track)
                .clipShape(Capsule())
            }
            .frame(height: 14)
            VStack(spacing: 11) {
                ForEach(counts.filter { $0.n > 0 }, id: \.kind) { row in
                    HStack(spacing: 8) {
                        RoundedRectangle(cornerRadius: 3).fill(Duty.color(row.kind)).frame(width: 10, height: 10)
                        Text(Duty.ko(row.kind)).font(.system(size: 13, weight: .semibold)).foregroundStyle(c.text)
                        Spacer()
                        Text("\(row.n)").font(.system(size: 13, weight: .heavy)).foregroundStyle(c.text)
                    }
                }
                if total > 0 {
                    HStack(spacing: 8) {
                        Image(systemName: "crown.fill").font(.system(size: 12)).foregroundStyle(Duty.gold)
                        Text("그 중 차지 근무").font(.system(size: 13, weight: .semibold)).foregroundStyle(c.text)
                        Spacer()
                        Text("\(chargeDays)일").font(.system(size: 13, weight: .heavy)).foregroundStyle(c.text)
                    }
                }
            }
            .padding(.top, 16)
        }
    }
}
