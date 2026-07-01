package com.nurseduty.wear

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// duty gradients mirror the phone handoff (1b); the watch carries the duty-colored header.
object WearStyle {
    fun gradient(kind: String): Brush = when (kind) {
        "Day" -> Brush.linearGradient(listOf(Color(0xFF3182F6), Color(0xFF5BA0FF)))
        "Mid" -> Brush.linearGradient(listOf(Color(0xFF14B8A6), Color(0xFF4FD1C5)))
        "Evening" -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF4D)))
        "Night" -> Brush.linearGradient(listOf(Color(0xFF5B5BD6), Color(0xFF8487F0)))
        "Off" -> Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFFB4C0D0)))
        else -> Brush.linearGradient(listOf(Color(0xFF8C8AA6), Color(0xFFAEA9C2)))
    }
    fun ko(kind: String) = when (kind) { "Day" -> "데이"; "Mid" -> "미드"; "Evening" -> "이브닝"; "Night" -> "나이트"; "Off" -> "휴무"; else -> "" }
    fun letter(kind: String) = when (kind) { "Day" -> "D"; "Mid" -> "M"; "Evening" -> "E"; "Night" -> "N"; "Off" -> "O"; else -> "·" }
    val Gold = Color(0xFFEAB308)
    val Success = Color(0xFF22C55E)
    val CardBg = Color(0xFF1C1C22)
}
