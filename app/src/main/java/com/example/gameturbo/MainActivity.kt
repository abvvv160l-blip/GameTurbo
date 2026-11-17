package com.example.gameturbo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Chỉ theo dõi nhiệt độ / Shizuku khi app đang hiển thị
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.monitor() }
        }

        setContent {
            GameTurboTheme {
                GameTurboApp(vm)
            }
        }
    }
}
