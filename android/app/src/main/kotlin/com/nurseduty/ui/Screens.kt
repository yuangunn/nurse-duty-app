@file:OptIn(ExperimentalMaterial3Api::class)

package com.nurseduty.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nurseduty.data.ChargeRules
import com.nurseduty.data.ChecklistItemEntity
import com.nurseduty.data.DutyProfileEntity
import com.nurseduty.data.QuickMemoEntity
import com.nurseduty.domain.DayKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

fun colorFromHex(hex: String): Color =
    runCatching { Color(("FF" + hex.removePrefix("#")).toLong(16)) }.getOrElse { Color.Gray }

internal fun dutyDisplay(p: DutyProfileEntity, charge: Boolean = false): String =
    if (p.kind == "Custom") p.name
    else "${p.name} · ${Duty.ko(p.kind)}${if (charge && ChargeRules.chargeable(p.kind)) "차지" else ""}"

// ---- reusable ----
@Composable
private fun SoftCard(modifier: Modifier = Modifier, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalNurse.current
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(c.cardBg)
            .border(1.dp, c.cardBorder, RoundedCornerShape(20.dp)).padding(pad),
        content = content,
    )
}

@Composable
internal fun Avatar(kind: String, size: Dp = 46.dp, radius: Dp = 14.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(Duty.gradient(kind)), Alignment.Center) {
        Text(Duty.letter(kind), style = NurseType.rowTitle.copy(fontWeight = FontWeight.W800), color = Color.White)
    }
}

@Composable
fun AppRoot(vm: NurseViewModel) {
    val c = LocalNurse.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var sheetDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var composerOpen by rememberSaveable { mutableStateOf(false) }
    var editProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var rotateMonth by rememberSaveable { mutableStateOf<Int?>(null) }
    val pillCheck = remember { PillCheckHolder() }

    // system back closes the topmost overlay instead of finishing the activity
    BackHandler(enabled = sheetDay != null || composerOpen || showSettings || editProfileId != null || rotateMonth != null) {
        when {
            sheetDay != null -> sheetDay = null
            composerOpen -> composerOpen = false
            editProfileId != null -> editProfileId = null
            rotateMonth != null -> rotateMonth = null
            else -> showSettings = false
        }
    }

    val tabStates = rememberSaveableStateHolder()   // keep per-tab state (month, scroll) across switches
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                tabStates.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> HomeScreen(vm, onGear = { showSettings = true }, onMemo = { tab = 3 })
                    1 -> CalendarScreen(vm, onDay = { sheetDay = it }, onRotate = { rotateMonth = it })
                    2 -> DutyScreen(vm, onEdit = { editProfileId = it })
                    3 -> MemoScreen(vm, onCompose = { composerOpen = true })
                    else -> PillCheckScreen(pillCheck)
                }
                }
            }
            BottomBar(tab) { tab = it }
        }
        if (sheetDay != null) AssignSheet(vm, sheetDay!!, onClose = { sheetDay = null }, onDay = { sheetDay = it })
        if (composerOpen) ComposerSheet(vm, onClose = { composerOpen = false })
        if (editProfileId != null) DutyEditSheet(vm, editProfileId!!, onClose = { editProfileId = null })
        if (rotateMonth != null) RotationSheet(vm, rotateMonth!!, onClose = { rotateMonth = null })
        if (showSettings) SettingsScreen(vm, onBack = { showSettings = false })
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    val c = LocalNurse.current
    val items = listOf(
        Triple("홈", Icons.Filled.Home, Icons.Outlined.Home),
        Triple("근무표", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
        Triple("듀티", Icons.Filled.Style, Icons.Outlined.Style),
        Triple("메모", Icons.Filled.EditNote, Icons.Outlined.EditNote),
        Triple("지참약", Icons.Filled.Medication, Icons.Outlined.Medication),
    )
    Row(
        Modifier.fillMaxWidth().background(c.tabBg).border(0.5.dp, c.tabBorder)
            .navigationBarsPadding()   // 3-button nav must not cover the tab labels (edge-to-edge)
            .padding(top = 9.dp, bottom = 8.dp),
    ) {
        items.forEachIndexed { i, (label, on, off) ->
            val sel = tab == i
            Column(
                Modifier.weight(1f).clickable { onTab(i) }.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(if (sel) on else off, label, tint = if (sel) c.tabSel else c.tabIdle, modifier = Modifier.size(24.dp))
                Text(label, style = NurseType.micro.copy(fontWeight = FontWeight.W600), color = if (sel) c.tabSel else c.tabIdle)
            }
        }
    }
}

// ==================== 지참약 (ward-pillcheck WebView wrap) ====================
// ponytail: WebView-wraps the existing PWA for UI unity now; native reimplement is the
// long-term goal. Attribute search works offline; photo-OCR file upload needs a
// WebChromeClient.onShowFileChooser — add when that path is wanted.
// ?embed=1: the PWA hides its install/open-in-browser banners for trusted embeds.
private const val PILLCHECK_URL = "https://yuangunn.github.io/ward-pillcheck/?embed=1"
private const val PILLCHECK_HOST = "yuangunn.github.io"

