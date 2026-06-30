import SwiftUI
import SwiftData
import NurseDutyModel

@main
struct NurseDutyApp: App {
    let container: ModelContainer

    init() {
        let c: ModelContainer
        do {
            c = try NurseDutyStore.makeContainer()              // App Group (shared w/ future widgets/watch)
        } catch {
            // ponytail: entitlement not applied (unsigned dev build) -> plain local store so the app still runs.
            c = try! ModelContainer(for: NurseDutyStore.schema)
        }
        container = c
        PresetSeeder.seedIfEmpty(ModelContext(c))
        PhoneConnectivity.shared.start(container: c)   // watch sync
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--seed-demo") {
            DemoSeed.fillNext35Days(ModelContext(c))
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--preview-widget") {
                WidgetPreviewView()
            } else {
                RootView()
            }
            #else
            RootView()
            #endif
        }
        .modelContainer(container)
    }
}
