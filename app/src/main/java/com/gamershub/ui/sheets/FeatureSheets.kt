package com.gamershub.ui.sheets

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.R
import com.gamershub.core.GyroConfig
import com.gamershub.core.GyroEngine
import com.gamershub.core.MacroKey
import com.gamershub.core.MappingStore
import com.gamershub.core.MonitorPrefs
import com.gamershub.core.PingEngine
import com.gamershub.core.PingResult
import com.gamershub.core.ScreenshotEngine
import com.gamershub.core.shizuku.Shell
import com.gamershub.overlay.CrosshairOverlayService
import com.gamershub.overlay.MappingOverlayService
import com.gamershub.overlay.MonitorOverlayService
import com.gamershub.overlay.MouseCursorService
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.BigButton
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.StatusPill
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ */
/* Shared bits                                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun SheetTitle(title: String, subtitle: String, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
        }
        CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
internal fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accent else GC.SurfaceHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else GC.TextSoft,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GC.TextSoft, fontSize = 12.sp, modifier = Modifier.width(96.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(display, color = GC.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
    }
}

/* ------------------------------------------------------------------ */
/* Ping optimizer                                                      */
/* ------------------------------------------------------------------ */

@Composable
fun PingOptimizerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    var results by remember { mutableStateOf<List<PingResult>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var dnsMs by remember { mutableIntStateOf(-1) }
    var report by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        busy = true
        scope.launch {
            val measured = PingEngine.measureAll()
            results = measured.sortedBy { if (it.latency < 0) 9999 else it.latency }
            dnsMs = PingEngine.dnsLatency()
            busy = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    SheetFrame(onDismiss) {
        SheetTitle("Delay Fixer", "Measured on this network, right now", onDismiss)

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (busy) "● Measuring" else "● ${PingEngine.currentPrivateDns().uppercase()} DNS")
            Spacer(Modifier.width(8.dp))
            Text(
                if (dnsMs >= 0) "DNS $dnsMs ms" else "DNS --",
                color = GC.TextDim,
                fontSize = 12.sp
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(GC.SurfaceHi)
                    .clickable(enabled = !busy) { refresh() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("RUN AGAIN", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (results.isEmpty()) {
            Text(
                if (busy) "Checking game servers…" else "No samples yet — tap RUN AGAIN.",
                color = GC.TextDim,
                fontSize = 13.sp
            )
        } else {
            results.forEach { result ->
                val color = when (result.grade) {
                    "EXCELLENT" -> Color(0xFF52E08A)
                    "GOOD" -> Color(0xFF7CE0C3)
                    "FAIR" -> Color(0xFFFFC145)
                    "POOR" -> Color(0xFFFF6B5E)
                    else -> GC.TextDim
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 7.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(GC.SurfaceHi)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(result.target.name, color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${result.target.tag} · jitter ${result.jitter} ms · loss ${result.loss}%",
                            color = GC.TextDim,
                            fontSize = 10.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                        repeat(4) { i ->
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height((6 + i * 4).dp)
                                    .background(if (i < result.bars) color else Color(0xFF3A302B))
                            )
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (result.latency >= 0) "${result.latency} ms" else "--",
                            color = color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(result.grade, color = GC.TextDim, fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        BigButton(
            text = "FIX NETWORK",
            onClick = {
                busy = true
                scope.launch {
                    val r = PingEngine.optimize(context)
                    report = if (r.applied) {
                        "Private DNS → ${r.dnsHost}\nDNS ${r.beforeMs} ms → ${r.afterMs} ms (gained ${r.gained} ms)"
                    } else {
                        "Grant Shizuku access to apply DNS + radio tweaks"
                    }
                    dnsMs = PingEngine.dnsLatency()
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Filled.PlayArrow
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                BigButton(
                    text = "UNDO DNS",
                    onClick = {
                        val ok = PingEngine.resetPrivateDns()
                        Toast.makeText(context, if (ok) "Private DNS is off" else "Needs the helper", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )
            }
            Box(Modifier.weight(1f)) {
                BigButton(
                    text = "SIGNAL TWEAKS",
                    onClick = {
                        val ok = PingEngine.applyRadioTweaks()
                        Toast.makeText(context, if (ok) "Scan rate lowered" else "Needs the helper", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )
            }
        }
        report?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = GC.TextSoft, fontSize = 12.sp)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Gyro calibration                                                    */
/* ------------------------------------------------------------------ */

@Composable
fun GyroCalibrationSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var config by remember { mutableStateOf(GyroEngine.load(context)) }
    var live by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }
    var accuracy by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var sampleCount by remember { mutableIntStateOf(0) }
    var quality by remember { mutableFloatStateOf(0f) }
    val samples = remember { mutableListOf<FloatArray>() }
    val manager = remember { GyroEngine.sensorManager(context) }
    val sensor = remember { GyroEngine.sensor(context) }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || event.values.size < 3) return
                val corrected = config.correct(event.values[0], event.values[1], event.values[2])
                live = corrected
                if (capturing) {
                    samples.add(floatArrayOf(event.values[0], event.values[1], event.values[2]))
                    sampleCount = samples.size
                    if (samples.size >= 90) {
                        val bias = GyroEngine.biasOf(samples)
                        quality = GyroEngine.drift(samples)
                        config = config.copy(
                            offX = bias[0],
                            offY = bias[1],
                            offZ = bias[2],
                            calibrated = true
                        )
                        capturing = false
                    }
                }
            }

            override fun onAccuracyChanged(s: Sensor?, value: Int) {
                accuracy = value
            }
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            if (manager != null) manager.unregisterListener(listener)
        }
    }

    SheetFrame(onDismiss) {
        SheetTitle(
            "Tilt Tune",
            if (sensor != null) "Live gyroscope · bias capture" else "No gyroscope detected",
            onDismiss
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("X", String.format(java.util.Locale.US, "%.3f", live.getOrElse(0) { 0f }), accent, Modifier.weight(1f))
            StatBox("Y", String.format(java.util.Locale.US, "%.3f", live.getOrElse(1) { 0f }), accent, Modifier.weight(1f))
            StatBox("Z", String.format(java.util.Locale.US, "%.3f", live.getOrElse(2) { 0f }), accent, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (config.calibrated) "● Calibrated" else "● Uncalibrated")
            Spacer(Modifier.width(8.dp))
            Text(
                "accuracy $accuracy · ${if (capturing) "sampling $sampleCount/90" else "idle"}",
                color = GC.TextDim,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        BigButton(
            text = if (capturing) "KEEP THE PHONE STILL…" else "TUNE (FLAT & STILL)",
            onClick = {
                samples.clear()
                sampleCount = 0
                capturing = true
                Toast.makeText(context, "Set the phone flat and keep still", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            primary = !capturing
        )
        Spacer(Modifier.height(10.dp))
        SliderRow(
            "FEEL",
            config.sens,
            0.2f..3f,
            String.format(java.util.Locale.US, "%.2fx", config.sens)
        ) { config = config.copy(sens = it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FLIP Y", color = GC.TextSoft, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Chip(if (config.invertY) "ON" else "OFF", config.invertY) {
                config = config.copy(invertY = !config.invertY)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                BigButton(
                    text = "KEEP",
                    onClick = {
                        GyroEngine.save(context, config)
                        Toast.makeText(context, "Look kept", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(Modifier.weight(1f)) {
                BigButton(
                    text = "UNDO",
                    onClick = {
                        config = GyroConfig()
                        samples.clear()
                        GyroEngine.save(context, config)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )
            }
        }
        if (quality > 0f) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Sample spread ${String.format(java.util.Locale.US, "%.4f", quality)} — " +
                    if (quality < 0.08f) "clean capture" else "retry on a still surface",
                color = GC.TextDim,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Offsets are applied to every gyro-driven overlay reading.",
            color = GC.TextDim,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GC.SurfaceHi)
            .padding(vertical = 10.dp)
    ) {
        Text(label, color = GC.TextDim, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/* ------------------------------------------------------------------ */
/* Mapping editor                                                      */
/* ------------------------------------------------------------------ */

@Composable
fun MappingEditorSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var keys by remember { mutableStateOf(MappingStore.load(context)) }
    var editMode by remember { mutableStateOf(MappingStore.editMode(context)) }
    var label by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(MappingStore.TAP) }
    var payload by remember { mutableStateOf("") }

    fun reload() {
        keys = MappingStore.load(context)
        if (MappingOverlayService.isRunning) MappingOverlayService.restart(context)
    }

    SheetFrame(onDismiss) {
        SheetTitle("Key Mapper", "${keys.size} keys · injected with shell identity", onDismiss)

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (MappingOverlayService.isRunning) "● Popup live" else "● Popup off")
            Spacer(Modifier.weight(1f))
            Chip(if (editMode) "EDIT" else "PLAY", editMode) {
                editMode = !editMode
                MappingStore.setEditMode(context, editMode)
                if (MappingOverlayService.isRunning) MappingOverlayService.restart(context)
            }
        }
        Spacer(Modifier.height(10.dp))

        keys.forEach { key ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GC.SurfaceHi)
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(key.label.take(2), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(key.label, color = GC.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${key.kind} · ${(key.x * 100).toInt()}% / ${(key.y * 100).toInt()}%",
                        color = GC.TextDim,
                        fontSize = 10.sp
                    )
                }
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Erase",
                    tint = GC.TextDim,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            MappingStore.remove(context, key.id)
                            reload()
                        }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Key name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MappingStore.kinds.forEach { option ->
                Chip(option, kind == option) { kind = option }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = payload,
            onValueChange = { payload = it },
            label = {
                Text(
                    when (kind) {
                        MappingStore.MULTI -> "times,gapMs (3,80)"
                        MappingStore.SWIPE -> "dx,dy,ms (200,0,220)"
                        MappingStore.KEYEVENT -> "keycode (4 = back)"
                        MappingStore.CMD -> "shell command"
                        else -> "not used for TAP"
                    }
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(9.dp))
        BigButton(
            text = "NEW KEY",
            onClick = {
                val name = label.ifBlank { "KEY" }.uppercase().take(6)
                val key = MacroKey(
                    id = "k" + System.currentTimeMillis(),
                    label = name,
                    x = 0.8f,
                    y = 0.5f,
                    kind = kind,
                    payload = payload.trim()
                )
                MappingStore.upsert(context, key)
                label = ""
                payload = ""
                reload()
            },
            modifier = Modifier.fillMaxWidth(),
            primary = false
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                BigButton(
                    text = if (MappingOverlayService.isRunning) "HIDE OVERLAY" else "SHOW OVERLAY",
                    onClick = { MappingOverlayService.toggle(context) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = if (MappingOverlayService.isRunning) Icons.Filled.Close else Icons.Filled.PlayArrow
                )
            }
            Box(Modifier.weight(1f)) {
                BigButton(
                    text = "TRY TAP",
                    onClick = {
                        val ok = MappingOverlayService.testInject(context)
                        Toast.makeText(
                            context,
                            if (ok) "Injection works" else "Needs the helper",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        BigButton(
            text = "PUT LAYOUT BACK",
            onClick = {
                MappingStore.save(context, MappingStore.defaults())
                reload()
            },
            modifier = Modifier.fillMaxWidth(),
            primary = false
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (Shell.available()) "Shell identity ready — taps, swipes and key events are real."
            else "Grant Shizuku to inject. Placement still works without it.",
            color = GC.TextDim,
            fontSize = 11.sp
        )
    }
}

/* ------------------------------------------------------------------ */
/* Monitor / mouse / crosshair / screenshot                            */
/* ------------------------------------------------------------------ */

@Composable
fun MonitorConfigSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var fields by remember { mutableStateOf(MonitorPrefs.fields(context)) }
    var scale by remember { mutableFloatStateOf(MonitorPrefs.scale(context)) }

    SheetFrame(onDismiss) {
        SheetTitle("Info Watcher", "Pick what floats on your game", onDismiss)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (MonitorOverlayService.isRunning) "● Live" else "● Off")
            Spacer(Modifier.weight(1f))
            BigButton(
                text = if (MonitorOverlayService.isRunning) "HALT" else "GO",
                onClick = { MonitorOverlayService.toggle(context) },
                modifier = Modifier.width(130.dp),
                primary = !MonitorOverlayService.isRunning
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            MonitorPrefs.ORDER.take(5).forEach { field ->
                Chip(MonitorPrefs.LABELS[field] ?: field, fields.contains(field)) {
                    MonitorPrefs.toggleField(context, field)
                    fields = MonitorPrefs.fields(context)
                    if (MonitorOverlayService.isRunning) MonitorOverlayService.restart(context)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            MonitorPrefs.ORDER.drop(5).forEach { field ->
                Chip(MonitorPrefs.LABELS[field] ?: field, fields.contains(field)) {
                    MonitorPrefs.toggleField(context, field)
                    fields = MonitorPrefs.fields(context)
                    if (MonitorOverlayService.isRunning) MonitorOverlayService.restart(context)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SliderRow("SIZE", scale, 0.7f..1.8f, String.format(java.util.Locale.US, "%.1fx", scale)) {
            scale = it
            MonitorPrefs.setScale(context, it)
            if (MonitorOverlayService.isRunning) MonitorOverlayService.restart(context)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "FPS is read from the game's own frame counter through Shizuku — it is the real rendered rate.",
            color = GC.TextDim,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Text("Drag the watcher · tap ✕ to shut it", color = accent, fontSize = 11.sp)
    }
}

@Composable
fun MouseCursorSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var sens by remember { mutableFloatStateOf(MouseCursorService.sens(context)) }

    SheetFrame(onDismiss) {
        SheetTitle(
            "Mouse Pointer",
            if (MouseCursorService.hasMouse()) "Physical mouse detected" else "Virtual trackpad mode",
            onDismiss
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (MouseCursorService.isRunning) "● Pointer live" else "● Off")
            Spacer(Modifier.weight(1f))
            BigButton(
                text = if (MouseCursorService.isRunning) "HALT" else "GO",
                onClick = { MouseCursorService.toggle(context) },
                modifier = Modifier.width(130.dp),
                primary = !MouseCursorService.isRunning
            )
        }
        Spacer(Modifier.height(12.dp))
        SliderRow("FEEL", sens, 0.3f..3.5f, String.format(java.util.Locale.US, "%.1fx", sens)) {
            sens = it
            MouseCursorService.setSens(context, it)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Drag the ◎ pad to move the pointer, tap it to send a real touch at the pointer, double-tap to close.",
            color = GC.TextDim,
            fontSize = 11.sp
        )
    }
}

@Composable
fun CrosshairConfigSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val prefs = remember { context.getSharedPreferences("gamershub", Context.MODE_PRIVATE) }
    var style by remember { mutableIntStateOf(prefs.getInt("cross_style", 1)) }
    var size by remember { mutableFloatStateOf(prefs.getFloat("cross_size", 1f)) }
    var alpha by remember { mutableFloatStateOf(prefs.getFloat("cross_alpha", 1f)) }
    var rot by remember { mutableFloatStateOf(prefs.getFloat("cross_rot", 0f)) }
    var color by remember { mutableIntStateOf(prefs.getInt("cross_color", 0xFFFF2D2D.toInt())) }

    fun persist() {
        prefs.edit()
            .putInt("cross_style", style)
            .putFloat("cross_size", size)
            .putFloat("cross_alpha", alpha)
            .putFloat("cross_rot", rot)
            .putInt("cross_color", color)
            .apply()
    }

    val palette = listOf(
        0xFFFF2D2D.toInt() to "RED",
        0xFF52E08A.toInt() to "GREEN",
        0xFF4FA8FF.toInt() to "BLUE",
        0xFFFFD23F.toInt() to "YELLOW",
        0xFFE0359B.toInt() to "PINK",
        0xFFFFFFFF.toInt() to "WHITE"
    )

    SheetFrame(onDismiss) {
        SheetTitle("Aim Mark", "7 styles · size · opacity · rotation", onDismiss)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (CrosshairOverlayService.isRunning) "● Live" else "● Off")
            Spacer(Modifier.weight(1f))
            BigButton(
                text = if (CrosshairOverlayService.isRunning) "HALT" else "GO",
                onClick = {
                    if (CrosshairOverlayService.isRunning) {
                        CrosshairOverlayService.stop(context)
                    } else {
                        persist()
                        CrosshairOverlayService.start(context)
                    }
                },
                modifier = Modifier.width(130.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            (1..7).forEach { index ->
                val res = when (index) {
                    2 -> R.drawable.cross2_new
                    3 -> R.drawable.cross3_new
                    4 -> R.drawable.cross4_new
                    5 -> R.drawable.cross5_new
                    6 -> R.drawable.cross6_new
                    7 -> R.drawable.cross7_new
                    else -> R.drawable.cross1_new
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (style == index) accent.copy(alpha = 0.25f) else GC.SurfaceHi)
                        .border(1.dp, if (style == index) accent else Color.Transparent, RoundedCornerShape(11.dp))
                        .clickable {
                            style = index
                            persist()
                            CrosshairOverlayService.restart(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(res),
                        contentDescription = "Style $index",
                        colorFilter = ColorFilter.tint(Color(color)),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            palette.forEach { (value, name) ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(value))
                        .border(
                            width = if (color == value) 2.dp else 0.dp,
                            color = if (color == value) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            color = value
                            persist()
                            if (CrosshairOverlayService.isRunning) CrosshairOverlayService.restart(context)
                        }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SliderRow("SIZE", size, 0.4f..2f, String.format(java.util.Locale.US, "%.1fx", size)) {
            size = it
            persist()
        }
        SliderRow("OPACITY", alpha, 0.1f..1f, "${(alpha * 100).toInt()}%") {
            alpha = it
            persist()
        }
        SliderRow("ROTATION", rot, 0f..1f, "${(rot * 360).toInt()}°") {
            rot = it
            persist()
        }
        Spacer(Modifier.height(6.dp))
        Text("Tap GO to set size, fade and turn.", color = GC.TextDim, fontSize = 11.sp)
    }
}

@Composable
fun ScreenshotSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var last by remember { mutableStateOf(ScreenshotEngine.lastShot()) }
    var busy by remember { mutableStateOf(false) }

    SheetFrame(onDismiss) {
        SheetTitle("Screen Grab", "screencap written to Pictures/GamersHub", onDismiss)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (Shell.available()) "● Shell ready" else "● Needs Shizuku")
            Spacer(Modifier.weight(1f))
            Text(ScreenshotEngine.DIR, color = GC.TextDim, fontSize = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        last?.let { shot ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(GC.SurfaceHi)
                    .padding(12.dp)
            ) {
                Text(shot.name, color = GC.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (shot.sizeBytes > 0) "${shot.sizeBytes / 1024} KB" else "in Pictures/GamersHub",
                    color = GC.TextDim,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        BigButton(
            text = if (busy) "GRABBING…" else "GRAB NOW",
            onClick = {
                busy = true
                val shot = ScreenshotEngine.capture()
                busy = false
                if (shot != null) {
                    last = shot
                    Toast.makeText(context, "Saved ${shot.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Grab failed — check Shizuku", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        BigButton(
            text = "OPEN PHOTOS",
            onClick = {
                val intent = ScreenshotEngine.galleryIntent(context)
                if (intent != null) {
                    runCatching { context.startActivity(intent) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            primary = false
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Captures run as shell, so the frame is the real screen including game HUD.",
            color = accent,
            fontSize = 11.sp
        )
    }
}