/** Lives in AppRoot so the WebView (and the SPA's in-memory state) survives tab switches. */
class PillCheckHolder {
    var webView: WebView? = null
    val loading = mutableStateOf(true)
    val failed = mutableStateOf(false)
    val canBack = mutableStateOf(false)
}

// The PWA sizes html/body/#root with height:100%/100dvh. In an embedded WebView the layout
// viewport resolves those to 0 (blank page) though window.innerHeight is correct — so pin the
// shell to innerHeight px and keep it synced on resize. Idempotent; upstream fix would drop this.
private const val PILLCHECK_HEIGHT_FIX =
    "(function(){if(window.__ndFix)return;window.__ndFix=1;function f(){var h=window.innerHeight+'px';" +
        "[document.documentElement,document.body,document.getElementById('root')].forEach(function(e){if(e)e.style.setProperty('height',h,'important')})}" +
        "f();window.addEventListener('resize',f)})()"

@Composable
fun PillCheckScreen(holder: PillCheckHolder) {
    val c = LocalNurse.current
    val loading by holder.loading
    val failed by holder.failed
    val canBack by holder.canBack

    BackHandler(enabled = canBack) { holder.webView?.goBack() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 54.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("지참약 식별", style = NurseType.h1, color = c.text)
                Text("환자 지참약 낱알 검색", style = NurseType.caption, color = c.sub)
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(c.cardBg)
                    .border(1.dp, c.cardBorder, CircleShape)
                    .clickable { holder.failed.value = false; holder.webView?.reload() },
                Alignment.Center,
            ) { Icon(Icons.Filled.Refresh, "새로고침", tint = c.sub, modifier = Modifier.size(18.dp)) }
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    // reuse the surviving instance across tab switches (detach from the old parent first)
                    holder.webView?.also { (it.parent as? android.view.ViewGroup)?.removeView(it) }
                        ?: WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(v: WebView?, req: android.webkit.WebResourceRequest?): Boolean {
                                    val url = req?.url ?: return false
                                    if (url.host == PILLCHECK_HOST) return false
                                    // Teams handover deep links, GitHub, tel/mailto → hand off to the OS
                                    runCatching {
                                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                    }
                                    return true
                                }
                                override fun onPageStarted(v: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    holder.loading.value = true; holder.failed.value = false
                                }
                                // reveal the SPA on first paint — onPageFinished waits for every subresource
                                override fun onPageCommitVisible(v: WebView?, url: String?) {
                                    holder.loading.value = false; v?.evaluateJavascript(PILLCHECK_HEIGHT_FIX, null)
                                }
                                override fun onPageFinished(v: WebView?, url: String?) {
                                    holder.loading.value = false; holder.canBack.value = v?.canGoBack() == true
                                    v?.evaluateJavascript(PILLCHECK_HEIGHT_FIX, null)
                                }
                                override fun onReceivedError(
                                    v: WebView?, req: android.webkit.WebResourceRequest?, err: android.webkit.WebResourceError?,
                                ) {
                                    if (req?.isForMainFrame == true) { holder.loading.value = false; holder.failed.value = true }
                                }
                            }
                            loadUrl(PILLCHECK_URL)
                            holder.webView = this
                        }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) Box(Modifier.fillMaxSize().background(c.bg), Alignment.Center) {
                CircularProgressIndicator(color = c.tabSel)
            }
            if (failed) Column(
                Modifier.fillMaxSize().background(c.bg).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("연결할 수 없어요", style = NurseType.cardTitle, color = c.text)
                Text("네트워크 연결을 확인한 뒤 다시 시도해 주세요.", style = NurseType.caption, color = c.sub,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(Duty.brandGradient)
                        .clickable { holder.failed.value = false; holder.webView?.reload() }
                        .padding(horizontal = 26.dp, vertical = 13.dp),
                ) { Text("다시 시도", style = NurseType.bodyStrong, color = Color.White) }
            }
        }
    }
}

