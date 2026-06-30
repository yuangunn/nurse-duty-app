import SwiftUI
import SwiftData
import NurseDutyModel

private struct DayPick: Identifiable {
    let date: Date
    var id: Double { date.timeIntervalSinceReferenceDate }
}

struct CalendarView: View {
    @Query private var assignments: [ShiftAssignment]
    @Query(sort: \DutyProfile.sortOrder) private var profiles: [DutyProfile]

    @State private var month: Date = Calendar.current.startOfDay(for: Date())
    @State private var pick: DayPick?

    private let cal = Calendar.current

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                header
                weekdayRow
                grid
                Spacer(minLength: 0)
            }
            .padding()
            .navigationTitle("근무표")
            .sheet(item: $pick) { p in AssignSheet(date: p.date) }
        }
    }

    // MARK: lookups
    private var profileByID: [UUID: DutyProfile] {
        Dictionary(profiles.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
    }
    private var assignmentByDay: [Date: ShiftAssignment] {
        Dictionary(assignments.map { (cal.startOfDay(for: $0.date), $0) }, uniquingKeysWith: { a, _ in a })
    }

    // MARK: header
    private var header: some View {
        HStack {
            Button { shiftMonth(-1) } label: { Image(systemName: "chevron.left") }
            Spacer()
            Text(monthTitle).font(.title3.weight(.semibold))
            Spacer()
            Button { shiftMonth(1) } label: { Image(systemName: "chevron.right") }
        }
        .padding(.horizontal, 4)
    }
    private func shiftMonth(_ n: Int) {
        if let m = cal.date(byAdding: .month, value: n, to: month) { month = m }
    }
    private var monthTitle: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "yyyy년 M월"
        return f.string(from: month)
    }

    // MARK: weekday row
    private var weekdaySymbols: [String] {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR")
        let s = f.shortStandaloneWeekdaySymbols ?? ["일", "월", "화", "수", "목", "금", "토"]
        let start = cal.firstWeekday - 1
        return Array(s[start...] + s[..<start])
    }
    private var weekdayRow: some View {
        HStack {
            ForEach(weekdaySymbols, id: \.self) { sym in
                Text(sym).font(.caption).foregroundStyle(.secondary)
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
        let lead = (weekday - cal.firstWeekday + 7) % 7
        var out: [Date?] = Array(repeating: nil, count: lead)
        for d in range { out.append(cal.date(byAdding: .day, value: d - 1, to: first)) }
        return out
    }
    private var grid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 6) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, day in
                if let day { dayCell(day) } else { Color.clear.frame(height: 56) }
            }
        }
    }
    private func dayCell(_ day: Date) -> some View {
        let assignment = assignmentByDay[cal.startOfDay(for: day)]
        let profile = assignment.flatMap { profileByID[$0.dutyProfileId] }
        return Button { pick = DayPick(date: day) } label: {
            VStack(spacing: 4) {
                Text("\(cal.component(.day, from: day))").font(.callout)
                if let profile {
                    Text(profile.name).font(.system(size: 10, weight: .semibold)).lineLimit(1)
                        .padding(.horizontal, 5).padding(.vertical, 2)
                        .background(Color(hex: profile.colorHex).opacity(0.22), in: Capsule())
                        .foregroundStyle(Color(hex: profile.colorHex))
                } else {
                    Color.clear.frame(height: 14)
                }
            }
            .frame(maxWidth: .infinity).frame(height: 56)
            .background(cal.isDateInToday(day) ? Color.accentColor.opacity(0.12) : Color.gray.opacity(0.06),
                        in: RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }
}
