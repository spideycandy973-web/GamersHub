package com.gamershub.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gamershub.R
import com.gamershub.data.AppRepo
import com.gamershub.data.DeviceStats
import com.gamershub.data.InstalledApp
import com.gamershub.theme.GC
import com.gamershub.theme.GameCornerTheme
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.BrandMark
import com.gamershub.ui.sheets.AdLoadingDialog
import com.gamershub.ui.sheets.AppSelectorSheet
import com.gamershub.ui.sheets.DisplayTuningSheet
import com.gamershub.ui.sheets.ExportPresetSheet
import com.gamershub.ui.sheets.GameReadySheet
import com.gamershub.ui.sheets.LaunchModeSheet
import com.gamershub.ui.sheets.LowEnergySheet
import com.gamershub.ui.sheets.ManagePresetsSheet
import com.gamershub.ui.sheets.PERM_CONFIGS
import com.gamershub.ui.sheets.PermKind
import com.gamershub.ui.sheets.PermissionSheet
import com.gamershub.ui.sheets.SettingsSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class Tab(val icon: ImageVector, val label: String) {
    PROFILE(Icons.Filled.Person, "Profile"),
    LOBBY(Icons.Filled.Gamepad, "Lobby"),
    LABS(Icons.Filled.SportsEsports, "Game-Labs")
}

sealed interface SheetState {
    data object None : SheetState
    data object AppSelector : SheetState
    data object Settings : SheetState
    data class Launch(val app: InstalledApp) : SheetState
    data class Permission(val index: Int) : SheetState
    data object DisplayTuning : SheetState
    data object LowEnergy : SheetState
    data object Ad : SheetState
    data object GameReady : SheetState
    data object ExportPreset : SheetState
    data object ManagePresets : SheetState
}

private const val SHIZUKU_PERMISSION_REQ = 7331

private fun hasShizuku(): Boolean = try {
    rikka.shizuku.Shizuku.pingBinder() &&
        rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
} catch (_: Throwable) {
    false
}

private fun hasUsage(context: Context): Boolean = try {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
    )
    mode == android.app.AppOpsManager.MODE_ALLOWED
} catch (_: Throwable) {
    false
}

