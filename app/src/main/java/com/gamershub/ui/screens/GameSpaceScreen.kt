package com.gamershub.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.boost.ThermalProfile
import com.gamershub.core.overlay.FloatingWidgetService
import com.gamershub.di.AppContainer
import com.gamershub.ui.Routes
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Game Space — the ROG-style gaming hub. Opens with a cinematic intro (tap to
 * skip), then the hub: one-tap control panel, performance modes, your games,
 * live status and quick controls. The panel also opens with an inward edge
 * swipe (the widget claims the edge band from the system back-gesture) or by
 * tapping the floating bubble — so the feature never depends on one path.
 */
@Composable
fun GameSpaceScreen(container: AppContainer, onOpen: (String) -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val widgetRunning by FloatingWidgetService.isRunning.collectAsStateWithLifecycle()
    val shellReady by container.shizuku.shellReady.collectAsStateWithLifecycle()
    val engineOn by container.engine.enabled.collectAsStateWithLifecycle()
    val thermal by container.thermal.activeProfile.collectAsStateWithLifecycle()
    val games by container.games.games.collectAsStateWithLifecycle()
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    var introDone by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { container.games.refresh() }
    }

    fun overlaySettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ================================================================ hub
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Game Space",
                style = MaterialTheme.typography.headlineLarge,
                color = g.text, modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                "ROG-style gaming overlay — swipe inward from a screen edge, tap the " +
                        "bubble, or use the one-tap button below to pull the control panel " +
                        "over any game.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("Overlay perm", hasOverlay, Modifier.weight(1f))
                StatusPill("Widget", widgetRunning, Modifier.weight(1f))
                StatusPill("Shizuku", shellReady, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            if (!hasOverlay) {
                GlassCard {
                    Text(
                        "Display-over-other-apps permission needed",
                        style = MaterialTheme.typography.titleMedium, color = g.text
                    )
                    Text(
                        "Android only lets an app draw a floating widget after you grant " +
                                "the overlay permission. Tap the button below, allow GamersHub, " +
                                "then come back.",
                        style = MaterialTheme.typography.labelSmall, color = g.textDim
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        "Open overlay settings",
                        onClick = { overlaySettings() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // ---- one-tap panel (the guaranteed entry) ----------------------
            GlassCard {
                Text(
                    "Open the control panel now",
                    style = MaterialTheme.typography.titleMedium, color = g.text
                )
                Text(
                    "One tap pops the full panel over anything on screen — boost, frame " +
                            "generation, refresh lock, render scale, sharpen, HUD, record, " +
                            "live, thermal, bypass charging. ✕ / tap outside to close.",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        "Open control panel",
                        onClick = {
                            hasOverlay = Settings.canDrawOverlays(context)
                            if (hasOverlay) {
                                if (!FloatingWidgetService.isRunning.value) {
                                    FloatingWidgetService.start(context)
                                }
                                FloatingWidgetService.openPanel(context)
                            } else {
                                overlaySettings()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        if (widgetRunning) "Stop widget" else "Start widget",
                        onClick = {
                            if (widgetRunning) {
                                FloatingWidgetService.stop(context)
                            } else {
                                if (Settings.canDrawOverlays(context)) {
                                    FloatingWidgetService.start(context)
                                } else {
                                    overlaySettings()
                                }
                            }
                            hasOverlay = Settings.canDrawOverlays(context)
                        },
                        modifier = Modifier.weight(1f),
                        primary = false
                    )
                }
            }

            // ---- performance modes ---------------------------------------
            SectionTitle("PERFORMANCE MODE")
            GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThermalProfile.entries.forEach { p ->
                        GlassChip(
                            when (p) {
                                ThermalProfile.COOL -> "❄ Cool"
                                ThermalProfile.BALANCED -> "⚖ Balanced"
                                ThermalProfile.BEAST -> "🔥 Beast"
                                ThermalProfile.EXTREME -> "⚡ Extreme"
                            },
                            selected = thermal == p
                        ) {
                            scope.launch { runCatching { container.thermal.setProfile(p) } }
                        }
                    }
                }
                Text(
                    "Thermal anti-throttle profile — applied to every game session. " +
                            "Beast/Extreme keep clocks high; Cool keeps the phone comfy.",
                    style = MaterialTheme.typography.labelSmall, color = g.textDim,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (!engineOn) {
                Spacer(Modifier.height(10.dp))
                GlassCard {
                    Text(
                        "Master switch is OFF",
                        style = MaterialTheme.typography.titleMedium, color = g.text
                    )
                    Text(
                        "Game Space works best with the engine armed — enable the master " +
                                "switch on the Dashboard so the panel's boost, frame generation " +
                                "and scaling controls take effect instantly.",
                        style = MaterialTheme.typography.labelSmall, color = g.textDim
                    )
                }
            }

            // ---- your games ----------------------------------------------
            if (games.isNotEmpty()) {
                SectionTitle("YOUR GAMES")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    games.take(6).forEach { entry ->
                        GlassCard(
                            modifier = Modifier
                                .widthIn(max = 92.dp)
                                .clickable { onOpen(Routes.GAMES) }
                        ) {
                            val iconBmp = remember(entry.packageName) {
                                runCatching {
                                    entry.icon?.toBitmap(64, 64)?.asImageBitmap()
                                }.getOrNull()
                            }
                            if (iconBmp != null) {
                                Image(
                                    bitmap = iconBmp,
                                    contentDescription = entry.label,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = g.text,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            SectionTitle("HOW TO REVEAL THE PANEL")
            GlassCard {
                GuideLine("1", "Swipe inward from the LEFT or RIGHT screen edge — the " +
                        "invisible edge strips claim that band from the system back-gesture.")
                Spacer(Modifier.height(8.dp))
                GuideLine("2", "Tap the floating bubble anywhere on screen.")
                Spacer(Modifier.height(8.dp))
                GuideLine("3", "Long-press the bubble for instant actions: boost, " +
                        "record, live, close panel — configurable in Settings → Gestures.")
                Spacer(Modifier.height(8.dp))
                GuideLine("→", "If a launcher still eats the swipe, use “Open control " +
                        "panel” above — it always works.")
            }

            SectionTitle("QUICK CONTROLS")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Render scale", onClick = { onOpen(Routes.SCALING) }, modifier = Modifier.weight(1f))
                GlassButton("Sharpening", onClick = { onOpen(Routes.ENHANCE) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Thermal", onClick = { onOpen(Routes.THERMAL) }, modifier = Modifier.weight(1f))
                GlassButton("Booster", onClick = { onOpen(Routes.BOOST) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Games", onClick = { onOpen(Routes.GAMES) }, modifier = Modifier.weight(1f))
                GlassButton("Live proof", onClick = { onOpen(Routes.PROOF) }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))
        }

        // ==================================================== cinematic intro
        if (!introDone) {
            var visible by remember { mutableStateOf(true) }
            val fade by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(650),
                label = "introFade"
            )
            val exitScale by animateFloatAsState(
                targetValue = if (visible) 1f else 1.06f,
                animationSpec = tween(650),
                label = "introExit"
            )
            val titleIn by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(750, delayMillis = 120),
                label = "titleIn"
            )
            val subIn by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(700, delayMillis = 380),
                label = "subIn"
            )
            val underline by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(900, delayMillis = 300),
                label = "underline"
            )

            val ambient = rememberInfiniteTransition(label = "introAmb")
            val driftA by ambient.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
                label = "driftA"
            )
            val driftB by ambient.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(7300, easing = LinearEasing), RepeatMode.Reverse),
                label = "driftB"
            )
            val ringRot by ambient.animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart),
                label = "ringRot"
            )
            val progress = remember { Animatable(0f) }

            LaunchedEffect(Unit) {
                progress.animateTo(1f, tween(2300))
            }
            LaunchedEffect(Unit) {
                delay(2350)
                visible = false
                delay(700)
                introDone = true
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = fade, scaleX = exitScale, scaleY = exitScale)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A0E17), Color(0xFF111A2B), Color(0xFF0A0E17))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (visible) {
                            visible = false
                            scope.launch {
                                delay(700)
                                introDone = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // drifting aurora blobs — slow, low-alpha, glassy depth
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -90f + driftA * 180f
                            translationY = -220f + driftB * 120f
                            alpha = 0.55f
                        }
                        .background(
                            Brush.radialGradient(
                                listOf(g.accent.copy(alpha = 0.22f), Color.Transparent)
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = 120f - driftB * 170f
                            translationY = 260f - driftA * 140f
                            alpha = 0.5f
                        }
                        .background(
                            Brush.radialGradient(
                                listOf(g.accent2.copy(alpha = 0.18f), Color.Transparent)
                            )
                        )
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // glass badge with a slow conic halo
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {
                        Box(
                            Modifier
                                .size(128.dp)
                                .graphicsLayer(rotationZ = ringRot)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color.Transparent,
                                            g.accent.copy(alpha = 0.55f),
                                            Color.Transparent,
                                            g.accent2.copy(alpha = 0.45f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Box(
                            Modifier
                                .size(94.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.SportsEsports,
                                contentDescription = null,
                                tint = g.text,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(26.dp))
                    Text(
                        "GAME SPACE",
                        style = MaterialTheme.typography.headlineLarge,
                        color = g.text,
                        letterSpacing = 12.sp,
                        modifier = Modifier.graphicsLayer {
                            alpha = titleIn
                            translationY = (1f - titleIn) * 34f
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .width((140 * underline).dp)
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, g.accent, Color.Transparent)
                                )
                            )
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "ROG-STYLE IN-GAME CONTROL",
                        style = MaterialTheme.typography.labelSmall,
                        color = g.textDim,
                        letterSpacing = 5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer(alpha = subIn)
                    )
                }

                // progress line + skip hint pinned to the bottom
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 46.dp)
                ) {
                    Box(
                        Modifier
                            .width(160.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x22FFFFFF))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(g.accent, g.accent2))
                                )
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "tap to skip",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0x66FFFFFF)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, on: Boolean, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (on) "●" else "○",
                style = MaterialTheme.typography.titleMedium,
                color = if (on) GhColors.Green else g.textDim
            )
            Text(
                "  $label",
                style = MaterialTheme.typography.labelSmall,
                color = if (on) g.text else g.textDim
            )
        }
    }
}

@Composable
private fun GuideLine(mark: String, text: String) {
    val g = LocalGlass.current
    Row {
        Text(
            mark,
            style = MaterialTheme.typography.titleMedium,
            color = g.accent,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = g.textDim
        )
    }
}
