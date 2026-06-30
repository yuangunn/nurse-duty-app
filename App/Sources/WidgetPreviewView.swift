#if DEBUG
import SwiftUI
import SwiftData
import NurseDutyModel

/// In-app render of the widget views (same code the extension uses) so the layout can be
/// verified without adding the widget to the home screen. Shown via --preview-widget.
struct WidgetPreviewView: View {
    @Environment(\.modelContext) private var ctx

    var body: some View {
        let snap = WidgetSnapshotBuilder.build(from: ctx)
        ScrollView {
            VStack(spacing: 22) {
                Text("위젯 미리보기").font(.title3.bold())

                label("홈 (Small)")
                WidgetHomeView(snapshot: snap)
                    .frame(width: 158, height: 158)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))

                label("홈 (Medium)")
                WidgetHomeView(snapshot: snap)
                    .frame(width: 338, height: 158)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))

                label("잠금화면 (Rectangular)")
                WidgetRectangularView(snapshot: snap)
                    .padding(8).frame(width: 170, height: 76)
                    .background(.black.opacity(0.85), in: RoundedRectangle(cornerRadius: 12))
                    .foregroundStyle(.white)

                label("잠금화면 (Circular / Inline)")
                HStack(spacing: 20) {
                    WidgetCircularView(snapshot: snap)
                        .frame(width: 64, height: 64)
                        .padding(8).background(.black.opacity(0.85), in: Circle()).foregroundStyle(.white)
                    WidgetInlineView(snapshot: snap)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(.black.opacity(0.85), in: Capsule()).foregroundStyle(.white)
                }
            }
            .padding()
        }
    }

    private func label(_ t: String) -> some View {
        Text(t).font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity, alignment: .leading)
    }
}
#endif
