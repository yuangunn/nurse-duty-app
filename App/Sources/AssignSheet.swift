import SwiftUI
import SwiftData
import NurseDutyModel

/// 1b assign sheet: pick one of the 5 shifts, optional charge toggle, 저장하기 advances to the
/// next day so a whole month can be entered in one flow (parity with Android).
struct AssignSheet: View {
    let date: Date
    var onAdvance: (Date) -> Void = { _ in }

    @Environment(\.modelContext) private var ctx
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @Query private var assignments: [ShiftAssignment]
    @Query(sort: \DutyProfile.sortOrder) private var profiles: [DutyProfile]
    @State private var pickedID: UUID?
    @State private var charge = false
    @State private var loaded = false

    private let cal = Calendar.current

    private var current: ShiftAssignment? {
        let key = DayKey.from(date, cal)
        return assignments.first { $0.dayKey == key }
    }
    private var active: [DutyProfile] { profiles.filter { !$0.isArchived } }
    private var picked: DutyProfile? { active.first { $0.id == pickedID } }
    private var chargeable: Bool { picked.map { ChargeRules.chargeable($0.kind) } == true }

    var body: some View {
        let c = NurseColors.of(scheme)
        VStack(alignment: .leading, spacing: 0) {
            Capsule().fill(c.grab).frame(width: 40, height: 5)
                .frame(maxWidth: .infinity).padding(.top, 12).padding(.bottom, 14)
            Text(dateTitle).font(.system(size: 13, weight: .semibold)).foregroundStyle(c.sub)
            Text("근무를 선택하세요").font(.system(size: 21, weight: .heavy)).foregroundStyle(c.text)
                .padding(.top, 2).padding(.bottom, 16)

            ScrollView {
                VStack(spacing: 9) {
                    ForEach(active) { p in
                        shiftRow(p, c: c)
                    }
                    if chargeable { chargeRow(c: c).padding(.top, 3) }
                }
            }

            HStack(spacing: 10) {
                Button {
                    try? Assignments.clear(in: ctx, date: date)
                    try? ctx.save(); rearm(ctx); dismiss()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "trash").font(.system(size: 14))
                        Text("지우기").font(.system(size: 15, weight: .bold))
                    }
                    .foregroundStyle(Duty.danger)
                    .padding(.horizontal, 18).padding(.vertical, 16)
                    .background(c.clearBg, in: RoundedRectangle(cornerRadius: 16))
                }
                Button {
                    guard let id = pickedID else { return }
                    _ = try? Assignments.upsert(in: ctx, date: date, dutyProfileId: id, charge: charge && chargeable)
                    try? ctx.save(); rearm(ctx)
                    if let next = cal.date(byAdding: .day, value: 1, to: date) { onAdvance(next) }
                } label: {
                    Text("저장하기").font(.system(size: 16, weight: .bold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 16)
                        .background(pickedID == nil ? AnyShapeStyle(Color(hex: "#B9C7DE")) : AnyShapeStyle(Duty.brandGradient),
                                    in: RoundedRectangle(cornerRadius: 16))
                }
                .disabled(pickedID == nil)
            }
            .padding(.top, 16)
        }
        .padding(.horizontal, 20).padding(.bottom, 26)
        .background(c.sheetBg)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            pickedID = current?.dutyProfileId
            charge = current?.charge == true
        }
        .onChange(of: date) { _, _ in   // 다음날 진행 시 해당 날짜의 기존 배정 반영
            pickedID = current?.dutyProfileId
            charge = current?.charge == true
        }
    }

    private func shiftRow(_ p: DutyProfile, c: NurseColors) -> some View {
        let sel = pickedID == p.id
        return Button {
            pickedID = p.id
            if !ChargeRules.chargeable(p.kind) { charge = false }
        } label: {
            HStack(spacing: 13) {
                DutyAvatar(kind: p.kind, size: 40, radius: 12)
                VStack(alignment: .leading, spacing: 2) {
                    Text(dutyDisplay(p)).font(.system(size: 16, weight: .bold)).foregroundStyle(c.text)
                    Text(p.timeText.isEmpty ? "—" : p.timeText)
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(c.sub)
                }
                Spacer()
                if sel {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 22))
                        .foregroundStyle(Color(hex: "#3182F6"))
                } else {
                    Circle().stroke(c.grab, lineWidth: 2).frame(width: 22, height: 22)
                }
            }
            .padding(13)
            .background(sel ? Color(hex: "#3182F6").opacity(scheme == .dark ? 0.14 : 0.07) : .clear,
                        in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16)
                .stroke(sel ? Color(hex: "#3182F6") : c.cardBorder, lineWidth: 2))
        }
        .buttonStyle(.plain)
    }

    private func chargeRow(c: NurseColors) -> some View {
        Button { charge.toggle() } label: {
            HStack(spacing: 11) {
                Image(systemName: "crown.fill").font(.system(size: 17)).foregroundStyle(Color(hex: "#CA9A08"))
                VStack(alignment: .leading, spacing: 2) {
                    Text("차지 간호사").font(.system(size: 15, weight: .bold)).foregroundStyle(c.text)
                    Text("이 번의 팀 리더 역할").font(.system(size: 12, weight: .semibold)).foregroundStyle(c.sub)
                }
                Spacer()
                Capsule().fill(charge ? Duty.gold : Color(hex: "#CBD5E1"))
                    .frame(width: 46, height: 28)
                    .overlay(alignment: charge ? .trailing : .leading) {
                        Circle().fill(.white).frame(width: 22, height: 22).padding(3)
                    }
            }
            .padding(.horizontal, 15).padding(.vertical, 13)
            .background(charge ? Duty.gold.opacity(scheme == .dark ? 0.13 : 0.10) : .clear,
                        in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16)
                .stroke(charge ? Duty.gold : c.cardBorder, lineWidth: 2))
        }
        .buttonStyle(.plain)
    }

    private var dateTitle: String {
        let f = DateFormatter(); f.locale = Locale(identifier: "ko_KR"); f.dateFormat = "M월 d일 (EEE)"
        return f.string(from: date)
    }
}
