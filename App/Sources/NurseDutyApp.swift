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
    }

    var body: some Scene {
        WindowGroup { RootView() }
            .modelContainer(container)
    }
}
