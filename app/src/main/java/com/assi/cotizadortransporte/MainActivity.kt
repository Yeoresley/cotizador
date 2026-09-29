package com.assi.cotizadortransporte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.assi.cotizadortransporte.ui.AppViewModel
import com.assi.cotizadortransporte.ui.TransportCostApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val prefs = remember { getSharedPreferences("cotiruta_prefs", MODE_PRIVATE) }
            var darkMode by remember {
                mutableStateOf(prefs.getBoolean("dark_mode", systemDark))
            }
            var showHelp by remember {
                mutableStateOf(prefs.getBoolean("show_help", true))
            }

            val lightColors = lightColorScheme(
                primary = Color(0xFF0D2A4A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD7E7F7),
                onPrimaryContainer = Color(0xFF071B30),
                secondary = Color(0xFF00866A),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFC3F1E3),
                surface = Color(0xFFF8FAFC),
                surfaceVariant = Color(0xFFE8EEF4)
            )
            val darkColors = darkColorScheme(
                primary = Color(0xFFA8C8E8),
                onPrimary = Color(0xFF0A243F),
                primaryContainer = Color(0xFF143B62),
                secondary = Color(0xFF63D9B8),
                onSecondary = Color(0xFF00382A),
                secondaryContainer = Color(0xFF00513F),
                surface = Color(0xFF101820),
                surfaceVariant = Color(0xFF26313C)
            )

            MaterialTheme(
                colorScheme = if (darkMode) darkColors else lightColors
            ) {
                TransportCostApp(
                    vm = vm,
                    darkMode = darkMode,
                    onDarkModeChange = { enabled ->
                        darkMode = enabled
                        prefs.edit().putBoolean("dark_mode", enabled).apply()
                    },
                    showHelp = showHelp,
                    onShowHelpChange = { enabled ->
                        showHelp = enabled
                        prefs.edit().putBoolean("show_help", enabled).apply()
                    }
                )
            }
        }
    }
}
