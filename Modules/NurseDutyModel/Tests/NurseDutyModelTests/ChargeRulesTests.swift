import Testing
import Foundation
import SwiftData
@testable import NurseDutyModel

struct ChargeRulesTests {
    @Test func chargeableKinds() {
        #expect(ChargeRules.chargeable("Day"))
        #expect(ChargeRules.chargeable("Evening"))
        #expect(ChargeRules.chargeable("Night"))
        #expect(!ChargeRules.chargeable("Mid"))
        #expect(!ChargeRules.chargeable("Off"))
        #expect(!ChargeRules.chargeable("Custom"))
    }

    @Test func modelDefaultsAreLegacySafe() throws {
        let container = try ModelContainer(
            for: DutyProfile.self, ShiftAssignment.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
        let ctx = ModelContext(container)
        let p = DutyProfile(name: "X", colorHex: "#000000")
        ctx.insert(p)
        #expect(p.kind == "Custom" && p.timeText.isEmpty)
        let a = ShiftAssignment(date: Date(), dutyProfileId: UUID())
        ctx.insert(a)
        #expect(a.charge == false)
    }
}
