import Foundation
import SwiftData

/// One duty per calendar day. Assigning a duty to a day that already has one REPLACES it
/// (find-or-update), so the calendar never grows duplicate rows for a date. Keyed by a
/// timezone-independent dayKey so travel/DST can't create duplicates.
public enum Assignments {

    @discardableResult
    public static func upsert(in context: ModelContext, date: Date, dutyProfileId: UUID,
                              charge: Bool = false,
                              calendar: Calendar = .current) throws -> ShiftAssignment {
        let key = DayKey.from(date, calendar)
        let existing = try context.fetch(
            FetchDescriptor<ShiftAssignment>(predicate: #Predicate { $0.dayKey == key })
        ).first
        if let existing {
            existing.dutyProfileId = dutyProfileId
            existing.charge = charge
            return existing
        }
        let assignment = ShiftAssignment(date: date, dutyProfileId: dutyProfileId, charge: charge, calendar: calendar)
        context.insert(assignment)
        return assignment
    }

    public static func clear(in context: ModelContext, date: Date,
                             calendar: Calendar = .current) throws {
        let key = DayKey.from(date, calendar)
        for a in try context.fetch(
            FetchDescriptor<ShiftAssignment>(predicate: #Predicate { $0.dayKey == key })
        ) {
            context.delete(a)
        }
    }
}
