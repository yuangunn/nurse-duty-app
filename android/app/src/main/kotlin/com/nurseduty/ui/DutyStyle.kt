package com.nurseduty.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Visual design constants for the 5 canonical shifts (from the 1b handoff). Charge RULES live in
 * data.ChargeRules (shared with the repository). */
object Duty {
    val KINDS = listOf("Day", "Mid", "Evening", "Night", "Off")

    fun color(kind: String) = when (kind) {
        "Day" -> Color(0xFF3182F6); "Mid" -> Color(0xFF14B8A6); "Evening" -> Color(0xFFF59E0B)
        "Night" -> Color(0xFF5B5BD6); "Off" -> Color(0xFF94A3B8); else -> Color(0xFF3182F6)
    }

    fun gradient(kind: String): Brush = when (kind) {
        "Day" -> Brush.linearGradient(listOf(Color(0xFF3182F6), Color(0xFF5BA0FF)))
        "Mid" -> Brush.linearGradient(listOf(Color(0xFF14B8A6), Color(0xFF4FD1C5)))
        "Evening" -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF4D)))
        "Night" -> Brush.linearGradient(listOf(Color(0xFF5B5BD6), Color(0xFF8487F0)))
        "Off" -> Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFFB4C0D0)))
        else -> Brush.linearGradient(listOf(Color(0xFF3182F6), Color(0xFF5BA0FF)))
    }

    val brandGradient = Brush.linearGradient(listOf(Color(0xFF3182F6), Color(0xFF5BA0FF)))

    fun letter(kind: String) = when (kind) { "Day" -> "D"; "Mid" -> "M"; "Evening" -> "E"; "Night" -> "N"; "Off" -> "O"; else -> "·" }
    fun short(kind: String) = when (kind) { "Day" -> "데이"; "Mid" -> "미드"; "Evening" -> "이브"; "Night" -> "나잇"; "Off" -> "오프"; else -> "" }
    fun ko(kind: String) = when (kind) { "Day" -> "데이"; "Mid" -> "미드"; "Evening" -> "이브닝"; "Night" -> "나이트"; "Off" -> "휴무"; else -> "" }

    data class Sky(val grad: Brush, val ringHole: Color, val deco: String, val greeting: String,
                   val restIcon: String = "", val restTitle: String = "", val restSub: String = "")

    fun sky(kind: String): Sky = when (kind) {
        "Day" -> Sky(Brush.linearGradient(listOf(Color(0xFF3E86F4), Color(0xFF5C9BF6), Color(0xFF8FBCF2))),
            Color(0xFF4E93F5), "sun", "좋은 아침이에요 ☀️")
        "Mid" -> Sky(Brush.linearGradient(listOf(Color(0xFF159FC4), Color(0xFF37B8CE), Color(0xFF86DBDE))),
            Color(0xFF2AA7C4), "noon", "미드 근무 화이팅이에요 🌤️")
        "Evening" -> Sky(Brush.linearGradient(listOf(Color(0xFFFF9E57), Color(0xFFFB7268), Color(0xFFC95E94))),
            Color(0xFFFB7A66), "sunset", "오후도 화이팅이에요 🌇")
        "Night" -> Sky(Brush.linearGradient(listOf(Color(0xFF241C57), Color(0xFF3D2F86), Color(0xFF5E4DBC))),
            Color(0xFF4636A0), "night", "오늘 밤도 안전 근무해요 🌙")
        "Off" -> Sky(Brush.linearGradient(listOf(Color(0xFF5B9E86), Color(0xFF86C3A2), Color(0xFFBFE3C8))),
            Color(0xFF5FA189), "calm", "푹 쉬는 날이에요 ☕", "☕", "오늘은 휴무예요", "알람도 체크리스트도 없어요.\n충분히 쉬고 다음 근무 때 만나요!")
        else -> Sky(Brush.linearGradient(listOf(Color(0xFF8C8AA6), Color(0xFFAEA9C2), Color(0xFFCFCBDD))),
            Color(0xFF8C8AA6), "calm", "오늘은 근무가 없어요", "🗓", "근무가 배정되지 않았어요", "근무표에서 오늘 날짜를 눌러\n근무를 배정해 보세요.")
    }

    val Gold = Color(0xFFEAB308)
    val GoldInk = Color(0xFFB4820A)
    val Success = Color(0xFF22C55E)
    val Danger = Color(0xFFEF4444)
}
