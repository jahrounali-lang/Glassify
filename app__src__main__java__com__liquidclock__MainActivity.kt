package com.liquidclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.liquidclock.ui.StudioApp
import com.liquidclock.ui.StudioTheme
import com.liquidclock.ui.StudioViewModel

class MainActivity : ComponentActivity() {
    private val vm: StudioViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StudioTheme { StudioApp(vm) } }
    }
}
