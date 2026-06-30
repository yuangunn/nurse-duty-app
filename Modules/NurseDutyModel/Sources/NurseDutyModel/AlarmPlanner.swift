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
            // archived profiles are soft-deleted: their assignments stay for history but must not fire alarms.
            guard let profile = profilesByID[assignment.dutyProfileId], !profile.isArchived else { continue }
            let shiftDate = assignment.day
            for alarm in profile.alarms where alarm.isEnabled {
                guard let fire = AlarmScheduling.fireDate(shiftDate: shiftDate, alarm: alarm,
                                                          calendar: calendar),
                      fire > now, fire <= end,
                      let comps = AlarmScheduling.triggerComponents(shiftDate: shiftDate,
                                                                    hour: alarm.hour, minute: alarm.minute,
                                                                    dayOffset: alarm.dayOffset,
                                                                    calendar: calendar)
                else { continue }
                let id = AlarmScheduling.notificationID(shiftDate: shiftDate,
                                                        dutyProfileID: profile.id, alarmID: alarm.id,
                                                        calendar: calendar)
                planned.append(PlannedAlarm(id: id, fireDate: fire, components: comps,
                                            title: alarm.label, body: profile.name + " 근무"))
            }
        }
        return Array(planned.sorted { $0.fireDate < $1.fireDate }.prefix(budget))
    }

    /// Reconcile against currently-pending identifiers. We RE-EMIT every desired occurrence (not
    /// just ids absent from pending) because the id encodes only day#duty#alarm — not the time or
    /// label. An edited alarm keeps its id, so to propagate the new fire time/content we must re-add
    /// it; UNUserNotificationCenter.add(_:) replaces a same-id request, so this stays idempotent.
    /// `remove` drops ids no longer desired (deleted/disabled/archived/out-of-window). We only ever
    /// touch ids we own — callers filter pending to ids containing "#".
    public static func reconcile(pending: Set<String>,
                                 desired: [PlannedAlarm]) -> (add: [PlannedAlarm], remove: [String]) {
        let desiredIDs = Set(desired.map(\.id))
        let remove = pending.subtracting(desiredIDs).sorted()
        return (desired, remove)
    }
}