// ==================== HOME ====================
@Composable
fun HomeScreen(vm: NurseViewModel, onGear: () -> Unit, onMemo: () -> Unit) {
    val c = LocalNurse.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    val items by vm.checklistItems.collectAsStateWithLifecycle()
    val checks by vm.checks.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val memos by vm.memos.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val userName by vm.userName.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { vm.refreshWeather(); onPauseOrDispose { } }

    val todayKey = DayKey.from(LocalDate.now())
    val a = assignments.firstOrNull { it.dayKey == todayKey }
    val profile = a?.let { asn -> profiles.firstOrNull { it.id == asn.dutyProfileId } }
    val kind = profile?.kind ?: "None"
    val charge = a?.charge == true && profile != null && ChargeRules.chargeable(kind)
    val sky = Duty.sky(kind)
    val hasWork = profile != null && kind != "Off"

    // today's checklist (charge item prepended)
    val base = profile?.let { p -> items.filter { it.dutyProfileId == p.id && !it.isArchived }.sortedBy { it.sortOrder } } ?: emptyList()
    val todayItems = (if (charge) listOf(ChargeRules.ITEM_ID to ChargeRules.ITEM_TEXT) else emptyList()) + base.map { it.id to it.text }
    val checkedToday = checks.filter { it.dayKey == todayKey }.map { it.checklistItemId }.toSet()
    val done = todayItems.count { checkedToday.contains(it.first) }
    val total = todayItems.size

    // next alarm: dayOffset-aware (night's 익일 06:00 sorts after 21:30) and not already past
    val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
    val nextAlarm = profile?.let { p ->
        alarms.filter { it.dutyProfileId == p.id && it.enabled }
            .sortedBy { it.dayOffset * 1440 + it.hour * 60 + it.minute }
            .firstOrNull { it.dayOffset * 1440 + it.hour * 60 + it.minute >= nowMin }
    }
    val pending = memos.filter { !it.isDone }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            // HERO
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)).background(sky.grad)) {
                HeroDeco(sky.deco)
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 54.dp, bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(todayDate(), style = NurseType.caption, color = Color.White.copy(0.9f))
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(0.2f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            if (weather.isLive) Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF7CFFB2)))
                            Icon(Icons.Filled.LocationOn, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(12.dp))
                            Text(weather.loc, style = NurseType.label.copy(fontWeight = FontWeight.W600), color = Color.White)
                            Icon(weatherIcon(weather.icon, kind != "Night"), null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Text(weather.temp, style = NurseType.caption.copy(fontWeight = FontWeight.W800), color = Color.White)
                        }
                        Spacer(Modifier.width(7.dp))
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(0.18f)).clickable { onGear() }, Alignment.Center) {
                            Icon(Icons.Filled.Settings, "설정", tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                    }
                    Text(
                        if (userName.isBlank()) sky.greeting else "${userName}님, ${sky.greeting}",
                        style = NurseType.greeting, color = Color.White, modifier = Modifier.padding(top = 8.dp),
                    )
                    if (charge) {
                        Row(Modifier.padding(top = 9.dp).clip(CircleShape).background(Color.White.copy(0.2f))
                            .border(1.dp, Color.White.copy(0.36f), CircleShape).padding(horizontal = 11.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Filled.WorkspacePremium, null, tint = Color(0xFFFFE49B), modifier = Modifier.size(13.dp))
                            Text("차지 간호사 · 팀 리더", style = NurseType.label, color = Color.White)
                        }
                    }
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (hasWork) ProgressRing(done, total, sky.ringHole)
                        else Box(Modifier.size(108.dp).clip(CircleShape).background(Color.White.copy(0.18f)), Alignment.Center) {
                            Text(sky.restIcon, style = NurseType.h1)
                        }
                        Column {
                            Text("오늘의 근무", style = NurseType.caption, color = Color.White.copy(0.82f))
                            Text(if (profile != null) dutyDisplay(profile, charge) else "근무 미배정",
                                style = NurseType.heroShift, color = Color.White, modifier = Modifier.padding(vertical = 2.dp))
                            Text(if (profile != null) (if (kind == "Off") "휴무" else profile.timeText) else "미배정",
                                style = NurseType.label, color = Color.White,
                                modifier = Modifier.clip(CircleShape).background(Color.White.copy(0.2f)).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    if (hasWork && nextAlarm != null) {
                        Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color.White.copy(0.18f)).padding(horizontal = 15.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            Icon(Icons.Filled.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("다음 알람 · ${nextAlarm.label}", style = NurseType.bodyStrong, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text(
                                (if (nextAlarm.dayOffset > 0) "익일 " else "") + "%02d:%02d".format(nextAlarm.hour, nextAlarm.minute),
                                style = NurseType.bodyStrong.copy(fontWeight = FontWeight.W800), color = Color.White,
                            )
                        }
                    }
                }
            }
        }
        item {
            if (hasWork) {
                SoftCard(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 13.dp), verticalAlignment = Alignment.Bottom) {
                        Text("오늘 체크리스트", style = NurseType.cardTitle, color = c.text)
                        Spacer(Modifier.weight(1f))
                        Text(progressNote(done, total), style = NurseType.label, color = c.faint)
                    }
                    todayItems.forEach { (id, text) ->
                        val on = checkedToday.contains(id)
                        val isCharge = id == ChargeRules.ITEM_ID
                        Row(Modifier.fillMaxWidth().toggleable(value = on, role = Role.Checkbox) { vm.toggleCheck(id, todayKey) }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            Icon(if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
                                tint = if (on) Duty.Success else if (c.dark) Color(0xFF3A3324) else Color(0xFFDCD2C0), modifier = Modifier.size(23.dp))
                            Text(text, style = NurseType.body, color = if (on) c.sub else c.text,
                                textDecoration = if (on) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
                            if (isCharge) Text("차지", style = NurseType.micro.copy(fontWeight = FontWeight.W800), color = Duty.GoldInk,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x29EAB308)).padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
            } else {
                SoftCard(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp), pad = 22.dp) {
                    Text(sky.restTitle, style = NurseType.cardTitle, color = c.text, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(sky.restSub, style = NurseType.caption, color = c.sub, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
                }
            }
        }
        item {
            SoftCard(Modifier.padding(16.dp, 14.dp, 16.dp, 24.dp).clickable { onMemo() }, pad = 16.dp) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("대기 메모 ", style = NurseType.caption.copy(fontWeight = FontWeight.W700), color = c.text)
                    Text("${pending.size}", style = NurseType.caption.copy(fontWeight = FontWeight.W700), color = Color(0xFF3182F6))
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = c.faint, modifier = Modifier.size(16.dp))
                }
                pending.take(2).forEach { m ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BedChip(m.bedTag, Color(0xFF3182F6))
                        Text(m.text, style = NurseType.caption, color = c.sub, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroDeco(deco: String) {
    when (deco) {
        "sun", "sunset" -> Box(Modifier.size(126.dp).offset(x = 300.dp, y = (-26).dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFFF6D6), Color(0x8CFFD56E), Color(0x00FFB347)))))
        "night" -> Box(Modifier.size(34.dp).offset(x = 330.dp, y = 14.dp).clip(CircleShape).background(Color(0xFFFBF3DC)))
        "noon" -> Box(Modifier.fillMaxWidth().height(118.dp).offset(y = (-42).dp))
        else -> {}
    }
}

@Composable
private fun ProgressRing(done: Int, total: Int, hole: Color) {
    val pct = if (total > 0) done.toFloat() / total else 0f
    Box(Modifier.size(108.dp), Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val sw = 12.dp.toPx()
            val inset = sw / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
            val tl = Offset(inset, inset)
            drawArc(Color.White.copy(0.30f), -90f, 360f, false, topLeft = tl, size = arcSize, style = Stroke(sw))
            drawArc(Color.White, -90f, pct * 360f, false, topLeft = tl, size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$done", style = NurseType.ringBig, color = Color.White)
                Text("/$total", style = NurseType.rowTitle, color = Color.White.copy(0.6f))
            }
            Text("완료", style = NurseType.micro.copy(fontWeight = FontWeight.W600), color = Color.White.copy(0.85f))
        }
    }
}

