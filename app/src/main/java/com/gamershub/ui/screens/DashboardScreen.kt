package com.gamershub.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gamershub.R
import com.gamershub.core.boost.BoostState
import com.gamershub.core.overlay.FloatingWidgetService
import com.gamershub.core.record.RecorderController
import com.gamershub.core.shizuku.ShizukuManager
import com.gamershub.di.AppContainer
import com.gamershub.ui.Routes
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.LineChart
import com.gamershub.ui.components.RadialGauge
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(container: AppContainer, nav: NavHostController) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val stats by container.monitor.stats.collectAsStateWithLifecycle()
    val boostState by container.boost.state.collectAsStateWithLifecycle()
    val shizukuStatus by container.shizuku.status.collectAsStateWithLifecycle()
    val shellReady by container.shizuku.shellReady.collectAsStateWithLifecycle()
    val recState by container.recorder.state.collectAsStateWithLifecycle()
    val history by container.analytics.history.collectAsStateWithLifecycle()
    val widgetRunning by FloatingWidgetService.isRunning.collectAsStateWithLifecycle()

    // Recorder events as toasts
    LaunchedEffect(Unit) {
        container.recorder.events.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Android 13+: notification permission for engine/capture status
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // ---------------- header
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "GamersHub",
                style = MaterialTheme.typography.headlineLarge,
                color = g.text
            )
            Spacer(Modifier.weight(1f))
            GlassChip(text = "Settings", selected = false) { nav.navigate(Routes.SETTINGS) }
        }

        // ---------------- hero banner
        GlassCard(highlight = true, corner = 24.dp) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(R.drawable.gh_banner),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        "EVERY FRAME. MAXED.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        "Real-time FPS boost · Thermal AI · 4K capture",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xCCFFFFFF)
                    )
                }
            }
        }

        // ---------------- shizuku status
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                shellReady -> GhColors.Green
                                shizukuStatus == ShizukuManager.Status.READY -> GhColors.Amber
                                else -> GhColors.Red
                            }
                        )
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (shizukuStatus) {
                            ShizukuManager.Status.READY ->
                                if (shellReady) "Shizuku connected" else "Shizuku ready"
                            ShizukuManager.Status.PERMISSION_NEEDED -> "Grant Shizuku permission"
                            ShizukuManager.Status.NOT_RUNNING -> "Shizuku is not running"
                            ShizukuManager.Status.NOT_INSTALLED -> "Shizuku not installed"
                        },
                        style = MaterialTheme.typography.titleMedium, color = g.text
                    )
                    Text(
                        when (shizukuStatus) {
                            ShizukuManager.Status.READY ->
                                if (shellReady) "Deep system optimizations unlocked"
                                else "Binding privileged shell…"
                            ShizukuManager.Status.PERMISSION_NEEDED ->
                                "Tap Allow to let GamersHub use Shizuku"
                            ShizukuManager.Status.NOT_RUNNING ->
                                "Start Shizuku via wireless debugging or root"
                            ShizukuManager.Status.NOT_INSTALLED ->
                                "Install Shizuku to unlock system-level boosting"
                        },
                        style = MaterialTheme.typography.labelSmall, color = g.textDim
                    )
                }
                when (shizukuStatus) {
                    ShizukuManager.Status.PERMISSION_NEEDED ->
                        GlassChip("Allow", selected = true) { container.shizuku.requestPermission() }
                    ShizukuManager.Status.NOT_RUNNING, ShizukuManager.Status.NOT_INSTALLED ->
                        GlassChip("Setup", selected = false) { nav.navigate(Routes.SETTINGS) }
                    ShizukuManager.Status.READY ->
                        GlassChip("Rebind", selected = false) { container.shizuku.refreshState() }
                }
            }
        }

        // ---------------- live gauges
        SectionTitle("LIVE TELEMETRY")
        GlassCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                RadialGauge(
                    label = "FPS",
                    value = stats.fps.coerceAtLeast(0f),
                    maxValue = maxOf(120f, stats.fps),
                    unit = "fps",
                    color = g.fpsColor(stats.fps, stats.refreshRateHz.toFloat())
                )
                RadialGauge(
                    label = "Battery",
                    value = stats.batteryTempC.coerceAtLeast(0f),
                    maxValue = 55f, unit = "°C",
                    color = g.tempColor(stats.batteryTempC)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                RadialGauge(
                    label = "CPU",
                    value = stats.cpuUsagePct.coerceAtLeast(0f),
                    maxValue = 100f, unit = "%",
                    subtitle = if (stats.cpuFreqMhz > 0) "${stats.cpuFreqMhz} MHz" else null
                )
                RadialGauge(
                    label = "RAM",
                    value = stats.memUsedMb.toFloat(),
                    maxValue = maxOf(stats.memTotalMb.toFloat(), 1f),
                    unit = "MB",
                    color = g.accent2
                )
            }
        }

        // ---------------- boost cta
        Spacer(Modifier.height(16.dp))
        val boosting = boostState is BoostState.Active
        GlassButton(
            text = if (boosting) "RESTORE SYSTEM" else "⚡ BOOST NOW",
            onClick = {
                scope.launch {
                    container.boost.toggleBoost(container.boost.defaultPlan())
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (boosting) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Boost running" +
                        ((boostState as? BoostState.Active)?.gamePackage?.let { " for $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = g.accent,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // ---------------- quick tiles
        SectionTitle("QUICK ACCESS")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(modifier = Modifier.weight(1f), onClick = {
                if (Settings.canDrawOverlays(context)) {
                    if (widgetRunning) FloatingWidgetService.stop(context)
                    else FloatingWidgetService.start(context)
                } else {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }) {
                Text("🎛 Floating Widget", style = MaterialTheme.typography.titleMedium, color = g.text)
                Text(
                    if (widgetRunning) "Active — tap to stop"
                    else if (Settings.canDrawOverlays(context)) "Start the in-game pop-up"
                    else "Grant overlay permission",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
            }
            GlassCard(modifier = Modifier.weight(1f), onClick = { nav.navigate(Routes.STREAM) }) {
                Text("📡 Go Live", style = MaterialTheme.typography.titleMedium, color = g.text)
                Text(
                    "YouTube · Facebook · Instagram",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
            }
        }

        // ---------------- mini fps chart
        if (history.size > 8) {
            SectionTitle("FPS HISTORY (10 MIN)")
            GlassCard {
                val pts = history.map { if (it.fps >= 0) it.fps else 0f }
                LineChart(points = pts, maxY = maxOf(120f, pts.maxOrNull() ?: 60f))
            }
        }

        if (recState is RecorderController.State.Active) {
            Spacer(Modifier.height(12.dp))
            GlassCard(highlight = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = GhColors.Red
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Capture session active",
                        style = MaterialTheme.typography.bodyMedium, color = g.text
                    )
                    Spacer(Modifier.weight(1f))
                    GlassChip("Stop", selected = true) { container.recorder.stop() }
                }
            }
        }

        Spacer(Modifier.height(110.dp)) // bottom-bar clearance
    }
}
