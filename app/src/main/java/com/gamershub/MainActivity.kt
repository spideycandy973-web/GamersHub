package com.gamershub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.gamershub.data.AppRepo
import com.gamershub.theme.GameCornerTheme
import com.gamershub.ui.RootShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val repo = AppRepo(applicationContext)
        setContent {
            GameCornerTheme {
                RootShell(repo)
            }
        }
    }
}