// ==================== CALENDAR ====================
@Composable
fun CalendarScreen(vm: NurseViewModel, onDay: (Int) -> Unit, onRotate: (Int) -> Unit) {
    val c = LocalNurse.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().run { year * 100 + monthValue }) }
    val month = YearMonth.of(monthKey / 100, monthKey % 100)
    val byDay = assignments.associateBy { it.dayKey }
    val byId = profiles.associateBy { it.id }
    val todayKey = DayKey.from(LocalDate.now())

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("근무표", style = NurseType.h1, color = c.text)
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(end = 10.dp).size(34.dp).clip(CircleShape).background(c.cardBg)
                    .border(1.dp, c.cardBorder, CircleShape).clickable { onRotate(monthKey) }, Alignment.Center) {
                    Icon(Icons.Filled.Repeat, "로테이션 일괄 입력", tint = c.sub, modifier = Modifier.size(16.dp))
                }
                Icon(Icons.Filled.ChevronLeft, "이전 달", tint = c.sub, modifier = Modifier.minimumInteractiveComponentSize().size(22.dp).clickable { monthKey = month.minusMonths(1).run { year * 100 + monthValue } })
                Text("${month.year}. ${month.monthValue}", style = NurseType.rowTitle, color = c.text, modifier = Modifier.padding(horizontal = 12.dp))
                Icon(Icons.Filled.ChevronRight, "다음 달", tint = c.sub, modifier = Modifier.minimumInteractiveComponentSize().size(22.dp).clickable { monthKey = month.plusMonths(1).run { year * 100 + monthValue } })
            }
        }
        item {
            SoftCard(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp), pad = 12.dp) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                        Text(d, style = NurseType.micro,
                            color = when (i) { 0 -> Color(0xFFE5484D).copy(0.75f); 6 -> Color(0xFF3182F6).copy(0.75f); else -> c.faint },
                            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                val first = month.atDay(1)
                val lead = first.dayOfWeek.value % 7
                val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until 7) {
                            val day = week.getOrNull(i)
                            Box(Modifier.weight(1f).height(48.dp)) {
                                if (day != null) {
                                    val dk = DayKey.from(day)
                                    val asn = byDay[dk]
                                    val p = asn?.let { byId[it.dutyProfileId] }
                                    val col = p?.let { colorFromHex(it.colorHex) }
                                    val isToday = dk == todayKey
                                    val cellBg = when {
                                        p != null && isToday -> col!!
                                        p != null -> col!!.copy(alpha = if (c.dark) 0.24f else 0.14f)
                                        else -> if (c.dark) Color(0xFF1B160E) else Color(0xFFF3EEE4)
                                    }
                                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)).background(cellBg)
                                        .then(when {
                                            isToday && p != null -> Modifier.border(1.5.dp, Color.White, RoundedCornerShape(11.dp))
                                            isToday -> Modifier.border(1.5.dp, c.tabSel, RoundedCornerShape(11.dp))   // unassigned today still stands out
                                            else -> Modifier
                                        })
                                        .clickable { onDay(dk) }) {
                                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            val restDay = day.dayOfWeek.value == 7 || dk in KoreanHolidays.table
                                            val satDay = day.dayOfWeek.value == 6
                                            Text("${day.dayOfMonth}", style = NurseType.label,
                                                color = when {
                                                    p != null -> if (isToday) Color.White else c.text
                                                    isToday -> c.tabSel
                                                    restDay -> Color(0xFFE5484D).copy(0.8f)   // 일요일·공휴일
                                                    satDay -> Color(0xFF3182F6).copy(0.7f)
                                                    else -> c.faint
                                                })
                                            if (p != null) Text(Duty.short(p.kind), style = NurseType.micro.copy(fontWeight = FontWeight.W800),
                                                color = if (isToday) Color.White.copy(0.92f) else col!!)
                                        }
                                        if (asn?.charge == true && p != null && ChargeRules.chargeable(p.kind))
                                            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(6.dp).clip(CircleShape).background(Duty.Gold))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            // stats for the month being viewed (dayKey / 100 == yyyymm)
            val inMonth = assignments.filter { it.dayKey / 100 == monthKey }
            val counts = Duty.KINDS.associateWith { k -> inMonth.count { byId[it.dutyProfileId]?.kind == k } }
            val total = counts.values.sum()
            val chargeDays = inMonth.count { asn -> asn.charge && byId[asn.dutyProfileId]?.let { ChargeRules.chargeable(it.kind) } == true }
            SoftCard(Modifier.padding(16.dp, 14.dp, 16.dp, 24.dp), pad = 18.dp) {
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${month.monthValue}월 근무", style = NurseType.cardTitle, color = c.text)
                    Spacer(Modifier.weight(1f))
                    Text("$total", style = NurseType.statBig, color = c.text)
                    Text("일", style = NurseType.caption.copy(fontWeight = FontWeight.W700), color = c.sub)
                }
                Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(c.track), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Duty.KINDS.forEach { k -> val n = counts[k] ?: 0; if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(Duty.color(k))) }
                }
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Duty.KINDS.filter { (counts[it] ?: 0) > 0 }.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            row.forEach { k ->
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Duty.color(k)))
                                    Text(Duty.ko(k), style = NurseType.caption, color = c.text, modifier = Modifier.weight(1f))
                                    Text("${counts[k]}", style = NurseType.caption.copy(fontWeight = FontWeight.W800), color = c.text)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.WorkspacePremium, null, tint = Duty.Gold, modifier = Modifier.size(15.dp))
                        Text("그 중 차지 근무", style = NurseType.caption, color = c.text, modifier = Modifier.weight(1f))
                        Text("${chargeDays}일", style = NurseType.caption.copy(fontWeight = FontWeight.W800), color = c.text)
                    }
                    // 연속 나이트 경고 — 입력 직후 한눈에 보이는 안전 신호
                    val nightStreak = run {
                        var best = 0; var cur = 0
                        (1..month.lengthOfMonth()).forEach { d ->
                            val k = byDay[monthKey * 100 + d]?.let { byId[it.dutyProfileId]?.kind }
                            if (k == "Night") { cur++; if (cur > best) best = cur } else cur = 0
                        }
                        best
                    }
                    if (nightStreak >= 3) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Duty.Danger.copy(0.10f)).padding(11.dp, 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.NightsStay, null, tint = Duty.Danger, modifier = Modifier.size(15.dp))
                            Text("연속 나이트 ${nightStreak}일 — 휴식을 꼭 챙겨주세요", style = NurseType.caption.copy(fontWeight = FontWeight.W700), color = Duty.Danger)
                        }
                    }
                }
            }
        }
    }
}

