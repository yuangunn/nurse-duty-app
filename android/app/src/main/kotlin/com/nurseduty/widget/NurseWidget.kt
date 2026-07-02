package com.nurseduty.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nurseduty.NurseApp
import com.nurseduty.data.TodayWidget
import com.nurseduty.ui.colorFromHex

class NurseWidget : GlanceAppWidget() {
    companion object {
        private val COMPACT = DpSize(60.dp, 60.dp)
        private val FULL = DpSize(180.dp, 90.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, FULL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = (context.applicationContext as NurseApp).repository.todaySnapshot()
        provideContent { WidgetContent(snap, LocalSize.current.width >= FULL.width) }
    }
}

@Composable
private fun WidgetContent(snap: TodayWidget, full: Boolean) {
    val ink = ColorProvider(Color(0xFF241D13))
    val gray = ColorProvider(Color(0xFF8A7D6A))
    val duty = snap.colorHex?.let { colorFromHex(it) } ?: Color(0xFF94A3B8)
    Column(
        GlanceModifier.fillMaxSize().background(Color(0xFFFFFDF9))
            .clickable(actionStartActivity<com.nurseduty.MainActivity>()).padding(14.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(11.dp).cornerRadius(4.dp).background(duty)) {}
            Spacer(GlanceModifier.width(7.dp))
            Text(snap.dutyName ?: "오늘 근무 없음",
                style = TextStyle(color = ColorProvider(duty), fontWeight = FontWeight.Bold, fontSize = 16.sp))
            Spacer(GlanceModifier.defaultWeight())
            if (snap.pendingMemos > 0) {
                Text("메모 ${snap.pendingMemos}", style = TextStyle(color = ColorProvider(Color(0xFFF59E0B)), fontWeight = FontWeight.Bold, fontSize = 12.sp))
            }
        }
        if (full) {
            Spacer(GlanceModifier.height(6.dp))
            if (snap.total > 0) {
                Text("체크리스트 ${snap.done}/${snap.total}", style = TextStyle(color = ink, fontWeight = FontWeight.Medium, fontSize = 14.sp))
            }
            snap.nextAlarm?.let {
                Spacer(GlanceModifier.height(2.dp))
                Text("⏰ $it", style = TextStyle(color = gray, fontSize = 13.sp))
            }
            Spacer(GlanceModifier.defaultWeight())
            // 빠른 메모: 받아쓰기(즉시 음성인식) + 메모 탭 열기
            Row(GlanceModifier.fillMaxWidth()) {
                Text(
                    "🎤 받아쓰기",
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    modifier = GlanceModifier.background(Color(0xFF3182F6)).cornerRadius(11.dp)
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                        .clickable(actionStartActivity<com.nurseduty.VoiceMemoActivity>()),
                )
                Spacer(GlanceModifier.width(7.dp))
                Text(
                    "메모 열기",
                    style = TextStyle(color = gray, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                    modifier = GlanceModifier.background(Color(0xFFF3ECE0)).cornerRadius(11.dp)
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                        .clickable(actionStartActivity<com.nurseduty.MainActivity>(
                            actionParametersOf(ActionParameters.Key<Int>("tab") to 3),
                        )),
                )
            }
        } else if (snap.total > 0) {
            Spacer(GlanceModifier.height(4.dp))
            Text("${snap.done}/${snap.total}", style = TextStyle(color = ink, fontWeight = FontWeight.Medium, fontSize = 13.sp))
        }
    }
}

class NurseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NurseWidget()
}
