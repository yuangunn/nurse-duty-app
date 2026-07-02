package com.nurseduty.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.util.concurrent.ListenableFuture
import com.nurseduty.domain.WearState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Glanceable "오늘 근무" tile — duty name/color, hours, progress, next alarm. Tap opens the app. */
class DutyTileService : TileService() {
    // onTileRequest arrives on the main thread; the DataClient task also completes there,
    // so blocking would deadlock. Resolve the future from a background coroutine instead.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch { completer.set(runCatching { buildTile() }.getOrElse { emptyTile() }) }
            "dutyTile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion("1").build())
            "dutyTileRes"
        }

    private suspend fun readState(): WearState? {
        val items = Wearable.getDataClient(this).dataItems.await()
        return try {
            items.firstOrNull { it.uri.path == "/today" }
                ?.let { DataMapItem.fromDataItem(it).dataMap.getString("json") }
                ?.let { runCatching { WearState.decode(it) }.getOrNull() }
        } finally {
            items.release()
        }
    }

    private fun dutyColor(kind: String): Int = when (kind) {
        "Day" -> 0xFF5BA0FF; "Mid" -> 0xFF2DD4BF; "Evening" -> 0xFFFBBF4D
        "Night" -> 0xFF8487F0; "Off" -> 0xFFB4C0D0; else -> 0xFFAEA9C2
    }.toInt()

    private suspend fun buildTile(): TileBuilders.Tile {
        val s = readState()
        val title = s?.dutyName?.let { "$it · ${WearStyle.ko(s.kind)}" } ?: "근무 미배정"
        val kind = s?.kind ?: "None"
        val done = s?.checklist?.count { it.checked } ?: 0
        val total = s?.checklist?.size ?: 0
        val sub = buildList {
            if (s?.charge == true) add("👑 차지")
            s?.timeText?.takeIf { kind != "None" }?.let { add(it) }
        }.joinToString(" · ").ifBlank { "근무표에서 배정하세요" }
        val foot = buildList {
            if (total > 0) add("$done/$total 완료")
            s?.nextAlarm?.let { add("⏰ $it") }
        }.joinToString("  ")

        val openApp = ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearActivity::class.java.name)
                            .build(),
                    ).build(),
            ).build()

        val column = LayoutElementBuilders.Column.Builder()
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openApp).build())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, title)
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(argb(dutyColor(kind)))
                    .build(),
            )
            .addContent(
                Text.Builder(this, sub)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .build(),
            )
            .apply {
                if (foot.isNotBlank()) {
                    addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
                    addContent(
                        Text.Builder(this@DutyTileService, foot)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                            .setColor(argb(0xB3FFFFFF.toInt()))
                            .build(),
                    )
                }
            }
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setFreshnessIntervalMillis(30 * 60_000L)   // self-heal ceiling; pushes refresh it sooner
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(column).build())
                            .build(),
                    ).build(),
            ).build()
    }

    private fun emptyTile(): TileBuilders.Tile = TileBuilders.Tile.Builder().setResourcesVersion("1").build()
}

/** Refreshes the tile whenever the phone pushes a new /today snapshot. */
class WatchDataListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        if (events.any { it.dataItem.uri.path == "/today" }) {
            TileService.getUpdater(this).requestUpdate(DutyTileService::class.java)
        }
    }
}
