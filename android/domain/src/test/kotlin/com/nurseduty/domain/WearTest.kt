package com.nurseduty.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WearTest {
    @Test
    fun wearStateRoundTrips() {
        val s = WearState(
            dayKey = 20260630, dutyName = "Night", colorHex = "#3B4A6B",
            nextAlarm = "인계 22:00", pendingMemos = 2,
            checklist = listOf(WearState.WearItem("a", "활력징후", true), WearState.WearItem("b", "투약", false)),
        )
        assertEquals(s, WearState.decode(WearState.encode(s)))
    }

    @Test
    fun wearCommandRoundTrips() {
        val toggle: WearCommand = WearCommand.ToggleCheck("item1", 20260630)
        val memo: WearCommand = WearCommand.AddMemo("m1", "1001:01", "진통제")
        assertEquals(toggle, WearCommand.decode(WearCommand.encode(toggle)))
        val back = WearCommand.decode(WearCommand.encode(memo))
        assertTrue(back is WearCommand.AddMemo && back.bedTag == "1001:01")
    }

    @Test
    fun setCheckAndSyncRoundTrip() {
        val set: WearCommand = WearCommand.SetCheck("item1", 20260702, checked = true)
        assertEquals(set, WearCommand.decode(WearCommand.encode(set)))
        val sync: WearCommand = WearCommand.Sync
        assertEquals(sync, WearCommand.decode(WearCommand.encode(sync)))
    }
}
