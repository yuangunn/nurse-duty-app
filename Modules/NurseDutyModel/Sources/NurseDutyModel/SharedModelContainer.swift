import Foundation
import SwiftData

public enum NurseDutyStore {
    // ponytail: must match the App Group entitlement added to the app / widget / watch targets in Phase 1.
    // App Group is shared app<->widget on the SAME device only; the watch gets data via WatchConnectivity.
    public static let appGroupID = "group.com.example.nurseduty"

    public static let schema = Schema([
        DutyProfile.self,
        AlarmItem.self,
        ChecklistItem.self,
        ChecklistCheck.self,
        ShiftAssignment.self,
        QuickMemo.self,
    ])

    /// One shared container. `inMemory` is for tests/previews (no entitlement needed);
    /// the real app uses the App Group container so widgets read the same store.
    public static func makeContainer(inMemory: Bool = false) throws -> ModelContainer {
        let config: ModelConfiguration = inMemory
            ? ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
            : ModelConfiguration(schema: schema, groupContainer: .identifier(appGroupID))
        return try ModelContainer(for: schema, configurations: config)
    }
}
