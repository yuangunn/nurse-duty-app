package com.nurseduty

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nurseduty.ui.AppRoot
import com.nurseduty.ui.NurseTheme
import com.nurseduty.ui.NurseViewModel

class MainActivity : ComponentActivity() {
    companion object {
        /** Widget/complication deep-link: which tab to show. Consumed by AppRoot. */
        val tabRequest = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun consumeTabExtra(intent: android.content.Intent?) {
        intent?.getIntExtra("tab", -1)?.takeIf { it >= 0 }?.let { tabRequest.value = it }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        consumeTabExtra(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()   // targetSdk 35 forces it on Android 15; opting in keeps older versions consistent
        consumeTabExtra(intent)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val vm: NurseViewModel = viewModel()
            val theme by vm.themePref.collectAsStateWithLifecycle()
            NurseTheme(
                dark = when (theme) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                },
            ) { AppRoot(vm) }
        }
    }
}
