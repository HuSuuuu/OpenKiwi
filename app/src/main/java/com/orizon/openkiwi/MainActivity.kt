package com.orizon.openkiwi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.orizon.openkiwi.core.widget.WidgetActionBus
import com.orizon.openkiwi.ui.navigation.AppNavigation
import com.orizon.openkiwi.ui.theme.OpenKiwiTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_WIDGET_PROMPT = "widget_prompt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchWidgetPrompt(intent)
        enableEdgeToEdge()
        setContent {
            val prefs = OpenKiwiApp.instance.container.userPreferences
            val fontKey by prefs.fontFamily.collectAsState(initial = "default")
            val accentKey by prefs.accentColor.collectAsState(initial = "green")

            OpenKiwiTheme(
                accentColorKey = accentKey,
                fontFamilyKey = fontKey
            ) {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatchWidgetPrompt(intent)
    }

    private fun dispatchWidgetPrompt(intent: Intent?) {
        val p = intent?.getStringExtra(EXTRA_WIDGET_PROMPT)?.trim().orEmpty()
        if (p.isNotBlank()) WidgetActionBus.emit(p)
    }
}
