package com.assi.cotizadortransporte

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.assi.cotizadortransporte.ui.AppViewModel
import com.assi.cotizadortransporte.ui.TransportCostApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TransportCostApp(vm)
            }
        }
    }
}