// ==================== DUTY ====================
@Composable
fun DutyScreen(vm: NurseViewModel, onEdit: (String) -> Unit) {
    val c = LocalNurse.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val items by vm.checklistItems.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("듀티", style = NurseType.h1, color = c.text)
                    Text("카드를 눌러 시간·알람·체크리스트를 편집하세요", style = NurseType.caption, color = c.sub)
                }
                Spacer(Modifier.weight(1f))
            }
        }
        items(profiles.filter { !it.isArchived }, key = { it.id }) { p ->
            Row(Modifier.padding(16.dp, 11.dp, 16.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.cardBg)
                .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp)).clickable { onEdit(p.id) }.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Avatar(p.kind)
                Column(Modifier.weight(1f)) {
                    Text(dutyDisplay(p), style = NurseType.rowTitle, color = c.text)
                    Text(p.timeText.ifBlank { "—" }, style = NurseType.caption, color = c.sub)
                }
                Chip("알람 ${alarms.count { it.dutyProfileId == p.id }}")
                Chip("체크 ${items.count { it.dutyProfileId == p.id && !it.isArchived }}")
                Icon(Icons.Filled.ChevronRight, "편집", tint = c.faint, modifier = Modifier.size(17.dp))
            }
        }
        item {
            Row(Modifier.padding(16.dp, 14.dp, 16.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(c.chargeInfoBg).border(1.dp, c.chargeInfoBorder, RoundedCornerShape(16.dp)).padding(15.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x29EAB308)), Alignment.Center) {
                    Icon(Icons.Filled.WorkspacePremium, null, tint = Color(0xFFCA9A08), modifier = Modifier.size(17.dp))
                }
                Text("차지(Charge)는 별도 번이 아니라 데이·이브닝·나이트의 팀 리더 역할이에요. 근무 배정할 때 '차지'를 켜면 표시됩니다.",
                    style = NurseType.caption, color = c.sub)
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    val c = LocalNurse.current
    Text(text, style = NurseType.micro, color = c.chipText,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.chipBg).padding(horizontal = 9.dp, vertical = 4.dp))
}

