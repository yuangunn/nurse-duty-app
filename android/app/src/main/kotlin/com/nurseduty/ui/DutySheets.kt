package com.nurseduty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseduty.data.AlarmEntity
import com.nurseduty.data.DutyProfileEntity
import com.nurseduty.domain.DayKey
import java.time.YearMonth
import java.util.UUID

// ==================== 듀티 편집 ====================
private class AlarmRow(a: AlarmEntity?) {
    val id: String = a?.id ?: UUID.randomUUID().toString()
    var label by mutableStateOf(a?.label ?: "")
    var hour by mutableStateOf(a?.hour?.toString() ?: "7")
    var minute by mutableStateOf(a?.minute?.let { "%02d".format(it) } ?: "00")
    var dayOffset by mutableStateOf(a?.dayOffset ?: 0)
    var enabled by mutableStateOf(a?.enabled != false)
}

private fun offsetLabel(off: Int) = when { off < 0 -> "전날"; off > 0 -> "익일"; else -> "당일" }

@Composable
fun DutyEditSheet(vm: NurseViewModel, profileId: String, onClose: () -> Unit) {
    val c = LocalNurse.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val allAlarms by vm.alarms.collectAsStateWithLifecycle()
    val allItems by vm.checklistItems.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == profileId } ?: return

    // snapshot the form once per open; saving writes everything back in one shot
    var timeText by remember(profileId) { mutableStateOf(profile.timeText) }
    val alarmRows = remember(profileId) {
        mutableStateListOf<AlarmRow>().apply {
            allAlarms.filter { it.dutyProfileId == profileId }.sortedBy { it.sortOrder }.forEach { add(AlarmRow(it)) }
        }
    }
    val removedAlarmIds = remember(profileId) { mutableStateListOf<String>() }
    val archivedItemIds = remember(profileId) { mutableStateListOf<String>() }
    val addedItems = remember(profileId) { mutableStateListOf<String>() }
    var newItemText by remember(profileId) { mutableStateOf("") }
    val existingItems = allItems.filter { it.dutyProfileId == profileId && !it.isArchived }.sortedBy { it.sortOrder }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x80140F08)).clickable { onClose() })
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(c.sheetBg)
                .navigationBarsPadding().imePadding().padding(20.dp, 12.dp, 20.dp, 26.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp).width(40.dp).height(5.dp).clip(CircleShape).background(c.grab))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(profile.kind, 40.dp, 12.dp)
                Column {
                    Text(dutyDisplay(profile), style = NurseType.sheetTitle, color = c.text)
                    Text("알람·체크리스트 편집", style = NurseType.caption, color = c.sub)
                }
            }

            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
                if (profile.kind != "Off") {
                    Text("근무 시간", style = NurseType.label, color = c.sub, modifier = Modifier.padding(bottom = 7.dp))
                    SheetField(timeText, { timeText = it }, "예: 06:00 – 14:00")
                    Spacer(Modifier.height(16.dp))
                }

                Row(Modifier.fillMaxWidth().padding(bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("알람", style = NurseType.label, color = c.sub, modifier = Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(9.dp)).background(c.chipBg)
                            .clickable { alarmRows.add(AlarmRow(null)) }.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, tint = c.chipText, modifier = Modifier.size(13.dp))
                        Text("알람 추가", style = NurseType.micro.copy(fontWeight = FontWeight.W700), color = c.chipText)
                    }
                }
                alarmRows.forEach { row ->
                    Column(
                        Modifier.padding(bottom = 9.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(c.inputBg).border(1.dp, c.inputBorder, RoundedCornerShape(14.dp)).padding(12.dp, 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.weight(1f)) { BasicTextFieldStyled(row.label, { row.label = it }, "알람 이름", KeyboardOptions.Default) }
                            Icon(
                                Icons.Filled.Close, "알람 삭제", tint = c.faint,
                                modifier = Modifier.size(18.dp).clickable { removedAlarmIds.add(row.id); alarmRows.remove(row) },
                            )
                        }
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.width(40.dp)) {
                                BasicTextFieldStyled(row.hour, { if (it.length <= 2) row.hour = it.filter(Char::isDigit) }, "시", KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                            Text(":", style = NurseType.bodyStrong, color = c.sub)
                            Box(Modifier.width(40.dp)) {
                                BasicTextFieldStyled(row.minute, { if (it.length <= 2) row.minute = it.filter(Char::isDigit) }, "분", KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                            Text(
                                offsetLabel(row.dayOffset), style = NurseType.micro.copy(fontWeight = FontWeight.W700), color = c.chipText,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.chipBg)
                                    .clickable { row.dayOffset = when (row.dayOffset) { -1 -> 0; 0 -> 1; else -> -1 } }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.clickable { row.enabled = !row.enabled }) { MiniSwitch(row.enabled) }
                        }
                    }
                }
                if (alarmRows.isEmpty()) Text("알람 없음", style = NurseType.caption, color = c.faint, modifier = Modifier.padding(bottom = 9.dp))

                Text("체크리스트", style = NurseType.label, color = c.sub, modifier = Modifier.padding(top = 7.dp, bottom = 7.dp))
                existingItems.filter { it.id !in archivedItemIds }.forEach { item ->
                    Row(
                        Modifier.padding(bottom = 7.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(c.inputBg).border(1.dp, c.inputBorder, RoundedCornerShape(12.dp)).padding(12.dp, 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.text, style = NurseType.body, color = c.text, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Close, "항목 삭제", tint = c.faint, modifier = Modifier.size(17.dp).clickable { archivedItemIds.add(item.id) })
                    }
                }
                addedItems.forEachIndexed { i, text ->
                    Row(
                        Modifier.padding(bottom = 7.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(c.inputBg).border(1.dp, Color(0xFF3182F6).copy(0.4f), RoundedCornerShape(12.dp)).padding(12.dp, 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text, style = NurseType.body, color = c.text, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Close, "항목 삭제", tint = c.faint, modifier = Modifier.size(17.dp).clickable { addedItems.removeAt(i) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) { SheetField(newItemText, { newItemText = it }, "새 체크 항목") }
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(if (newItemText.isBlank()) c.chipBg else Color(0xFF3182F6))
                            .clickable(enabled = newItemText.isNotBlank()) { addedItems.add(newItemText.trim()); newItemText = "" }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                    ) { Text("추가", style = NurseType.label.copy(fontWeight = FontWeight.W800), color = if (newItemText.isBlank()) c.faint else Color.White) }
                }
            }

            Box(
                Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Duty.brandGradient)
                    .clickable {
                        val alarms = alarmRows.mapIndexed { i, r ->
                            AlarmEntity(
                                r.id, profileId, r.label.ifBlank { "알람" },
                                (r.hour.toIntOrNull() ?: 0).coerceIn(0, 23),
                                (r.minute.toIntOrNull() ?: 0).coerceIn(0, 59),
                                r.dayOffset, r.enabled, i,
                            )
                        }
                        vm.applyDutyEdit(profile, timeText.trim(), alarms, removedAlarmIds.toSet(), addedItems.toList(), archivedItemIds.toSet())
                        onClose()
                    }
                    .padding(vertical = 16.dp),
                Alignment.Center,
            ) { Text("저장하기", style = NurseType.rowTitle, color = Color.White) }
        }
    }
}

