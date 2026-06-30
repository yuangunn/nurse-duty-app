import Foundation

/// A concrete, schedulable alarm occurrence — the pure output of planning, before it touches
/// UNUserNotificationCenter. `id` is the deterministic notification identifier.
public struct PlannedAlarm: Equatable, Identifiable {
    public let id: String                  // "YYYY-MM-DD#dutyProfileID#alarmID"
    public let fireDate: Date              // concrete instant, for sort + budget
    public let components: DateComponents  // for UNCalendarNotificationTrigger (timeZone == nil)
    public let title: String
    public let body: String

    public init(id: String, fireDate: Date, components: DateComponents, title: String, body: String) {
        self.id = id; self.fireDate = fireDate; self.components = components
        self.title = title; self.body = body
    }
}

/// The rolling-window scheduler brain. iOS keeps only the 64 soonest PENDING local notifications
/// and silently drops the rest, so we NEVER pre-schedule a whole month — we plan the soonest
/// `budget` (< 64) occurrences inside a window and reconcile idempotently against what's pending.
public enum AlarmPlanner {

    /// Enabled-alarm occurrences for assignments whose fire time is in (now, now+windowDays],
    /// sorted soonest-first and capped to `budget`.
    public static func plan(assignments: [ShiftAssignment],
                            profilesByID: [UUID: DutyProfile],
                            from now: Date,
                            windowDays: Int,
                            budget: Int,
                            calendar: Calendar = .current) -> [PlannedAlarm] {
        guard let end = calendar.date(byAdding: .day, value: windowDays,
                                      to: calendar.startOfDay(for: now)) else { return [] }
        var planned: [PlannedAlarm] = []
        for assignment in assignments {
            guard let profile = profilesByID[assignment.dutyProfileId] else { continue }
            for alarm in profile.alarms where alarm.isEnabled {
                guard let fire = AlarmScheduling.fireDate(shiftDate: assignment.date, alarm: alarm,
                                                          calendar: calendar),
                      fire > now, fire <= end,
                      let comps = AlarmScheduling.triggerComponents(shiftDate: assignment.date,
                                                                    hour: alarm.hour, minute: alarm.minute,
                                                                    dayOffset: alarm.dayOffset,
                                                                    calendar: calendar)
                else { continue }
                let id = AlarmScheduling.notificationID(shiftDate: assignment.date,
                                                        dutyProfileID: profile.id, alarmID: alarm.id,
                                                        calendar: calendar)
                planned.append(PlannedAlarm(id: id, fireDate: fire, components: comps,
                                            title: alarm.label, body: profile.name + " 근무"))
            }
        }
        return Array(planned.sorted { $0.fireDate < $1.fireDate }.prefix(budget))
    }

    /// Idempotent diff: what to add and what stale identifiers to remove. We only ever compare
    /// against alarm identifiers we own — callers filter pending to ids containing "#".
    public static func reconcile(pending: Set<String>,
                                 desired: [PlannedAlarm]) -> (add: [PlannedAlarm], remove: [String]) {
        let desiredIDs = Set(desired.map(\.id))
        let add = desired.filter { !pending.contains($0.id) }
        let remove = pending.subtracting(desiredIDs).sorted()
        return (add, remove)
    }
}
