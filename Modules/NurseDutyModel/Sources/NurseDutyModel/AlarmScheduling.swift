import Foundation

/// Pure derivation of when an alarm fires. Alarms are templates on a profile; the actual
/// fire instant is (shift date + dayOffset days) at the alarm's local wall-clock hour:minute.
/// This is the heart of the night-shift-crosses-midnight handling.
public enum AlarmScheduling {

    public static func fireDate(shiftDate: Date, hour: Int, minute: Int, dayOffset: Int,
                                calendar: Calendar = .current) -> Date? {
        let base = calendar.startOfDay(for: shiftDate)
        guard let day = calendar.date(byAdding: .day, value: dayOffset, to: base) else { return nil }
        return calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day)
    }

    public static func fireDate(shiftDate: Date, alarm: AlarmItem,
                                calendar: Calendar = .current) -> Date? {
        fireDate(shiftDate: shiftDate, hour: alarm.hour, minute: alarm.minute,
                 dayOffset: alarm.dayOffset, calendar: calendar)
    }

    /// Calendar trigger components for UNCalendarNotificationTrigger.
    /// timeZone stays nil on purpose -> fires at the user's CURRENT local wall-clock time
    /// and auto-adjusts across timezone/DST (correct for a 07:00 handover alarm).
    public static func triggerComponents(shiftDate: Date, hour: Int, minute: Int, dayOffset: Int,
                                         calendar: Calendar = .current) -> DateComponents? {
        guard let fire = fireDate(shiftDate: shiftDate, hour: hour, minute: minute,
                                  dayOffset: dayOffset, calendar: calendar) else { return nil }
        var c = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
        c.timeZone = nil
        return c
    }

    /// Deterministic notification identifier so the Phase-2 reconciler can diff
    /// pending vs desired idempotently (no removeAll + re-add).
    public static func notificationID(shiftDate: Date, dutyProfileID: UUID, alarmID: UUID,
                                      calendar: Calendar = .current) -> String {
        let c = calendar.dateComponents([.year, .month, .day],
                                        from: calendar.startOfDay(for: shiftDate))
        let ymd = String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
        return "\(ymd)#\(dutyProfileID.uuidString)#\(alarmID.uuidString)"
    }
}