@Composable
private fun BedChip(bed: String, color: Color) =
    Text(bed.ifBlank { "병상" }, style = NurseType.label.copy(fontWeight = FontWeight.W800), color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(color).padding(horizontal = 7.dp, vertical = 3.dp))

// ==================== MEMO ====================
@Composable
fun MemoScreen(vm: NurseViewModel, onCompose: () -> Unit) {
    val c = LocalNurse.current
    val memos by vm.memos.collectAsStateWithLifecycle()
    val pending = memos.filter { !it.isDone }
    val done = memos.filter { it.isDone }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
            item { Text("빠른 메모", style = NurseType.h1, color = c.text, modifier = Modifier.padding(start = 20.dp, top = 54.dp)) }
            item { Text("대기 ${pending.size}", style = NurseType.caption.copy(fontWeight = FontWeight.W800), color = c.sub, modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 9.dp)) }
            items(pending, key = { it.id }) { m -> MemoCard(m, false) { vm.setMemoDone(m, true) } }
            if (done.isNotEmpty()) {
                item { Text("완료 ${done.size}", style = NurseType.caption.copy(fontWeight = FontWeight.W800), color = c.sub, modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 9.dp)) }
                items(done, key = { it.id }) { m -> MemoCard(m, true, onDelete = { vm.deleteMemo(m) }) { vm.setMemoDone(m, false) } }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp).clip(CircleShape).background(Duty.brandGradient).clickable { onCompose() }, Alignment.Center) {
            Icon(Icons.Filled.Edit, "메모 추가", tint = Color.White, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun MemoCard(m: QuickMemoEntity, isDone: Boolean, onDelete: (() -> Unit)? = null, onToggle: () -> Unit) {
    val c = LocalNurse.current
    Row(Modifier.padding(16.dp, 0.dp, 16.dp, 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.cardBg)
        .border(1.dp, c.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp).then(if (isDone) Modifier.alpha(0.62f) else Modifier),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        BedChip(m.bedTag, if (isDone) c.faint else Color(0xFF3182F6))
        Column(Modifier.weight(1f)) {
            Text(m.text, style = NurseType.bodyStrong.copy(fontWeight = FontWeight.W500), color = if (isDone) c.sub else c.text,
                textDecoration = if (isDone) TextDecoration.LineThrough else null)
        }
        Icon(if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle, "완료", tint = if (isDone) Duty.Success else c.faint,
            modifier = Modifier.minimumInteractiveComponentSize().size(23.dp).clickable { onToggle() })
        if (onDelete != null) {
            Icon(Icons.Filled.Close, "메모 삭제", tint = c.faint,
                modifier = Modifier.minimumInteractiveComponentSize().size(19.dp).clickable { onDelete() })
        }
    }
}

// ==================== ASSIGN SHEET ====================
@Composable
fun AssignSheet(vm: NurseViewModel, day: Int, onClose: () -> Unit, onDay: (Int) -> Unit) {
    val c = LocalNurse.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    val active = profiles.filter { !it.isArchived }
    val existing = assignments.firstOrNull { it.dayKey == day }
    var pickId by remember(day) { mutableStateOf(existing?.dutyProfileId) }
    var charge by remember(day) { mutableStateOf(existing?.charge == true) }
    var note by remember(day) { mutableStateOf(existing?.note ?: "") }
    val picked = active.firstOrNull { it.id == pickId }
    val chargeable = picked != null && ChargeRules.chargeable(picked.kind)

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x80140F08)).clickable { onClose() })
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(c.sheetBg).navigationBarsPadding().padding(20.dp, 12.dp, 20.dp, 26.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp).width(40.dp).height(5.dp).clip(CircleShape).background(c.grab))
            Text(dayLabel(day), style = NurseType.caption, color = c.sub)
            Text("근무를 선택하세요", style = NurseType.sheetTitle, color = c.text, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
            active.forEach { p ->
                val sel = pickId == p.id
                Row(Modifier.padding(bottom = 9.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (sel) Color(0xFF3182F6).copy(if (c.dark) 0.14f else 0.07f) else Color.Transparent)
                    .border(2.dp, if (sel) Color(0xFF3182F6) else c.cardBorder, RoundedCornerShape(16.dp))
                    .clickable { pickId = p.id; if (!ChargeRules.chargeable(p.kind)) charge = false }.padding(13.dp, 13.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Avatar(p.kind, 40.dp, 12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(dutyDisplay(p), style = NurseType.rowTitle, color = c.text)
                        Text(p.timeText.ifBlank { "—" }, style = NurseType.label.copy(fontWeight = FontWeight.W600), color = c.sub)
                    }
                    if (sel) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF3182F6), modifier = Modifier.size(24.dp))
                    else Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, c.grab, CircleShape))
                }
            }
            if (chargeable) {
                Row(Modifier.padding(top = 3.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (charge) Duty.Gold.copy(if (c.dark) 0.13f else 0.10f) else Color.Transparent)
                    .border(2.dp, if (charge) Duty.Gold else c.cardBorder, RoundedCornerShape(16.dp))
                    .clickable { charge = !charge }.padding(15.dp, 13.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Icon(Icons.Filled.WorkspacePremium, null, tint = Color(0xFFCA9A08), modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("차지 간호사", style = NurseType.bodyStrong.copy(fontWeight = FontWeight.W700), color = c.text)
                        Text("이 번의 팀 리더 역할", style = NurseType.label.copy(fontWeight = FontWeight.W600), color = c.sub)
                    }
                    MiniSwitch(charge)
                }
            }
            Spacer(Modifier.height(12.dp))
            SheetField(note, { note = it }, "인계 메모 (선택)")
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.clip(RoundedCornerShape(16.dp)).background(c.clearBg).clickable { vm.clearAssign(day); onClose() }.padding(18.dp, 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = Duty.Danger, modifier = Modifier.size(18.dp))
                    Text("지우기", style = NurseType.bodyStrong.copy(fontWeight = FontWeight.W700), color = Duty.Danger)
                }
                Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Duty.brandGradient)
                    .clickable(enabled = pickId != null) { pickId?.let { vm.assign(day, it, charge && chargeable, note.trim().ifBlank { null }); onDay(nextDay(day)) } }
                    .padding(vertical = 16.dp), Alignment.Center) {
                    Text("저장하기", style = NurseType.rowTitle, color = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun MiniSwitch(on: Boolean) {
    Box(Modifier.width(46.dp).height(28.dp).clip(CircleShape).background(if (on) Duty.Gold else Color(0xFFCBD5E1))) {
        Box(Modifier.padding(3.dp).offset(x = if (on) 18.dp else 0.dp).size(22.dp).clip(CircleShape).background(Color.White))
    }
}

// ==================== COMPOSER ====================
@Composable
fun ComposerSheet(vm: NurseViewModel, onClose: () -> Unit) {
    val c = LocalNurse.current
    var bed by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color(0x80140F08)).clickable { onClose() })
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(c.sheetBg).navigationBarsPadding().imePadding().padding(20.dp, 12.dp, 20.dp, 26.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp).width(40.dp).height(5.dp).clip(CircleShape).background(c.grab))
            Text("빠른 메모 추가", style = NurseType.sheetTitle, color = c.text, modifier = Modifier.padding(bottom = 16.dp))
            Text("병상", style = NurseType.label, color = c.sub, modifier = Modifier.padding(bottom = 7.dp))
            SheetField(bed, { bed = it }, "예: 1002:03", KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            Spacer(Modifier.height(14.dp))
            Text("메모", style = NurseType.label, color = c.sub, modifier = Modifier.padding(bottom = 7.dp))
            SheetField(text, { text = it }, "예: 진통제 호소 — 처방 확인")
            Spacer(Modifier.height(18.dp))
            val enabled = text.trim().isNotEmpty()
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (enabled) Duty.brandGradient else Brush.linearGradient(listOf(Color(0xFFB9C7DE), Color(0xFFB9C7DE))))
                .clickable(enabled = enabled) { vm.addMemo(bed.trim(), text.trim()); onClose() }.padding(vertical = 16.dp), Alignment.Center) {
                Text("추가하기", style = NurseType.rowTitle, color = Color.White)
            }
        }
    }
}

