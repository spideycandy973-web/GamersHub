package com.gamershub.ui

import com.gamershub.ui.components.AIcon

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gamershub.GameSpaceActivity
import com.gamershub.data.AppRepo
import com.gamershub.data.DeviceStats
import com.gamershub.data.InstalledApp
import com.gamershub.theme.GC
import com.gamershub.theme.GameCornerTheme
import com.gamershub.theme.LocalAccent
import com.gamershub.theme.accentNow
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
    LOBBY(Icons.Filled.Gamepad, "Lobby"),
    LABS(Icons.Filled.Science, "Game-Labs"),
    TOOLS(Icons.Filled.Widgets, "Tools"),
    PROFILE(Icons.Filled.Person, "Profile")
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
    data object GameReady : SheetState
    data object Ping : SheetState
    data object Gyro : SheetState
    data object Mapping : SheetState
    data object MonitorCfg : SheetState
    data object Mouse : SheetState
    data object Crosshair : SheetState
    data object Screenshot : SheetState
    data object Record : SheetState
    data object Live : SheetState
    data object Graphics : SheetState
    data object Spoof : SheetState
    data object PanelHub : SheetState
}

/** Root shell: tabs, octagon nav, sheets, live state. */
@Composable
fun RootShell(repo: AppRepo, initialSheet: String? = null) {
    val context = LocalContext.current
    var accentIdx by remember { mutableIntStateOf(repo.accentIndex()) }
    // Index 4 means "the colour this slider made"; 0 in prefs means no custom colour yet.
    var customAccent by remember {
        mutableStateOf(
            runCatching {
                val argb = repo.customAccent()
                if (argb == 0) null else Color(argb)
            }.getOrNull()
        )
    }
    var iconIdx by remember { mutableIntStateOf(com.gamershub.data.AppIcon.current(context)) }

    GameCornerTheme(if (customAccent != null) com.gamershub.theme.GC.CUSTOM else accentIdx, customAccent) {
        val accent = LocalAccent.current
        var tab by remember { mutableStateOf(Tab.LOBBY) }
        var sheet by remember { mutableStateOf<SheetState>(SheetState.None) }

        var pendingLaunch by remember { mutableStateOf<InstalledApp?>(null) }
        var showIntro by remember { mutableStateOf(true) }
        // Stateful toggles — the source of truth for animated Toggle composables.
        val toggles = remember { mutableStateMapOf<String, Boolean>() }
        fun toggleGet(key: String): Boolean = when (key) {
            // Read straight from the running service so the switch can never claim
            // "on" while the overlay is actually gone.
            "magic_mapper" -> com.gamershub.overlay.MappingOverlayService.isRunning
            else -> toggles[key] ?: repo.toggle(key).also { toggles[key] = it }
        }
        fun toggleSet(key: String, v: Boolean) {
            // Keys that own a real overlay also switch it here, so the switch is never
            // just a stored flag: turning it on puts the overlay on screen, off removes it.
            runCatching {
                when (key) {
                    "magic_mapper" -> {
                        if (v) com.gamershub.overlay.MappingOverlayService.start(context)
                        else com.gamershub.overlay.MappingOverlayService.stop(context)
                    }
                }
            }
            toggles[key] = v
            repo.setToggle(key, v)
        }

        val apps by produceState(initialValue = emptyList<InstalledApp>()) {
            value = withContext(Dispatchers.IO) { repo.installedApps() }
        }
        val addedVersion = remember { mutableIntStateOf(0) }
        val library = remember(apps, addedVersion.intValue) { apps.filter { repo.isAdded(it.pkg) } }

        // Live stats — resilient IO loops that can never die on error.
        var cpu by remember { mutableStateOf(DeviceStats.cpu()) }
        var mem by remember { mutableStateOf(DeviceStats.mem(context)) }
        var storage by remember { mutableStateOf(DeviceStats.storage()) }
        var ping by remember { mutableStateOf(-1) }
        LaunchedEffect(Unit) {
            // Only the Lobby and Profile show live numbers — sampling while the user is
            // on Tools/Labs/Game Space was pure jank for nothing.
            while (true) {
                if (tab == Tab.LOBBY || tab == Tab.PROFILE) {
                    runCatching {
                        cpu = withContext(Dispatchers.IO) { DeviceStats.cpu() }
                        mem = withContext(Dispatchers.IO) { DeviceStats.mem(context) }
                    }
                }
                delay(3000)
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                runCatching { storage = withContext(Dispatchers.IO) { DeviceStats.storage() } }
                delay(30000)
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                runCatching { ping = withContext(Dispatchers.IO) { DeviceStats.pingMs() } }
                delay(20000)
            }
        }

        // Session timer — handed to Profile as a lambda so the tick never recomposes the shell.
        val sessionSeconds = remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) { delay(1000); sessionSeconds.intValue++ }
        }
        val sessionTime = {
            val s = sessionSeconds.intValue
            String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        }

        // Permission status — refreshed every time the app resumes (this is
        // what makes "grant in Settings → come back" actually update the UI).
        var resumeTick by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) {
            resumeTick++
            // Bind the shell on every resume and stop asking once it is granted.
            com.gamershub.core.shizuku.Shell.ensure(context)
            if (com.gamershub.core.shizuku.Shell.granted()) repo.setFirstRunDone()
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

        // One-time repair of graphics overrides that older builds forced globally.
        // They are the reason some devices showed green/corrupted surfaces in
        // unrelated apps (photo editors, video editors) until a reboot.
        LaunchedEffect(Unit) {
            val prefs = context.getSharedPreferences("gamershub", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("gfx_repair_v634", false)) {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { com.gamershub.core.GfxSafe.repair(context) }
                }
                prefs.edit().putBoolean("gfx_repair_v634", true).apply()
            }
        }

        // Device identity repair — runs on EVERY launch, not once.
        // An older build permanently renamed the phone to a spoof preset
        // ("REDMAGIC 9 Pro" on a Redmi, for example). This is idempotent: it only
        // writes the setting when the current name is one of our presets, or when a
        // spoofed name is still flagged as active.
        LaunchedEffect(Unit) {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.gamershub.core.DeviceSpoofer.cleanStaleSpoofName(context) }
                runCatching {
                    if (com.gamershub.core.DeviceSpoofer.nameSpoofActive(context)) {
                        com.gamershub.core.DeviceSpoofer.restoreDeviceName(context)
                    }
                }
            }
        }

        fun sheetFor(rawKey: String?): SheetState = when (rawKey?.substringBefore('#')) {
            "ping" -> SheetState.Ping
            "gyro" -> SheetState.Gyro
            "mapping" -> SheetState.Mapping
            "monitor" -> SheetState.MonitorCfg
            "mouse" -> SheetState.Mouse
            "crosshair" -> SheetState.Crosshair
            "screenshot" -> SheetState.Screenshot
            "display" -> SheetState.DisplayTuning
            "presets" -> SheetState.Presets
            "activation" -> SheetState.Activation
            "settings" -> SheetState.Settings
            "record" -> SheetState.Record
            "live" -> SheetState.Live
            "graphics" -> SheetState.Graphics
            "spoof" -> SheetState.Spoof
            "panel" -> SheetState.PanelHub
            "apps" -> SheetState.AppSelector
            else -> SheetState.None
        }

        // Deep link from the floating panel / home-screen shortcuts.
        LaunchedEffect(initialSheet) {
            val target = sheetFor(initialSheet)
            if (target != SheetState.None) sheet = target
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
                        // Bind first: if the grant already exists nothing is asked again.
                        com.gamershub.core.shizuku.Shell.ensure(context)
                        val shizukuAlive = com.gamershub.core.shizuku.Shell.running()
                        if (com.gamershub.core.shizuku.Shell.granted()) {
                            com.gamershub.core.shizuku.Shell.ensure(context)
                            Toast.makeText(context, "Shizuku already allowed — linked", Toast.LENGTH_SHORT).show()
                        } else if (shizukuAlive) {
                            val prompted = com.gamershub.core.shizuku.Shell
                                .requestPermission(SHIZUKU_PERMISSION_REQ)
                            Toast.makeText(
                                context,
                                if (prompted) "Accept the Shizuku prompt to grant access"
                                else "Already requested — grant GamersHub in Shizuku",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val intent = listOf(
                                "moe.shizuku.privileged.api",
                                "moe.shizuku.manager"
                            ).firstNotNullOfOrNull { runCatching { context.packageManager.getLaunchIntentForPackage(it) }.getOrNull() }
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                Toast.makeText(context, "Start the Shizuku service, then return", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Install Shizuku to unlock the deep tools", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }.isSuccess
            // If the vendor settings page is missing, fall back to app details
            // where every permission can still be granted by hand.
            if (!opened && kind != PermKind.NOTIFICATION && kind != PermKind.SHIZUKU) {
                Toast.makeText(context, "Opening app details — allow it there", Toast.LENGTH_LONG).show()
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
            com.gamershub.GamersHubApp.step("tab:${tabTarget.name}")
            when (tabTarget) {
                Tab.PROFILE -> ProfileScreen(
                    version = "6.10.2",
                    sessionTime = sessionTime,
                    cpu = cpu, mem = mem, storage = storage, ping = ping,
                    envLevel = envLevel, envScore = envScore,
                    onPrivilege = { sheet = SheetState.Activation },
                    onSettings = { sheet = SheetState.Settings }
                )
                Tab.LOBBY -> LobbyScreen(
                    cpuPct = cpu.pct,
                    library = library,
                    version = "6.10.2",
                    playMinutes = { pkg -> repo.playMinutes(pkg) },
                    libraryCount = library.size,
                    onSelectApps = { sheet = SheetState.AppSelector },
                    onAddGames = { sheet = SheetState.AppSelector },
                    onGameTap = { app -> sheet = SheetState.Launch(app) },
                    onGameSpace = {
                        if (library.isEmpty()) sheet = SheetState.AppSelector
                        else sheet = SheetState.Launch(library.first())
                    }
                )
                Tab.TOOLS -> ToolsScreen(
                    accentIdx = accentIdx,
                    onOpenSheet = { key -> sheet = sheetFor(key) },
                    onOpenGameSpace = { openGameSpace(context, library.firstOrNull()?.pkg ?: "") }
                )
                Tab.LABS -> GameLabsScreen(
                    envLevel = envLevel,
                    toggleState = ::toggleGet,
                    onToggle = ::toggleSet,
                    onExportPresets = {
                        repo.addPreset(displayInfo)
                        Toast.makeText(context, "Look kept: $displayInfo", Toast.LENGTH_SHORT).show()
                    },
                    onManagePresets = { sheet = SheetState.Presets },
                    onTuneDisplay = { sheet = SheetState.DisplayTuning },
                    onGameReady = { sheet = SheetState.GameReady },
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
                        val navBg by animateColorAsState(
                            if (selected) GC.BrownPill else Color.Transparent, tween(220), label = "navBg"
                        )
                        val navTint by animateColorAsState(
                            if (selected) accent else GC.TextDim, tween(220), label = "navTint"
                        )
                        val navInter = remember { MutableInteractionSource() }
                        val navPressed by navInter.collectIsPressedAsState()
                        val navScale by animateFloatAsState(
                            if (navPressed) 0.93f else 1f,
                            spring(dampingRatio = 0.5f, stiffness = 900f), label = "navScale"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .graphicsLayer { scaleX = navScale; scaleY = navScale }
                                .clip(RoundedCornerShape(12.dp))
                                .background(navBg)
                                .clickable(navInter, indication = null) { tab = t }
                                .padding(horizontal = if (selected) 18.dp else 15.dp, vertical = 12.dp)
                        ) {
                            AIcon(
                                t.icon, contentDescription = t.label,
                                tint = navTint,
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

            // Floating session monitor strip (draggable)
            if (toggleGet("session")) {
                MonitorStrip(context)
            }

            // Sheet overlay host
            val current = sheet
            if (current != SheetState.None) com.gamershub.GamersHubApp.step("sheet:$current")
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
                        accentIndex = if (customAccent != null) com.gamershub.theme.GC.CUSTOM else accentIdx,
                        onAccent = { i ->
                            accentIdx = i
                            customAccent = null
                            repo.setAccentIndex(i)
                            repo.setCustomAccent(0)
                        },
                        customColor = customAccent,
                        onCustom = { colour ->
                            customAccent = colour
                            repo.setAccentIndex(com.gamershub.theme.GC.CUSTOM)
                            repo.setCustomAccent(colour.toArgb())
                        },
                        iconIndex = iconIdx,
                        onIcon = { i ->
                            iconIdx = i
                            com.gamershub.data.AppIcon.set(context, i)
                            Toast.makeText(
                                context,
                                "Icon set to ${com.gamershub.data.AppIcon.label(i)} — check your home screen",
                                Toast.LENGTH_SHORT
                            ).show()
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
                            // Launches are free — no energy gate, no ads, no waiting.
                            pendingLaunch = current.app
                            sheet = SheetState.None
                            context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
                                .edit().putString("last_game_pkg", current.app.pkg).apply()
                            if (gameSpace) openGameSpace(context, current.app.pkg)
                            else repo.launch(current.app.pkg)
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    is SheetState.Permission -> PermissionSheet(
                        config = PERM_CONFIGS[current.index.coerceIn(0, PERM_CONFIGS.lastIndex)],
                        granted = when (PERM_CONFIGS[current.index.coerceIn(0, PERM_CONFIGS.lastIndex)].kind) {
                            PermKind.NOTIFICATION -> notifGranted
                            PermKind.OVERLAY -> Settings.canDrawOverlays(context)
                            PermKind.WRITE_SETTINGS -> Settings.System.canWrite(context)
                            PermKind.USAGE -> hasUsage(context)
                            PermKind.SHIZUKU -> hasShizuku()
                        },
                        onAllow = {
                            allowPermission(current.index)
                            if (current.index + 1 < PERM_CONFIGS.size) {
                                sheet = SheetState.Permission(current.index + 1)
                            } else {
                                repo.setFirstRunDone()
                                sheet = SheetState.None
                            }
                        },
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
                    SheetState.GameReady -> com.gamershub.ui.sheets.GameReadySheet(
                        onDone = {
                            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                            runCatching {
                                am.runningAppProcesses?.forEach { p ->
                                    if (p.processName != context.packageName) am.killBackgroundProcesses(p.processName)
                                }
                            }
                            Toast.makeText(context, "Phone ready — hidden apps cleared", Toast.LENGTH_SHORT).show()
                            sheet = SheetState.None
                        },
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Ping -> com.gamershub.ui.sheets.PingOptimizerSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Gyro -> com.gamershub.ui.sheets.GyroCalibrationSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Mapping -> com.gamershub.ui.sheets.MappingEditorSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.MonitorCfg -> com.gamershub.ui.sheets.MonitorConfigSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Mouse -> com.gamershub.ui.sheets.MouseCursorSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Crosshair -> com.gamershub.ui.sheets.CrosshairConfigSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Screenshot -> com.gamershub.ui.sheets.ScreenshotSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Record -> com.gamershub.ui.sheets.RecordSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Live -> com.gamershub.ui.sheets.LiveSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Graphics -> com.gamershub.ui.sheets.GraphicsSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.Spoof -> com.gamershub.ui.sheets.SpoofSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.PanelHub -> com.gamershub.ui.sheets.OverlayHubSheet(
                        onDismiss = { sheet = SheetState.None }
                    )
                    SheetState.None -> {}
                }
            }
        }

        // A failure now leaves a card with the actual words and a way to send them on.
        val crashAtLaunch = remember {
            runCatching {
                val f = java.io.File(context.filesDir, "crash.log")
                if (f.exists() && System.currentTimeMillis() - f.lastModified() < 30 * 60 * 1000L)
                    f.readText().trim().takeLast(2600) else ""
            }.getOrDefault("")
        }
        var showCrash by remember { mutableStateOf(crashAtLaunch.isNotBlank()) }
        if (showCrash) {
            val crashSteps = remember(crashAtLaunch) {
                crashAtLaunch.lineSequence().firstOrNull { it.startsWith("steps:") }.orEmpty()
            }
            val crashHead = remember(crashAtLaunch) {
                crashAtLaunch.lineSequence()
                    .filterNot { it.startsWith("steps:") || it.startsWith("===") }
                    .joinToString("\n")
                    .trim()
            }
            Box(
                Modifier.fillMaxSize().background(Color(0xF2000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(GC.Surface)
                        .padding(18.dp)
                ) {
                    Text(
                        "It stopped here",
                        style = MaterialTheme.typography.titleMedium,
                        color = GC.Text,
                        fontSize = 21.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Copy or send this on — it names the exact step that failed.",
                        color = GC.TextDim,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            crashHead.ifBlank { "no details were written" },
                            color = GC.TextSoft,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .heightIn(max = 230.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                    if (crashSteps.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            crashSteps.removePrefix("steps: ").trim(),
                            color = GC.TextDim,
                            fontSize = 9.sp,
                            maxLines = 3
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(
                            text = "Grab text",
                            onClick = {
                                runCatching {
                                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                    cm?.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "GamersHub error",
                                            crashAtLaunch.ifBlank { crashHead }
                                        )
                                    )
                                    Toast.makeText(context, "Text grabbed", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        BigButton(
                            text = "Pass on",
                            onClick = {
                                runCatching {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "GamersHub error report")
                                        putExtra(Intent.EXTRA_TEXT, crashAtLaunch.ifBlank { crashHead })
                                    }
                                    context.startActivity(
                                        Intent.createChooser(send, "Send the error").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            primary = false
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    BigButton(
                        text = "Shut",
                        onClick = { showCrash = false },
                        modifier = Modifier.fillMaxWidth(),
                        primary = false
                    )
                }
            }
        }

        // The intro must be the LAST thing in the tree: it is opaque, and anything
        // declared after it would cover it completely (that was why it never showed).
        if (showIntro) {
            Box(Modifier.fillMaxSize().zIndex(80f)) {
                IntroAnimation(onDone = { showIntro = false })
            }
        }
    }
}

private fun openGameSpace(context: Context, pkg: String) {
    runCatching {
        context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
            .edit().putString("last_game_pkg", pkg).apply()
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
            Text("Screen Setups", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))
        if (presets.isEmpty()) {
            Text("No saved looks yet. Use Save in the lab.", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 14.sp)
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
private fun hasShizuku(): Boolean = com.gamershub.core.shizuku.Shell.granted()

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

/**
 * Boot intro — the original intro video, decoded into a TextureView.
 *
 * A TextureView (instead of a SurfaceView) renders inside the normal view
 * hierarchy, so the picture can never end up behind the Compose layer (which is
 * what used to leave a black screen while the audio kept playing). If the clip
 * cannot be decoded the animated gamepad art takes over, silently.
 */
@Composable
internal fun IntroAnimation(onDone: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun finish() {
        if (!done) {
            done = true
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(6500)
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { finish() }
    ) {
        if (!failed) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.view.TextureView(ctx).apply {
                        surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                texture: android.graphics.SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                try {
                                    val afd = ctx.resources.openRawResourceFd(
                                        com.gamershub.R.raw.intro_1080_portrait_comp
                                    )
                                    val player = android.media.MediaPlayer()
                                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                    afd.close()
                                    player.setSurface(android.view.Surface(texture))
                                    player.isLooping = false
                                    player.setVolume(1f, 1f)
                                    player.setOnPreparedListener { it.start() }
                                    player.setOnCompletionListener { finish() }
                                    player.setOnErrorListener { _, _, _ ->
                                        failed = true
                                        finish()
                                        true
                                    }
                                    player.prepareAsync()
                                    tag = player
                                    // Centre-crop the 9:16 clip so it fills the screen like the original.
                                    if (width > 0 && height > 0) {
                                        val videoW = 1080f
                                        val videoH = 1920f
                                        val cover = maxOf(width / videoW, height / videoH)
                                        val sx = (cover * videoW) / width
                                        val sy = (cover * videoH) / height
                                        val matrix = android.graphics.Matrix()
                                        matrix.setScale(sx, sy, width / 2f, height / 2f)
                                        runCatching { setTransform(matrix) }
                                    }
                                } catch (_: Throwable) {
                                    failed = true
                                    finish()
                                }
                            }

                            override fun onSurfaceTextureSizeChanged(
                                texture: android.graphics.SurfaceTexture,
                                width: Int,
                                height: Int
                            ) = Unit

                            override fun onSurfaceTextureDestroyed(
                                texture: android.graphics.SurfaceTexture
                            ): Boolean {
                                (tag as? android.media.MediaPlayer)?.let { runCatching { it.release() } }
                                tag = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(com.gamershub.R.drawable.intro_gamepad),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 42.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x66000000))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                "TAP TO SKIP",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xAAFFFFFF),
                fontSize = 10.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

/** Draggable floating session-monitor strip (live chips, red drag handle). */
@Composable
private fun MonitorStrip(context: android.content.Context) {
    val accent = LocalAccent.current
    val stats by produceState(initialValue = Triple("0", "0", "--")) {
        while (true) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val cpu = com.gamershub.data.DeviceStats.cpu()
                val mem = com.gamershub.data.DeviceStats.mem(context)
                val batt = com.gamershub.data.DeviceStats.battery(context)
                Triple(cpu.pct.toString(), mem.pct.toString(), batt.second.toInt().toString() + "°C")
            }
            kotlinx.coroutines.delay(2500)
        }
    }
    var offX by remember { mutableStateOf(0f) }
    var offY by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
                .offset { androidx.compose.ui.unit.IntOffset(offX.roundToInt(), offY.roundToInt()) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> offX += delta }
                )
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> offY += delta }
                )
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xF0221314))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            AIcon(Icons.Filled.Add, contentDescription = "Drag", tint = accentNow(), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            StripChip("CPU", stats.first + "%")
            StripChip("RAM", stats.second + "%")
            StripChip("BAT", stats.third)
            StripChip("FPS", "0")
            StripChip("CLK", java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()))
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
