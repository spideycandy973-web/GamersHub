package com.gamershub.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gamershub.GameSpaceActivity
import com.gamershub.data.AppRepo
import com.gamershub.data.DeviceStats
import com.gamershub.data.InstalledApp
import com.gamershub.theme.GC
import com.gamershub.theme.GameCornerTheme
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.BigButton
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.Grabber
import com.gamershub.ui.sheets.AppSelectorSheet
import com.gamershub.ui.sheets.LaunchModeSheet
import com.gamershub.ui.sheets.PERM_CONFIGS
import rikka.shizuku.Shizuku
import com.gamershub.ui.sheets.PermKind
import com.gamershub.ui.sheets.PermissionSheet
import com.gamershub.ui.sheets.SheetFrame
import com.gamershub.ui.sheets.SettingsSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Tab(val icon: ImageVector, val label: String) {
    PROFILE(Icons.Filled.Person, "Profile"),
    LOBBY(Icons.Filled.Gamepad, "Lobby"),
    LABS(Icons.Filled.Science, "Game-Labs")
}

sealed interface SheetState {
    data object None : SheetState
    data object AppSelector : SheetState
    data object Settings : SheetState
    data object Presets : SheetState
    data class Launch(val app: InstalledApp) : SheetState
    data class Permission(val index: Int) : SheetState
    data object DisplayTuning : SheetState
    data object Activation : SheetState
}

