package com.iqbalansyor.llm_on_device

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.iqbalansyor.llm_on_device.ui.ChatScreen
import com.iqbalansyor.llm_on_device.ui.theme.LlmondeviceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LlmondeviceTheme {
                ChatScreen()
            }
        }
    }
}