package com.nurseduty.data

/** Charge = a per-assignment modifier on Day/Evening/Night (team leader). Adds one checklist item. */
object ChargeRules {
    const val ITEM_ID = "charge:handover"
    const val ITEM_TEXT = "팀 배정·인수인계 확인"
    fun chargeable(kind: String) = kind == "Day" || kind == "Evening" || kind == "Night"
}