/** Root shell: tabs, octagon nav, sheets, live state. */
@Composable
fun RootShell(repo: AppRepo) {
    val context = LocalContext.current
    var accentIdx by remember { mutableIntStateOf(repo.accentIndex()) }

    GameCornerTheme(accentIdx) {
        val accent = LocalAccent.current
        var tab by remember { mutableStateOf(Tab.LOBBY) }
        var sheet by remember { mutableStateOf<SheetState>(SheetState.None) }
        val uid = remember { repo.userId() }

        // Stateful toggles — the source of truth for animated Toggle composables.
        val toggles = remember { mutableStateMapOf<String, Boolean>() }
        fun toggleGet(key: String): Boolean = toggles[key] ?: repo.toggle(key).also { toggles[key] = it }
        fun toggleSet(key: String, v: Boolean) {
            toggles[key] = v
            repo.setToggle(key, v)
        }

        val apps by produceState(initialValue = emptyList<InstalledApp>()) {
            value = withContext(Dispatchers.IO) { repo.installedApps() }
        }
        val addedVersion = remember { mutableIntStateOf(0) }
        val library = remember(apps, addedVersion.intValue) { apps.filter { repo.isAdded(it.pkg) } }

        // Live stats
        var cpu by remember { mutableStateOf(DeviceStats.cpu()) }
        var mem by remember { mutableStateOf(DeviceStats.mem(context)) }
        var storage by remember { mutableStateOf(DeviceStats.storage()) }
        var ping by remember { mutableStateOf(-1) }
        LaunchedEffect(Unit) {
            while (true) {
                cpu = DeviceStats.cpu(); mem = DeviceStats.mem(context); storage = DeviceStats.storage()
                delay(3000)
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                ping = withContext(Dispatchers.IO) { DeviceStats.pingMs() }
                delay(8000)
            }
        }

        // Session timer
        var sessionSeconds by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) { delay(1000); sessionSeconds++ }
        }
        val sessionTime = String.format("%02d:%02d:%02d", sessionSeconds / 3600, (sessionSeconds % 3600) / 60, sessionSeconds % 60)

        // Permission status — refreshed every time the app resumes (this is
        // what makes "grant in Settings → come back" actually update the UI).
        var resumeTick by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) {
            resumeTick++
            onPauseOrDispose { }
        }
        val envLevel = run {
            resumeTick
            var level = 0
            if (Settings.canDrawOverlays(context)) level++
            if (Settings.System.canWrite(context)) level++
            if (hasUsage(context)) level++
            level
        }
        val notifGranted = run {
            resumeTick
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        val envScore = (envLevel * 20 + (if (notifGranted) 20 else 0) + (if (ping >= 0) 20 else 0)).coerceIn(0, 100)

        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { resumeTick++ }

        // Refresh permission state when the user answers the Shizuku prompt.
        DisposableEffect(Unit) {
            val listener = Shizuku.OnRequestPermissionResultListener { _, _ -> resumeTick++ }
            try {
                Shizuku.addRequestPermissionResultListener(listener)
            } catch (_: Throwable) { }
            onDispose {
                try {
                    Shizuku.removeRequestPermissionResultListener(listener)
                } catch (_: Throwable) { }
            }
        }

        val displayInfo = run {
            val dm = context.resources.displayMetrics
            "${dm.widthPixels}x${dm.heightPixels} · ${dm.densityDpi}dp"
        }

        LaunchedEffect(Unit) {
            if (!repo.firstRunDone()) sheet = SheetState.Permission(0)
        }

        fun openAppDetails() {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        fun allowPermission(index: Int) {
            val kind = PERM_CONFIGS[index].kind
            val opened = runCatching {
                when (kind) {
                    PermKind.NOTIFICATION -> notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    PermKind.OVERLAY -> context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    PermKind.WRITE_SETTINGS -> context.startActivity(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    PermKind.USAGE -> context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    PermKind.SHIZUKU -> {
                        val shizukuAlive = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
                        if (shizukuAlive) {
                            // Real consent prompt from the Shizuku service — nothing
                            // is granted implicitly any more.
                            try {
                                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQ)
                                Toast.makeText(context, "Accept the Shizuku prompt to grant access", Toast.LENGTH_SHORT).show()
                            } catch (_: Throwable) {
                                Toast.makeText(context, "Shizuku is running but refused the request", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val intent = listOf(
                                "moe.shizuku.privileged.api",
                                "moe.shizuku.manager"
                            ).firstNotNullOfOrNull { runCatching { context.packageManager.getLaunchIntentForPackage(it) }.getOrNull() }
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                Toast.makeText(context, "Start the Shizuku service, then come back", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Install Shizuku to enable the advanced service", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }.isSuccess
            // If the vendor settings page is missing, fall back to app details
            // where every permission can still be granted by hand.
            if (!opened && kind != PermKind.NOTIFICATION && kind != PermKind.SHIZUKU) {
                Toast.makeText(context, "Opening app details — grant the permission there", Toast.LENGTH_LONG).show()
                openAppDetails()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(GC.Bg)
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (slideInHorizontally(tween(360)) { it / 6 } + fadeIn(tween(360)) + scaleIn(tween(360), initialScale = 0.97f))
                        .togetherWith(slideOutHorizontally(tween(240)) { -it / 8 } + fadeOut(tween(240)))
                },
                label = "tab",
                modifier = Modifier.fillMaxSize()
            ) { tabTarget ->
            when (tabTarget) {
                Tab.PROFILE -> ProfileScreen(
                    uid = uid,
                    version = "V4.1.0-21",
                    sessionTime = sessionTime,
                    cpu = cpu, mem = mem, storage = storage, ping = ping,
                    envLevel = envLevel, envScore = envScore,
                    onPrivilege = { sheet = SheetState.Activation },
                    onSettings = { sheet = SheetState.Settings }
                )
                Tab.LOBBY -> LobbyScreen(
                    uid = uid,
                    cpuPct = cpu.pct,
                    library = library,
                    version = "V4.1.0-21",
                    playMinutes = { pkg -> repo.playMinutes(pkg) },
                    onAddGames = { sheet = SheetState.AppSelector },
                    onGameTap = { app -> sheet = SheetState.Launch(app) },
                    onGameSpace = {
                        if (library.isEmpty()) sheet = SheetState.AppSelector
                        else openGameSpace(context, library.first().pkg)
                    }
                )
                Tab.LABS -> GameLabsScreen(
                    envLevel = envLevel,
                    toggleState = ::toggleGet,
                    onToggle = ::toggleSet,
                    onExportPresets = {
                        repo.addPreset(displayInfo)
                        Toast.makeText(context, "Preset exported: $displayInfo", Toast.LENGTH_SHORT).show()
                    },
                    onManagePresets = { sheet = SheetState.Presets },
                    onTuneDisplay = { sheet = SheetState.DisplayTuning },
                    displayInfo = displayInfo
                )
            }
            }

            // Floating octagon bottom nav
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CutCornerShape(topStart = 15.dp, topEnd = 15.dp, bottomStart = 15.dp, bottomEnd = 15.dp))
                        .background(GC.SurfaceHi)
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Tab.entries.forEach { t ->
                        val selected = t == tab
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) GC.BrownPill else Color.Transparent)
                                .clickable { tab = t }
                                .padding(horizontal = if (selected) 18.dp else 15.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                t.icon, contentDescription = t.label,
                                tint = if (selected) accent else GC.TextDim,
                                modifier = Modifier.size(23.dp)
                            )
                            if (selected) {
                                Spacer(Modifier.width(9.dp))
                                Text(t.label, style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // Sheet overlay host
            val current = sheet
            AnimatedVisibility(
                visible = current != SheetState.None,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.fillMaxSize()
            ) {
                when (current) {
                    SheetState.AppSelector -> AppSelectorSheet(
                        apps = apps,
                        added = repo.added(),
                        isGame = { pkg ->
                            try {
                                context.packageManager.getApplicationInfo(pkg, 0)
                                    .category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                            } catch (_: Throwable) { false }
                        },
                        playMinutes = { pkg -> repo.playMinutes(pkg) },
                        onToggle = { pkg, v ->
                            repo.setAdded(pkg, v)
                            addedVersion.intValue++
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Settings -> SettingsSheet(
                        accentIndex = accentIdx,
                        onAccent = { i ->
                            accentIdx = i
                            repo.setAccentIndex(i)
                        },
                        onLanguage = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Presets -> PresetsSheet(
                        presets = repo.presets(),
                        onClear = { repo.clearPresets(); sheet = SheetState.None },
                        onDismiss = { sheet = SheetState.None }
                    )
                    is SheetState.Launch -> LaunchModeSheet(
                        app = current.app,
                        version = "App",
                        playMinutes = repo.playMinutes(current.app.pkg),
                        onLaunch = { gameSpace ->
                            if (gameSpace) openGameSpace(context, current.app.pkg)
                            else repo.launch(current.app.pkg)
                            sheet = SheetState.None
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    is SheetState.Permission -> PermissionSheet(
                        config = PERM_CONFIGS[current.index],
                        granted = when (PERM_CONFIGS[current.index].kind) {
                            PermKind.NOTIFICATION -> notifGranted
                            PermKind.OVERLAY -> Settings.canDrawOverlays(context)
                            PermKind.WRITE_SETTINGS -> Settings.System.canWrite(context)
                            PermKind.USAGE -> hasUsage(context)
                            PermKind.SHIZUKU -> hasShizuku()
                        },
                        onAllow = { allowPermission(current.index) },
                        onLater = {
                            if (current.index + 1 < PERM_CONFIGS.size) sheet = SheetState.Permission(current.index + 1)
                            else { repo.setFirstRunDone(); sheet = SheetState.None }
                        },
                        onOpenDetail = { openAppDetails() }
                    )
                    SheetState.DisplayTuning -> com.gamershub.ui.sheets.DisplayTuningSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Activation -> com.gamershub.ui.sheets.ActivationSheet(
                        envLevel = envLevel,
                        shizukuActive = hasShizuku(),
                        onOpenShizuku = {
                            runCatching {
                                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    ?: context.packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                            }
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.None -> {}
                }
            }
        }
    }
}

private fun openGameSpace(context: Context, pkg: String) {
    runCatching {
        context.startActivity(
            Intent(context, GameSpaceActivity::class.java)
                .putExtra(GameSpaceActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun PresetsSheet(presets: List<String>, onClear: () -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Display Presets", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))
        if (presets.isEmpty()) {
            Text("No saved presets yet. Use Export in Game-Labs.", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
        } else {
            presets.forEach { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GC.SurfaceHi)
                        .padding(13.dp)
                ) {
                    Text(line, style = MaterialTheme.typography.labelLarge, color = GC.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        BigButton("Clear All", onClear, Modifier.fillMaxWidth(), primary = false, leadingIcon = Icons.Filled.Delete, textColor = accent)
    }
}

private const val SHIZUKU_PERMISSION_REQ = 7331

/** True only when the Shizuku service is alive AND the user has granted US access. */
private fun hasShizuku(): Boolean = try {
    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
} catch (_: Throwable) {
    false
}

private fun hasUsage(context: Context): Boolean = try {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
        appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    mode == AppOpsManager.MODE_ALLOWED
} catch (_: Throwable) {
    false
}