@Composable
internal fun SheetField(value: String, onValue: (String) -> Unit, placeholder: String, ko: KeyboardOptions = KeyboardOptions.Default) {
    val c = LocalNurse.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(c.inputBg).border(1.5.dp, c.inputBorder, RoundedCornerShape(13.dp)).padding(15.dp, 13.dp)) {
        BasicTextFieldStyled(value, onValue, placeholder, ko)
    }
}

@Composable
internal fun BasicTextFieldStyled(value: String, onValue: (String) -> Unit, placeholder: String, ko: KeyboardOptions) {
    val c = LocalNurse.current
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onValue, singleLine = true, keyboardOptions = ko,
        textStyle = NurseType.bodyStrong.copy(color = c.text),
        cursorBrush = Brush.linearGradient(listOf(Color(0xFF3182F6), Color(0xFF3182F6))),
        decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, style = NurseType.bodyStrong, color = c.faint); inner() },
        modifier = Modifier.fillMaxWidth(),
    )
}

// ==================== SETTINGS ====================
@Composable
fun SettingsScreen(vm: NurseViewModel, onBack: () -> Unit) {
    val c = LocalNurse.current
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri, context.contentResolver) { ok -> message = if (ok) "내보내기 완료" else "내보내기 실패" }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri, context.contentResolver) { ok -> message = if (ok) "복원 완료" else "복원 실패" }
    }
    message?.let { msg -> LaunchedEffect(msg) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); message = null } }

    val byId = profiles.associateBy { it.id }
    val ym = YearMonth.now().let { it.year * 10000 + it.monthValue * 100 }
    val counts = assignments.filter { it.dayKey in ym..(ym + 99) }.groupingBy { byId[it.dutyProfileId]?.kind ?: "?" }.eachCount()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ChevronLeft, "뒤로", tint = c.text, modifier = Modifier.size(28.dp).clickable { onBack() })
                    Text("설정", style = NurseType.h1, color = c.text)
                }
            }
            item { Text("프로필", style = NurseType.label, color = c.sub, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)) }
            item {
                val userName by vm.userName.collectAsStateWithLifecycle()
                SoftCard(Modifier.padding(horizontal = 16.dp), pad = 14.dp) {
                    Text("이름", style = NurseType.label, color = c.sub, modifier = Modifier.padding(bottom = 7.dp))
                    SheetField(userName, { vm.setUserName(it.take(12)) }, "예: 지현 (홈 인사말에 표시)")
                }
            }
            item { Text("이번 달", style = NurseType.label, color = c.sub, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)) }
            item {
                SoftCard(Modifier.padding(horizontal = 16.dp), pad = 6.dp) {
                    val rows = Duty.KINDS.filter { (counts[it] ?: 0) > 0 }
                    if (rows.isEmpty()) Text("이번 달 배정 없음", style = NurseType.body, color = c.sub, modifier = Modifier.padding(12.dp))
                    rows.forEach { k ->
                        Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Duty.color(k)))
                            Text(Duty.ko(k), style = NurseType.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                            Text("${counts[k]}일", style = NurseType.bodyStrong, color = c.sub)
                        }
                    }
                }
            }
            item { Text("화면", style = NurseType.label, color = c.sub, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)) }
            item {
                val theme by vm.themePref.collectAsStateWithLifecycle()
                SoftCard(Modifier.padding(horizontal = 16.dp), pad = 14.dp) {
                    Text("테마", style = NurseType.label, color = c.sub, modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "시스템", "light" to "라이트", "dark" to "다크").forEach { (v, label) ->
                            val sel = theme == v
                            Text(label, style = NurseType.caption.copy(fontWeight = FontWeight.W700),
                                color = if (sel) Color.White else c.sub,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) Color(0xFF3182F6) else c.chipBg)
                                    .clickable { vm.setThemePref(v) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
            }
            item { Text("백업", style = NurseType.label, color = c.sub, modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)) }
            item {
                SoftCard(Modifier.padding(horizontal = 16.dp), pad = 6.dp) {
                    SettingRow(Icons.Filled.Upload, "내보내기 (JSON)") { exporter.launch("NurseDuty-backup.json") }
                    SettingRow(Icons.Filled.Download, "불러오기 (복원)") { importer.launch(arrayOf("application/json")) }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalNurse.current
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.chipBg), Alignment.Center) { Icon(icon, null, tint = c.sub, modifier = Modifier.size(19.dp)) }
        Text(label, style = NurseType.rowTitle.copy(fontWeight = FontWeight.W600), color = c.text, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = c.faint, modifier = Modifier.size(18.dp))
    }
}

