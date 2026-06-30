import Foundation
import UserNotifications
import WidgetKit
import SwiftData
import NurseDutyModel

/// Thin side-effecting wrapper around the pure AlarmPlanner. Plans the soonest `budget`
/// occurrences in a rolling window and reconciles them idempotently against pending
/// notifications — never a removeAll + re-add.
@MainActor
enum NotificationScheduler {
    static let windowDays = 28
    static let budget = 50      // ponytail: < iOS's 64-pending cap, with headroom.

    static func requestAuthorization() async {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--seed-demo") { return }  // no prompt in verify/screenshot mode
        #endif
        _ = try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge])
    }

    static func reconcile(context: ModelContext, now: Date = Date(), calendar: Calendar = .current) async {
        let center = UNUserNotificationCenter.current()
        let assignments = (try? context.fetch(FetchDescriptor<ShiftAssignment>())) ?? []
        let profiles = (try? context.fetch(FetchDescriptor<DutyProfile>())) ?? []
        let byID = Dictionary(profiles.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })

        let desired = AlarmPlanner.plan(assignments: assignments, profilesByID: byID,
                                        from: now, windowDays: windowDays, budget: budget,
                                        calendar: calendar)

        let pending = await center.pendingNotificationRequests()
        let ours = Set(pending.map(\.identifier).filter { $0.contains("#") })   // our ids carry "#"; leave others alone
        let (add, remove) = AlarmPlanner.reconcile(pending: ours, desired: desired)

        #if DEBUG
        NSLog("🔔 reconcile: desired=\(desired.count) (cap \(budget)) pendingBefore=\(ours.count) add=\(add.count) remove=\(remove.count)")
        #endif

        if !remove.isEmpty { center.removePendingNotificationRequests(withIdentifiers: remove) }
        for p in add {
            let content = UNMutableNotificationContent()
            content.title = p.title
            content.body = p.body
            content.sound = .default
            content.interruptionLevel = .timeSensitive   // break through Focus (time-sensitive entitlement)
            let trigger = UNCalendarNotificationTrigger(dateMatching: p.components, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: p.id, content: content, trigger: trigger))
        }
    }
}

/// Fire-and-forget re-arm + widget refresh from a main-actor view after an edit that changes alarms.
@MainActor
func rearm(_ context: ModelContext) {
    Task { await NotificationScheduler.reconcile(context: context) }
    WidgetCenter.shared.reloadAllTimelines()
    PhoneConnectivity.shared.push()
}

/// Refresh widgets + watch after a write that doesn't touch alarms (checklist tick, memo add/done).
@MainActor
func refreshWidgets() {
    WidgetCenter.shared.reloadAllTimelines()
    PhoneConnectivity.shared.push()
}
