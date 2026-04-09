package com.example.fuelconsumptionapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.example.fuelconsumptionapp.ui.FuelDashboardApp
import com.example.fuelconsumptionapp.ui.theme.ROV_ControllerTheme
import android.view.WindowManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ROV_ControllerTheme {
                val bg = MaterialTheme.colorScheme.background
                SideEffect { window.statusBarColor = bg.toArgb() }

                Surface(color = bg) {
                    FuelDashboardApp()
                }
            }
        }
    }
}