// ==================== 로테이션 일괄 입력 ====================
@Composable
fun RotationSheet(vm: NurseViewModel, monthKey: Int, onClose: () -> Unit) {
    val c = LocalNurse.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val active = profiles.filter { !it.isArchived }
    val month = YearMonth.of(monthKey / 100, monthKey % 100)
    val pattern = remember { mutableStateListOf<DutyProfileEntity>() }
    var startDay by remember { mutableStateOf("1") }
    var overwrite by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x80140F08)).clickable { onClose() })
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(c.sheetBg)
                .navigationBarsPadding().imePadding().padding(20.dp, 12.dp, 20.dp, 26.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp).width(40.dp).height(5.dp).clip(CircleShape).background(c.grab))
            Text("${month.monthValue}월 로테이션 일괄 입력", style = NurseType.sheetTitle, color = c.text)
            Text("근무를 순서대로 탭해 패턴을 만들면 월말까지 반복 적용돼요.", style = NurseType.caption, color = c.sub, modifier = Modifier.padding(top = 3.dp, bottom = 15.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                active.forEach { p ->
                    Column(
                        Modifier.clip(RoundedCornerShape(13.dp)).clickable { pattern.add(p) }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Avatar(p.kind, 42.dp, 13.dp)
                        Text(Duty.ko(p.kind).ifBlank { p.name }, style = NurseType.micro, color = c.sub)
                    }
                }
            }

            // pattern preview
            Row(
                Modifier.padding(top = 13.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(c.inputBg).border(1.dp, c.inputBorder, RoundedCornerShape(14.dp)).padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pattern.isEmpty()) {
                    Text("패턴 없음 — 위에서 근무를 탭하세요", style = NurseType.caption, color = c.faint, modifier = Modifier.weight(1f))
                } else {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        pattern.take(9).forEach { Avatar(it.kind, 26.dp, 8.dp) }
                        if (pattern.size > 9) Text("+${pattern.size - 9}", style = NurseType.caption, color = c.sub)
                    }
                }
                if (pattern.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Backspace, "하나 지우기", tint = c.sub,
                        modifier = Modifier.size(20.dp).clickable { pattern.removeAt(pattern.size - 1) },
                    )
                }
            }

            Row(Modifier.padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${month.monthValue}월", style = NurseType.bodyStrong, color = c.text)
                Box(Modifier.width(44.dp)) {
                    SheetField(startDay, { if (it.length <= 2) startDay = it.filter(Char::isDigit) }, "1", KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text("일부터 월말까지", style = NurseType.bodyStrong, color = c.text)
            }

            Row(
                Modifier.padding(top = 13.dp).fillMaxWidth().clickable { overwrite = !overwrite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("기존 배정 덮어쓰기", style = NurseType.bodyStrong.copy(fontWeight = FontWeight.W700), color = c.text)
                    Text("끄면 이미 배정된 날은 건너뛰어요", style = NurseType.label, color = c.sub)
                }
                MiniSwitch(overwrite)
            }

            Box(
                Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (pattern.isEmpty()) Color(0xFFB9C7DE) else Color(0xFF3182F6))
                    .clickable(enabled = pattern.isNotEmpty()) {
                        val day = (startDay.toIntOrNull() ?: 1).coerceIn(1, month.lengthOfMonth())
                        vm.assignPattern(month.atDay(day), month.atEndOfMonth(), pattern.map { it.id }, overwrite)
                        onClose()
                    }
                    .padding(vertical = 16.dp),
                Alignment.Center,
            ) { Text("적용하기", style = NurseType.rowTitle, color = Color.White) }
        }
    }
}
