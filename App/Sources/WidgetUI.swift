import SwiftUI
import NurseDutyModel

// Shared between the app target and the widget extension target (listed in both in project.yml).
// Plain SwiftUI only — no widget-only modifiers here — so the app can also render these for preview.

extension Color {
    init(hex: String) {
        let s = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var rgb: UInt64 = 0
        Scanner(string: s).scanHexInt64(&rgb)
        self.init(red: Double((rgb >> 16) & 0xFF) / 255,
                  green: Double((rgb >> 8) & 0xFF) / 255,
                  blue: Double(rgb & 0xFF) / 255)
    }
}

private func dutyColor(_ hex: String?) -> Color { Color(hex: hex ?? "#9AA0A6") }

/// systemSmall / systemMedium home-screen widget.
struct WidgetHomeView: View {
    let snapshot: WidgetSnapshot
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Circle().fill(dutyColor(snapshot.dutyColorHex)).frame(width: 10, height: 10)
                Text(snapshot.dutyName ?? "근무 없음").font(.headline).lineLimit(1)
                Spacer()
                if snapshot.pendingMemoCount > 0 {
                    Label("\(snapshot.pendingMemoCount)", systemImage: "note.text")
                        .font(.caption2.bold()).foregroundStyle(.orange)
                }
            }
            if snapshot.checklistTotal > 0 {
                ProgressView(value: Double(snapshot.checklistDone), total: Double(snapshot.checklistTotal))
                    .tint(dutyColor(snapshot.dutyColorHex))
                Text("체크리스트 \(snapshot.checklistDone)/\(snapshot.checklistTotal)")
                    .font(.caption2).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            nextAlarmRow
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    @ViewBuilder private var nextAlarmRow: some View {
        if let label = snapshot.nextAlarmLabel, let date = snapshot.nextAlarmDate {
            HStack(spacing: 4) {
                Image(systemName: "bell.fill").font(.caption2)
                Text(label).font(.caption).lineLimit(1)
                Spacer()
                Text(date, format: .dateTime.hour().minute()).font(.caption.monospacedDigit())
            }
            .foregroundStyle(.secondary)
        } else {
            Text("예정 알람 없음").font(.caption2).foregroundStyle(.tertiary)
        }
    }
}

/// accessoryInline lock-screen widget (single line next to the clock).
struct WidgetInlineView: View {
    let snapshot: WidgetSnapshot
    var body: some View {
        if let label = snapshot.nextAlarmLabel, let date = snapshot.nextAlarmDate {
            Text("\(snapshot.dutyName ?? "Off") · \(date, format: .dateTime.hour().minute()) \(label)")
        } else {
            Text(snapshot.dutyName ?? "근무 없음")
        }
    }
}

/// accessoryCircular lock-screen widget — checklist completion gauge.
struct WidgetCircularView: View {
    let snapshot: WidgetSnapshot
    var body: some View {
        Gauge(value: Double(snapshot.checklistDone), in: 0...Double(max(snapshot.checklistTotal, 1))) {
            Image(systemName: "checklist")
        } currentValueLabel: {
            Text("\(snapshot.checklistDone)").font(.system(.body, design: .rounded))
        }
        .gaugeStyle(.accessoryCircularCapacity)
    }
}

/// accessoryRectangular lock-screen widget — duty + next alarm + memos.
struct WidgetRectangularView: View {
    let snapshot: WidgetSnapshot
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Image(systemName: "stethoscope").font(.caption2)
                Text(snapshot.dutyName ?? "근무 없음").font(.headline).lineLimit(1)
                if snapshot.pendingMemoCount > 0 {
                    Spacer()
                    Text("메모 \(snapshot.pendingMemoCount)").font(.caption2).foregroundStyle(.secondary)
                }
            }
            if let label = snapshot.nextAlarmLabel, let date = snapshot.nextAlarmDate {
                Text("\(Text(date, format: .dateTime.hour().minute()))  \(label)")
                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            if snapshot.checklistTotal > 0 {
                Text("체크 \(snapshot.checklistDone)/\(snapshot.checklistTotal)")
                    .font(.caption2).foregroundStyle(.secondary)
            }
        }
    }
}
