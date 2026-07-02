import Foundation

/// Charge (팀 리더) is a modifier on Day/Evening/Night assignments — mirrors Android ChargeRules.
public enum ChargeRules {
    /// Stable virtual checklist-item id for the prepended "팀 배정·인수인계 확인" row.
    public static let itemID = UUID(uuidString: "00000000-0000-0000-0000-0000C4A26E01")!
    public static let itemText = "팀 배정·인수인계 확인"

    public static func chargeable(_ kind: String) -> Bool {
        kind == "Day" || kind == "Evening" || kind == "Night"
    }
}
