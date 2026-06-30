@file:OptIn(ExperimentalMaterial3Api::class)

package com.nurseduty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseduty.data.AlarmEntity
import com.nurseduty.data.ChecklistItemEntity
import com.nurseduty.data.DutyProfileEntity
import com.nurseduty.data.QuickMemoEntity
import com.nurseduty.domain.DayKey
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

fun colorFromHex(hex: String): Color =
    runCatching { Color(("FF" + hex.removePrefix("#")).toLong(16)) }.getOrElse { Color.Gray }

val swatches = listOf("#4F86C6", "#E8A33D", "#3B4A6B", "#9AA0A6", "#C0504D", "#5B9279", "#8E6BAD", "#D16BA5")

@Composable
fun AppRoot(vm: NurseViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            val tabs = listOf("오늘" to Icons.Default.Checklist, "근무표" to Icons.Default.CalendarMonth,
                "듀티" to Icons.Default.Group, "메모" to Icons.Default.EditNote)
            tabs.forEachIndexed { i, (label, icon) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i },
                    icon = { Icon(icon, label) }, label = { Text(label) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> TodayScreen(vm)
                1 -> CalendarScreen(vm)
                2 -> DutyTab(vm)
                else -> MemoScreen(vm)
            }
        }
    }
}

@Composable
fun TodayScreen(vm: NurseViewModel) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    val items by vm.checklistItems.collectAsStateWithLifecycle()
    val checks by vm.checks.collectAsStateWithLifecycle()
    val todayKey = DayKey.from(LocalDate.now())
    val profile = assignments.firstOrNull { it.dayKey == todayKey }
        ?.let { a -> profiles.firstOrNull { it.id == a.dutyProfileId } }
    val checkedToday = checks.filter { it.dayKey == todayKey }.map { it.checklistItemId }.toSet()

    Scaffold(topBar = { TopAppBar(title = { Text(LocalDate.now().toString()) }) }) { pad ->
        if (profile == null) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Text("오늘 근무가 없습니다. 근무표에서 배정하세요.", color = Color.Gray)
            }
        } else {
            val list = items.filter { it.dutyProfileId == profile.id && !it.isArchived }.sortedBy { it.sortOrder }
            val done = list.count { checkedToday.contains(it.id) }
            Column(Modifier.padding(pad).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(colorFromHex(profile.colorHex)))
                    Spacer(Modifier.width(8.dp))
                    Text(profile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    if (list.isNotEmpty()) Text("$done/${list.size}", color = Color.Gray)
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn {
                    items(list, key = { it.id }) { item ->
                        val on = checkedToday.contains(item.id)
                        ListItem(
                            headlineContent = {
                                Text(item.text, textDecoration = if (on) TextDecoration.LineThrough else null,
                                    color = if (on) Color.Gray else Color.Unspecified)
                            },
                            leadingContent = {
                                Icon(if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                                    tint = if (on) MaterialTheme.colorScheme.primary else Color.Gray)
                            },
                            modifier = Modifier.clickable { vm.toggleCheck(item.id, todayKey) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(vm: NurseViewModel) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var pick by remember { mutableStateOf<LocalDate?>(null) }
    val byDay = assignments.associateBy { it.dayKey }
    val profileById = profiles.associateBy { it.id }

    Scaffold(topBar = { TopAppBar(title = { Text("근무표") }) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "이전") }
                Spacer(Modifier.weight(1f))
                Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "다음") }
            }
            Row {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Text(it, Modifier.weight(1f), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            }
            val first = month.atDay(1)
            val lead = first.dayOfWeek.value % 7
            val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            Column {
                cells.chunked(7).forEach { week ->
                    Row {
                        for (i in 0 until 7) {
                            val day = week.getOrNull(i)
                            Box(Modifier.weight(1f).height(54.dp).padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (day != null) Color(0x11000000) else Color.Transparent)
                                .clickable(enabled = day != null) { pick = day }) {
                                if (day != null) {
                                    val a = byDay[DayKey.from(day)]
                                    val p = a?.let { profileById[it.dutyProfileId] }
                                    Column(Modifier.fillMaxSize().padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${day.dayOfMonth}", style = MaterialTheme.typography.bodySmall)
                                        if (p != null) {
                                            Text(p.name.take(4), style = MaterialTheme.typography.labelSmall,
                                                color = colorFromHex(p.colorHex), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val day = pick
    if (day != null) {
        val current = byDay[DayKey.from(day)]
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text("${day.monthValue}월 ${day.dayOfMonth}일") },
            text = {
                Column {
                    profiles.filter { !it.isArchived }.forEach { p ->
                        Row(Modifier.fillMaxWidth().clickable {
                            vm.assign(DayKey.from(day), p.id); pick = null
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(colorFromHex(p.colorHex)))
                            Spacer(Modifier.width(8.dp))
                            Text(p.name)
                            if (current?.dutyProfileId == p.id) {
                                Spacer(Modifier.weight(1f)); Icon(Icons.Default.Check, null)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = null }) { Text("닫기") } },
            dismissButton = {
                if (current != null) TextButton(onClick = { vm.clearAssign(DayKey.from(day)); pick = null }) { Text("지우기") }
            },
        )
    }
}

@Composable
fun DutyTab(vm: NurseViewModel) {
    var selected by remember { mutableStateOf<String?>(null) }
    if (selected == null) DutyListScreen(vm) { selected = it }
    else DutyDetailScreen(vm, selected!!) { selected = null }
}

@Composable
fun DutyListScreen(vm: NurseViewModel, onOpen: (String) -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val items by vm.checklistItems.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("듀티") }) },
        floatingActionButton = { FloatingActionButton(onClick = { editing = true }) { Icon(Icons.Default.Add, "추가") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            items(profiles.filter { !it.isArchived }, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    leadingContent = { Box(Modifier.size(16.dp).clip(CircleShape).background(colorFromHex(p.colorHex))) },
                    supportingContent = {
                        Text("알람 ${alarms.count { it.dutyProfileId == p.id }} · 체크 ${items.count { it.dutyProfileId == p.id && !it.isArchived }}")
                    },
                    trailingContent = {
                        IconButton(onClick = { vm.archiveProfile(p) }) { Icon(Icons.Default.Archive, "보관") }
                    },
                    modifier = Modifier.clickable { onOpen(p.id) },
                )
                HorizontalDivider()
            }
        }
    }
    if (editing) ProfileDialog(null, onDismiss = { editing = false }) { name, color ->
        vm.saveProfile(null, name, color); editing = false
    }
}

@Composable
fun DutyDetailScreen(vm: NurseViewModel, profileId: String, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val items by vm.checklistItems.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == profileId } ?: return
    val myAlarms = alarms.filter { it.dutyProfileId == profileId }.sortedBy { it.sortOrder }
    val myItems = items.filter { it.dutyProfileId == profileId && !it.isArchived }.sortedBy { it.sortOrder }

    var alarmEdit by remember { mutableStateOf<AlarmEntity?>(null) }
    var addingAlarm by remember { mutableStateOf(false) }
    var addingChecklist by remember { mutableStateOf(false) }
    var profileEdit by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(profile.name) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } },
            actions = { TextButton(onClick = { profileEdit = true }) { Text("편집") } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { SectionHeader("알람") }
            items(myAlarms, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text(a.label.ifBlank { "(이름 없음)" }) },
                    supportingContent = { if (a.dayOffset > 0) Text("익일", color = Color(0xFFE8A33D)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d:%02d".format(a.hour, a.minute))
                            IconButton(onClick = { vm.deleteAlarm(a) }) { Icon(Icons.Default.Delete, "삭제") }
                        }
                    },
                    modifier = Modifier.clickable { alarmEdit = a },
                )
                HorizontalDivider()
            }
            item {
                TextButton(onClick = { addingAlarm = true }, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Default.Add, null); Text(" 알람 추가")
                }
            }
            item { SectionHeader("체크리스트") }
            items(myItems, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.text) },
                    trailingContent = { IconButton(onClick = { vm.archiveChecklistItem(c) }) { Icon(Icons.Default.Delete, "삭제") } },
                )
                HorizontalDivider()
            }
            item {
                TextButton(onClick = { addingChecklist = true }, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Default.Add, null); Text(" 체크리스트 추가")
                }
            }
        }
    }

    alarmEdit?.let { a ->
        AlarmDialog(a, onDismiss = { alarmEdit = null }) { saved -> vm.saveAlarm(saved); alarmEdit = null }
    }
    if (addingAlarm) {
        AlarmDialog(vm.newAlarm(profileId, myAlarms.size), onDismiss = { addingAlarm = false }) { saved ->
            vm.saveAlarm(saved); addingAlarm = false
        }
    }
    if (addingChecklist) {
        TextDialog("체크리스트 추가", "", onDismiss = { addingChecklist = false }) { text ->
            vm.saveChecklistItem(vm.newChecklistItem(profileId, text, myItems.size)); addingChecklist = false
        }
    }
    if (profileEdit) {
        ProfileDialog(profile, onDismiss = { profileEdit = false }) { name, color ->
            vm.saveProfile(profile.id, name, color); profileEdit = false
        }
    }
}

