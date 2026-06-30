import SwiftUI
import UIKit

struct RootView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.modelContext) private var ctx

    var body: some View {
        TabView {
            TodayView()
                .tabItem { Label("오늘", systemImage: "checklist") }
            CalendarView()
                .tabItem { Label("근무표", systemImage: "calendar") }
            ProfilesView()
                .tabItem { Label("듀티", systemImage: "person.2.badge.gearshape") }
        }
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
    }
}
