package com.wakaroute.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wakaroute.app.ui.RootScreen
import com.wakaroute.app.ui.theme.WakaRouteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val services = (application as WakaRouteApplication).services

        setContent {
            WakaRouteTheme {
                RootScreen(services = services)
            }
        }
    }
}
