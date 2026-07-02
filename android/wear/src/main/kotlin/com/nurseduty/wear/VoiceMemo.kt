package com.nurseduty.wear

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import android.graphics.drawable.Icon
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.nurseduty.domain.WearCommand
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Watch-face complication tap → straight into system dictation → quick memo via the DataItem
 * queue (delivered to the phone even if it's unreachable right now). No UI of our own.
 */
class WearVoiceMemoActivity : ComponentActivity() {

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            ?: RemoteInput.getResultsFromIntent(res.data ?: Intent())?.getCharSequence(MEMO_KEY)?.toString()?.trim()
        saveAndFinish(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "빠른 메모")
        }
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // no recognizer — fall back to the system remote input (voice among its options)
            val fallback = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putRemoteInputsExtra(
                fallback,
                listOf(RemoteInput.Builder(MEMO_KEY).setLabel("메모").setAllowFreeFormInput(true).build()),
            )
            runCatching { speech.launch(fallback) }.onFailure { finish() }
        }
    }

    private fun saveAndFinish(text: String?) {
        if (text.isNullOrBlank()) { finish(); return }
        lifecycleScope.launch {
            runCatching {
                val cmd = WearCommand.AddMemo(UUID.randomUUID().toString(), "", text)
                val req = PutDataMapRequest.create("/cmd/${UUID.randomUUID()}").apply {
                    dataMap.putString("json", WearCommand.encode(cmd))
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(this@WearVoiceMemoActivity).putDataItem(req).await()
            }
            Toast.makeText(applicationContext, "메모 저장: $text", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private companion object { const val MEMO_KEY = "memo" }
}

/** Watch-face shortcut: a mic complication that jumps straight into dictation. */
class MemoComplicationService : ComplicationDataSourceService() {

    private fun tap(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, WearVoiceMemoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun mono() = MonochromaticImage.Builder(
        Icon.createWithResource(this, R.drawable.ic_mic),
    ).build()

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        listener.onComplicationData(build(request.complicationType))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type)

    private fun build(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.MONOCHROMATIC_IMAGE ->
            MonochromaticImageComplicationData.Builder(
                monochromaticImage = mono(),
                contentDescription = PlainComplicationText.Builder("빠른 메모 받아쓰기").build(),
            ).setTapAction(tap()).build()
        ComplicationType.SHORT_TEXT ->
            ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("메모").build(),
                contentDescription = PlainComplicationText.Builder("빠른 메모 받아쓰기").build(),
            ).setMonochromaticImage(mono()).setTapAction(tap()).build()
        else -> null
    }
}