// ---- helpers ----
private fun todayDate(): String {
    val d = LocalDate.now(); val dow = listOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.value - 1]
    return "${d.monthValue}월 ${d.dayOfMonth}일 ($dow)"
}
internal fun dayLabel(dayKey: Int): String { val d = DayKey.toLocalDate(dayKey); return "${d.monthValue}월 ${d.dayOfMonth}일" }
private fun nextDay(dayKey: Int): Int = DayKey.from(DayKey.toLocalDate(dayKey).plusDays(1))
private fun progressNote(done: Int, total: Int): String {
    val rem = total - done
    return if (rem == 0) "모두 완료! 고생하셨어요 🎉" else if (rem == 1) "하나만 더 하면 끝나요!" else "$done/$total 완료"
}
private fun weatherIcon(kind: String, day: Boolean): ImageVector = when (kind) {
    "sun" -> if (day) Icons.Filled.WbSunny else Icons.Filled.NightlightRound
    "cloudsun" -> Icons.Filled.Cloud; "cloud" -> Icons.Filled.Cloud; "fog" -> Icons.Filled.Cloud
    "rain" -> Icons.Filled.WaterDrop; "snow" -> Icons.Filled.AcUnit; "storm" -> Icons.Filled.Bolt
    else -> Icons.Filled.WbSunny
}
