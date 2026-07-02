import SwiftUI
import UIKit

struct RootView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.modelContext) private var ctx
    @State private var selection = 0

    var body: some View {
        TabView(selection: $selection) {
            TodayView(onMemoTab: { selection = 3 }).tag(0)
                .tabItem { Label("오늘", systemImage: "checklist") }
            CalendarView().tag(1)
                .tabItem { Label("근무표", systemImage: "calendar") }
            ProfilesView().tag(2)
                .tabItem { Label("듀티", systemImage: "person.2.badge.gearshape") }
            MemoView().tag(3)
                .tabItem { Label("메모", systemImage: "note.text") }
        }
        .tint(Color(hex: "#3182F6"))
        // Two separate tasks: requestAuthorization() suspends until the user answers the prompt,
        // and arming must NOT wait on that — add() schedules regardless of authorization.
        .task { await NotificationScheduler.requestAuthorization() }
        .task { await NotificationScheduler.reconcile(context: ctx) }   // first-launch arm (.onChange won't fire for the initial .active)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { rearm(ctx) }            // re-arm on every foreground return
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
            rearm(ctx)                                    // midnight / DST / timezone
        }
        .onAppear {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--tab-memo") { selection = 3 }
            if ProcessInfo.processInfo.arguments.contains("--tab-calendar") { selection = 1 }
            #endif
        }
        .onOpenURL { url in
            if url.host == "memo" { selection = 3 }   // widget tap -> 메모 탭 (캡처 한 탭 거리)
        }
    }
}
