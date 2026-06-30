import Foundation
import SwiftData

/// Per-date checklist state. A check is keyed by (item id, day) — presence == checked — so
/// today's checks never bleed into tomorrow, past days stay intact, and editing an item's
/// text never disturbs history (the check references the id, not the text).
public enum Checklist {

    public static func isChecked(_ itemID: UUID, on date: Date, in context: ModelContext,
                                 calendar: Calendar = .current) -> Bool {
        let day = calendar.startOfDay(for: date)
        let count = (try? context.fetchCount(
            FetchDescriptor<ChecklistCheck>(predicate: #Predicate { $0.checklistItemId == itemID && $0.date == day })
        )) ?? 0
        return count > 0
    }

    /// Check ↔ uncheck. Inserts a ChecklistCheck if absent, deletes it if present.
    public static func toggle(_ itemID: UUID, on date: Date, in context: ModelContext,
                              calendar: Calendar = .current) {
        let day = calendar.startOfDay(for: date)
        let existing = (try? context.fetch(
            FetchDescriptor<ChecklistCheck>(predicate: #Predicate { $0.checklistItemId == itemID && $0.date == day })
        )) ?? []
        if let first = existing.first {
            context.delete(first)
        } else {
            context.insert(ChecklistCheck(checklistItemId: itemID, date: day, calendar: calendar))
        }
    }

    /// Item ids checked on a given day — for rendering a whole list at once.
    public static func checkedIDs(on date: Date, in context: ModelContext,
                                  calendar: Calendar = .current) -> Set<UUID> {
        let day = calendar.startOfDay(for: date)
        let rows = (try? context.fetch(
            FetchDescriptor<ChecklistCheck>(predicate: #Predicate { $0.date == day })
        )) ?? []
        return Set(rows.map(\.checklistItemId))
    }
}
