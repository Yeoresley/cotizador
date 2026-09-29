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
import com.assi.cotizadortransporte.ui.AppViewModel
import com.assi.cotizadortransporte.ui.TransportCostApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val prefs = remember { getSharedPreferences("assi_cotizador_prefs", MODE_PRIVATE) }
            var darkMode by remember {
                mutableStateOf(prefs.getBoolean("dark_mode", systemDark))
            }

            MaterialTheme(
                colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()
            ) {
                TransportCostApp(
                    vm = vm,
                    darkMode = darkMode,
                    onDarkModeChange = { enabled ->
                        darkMode = enabled
                        prefs.edit().putBoolean("dark_mode", enabled).apply()
                    }
                )
            }
        }
    }
}
