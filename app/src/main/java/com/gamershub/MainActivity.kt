package com.gamershub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.gamershub.data.AppRepo
import com.gamershub.ui.RootShell

class MainActivity : ComponentActivity() {

    /** Sheet requested from outside (floating panel button, shortcut, deep link). */
    private var openSheet by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        openSheet = intent?.getStringExtra(EXTRA_OPEN)
        val repo = AppRepo(applicationContext)
        // Bind the Shizuku user service immediately when the grant already exists.
        runCatching { com.gamershub.core.shizuku.Shell.ensure(this) }
        // Self-heal the launcher alias after an app update: exactly one must be enabled.
        runCatching { com.gamershub.data.AppIcon.set(this, com.gamershub.data.AppIcon.current(this)) }
        setContent {
            RootShell(repo, openSheet)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-bind after returning from the Shizuku prompt / app switch.
        runCatching { com.gamershub.core.shizuku.Shell.ensure(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSheet = intent.getStringExtra(EXTRA_OPEN)
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
