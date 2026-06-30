import WidgetKit
import SwiftUI
import SwiftData
import NurseDutyModel

struct NurseDutyEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> NurseDutyEntry {
        NurseDutyEntry(date: Date(), snapshot: .placeholder)
    }

    func getSnapshot(in context: Context, completion: @escaping (NurseDutyEntry) -> Void) {
        completion(NurseDutyEntry(date: Date(), snapshot: context.isPreview ? .placeholder : currentSnapshot()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NurseDutyEntry>) -> Void) {
        let snap = currentSnapshot()
        let entry = NurseDutyEntry(date: Date(), snapshot: snap)
        // Refresh at the next alarm (so a fired alarm drops off), else at the next midnight (day roll-over).
        let cal = Calendar.current
        let nextMidnight = cal.date(byAdding: .day, value: 1, to: cal.startOfDay(for: Date()))
        let refresh = [snap.nextAlarmDate, nextMidnight].compactMap { $0 }.filter { $0 > Date() }.min()
            ?? Date().addingTimeInterval(3600)
        completion(Timeline(entries: [entry], policy: .after(refresh)))
    }

    private func currentSnapshot() -> WidgetSnapshot {
        guard let container = try? NurseDutyStore.makeContainer() else { return .placeholder }
        return WidgetSnapshotBuilder.build(from: ModelContext(container))
    }
}

struct NurseDutyEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: NurseDutyEntry

    var body: some View {
        switch family {
        case .accessoryInline:      WidgetInlineView(snapshot: entry.snapshot)
        case .accessoryCircular:    WidgetCircularView(snapshot: entry.snapshot)
        case .accessoryRectangular: WidgetRectangularView(snapshot: entry.snapshot)
        default:                    WidgetHomeView(snapshot: entry.snapshot)
        }
    }
}

struct NurseDutyWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NurseDutyWidget", provider: Provider()) { entry in
            NurseDutyEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
                .widgetURL(URL(string: "nurseduty://memo"))
        }
        .configurationDisplayName("오늘 근무")
        .description("오늘 듀티 · 다음 알람 · 미처리 메모")
        .supportedFamilies([.systemSmall, .systemMedium,
                            .accessoryInline, .accessoryCircular, .accessoryRectangular])
    }
}

@main
struct NurseDutyWidgetBundle: WidgetBundle {
    var body: some Widget { NurseDutyWidget() }
}
