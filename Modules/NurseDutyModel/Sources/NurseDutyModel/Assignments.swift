import Foundation
import SwiftData

/// One duty per calendar day. Assigning a duty to a day that already has one REPLACES it
/// (find-or-update), so the calendar never grows duplicate rows for a date.
public enum Assignments {

    @discardableResult
    public static func upsert(in context: ModelContext, date: Date, dutyProfileId: UUID,
                              calendar: Calendar = .current) throws -> ShiftAssignment {
        let day = calendar.startOfDay(for: date)
        let existing = try context.fetch(
            FetchDescriptor<ShiftAssignment>(predicate: #Predicate { $0.date == day })
        ).first
        if let existing {
            existing.dutyProfileId = dutyProfileId
            return existing
        }
        let assignment = ShiftAssignment(date: day, dutyProfileId: dutyProfileId, calendar: calendar)
        context.insert(assignment)
        return assignment
    }

    public static func clear(in context: ModelContext, date: Date,
                             calendar: Calendar = .current) throws {
        let day = calendar.startOfDay(for: date)
        for a in try context.fetch(
            FetchDescriptor<ShiftAssignment>(predicate: #Predicate { $0.date == day })
        ) {
            context.delete(a)
        }
    }
}
