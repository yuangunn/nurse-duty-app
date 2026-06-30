import Foundation
import SwiftData

/// Per-date checklist state. A check is keyed by (item id, dayKey) — presence == checked — so
/// today's checks never bleed into tomorrow, past days stay intact, editing an item's text never
/// disturbs history, and timezone/DST changes can't mis-date or duplicate a check.
public enum Checklist {

    public static func isChecked(_ itemID: UUID, on date: Date, in context: ModelContext,
                                 calendar: Calendar = .current) -> Bool {
        let key = DayKey.from(date, calendar)
        let count = (try? context.fetchCount(
            FetchDescriptor<ChecklistCheck>(predicate: #Predicate { $0.checklistItemId == itemID && $0.dayKey == key })
        )) ?? 0
        return count > 0
    }

    /// Check ↔ uncheck. Inserts a ChecklistCheck if absent, deletes it if present.
    public static func toggle(_ itemID: UUID, on date: Date, in context: ModelContext,
                              calendar: Calendar = .current) {
        let key = DayKey.from(date, calendar)
        let existing = (try? context.fetch(
            FetchDescriptor<ChecklistCheck>(predicate: #Predicate { $0.checklistItemId == itemID && $0.dayKey == key })
        )) ?? []
        if let first = existing.first {
            context.delete(first)
        } else {
            context.insert(ChecklistCheck(checklistItemId: itemID, date: date, calendar: calendar))
        }
    }

    /// Item ids checked on a given day — for rendering a whole list at once.
    public static func checkedIDs(on date: Date, in context: ModelContext,
                                  calendar: Calendar = .current) -> Set<UUID> {
        let key = DayKey.from(date, calendar)
        let rows = (try? context.fetch(
            FetchDescriptor<ChecklistCheck>(predicate: #Predicate { $0.dayKey == key })
        )) ?? []
        return Set(rows.map(\.checklistItemId))
    }
}