@Composable
fun MemoScreen(vm: NurseViewModel) {
    val memos by vm.memos.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val pending = memos.filter { !it.isDone }
    val done = memos.filter { it.isDone }

    Scaffold(
        topBar = { TopAppBar(title = { Text("빠른 메모") }) },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Edit, "메모") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (memos.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { Text("라운딩 중 환자 요청을 적어두세요.", color = Color.Gray) } }
            }
            if (pending.isNotEmpty()) item { SectionHeader("대기 ${pending.size}") }
            items(pending, key = { it.id }) { m -> MemoRow(m, vm) }
            if (done.isNotEmpty()) item { SectionHeader("완료") }
            items(done, key = { it.id }) { m -> MemoRow(m, vm) }
        }
    }
    if (adding) MemoDialog(onDismiss = { adding = false }) { bed, text -> vm.addMemo(bed, text); adding = false }
}

@Composable
private fun MemoRow(m: QuickMemoEntity, vm: NurseViewModel) {
    ListItem(
        headlineContent = { Text(m.text, color = if (m.isDone) Color.Gray else Color.Unspecified) },
        overlineContent = { if (m.bedTag.isNotBlank()) Text(m.bedTag, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        leadingContent = {
            IconButton(onClick = { vm.setMemoDone(m, !m.isDone) }) {
                Icon(if (m.isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, "완료")
            }
        },
        trailingContent = { IconButton(onClick = { vm.deleteMemo(m) }) { Icon(Icons.Default.Delete, "삭제") } },
    )
    HorizontalDivider()
}

@Composable
private fun SectionHeader(text: String) =
    Text(text, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = Color.Gray)

// ---- dialogs ----

@Composable
fun ProfileDialog(existing: DutyProfileEntity?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableStateOf(existing?.colorHex ?: swatches.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "새 듀티" else "듀티 편집") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Row {
                    swatches.forEach { hex ->
                        Box(Modifier.padding(4.dp).size(30.dp).clip(CircleShape).background(colorFromHex(hex))
                            .clickable { color = hex }, Alignment.Center) {
                            if (hex == color) Icon(Icons.Default.Check, null, tint = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), color) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun AlarmDialog(alarm: AlarmEntity, onDismiss: () -> Unit, onSave: (AlarmEntity) -> Unit) {
    var label by remember { mutableStateOf(alarm.label) }
    var hour by remember { mutableStateOf(alarm.hour.toString()) }
    var minute by remember { mutableStateOf("%02d".format(alarm.minute)) }
    var nextDay by remember { mutableStateOf(alarm.dayOffset > 0) }
    var enabled by remember { mutableStateOf(alarm.enabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("알람") },
        text = {
            Column {
                OutlinedTextField(label, { label = it }, label = { Text("이름 (예: 인계)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("시") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
                    Spacer(Modifier.width(8.dp)); Text(":"); Spacer(Modifier.width(8.dp))
                    OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("분") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(nextDay, { nextDay = it }); Spacer(Modifier.width(8.dp)); Text("다음 날(익일)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(enabled, { enabled = it }); Spacer(Modifier.width(8.dp)); Text("알람 켜기")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 0
                val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                if (label.isNotBlank()) onSave(alarm.copy(label = label.trim(), hour = h, minute = m,
                    dayOffset = if (nextDay) 1 else 0, enabled = enabled))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text("할 일") }) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text.trim()) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun MemoDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var bed by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("빠른 메모") },
        text = {
            Column {
                OutlinedTextField(bed, { bed = it }, label = { Text("병상 (예: 1001:01)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, label = { Text("메모 (예: 진통제 호소)") })
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(bed.trim(), text.trim()) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
