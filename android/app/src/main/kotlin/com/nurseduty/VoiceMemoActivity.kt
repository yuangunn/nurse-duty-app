package com.nurseduty

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

/**
 * Transparent trampoline: widget mic button → system speech recognizer → quick memo, no UI of
 * our own. The recognizer app owns the RECORD_AUDIO permission, so we need none.
 */
class VoiceMemoActivity : ComponentActivity() {

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (!text.isNullOrBlank()) {
            val app = application as NurseApp
            app.scope.launch { runCatching { app.repository.addMemo("", text) } }
            Toast.makeText(applicationContext, "메모 저장: $text", Toast.LENGTH_SHORT).show()
        }
        finish()
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
            // no recognizer on this device/emulator — fall back to the in-app composer
            Toast.makeText(applicationContext, "음성 인식을 사용할 수 없어 메모 탭을 엽니다", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java).putExtra("tab", 3)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
    }
}
