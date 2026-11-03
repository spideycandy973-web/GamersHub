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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.gamershub.data.AppRepo
import com.gamershub.data.DeviceStats
import com.gamershub.data.InstalledApp
import com.gamershub.theme.GC
import com.gamershub.ui.sheets.AppSelectorSheet
import com.gamershub.ui.sheets.LowEnergySheet
import com.gamershub.ui.sheets.LaunchModeSheet
import com.gamershub.ui.sheets.PERM_CONFIGS
import com.gamershub.ui.sheets.PermissionSheet
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
    data object LowEnergy : SheetState
    data object AppSelector : SheetState
    data class Launch(val app: InstalledApp) : SheetState
    data class Permission(val index: Int) : SheetState
}

/** Root shell: tabs, octagon bottom nav and the sheet overlay host. */
@Composable
fun RootShell(repo: AppRepo) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.LOBBY) }
    var sheet by remember { mutableStateOf<SheetState>(SheetState.None) }
    var uid by remember { mutableStateOf(repo.userId()) }
    var energy by remember { mutableStateOf(repo.energy()) }
    var premium by remember { mutableStateOf(repo.premium()) }

    val apps by produceState(initialValue = emptyList<InstalledApp>()) {
        value = withContext(Dispatchers.IO) { repo.installedApps() }
    }
    val library = remember(apps, sheet) { apps.filter { repo.isAdded(it.pkg) } }

    // Live device stats (3 s cadence) + ping (background).
    var cpu by remember { mutableStateOf(DeviceStats.cpu()) }
    var mem by remember { mutableStateOf(DeviceStats.mem(context)) }
    var storage by remember { mutableStateOf(DeviceStats.storage()) }
    var ping by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        while (true) {
            cpu = DeviceStats.cpu()
            mem = DeviceStats.mem(context)
            storage = DeviceStats.storage()
            delay(3000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            ping = withContext(Dispatchers.IO) { DeviceStats.pingMs() }
            delay(8000)
        }
    }

    // Environment level: overlay + write-settings + usage access (real checks).
    val envLevel = envLevel(context)
    val envScore = (envLevel * 20 + (if (hasNotification(context)) 20 else 0) + (if (ping >= 0) 20 else 0))
        .coerceIn(0, 100)

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* state read live on next recomposition */ }

    // First-run permission flow, one sheet at a time (like the reference).
    LaunchedEffect(Unit) {
        if (!repo.firstRunDone()) {
            sheet = SheetState.Permission(0)
        }
    }

    fun allowPermission(index: Int) {
        val kind = PERM_CONFIGS[index].kind
        try {
            when (kind) {
                com.gamershub.ui.sheets.PermKind.NOTIFICATION ->
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                com.gamershub.ui.sheets.PermKind.OVERLAY -> context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                com.gamershub.ui.sheets.PermKind.WRITE_SETTINGS -> context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                com.gamershub.ui.sheets.PermKind.USAGE -> context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                com.gamershub.ui.sheets.PermKind.SHIZUKU -> {
                    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "Install Shizuku to enable advanced service", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (_: Throwable) {
            Toast.makeText(context, "Opening settings failed — grant it manually", Toast.LENGTH_SHORT).show()
        }
        val next = index + 1
        if (next < PERM_CONFIGS.size) sheet = SheetState.Permission(next)
        else {
            repo.setFirstRunDone()
            sheet = SheetState.None
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(GC.Bg)
    ) {
        when (tab) {
            Tab.PROFILE -> ProfileScreen(
                uid = uid,
                version = "V4.0.0-20",
                premium = premium,
                cpu = cpu, mem = mem, storage = storage, ping = ping,
                envLevel = envLevel, envScore = envScore,
                onBuyRenew = { premium = true; repo.setPremium(true) },
                onPrivilege = { sheet = SheetState.Permission(0) },
                onSettings = { sheet = SheetState.Permission(0) }
            )
            Tab.LOBBY -> LobbyScreen(
                uid = uid,
                energy = energy,
                library = library,
                version = "V4.0.0-20",
                playMinutes = { pkg -> repo.playMinutes(pkg) },
                onAddGames = { sheet = SheetState.AppSelector },
                onGameTap = { app -> sheet = SheetState.Launch(app) },
                onGameSpace = {
                    if (library.isEmpty()) sheet = SheetState.AppSelector
                    else if (energy >= 80) sheet = SheetState.Launch(library.first())
                    else sheet = SheetState.LowEnergy
                }
            )
            Tab.LABS -> GameLabsScreen(
                activeCount = listOf("dns", "session", "quality", "clarity", "touch").count { repo.toggle(it) },
                toggleState = { key -> repo.toggle(key) },
                onToggle = { key, v -> repo.setToggle(key, v) }
            )
        }

        // Floating octagon bottom nav
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CutCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(GC.SurfaceHi)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Tab.entries.forEach { t ->
                    val selected = t == tab
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (selected) GC.BrownPill else Color.Transparent)
                            .clickable { tab = t }
                            .padding(horizontal = if (selected) 20.dp else 16.dp, vertical = 13.dp)
                    ) {
                        Icon(
                            t.icon, contentDescription = t.label,
                            tint = if (selected) GC.Orange else GC.TextDim,
                            modifier = Modifier.size(26.dp)
                        )
                        if (selected) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                t.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = GC.Orange,
                                fontSize = 18.sp
                            )
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
                SheetState.LowEnergy -> LowEnergySheet(
                    onWatchAd = {
                        repo.addEnergy(120); energy = repo.energy(); sheet = SheetState.None
                    },
                    onBuyPremium = {
                        premium = true; repo.setPremium(true); sheet = SheetState.None
                    },
                    onDismiss = { sheet = SheetState.None }
                )
                SheetState.AppSelector -> AppSelectorSheet(
                    apps = apps,
                    added = repo.added(),
                    isGame = { pkg ->
                        try {
                            val ai = context.packageManager.getApplicationInfo(pkg, 0)
                            ai.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                        } catch (_: Throwable) { false }
                    },
                    playMinutes = { pkg -> repo.playMinutes(pkg) },
                    onToggle = { pkg, v ->
                        repo.setAdded(pkg, v)
                        // trigger recomposition of library
                        sheet = SheetState.AppSelector
                    },
                    onDismiss = { sheet = SheetState.None }
                )
                is SheetState.Launch -> LaunchModeSheet(
                    app = current.app,
                    version = "App",
                    playMinutes = repo.playMinutes(current.app.pkg),
                    energy = energy,
                    onLaunch = { gameSpace ->
                        val cost = if (gameSpace) 80 else 40
                        repo.addEnergy(-cost)
                        energy = repo.energy()
                        repo.launch(current.app.pkg)
                        sheet = SheetState.None
                    },
                    onLowEnergy = { sheet = SheetState.LowEnergy },
                    onDismiss = { sheet = SheetState.None }
                )
                is SheetState.Permission -> PermissionSheet(
                    config = PERM_CONFIGS[current.index],
                    onAllow = { allowPermission(current.index) },
                    onLater = {
                        if (current.index + 1 < PERM_CONFIGS.size) sheet = SheetState.Permission(current.index + 1)
                        else { repo.setFirstRunDone(); sheet = SheetState.None }
                    },
                    onOpenDetail = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                SheetState.None -> {}
            }
        }
    }
}

private fun envLevel(context: Context): Int {
    var level = 0
    if (Settings.canDrawOverlays(context)) level++
    if (Settings.System.canWrite(context)) level++
    if (hasUsage(context)) level++
    return level
}

private fun hasNotification(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun hasUsage(context: Context): Boolean = try {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
        )
    }
    mode == AppOpsManager.MODE_ALLOWED
} catch (_: Throwable) {
    false
}
