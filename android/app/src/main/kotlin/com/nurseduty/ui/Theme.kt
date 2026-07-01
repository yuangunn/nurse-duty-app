package com.nurseduty.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Warm "당근" fintech tokens (1b handoff). Most screens read these directly for pixel control.
data class NurseColors(
    val dark: Boolean,
    val bg: Color, val text: Color, val sub: Color, val faint: Color,
    val cardBg: Color, val cardBorder: Color,
    val tabBg: Color, val tabBorder: Color, val tabIdle: Color, val tabSel: Color,
    val chipBg: Color, val chipText: Color, val track: Color,
    val sheetBg: Color, val grab: Color, val clearBg: Color,
    val inputBg: Color, val inputBorder: Color,
    val chargeInfoBg: Color, val chargeInfoBorder: Color,
)

val LightNurse = NurseColors(
    dark = false,
    bg = Color(0xFFFBF7F0), text = Color(0xFF241D13), sub = Color(0xFF8A7D6A), faint = Color(0xFFB6A992),
    cardBg = Color(0xFFFFFDF9), cardBorder = Color(0xFFEFE7D9),
    tabBg = Color(0xF0FBF7F0), tabBorder = Color(0xFFECE3D3), tabIdle = Color(0xFFA99B86), tabSel = Color(0xFF3182F6),
    chipBg = Color(0xFFF3ECE0), chipText = Color(0xFF8A7D6A), track = Color(0xFFF0E9DC),
    sheetBg = Color(0xFFFFFDF9), grab = Color(0xFFE2D8C7), clearBg = Color(0xFFFBECEA),
    inputBg = Color(0xFFF7F1E7), inputBorder = Color(0xFFE8DECB),
    chargeInfoBg = Color(0x1AEAB308), chargeInfoBorder = Color(0x40EAB308),
)

val DarkNurse = NurseColors(
    dark = true,
    bg = Color(0xFF15110B), text = Color(0xFFF4EEE2), sub = Color(0xFF9C9081), faint = Color(0xFF6E6555),
    cardBg = Color(0xFF211B12), cardBorder = Color(0xFF2E2619),
    tabBg = Color(0xF015110B), tabBorder = Color(0xFF2A2318), tabIdle = Color(0xFF8C8170), tabSel = Color(0xFF5B9BFF),
    chipBg = Color(0xFF2A2317), chipText = Color(0xFFB7AB98), track = Color(0xFF2A2317),
    sheetBg = Color(0xFF1C1710), grab = Color(0xFF39301F), clearBg = Color(0xFF2A1614),
    inputBg = Color(0xFF171208), inputBorder = Color(0xFF33291A),
    chargeInfoBg = Color(0x17EAB308), chargeInfoBorder = Color(0x38EAB308),
)

val LocalNurse = staticCompositionLocalOf { LightNurse }

@Composable
fun NurseTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkNurse else LightNurse
    val scheme = if (dark) {
        darkColorScheme(primary = Color(0xFF5B9BFF), background = c.bg, surface = c.cardBg,
            onPrimary = Color.White, onBackground = c.text, onSurface = c.text)
    } else {
        lightColorScheme(primary = Color(0xFF3182F6), background = c.bg, surface = c.cardBg,
            onPrimary = Color.White, onBackground = c.text, onSurface = c.text)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalNurse provides c) {
            Surface(color = c.bg) { content() }
        }
    }
}

private fun ts(size: Double, w: FontWeight, tracking: Double = 0.0) =
    TextStyle(fontSize = size.sp, fontWeight = w, letterSpacing = tracking.sp)

// Named styles matching the handoff scale (color set at call site from LocalNurse). Pretendard
// bundling is a polish step; system Korean font carries the weights.
object NurseType {
    val h1 = ts(24.0, FontWeight.W800, -0.6)          // page titles 근무표/듀티/메모
    val heroShift = ts(23.0, FontWeight.W800, -0.6)   // hero shift name
    val greeting = ts(19.0, FontWeight.W800, -0.4)    // hero greeting
    val sheetTitle = ts(21.0, FontWeight.W800, -0.5)
    val cardTitle = ts(14.5, FontWeight.W800)
    val rowTitle = ts(16.0, FontWeight.W700)
    val body = ts(15.0, FontWeight.W500)
    val bodyStrong = ts(15.0, FontWeight.W600)
    val caption = ts(13.0, FontWeight.W600)
    val label = ts(12.0, FontWeight.W700)
    val micro = ts(11.0, FontWeight.W700)
    val ringBig = ts(25.0, FontWeight.W800, -1.0)
    val statBig = ts(21.0, FontWeight.W800)
}
