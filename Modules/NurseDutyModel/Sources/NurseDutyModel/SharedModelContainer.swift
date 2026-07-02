import Foundation
import SwiftData

public enum NurseDutyStore {
    // ponytail: must match the App Group entitlement added to the app / widget / watch targets in Phase 1.
    // App Group is shared app<->widget on the SAME device only; the watch gets data via WatchConnectivity.
    public static let appGroupID = "group.com.yuangunn.nurseduty"

    public static let schema = Schema([
        DutyProfile.self,
        AlarmItem.self,
        ChecklistItem.self,
        ChecklistCheck.self,
        ShiftAssignment.self,
        QuickMemo.self,
    ])

    /// One shared container. `inMemory` is for tests/previews; the real (signed) app uses the
    /// App Group container so widgets read the same store.
    ///
    /// We probe the App Group URL first because SwiftData calls `fatalError` (not throw) when the
    /// entitlement is missing — so an unsigned dev build can't be caught, only avoided. If the
    /// group container isn't active, fall back to a plain local store so the app still runs.
    public static func makeContainer(inMemory: Bool = false) throws -> ModelContainer {
        let config: ModelConfiguration
        if inMemory {
            config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        } else if FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupID) != nil {
            config = ModelConfiguration(schema: schema, groupContainer: .identifier(appGroupID))
        } else {
            config = ModelConfiguration(schema: schema)   // ponytail: no App Group entitlement -> local store
        }
        return try ModelContainer(for: schema, configurations: config)
    }
}
