import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            CalendarView()
                .tabItem { Label("근무표", systemImage: "calendar") }
            ProfilesView()
                .tabItem { Label("듀티", systemImage: "person.2.badge.gearshape") }
        }
    }
}