@Composable
fun RootShell(repo: AppRepo) {
    val context = LocalContext.current
    var accentIdx by remember { mutableIntStateOf(repo.accentIndex()) }

    GameCornerTheme(accentIdx) {
        val accent = LocalAccent.current
        var tab by remember { mutableStateOf(Tab.LOBBY) }
        var sheet by remember { mutableStateOf<SheetState>(SheetState.None) }
        val uid = remember { repo.userId() }

        // ---- energy + intro ----
        var energy by remember { mutableIntStateOf(repo.energy()) }
        var pendingLaunch by remember { mutableStateOf<InstalledApp?>(null) }
        var showIntro by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            repo.tickEnergy()
            energy = repo.energy()
            while (true) {
                kotlinx.coroutines.delay(30_000)
                repo.tickEnergy()
                energy = repo.energy()
            }
        }
        if (showIntro) {
            IntroAnimation(onDone = { showIntro = false })
        }

        // ---- stateful toggles ----
        val toggles = remember { mutableStateMapOf<String, Boolean>() }
        fun toggleGet(key: String): Boolean = toggles[key] ?: repo.toggle(key).also { toggles[key] = it }
        fun toggleSet(key: String, v: Boolean) {
            toggles[key] = v
            repo.setToggle(key, v)
        }

        val apps by produceState(initialValue = emptyList<InstalledApp>()) {
            value = loadApps(repo)
        }
        val addedVersion = remember { mutableIntStateOf(0) }
        val library = remember(apps, addedVersion.value) {
            val added = repo.added()
            apps.filter { it.pkg in added }
        }

        // ---- live stats ----
        val cpu by produceState(initialValue = DeviceStats.cpu()) {
            while (true) { value = DeviceStats.cpu(); kotlinx.coroutines.delay(2000) }
        }
        val mem by produceState(initialValue = DeviceStats.mem(context)) {
            while (true) { value = DeviceStats.mem(context); kotlinx.coroutines.delay(2000) }
        }
        val storage by produceState(initialValue = DeviceStats.storage()) {
            while (true) { value = DeviceStats.storage(); kotlinx.coroutines.delay(5000) }
        }
        val ping by produceState(initialValue = -1) {
            while (true) { value = DeviceStats.pingMs(); kotlinx.coroutines.delay(5000) }
        }
        val sessionSeconds by produceState(initialValue = 0) {
            while (true) { kotlinx.coroutines.delay(1000); value += 1 }
        }
        val sessionTime = String.format(Locale.US, "%02d:%02d:%02d", sessionSeconds / 3600, (sessionSeconds % 3600) / 60, sessionSeconds % 60)

        var resumeTick by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) {
            resumeTick++
            repo.tickEnergy()
            energy = repo.energy()
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
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        val envScore = (envLevel * 20 + (if (notifGranted) 20 else 0) + (if (ping >= 0) 20 else 0)).coerceIn(0, 100)

        val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { resumeTick++ }

        DisposableEffect(Unit) {
            val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ -> resumeTick++ }
            try { rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener) } catch (_: Throwable) { }
            onDispose {
                try { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener) } catch (_: Throwable) { }
            }
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
                    PermKind.NOTIFICATION -> notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
                        val alive = try { rikka.shizuku.Shizuku.pingBinder() } catch (_: Throwable) { false }
                        if (alive) {
                            try {
                                rikka.shizuku.Shizuku.requestPermission(SHIZUKU_PERMISSION_REQ)
                                Toast.makeText(context, "Accept the Shizuku prompt to grant access", Toast.LENGTH_SHORT).show()
                            } catch (_: Throwable) {
                                Toast.makeText(context, "Shizuku refused the request", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val intent = listOf("moe.shizuku.privileged.api", "moe.shizuku.manager")
                                .firstNotNullOfOrNull { runCatching { context.packageManager.getLaunchIntentForPackage(it) }.getOrNull() }
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
            if (!opened && kind != PermKind.NOTIFICATION && kind != PermKind.SHIZUKU) {
                Toast.makeText(context, "Opening app details — grant the permission there", Toast.LENGTH_LONG).show()
                openAppDetails()
            }
        }

        val displayInfo = run {
            val dm = context.resources.displayMetrics
            "${dm.widthPixels}x${dm.heightPixels} · ${dm.densityDpi}dp"
        }

        Box(Modifier.fillMaxSize().background(GC.Bg)) {
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
                        version = "V2.5.3-25300",
                        sessionTime = sessionTime,
                        cpu = cpu, mem = mem, storage = storage, ping = ping,
                        envLevel = envLevel, envScore = envScore,
                        onPrivilege = { sheet = SheetState.Permission(0) },
                        onSettings = { sheet = SheetState.Settings },
                        onBuyRenew = {
                            repo.setPremium(true)
                            Toast.makeText(context, "Premium active (demo)", Toast.LENGTH_LONG).show()
                        }
                    )
                    Tab.LOBBY -> LobbyScreen(
                        uid = uid,
                        library = library,
                        playMinutes = { pkg -> repo.playMinutes(pkg) },
                        energy = energy,
                        onAddEnergy = { sheet = SheetState.Ad },
                        onAddGames = { sheet = SheetState.AppSelector },
                        onGameTap = { app -> sheet = SheetState.Launch(app) },
                        onGameSpace = {
                            if (library.isEmpty()) sheet = SheetState.AppSelector
                            else sheet = SheetState.Launch(library.first())
                        }
                    )
                    Tab.LABS -> GameLabsScreen(
                        envLevel = envLevel,
                        toggleState = ::toggleGet,
                        onToggle = ::toggleSet,
                        onExportPresets = { sheet = SheetState.ExportPreset },
                        onManagePresets = { sheet = SheetState.ManagePresets },
                        onTuneDisplay = { sheet = SheetState.DisplayTuning },
                        onGameReady = { sheet = SheetState.GameReady },
                        displayInfo = displayInfo
                    )
                }
            }

            if (toggleGet("session")) {
                MonitorStrip(context)
            }

            // Bottom nav
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
                        .padding(5.dp)
                ) {
                    Tab.entries.forEach { t ->
                        val selected = tab == t
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { tab = t }
                                .padding(horizontal = 18.dp, vertical = 11.dp)
                        ) {
                            Icon(t.icon, contentDescription = t.label, tint = if (selected) accent else GC.TextSoft, modifier = Modifier.size(20.dp))
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                Text(t.label, style = MaterialTheme.typography.labelLarge, color = accent, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            // Sheet host
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
                        isGame = { pkg -> apps.find { it.pkg == pkg }?.name?.contains("game", true) == true || repo.playMinutes(pkg) > 60 },
                        playMinutes = { pkg -> repo.playMinutes(pkg) },
                        onToggle = { pkg, on ->
                            repo.setAdded(pkg, on)
                            addedVersion.value++
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Settings -> SettingsSheet(
                        accentIndex = accentIdx,
                        onAccent = {
                            accentIdx = it
                            repo.setAccentIndex(it)
                        },
                        onLanguage = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    is SheetState.Launch -> LaunchModeSheet(
                        app = current.app,
                        playMinutes = repo.playMinutes(current.app.pkg),
                        onLaunch = { gameSpace ->
                            val cost = if (gameSpace) 80 else 40
                            if (repo.premium() || energy >= cost) {
                                if (!repo.premium()) {
                                    repo.setEnergy(energy - cost)
                                    energy = repo.energy()
                                }
                                pendingLaunch = current.app
                                sheet = SheetState.None
                                if (gameSpace) {
                                    context.startActivity(
                                        Intent(context, com.gamershub.GameSpaceActivity::class.java)
                                            .putExtra(com.gamershub.GameSpaceActivity.EXTRA_PKG, current.app.pkg)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } else repo.launch(current.app.pkg)
                            } else {
                                pendingLaunch = current.app
                                sheet = SheetState.LowEnergy
                            }
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
                    SheetState.DisplayTuning -> DisplayTuningSheet(onDismiss = { sheet = SheetState.None })
                    SheetState.LowEnergy -> LowEnergySheet(
                        needed = 80,
                        onWatchAd = { sheet = SheetState.Ad },
                        onBuyPremium = {
                            repo.setPremium(true)
                            Toast.makeText(context, "Premium active — launches are free (demo)", Toast.LENGTH_LONG).show()
                            sheet = SheetState.None
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Ad -> Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                        AdLoadingDialog(onFinished = { reward ->
                            repo.addEnergy(reward)
                            energy = repo.energy()
                            sheet = pendingLaunch?.let { SheetState.Launch(it) } ?: SheetState.None
                        })
                    }
                    SheetState.GameReady -> GameReadySheet(
                        onDone = {
                            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            runCatching {
                                am.runningAppProcesses?.forEach { p ->
                                    if (p.processName != context.packageName) am.killBackgroundProcesses(p.processName)
                                }
                            }
                            Toast.makeText(context, "Device ready — background apps cleared", Toast.LENGTH_SHORT).show()
                            sheet = SheetState.None
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.ExportPreset -> ExportPresetSheet(
                        displayInfo = displayInfo,
                        onExport = { creator, name ->
                            repo.setToggle("last_preset_${name.ifBlank { "preset" }}", true)
                            Toast.makeText(context, "Exported $name by ${creator.ifBlank { "you" }}", Toast.LENGTH_SHORT).show()
                            sheet = SheetState.None
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.ManagePresets -> ManagePresetsSheet(
                        presets = emptyList(),
                        onImport = {
                            Toast.makeText(context, "Pick a preset file to import", Toast.LENGTH_SHORT).show()
                        },
                        onClear = {
                            Toast.makeText(context, "Presets cleared", Toast.LENGTH_SHORT).show()
                            sheet = SheetState.None
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.None -> {}
                }
            }
        }
    }
}

private suspend fun loadApps(repo: AppRepo): List<InstalledApp> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.installedApps() }

/* ------------------------------------------------------------------ */

@Composable
private fun IntroAnimation(onDone: () -> Unit) {
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        phase = 1
        kotlinx.coroutines.delay(1500)
        phase = 2
        kotlinx.coroutines.delay(1400)
        onDone()
    }
    val creatureAlpha by androidx.compose.animation.core.animateFloatAsState(
        when (phase) { 0 -> 0f; 1 -> 1f; else -> 0f }, label = "introA"
    )
    val logoAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (phase >= 2) 1f else 0f, label = "introB"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDone),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.intro_creature),
            contentDescription = null,
            alpha = creatureAlpha,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (phase >= 2) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = logoAlpha }
            ) {
                BrandMark(Modifier.size(100.dp), tint = Color(0xFFE03535))
                Spacer(Modifier.height(12.dp))
                Text("GAME CORNER", style = MaterialTheme.typography.displaySmall, color = GC.Text, fontSize = 34.sp, fontFamily = GC.Display)
            }
        }
    }
}

@Composable
private fun MonitorStrip(context: Context) {
    val stats by produceState(initialValue = Triple("0", "0", "--")) {
        while (true) {
            val cpu = DeviceStats.cpu()
            val mem = DeviceStats.mem(context)
            val batt = DeviceStats.battery(context)
            value = Triple(cpu.pct.toString(), mem.pct.toString(), batt.second.toInt().toString() + "°C")
            kotlinx.coroutines.delay(1200)
        }
    }
    var offX by remember { mutableStateOf(0f) }
    var offY by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 54.dp)
                .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
                .draggable(orientation = Orientation.Horizontal, state = rememberDraggableState { delta -> offX += delta })
                .draggable(orientation = Orientation.Vertical, state = rememberDraggableState { delta -> offY += delta })
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xF0221314))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Drag", tint = Color(0xFFFF3B3B), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            StripChip("CPU", stats.first + "%")
            StripChip("RAM", stats.second + "%")
            StripChip("BAT", stats.third)
            StripChip("FPS", "0")
            StripChip("CLK", SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()))
        }
    }
}

@Composable
private fun StripChip(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 5.dp)) {
        Text(value, style = MaterialTheme.typography.labelMedium, color = Color.White, fontSize = 10.sp)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 8.sp)
    }
}
