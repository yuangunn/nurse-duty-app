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

/** Command the watch sends back to the phone (MessageClient). */
@Serializable
sealed interface WearCommand {
    @Serializable data class ToggleCheck(val itemId: String, val dayKey: Int) : WearCommand
    @Serializable data class AddMemo(val id: String, val bedTag: String, val text: String) : WearCommand

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun encode(c: WearCommand): String = json.encodeToString(c)
        fun decode(s: String): WearCommand = json.decodeFromString(s)
    }
}
