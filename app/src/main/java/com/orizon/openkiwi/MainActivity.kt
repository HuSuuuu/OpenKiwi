package com.orizon.openkiwi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.orizon.openkiwi.ui.navigation.AppNavigation
import com.orizon.openkiwi.ui.theme.OpenKiwiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenKiwiTheme {
                AppNavigation()
            }
        }
    }
}
