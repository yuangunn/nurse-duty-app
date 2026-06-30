import Foundation

/// Timezone-independent calendar-day key (yyyymmdd). Storing this instead of a wall-clock-midnight
/// `Date` means a day never shifts or duplicates when the device timezone or DST changes.
public enum DayKey {
    public static func from(_ date: Date, _ calendar: Calendar = .current) -> Int {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return (c.year ?? 0) * 10000 + (c.month ?? 0) * 100 + (c.day ?? 0)
    }

    /// Reconstruct the start of that calendar day (for display + fire-time math).
    public static func date(_ key: Int, _ calendar: Calendar = .current) -> Date {
        var c = DateComponents()
        c.year = key / 10000
        c.month = (key / 100) % 100
        c.day = key % 100
        return calendar.date(from: c) ?? Date(timeIntervalSinceReferenceDate: 0)
    }
}
