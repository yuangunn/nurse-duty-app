package com.nurseduty.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Snapshot the phone pushes to the watch (latest-wins via the Data Layer). */
@Serializable
data class WearState(
    val dayKey: Int = 0,
    val dutyName: String? = null,
    val kind: String = "None",
    val colorHex: String? = null,
    val timeText: String? = null,
    val charge: Boolean = false,
    val nextAlarm: String? = null,
    val pendingMemos: Int = 0,
    val checklist: List<WearItem> = emptyList(),
) {
    @Serializable
    data class WearItem(val id: String, val text: String, val checked: Boolean)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun encode(s: WearState): String = json.encodeToString(s)
        fun decode(s: String): WearState = json.decodeFromString(s)
    }
}

/** Command the watch sends back to the phone (DataItem queue; survives disconnection). */
@Serializable
sealed interface WearCommand {
    /** Legacy toggle (non-idempotent) — kept for old watch builds; new builds send SetCheck. */
    @Serializable data class ToggleCheck(val itemId: String, val dayKey: Int) : WearCommand
    /** Idempotent: safe under the Data Layer's at-least-once delivery. */
    @Serializable data class SetCheck(val itemId: String, val dayKey: Int, val checked: Boolean) : WearCommand
    @Serializable data class AddMemo(val id: String, val bedTag: String, val text: String) : WearCommand
    /** Ask the phone to push a fresh /today state (watch woke up stale). */
    @Serializable data object Sync : WearCommand

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun encode(c: WearCommand): String = json.encodeToString(c)
        fun decode(s: String): WearCommand = json.decodeFromString(s)
    }
}
