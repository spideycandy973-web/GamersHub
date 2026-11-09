package com.gamershub.ui.sheets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.gamershub.core.DeviceSpoofer
import com.gamershub.core.GfxEngine
import com.gamershub.core.RenderEngine
import com.gamershub.core.upscale.UpscaleMirror
import com.gamershub.core.MonitorPrefs
import com.gamershub.core.capture.Recorder
import com.gamershub.core.capture.RecorderService
import com.gamershub.core.capture.Stream
import com.gamershub.core.capture.StreamService
import com.gamershub.core.shizuku.Shell
import com.gamershub.overlay.FloatingPanelService
import com.gamershub.overlay.MonitorOverlayService
import com.gamershub.theme.GC
import com.gamershub.theme.LocalAccent
import com.gamershub.ui.components.BigButton
import com.gamershub.ui.components.CircleIconButton
import com.gamershub.ui.components.StatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/* ------------------------------------------------------------------ */
/* Screen recorder                                                     */
/* ------------------------------------------------------------------ */

@Composable
fun RecordSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val prefs = remember { context.getSharedPreferences("gamershub", Context.MODE_PRIVATE) }

    var height by remember { mutableIntStateOf(prefs.getInt("rec_height", 1080)) }
    var fps by remember { mutableIntStateOf(prefs.getInt("rec_fps", 60)) }
    var mic by remember { mutableStateOf(prefs.getBoolean("rec_mic", false)) }
    var bitrate by remember { mutableIntStateOf(prefs.getInt("rec_bitrate", 12_000_000)) }
    var tick by remember { mutableIntStateOf(0) }
    var permissionError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            prefs.edit()
                .putInt("rec_height", height)
                .putInt("rec_fps", fps)
                .putInt("rec_bitrate", bitrate)
                .putBoolean("rec_mic", mic)
                .apply()
            RecorderService.start(context, result.resultCode, data, mic, height, fps, bitrate)
            Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
        } else {
            permissionError = "Screen capture permission was declined"
        }
    }

    val recordings = remember(tick) { Recorder.recordings() }

    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Screen Recorder", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
                Text("MP4 · H.264 · Movies/GamersHub", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
            }
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (Recorder.isRecording) "● Recording ${Recorder.formatDuration(Recorder.elapsedMs())}" else "● Standby")
            Spacer(Modifier.weight(1f))
            Text("${height}p · ${fps}fps · ${bitrate / 1_000_000}Mbps", color = GC.TextDim, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(720, 1080, 1440, 2160).forEach { option ->
                Chip("${option}p", height == option) { height = option }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 60, 90, 120).forEach { option ->
                Chip("$option fps", fps == option) { fps = option }
            }
        }
        Spacer(Modifier.height(10.dp))
        SliderRow("BITRATE", bitrate / 1_000_000f, 2f..40f, "${bitrate / 1_000_000}M") {
            bitrate = (it * 1_000_000).toInt()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MICROPHONE", color = GC.TextSoft, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Chip(if (mic) "ON" else "OFF", mic) { mic = !mic }
        }
        Spacer(Modifier.height(12.dp))

        if (Recorder.isRecording) {
            BigButton(
                text = "STOP & SAVE",
                onClick = { RecorderService.stop(context) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Saving to ${Recorder.lastPath.ifBlank { "Movies/GamersHub" }}",
                color = GC.TextDim,
                fontSize = 11.sp
            )
        } else {
            BigButton(
                text = "START RECORDING",
                onClick = {
                    val intent = RecorderService.captureIntent(context)
                    if (intent == null) {
                        permissionError = "Screen capture is not available on this device"
                    } else {
                        launcher.launch(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.PlayArrow
            )
        }
        if (permissionError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(permissionError, color = Color(0xFFFFB4AE), fontSize = 12.sp)
        }
        if (Recorder.lastError.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("Last error: ${Recorder.lastError}", color = Color(0xFFFFB4AE), fontSize = 11.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("RECORDINGS · ${recordings.size}", color = GC.TextDim, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        if (recordings.isEmpty()) {
            Text("Nothing recorded yet.", color = GC.TextDim, fontSize = 12.sp)
        } else {
            recordings.take(8).forEach { file ->
                RecordingRow(context, file, accent)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun RecordingRow(context: Context, file: File, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GC.SurfaceHi)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(file.name, color = GC.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(Recorder.formatSize(file.length()), color = GC.TextDim, fontSize = 10.sp)
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = accent,
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    runCatching {
                        val uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".files",
                            file
                        )
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(uri, "video/mp4")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Filled.Share,
            contentDescription = "Share",
            tint = GC.TextSoft,
            modifier = Modifier
                .size(18.dp)
                .clickable {
                    runCatching {
                        val uri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".files",
                            file
                        )
                        context.startActivity(
                            Intent(Intent.ACTION_SEND)
                                .setType("video/mp4")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Delete",
            tint = GC.TextDim,
            modifier = Modifier
                .size(18.dp)
                .clickable { Recorder.delete(file) }
        )
    }
}

/* ------------------------------------------------------------------ */
/* Go live (RTMP)                                                      */
/* ------------------------------------------------------------------ */

@Composable
fun LiveSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val prefs = remember { context.getSharedPreferences("gamershub", Context.MODE_PRIVATE) }

    var url by remember { mutableStateOf(prefs.getString("rtmp_url", "") ?: "") }
    var height by remember { mutableIntStateOf(prefs.getInt("live_height", 1080)) }
    var bitrate by remember { mutableIntStateOf(prefs.getInt("live_bitrate", 8_000_000)) }
    var message by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            prefs.edit()
                .putString("rtmp_url", url)
                .putInt("live_height", height)
                .putInt("live_bitrate", bitrate)
                .apply()
            StreamService.start(context, result.resultCode, data, url.trim(), height, 60, bitrate)
            message = "Connecting to the ingest server…"
        } else {
            message = "Screen capture permission was declined"
        }
    }

    val live = remember(tick) {
        Triple(Stream.isStreaming, Stream.status, Stream.fps)
    }

    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Go Live", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
                Text("Real RTMP publish · H.264", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
            }
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (live.first) "● ${live.second} ${Recorder.formatDuration(Stream.elapsedMs())}" else "● ${live.second}")
            Spacer(Modifier.weight(1f))
            if (live.first) {
                Text(
                    "${"%.1f".format(Stream.mbps())} Mbps · ${live.third} fps",
                    color = GC.TextDim,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("rtmp://host/app/streamKey") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("1080p", height == 1080) { height = 1080 }
            Chip("720p", height == 720) { height = 720 }
            Chip("1440p", height == 1440) { height = 1440 }
        }
        Spacer(Modifier.height(8.dp))
        SliderRow("BITRATE", bitrate / 1_000_000f, 2f..20f, "${bitrate / 1_000_000}M") {
            bitrate = (it * 1_000_000).toInt()
        }
        Spacer(Modifier.height(10.dp))

        if (Stream.isStreaming) {
            BigButton(
                text = "END STREAM",
                onClick = { StreamService.stop(context) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            BigButton(
                text = "GO LIVE",
                onClick = {
                    if (!com.gamershub.core.capture.RtmpClient.isValid(url)) {
                        message = "Enter a full rtmp:// URL including the stream key"
                    } else {
                        val intent = RecorderService.captureIntent(context)
                        if (intent == null) message = "Screen capture is not available"
                        else launcher.launch(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Filled.PlayArrow
            )
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = GC.TextSoft, fontSize = 12.sp)
        }
        if (Stream.lastError.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(Stream.lastError, color = Color(0xFFFFB4AE), fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Paste the RTMP URL + stream key from YouTube Studio, Twitch or any RTMP endpoint. " +
                "The app publishes the encoded H.264 stream directly — no relay, no key stored anywhere else.",
            color = GC.TextDim,
            fontSize = 11.sp
        )
    }
}

/* ------------------------------------------------------------------ */
/* Graphics enhancer                                                   */
/* ------------------------------------------------------------------ */

@Composable
private fun BiSliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    display: String,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = GC.TextSoft, fontSize = 12.sp, modifier = Modifier.width(92.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.weight(1f)
        )
        Text(
            display,
            color = if (value == 0 || display == "DEFAULT") GC.TextDim else GC.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(70.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

private fun signed(value: Int): String = when {
    value == 0 -> "DEFAULT"
    value > 0 -> "+$value"
    else -> "$value"
}

@Composable
fun GraphicsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("gamershub", Context.MODE_PRIVATE) }

    // Signed knobs — 0 is the game's own setting, negative is below it, positive above it.
    var sharpen by remember { mutableIntStateOf(prefs.getInt("k_sharpen", 0)) }
    var tex by remember { mutableIntStateOf(prefs.getInt("k_tex", 0)) }
    var shd by remember { mutableIntStateOf(prefs.getInt("k_shd", 0)) }
    var fx by remember { mutableIntStateOf(prefs.getInt("k_fx", 0)) }
    var aa by remember { mutableIntStateOf(prefs.getInt("k_aa", 0)) }
    var post by remember { mutableIntStateOf(prefs.getInt("k_post", 0)) }
    var gameScale by remember { mutableIntStateOf(prefs.getInt("k_scale", 100)) }
    var screenScale by remember { mutableIntStateOf(prefs.getInt("k_screen", 100)) }
    var fps by remember { mutableIntStateOf(prefs.getInt("k_fps", 0)) }
    var mode by remember { mutableStateOf(prefs.getString("k_mode", "performance") ?: "performance") }
    var angle by remember { mutableStateOf(prefs.getBoolean("k_angle", false)) }

    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tick++
        }
    }

    val target = remember(tick) { lastGame(context) }
    val shellReady = remember(tick) { runCatching { Shell.available() }.getOrDefault(false) }
    val lossless = remember(tick) { runCatching { RenderEngine.losslessState(context) }.getOrNull() }
    val engineOn = remember(tick) { UpscaleMirror.isRunning }
    val engineState = remember(tick) { UpscaleMirror.state() }

    fun persist() {
        prefs.edit()
            .putInt("k_sharpen", sharpen).putInt("k_tex", tex).putInt("k_shd", shd)
            .putInt("k_fx", fx).putInt("k_aa", aa).putInt("k_post", post)
            .putInt("k_scale", gameScale).putInt("k_screen", screenScale).putInt("k_fps", fps)
            .putString("k_mode", mode).putBoolean("k_angle", angle)
            .apply()
    }

    fun knobs() = GfxEngine.Knobs(
        sharpen = sharpen, texture = tex, shadow = shd, effects = fx, aa = aa, post = post,
        renderScale = gameScale, fps = fps
    )

    fun run() {
        if (busy) return
        busy = true
        log = "Starting engine…"
        // Live sharpening happens in the GL engine, so push it before the capture prompt.
        UpscaleMirror.sharpen = sharpen / 100f
        persist()
        scope.launch {
            val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    RenderEngine.applyKnobs(context, target?.first ?: "", knobs(), mode, angle, screenScale)
                }.getOrNull()
            }
            runCatching { UpscaleMirror.start(context) }
            busy = false
            log = (report?.details() ?: "Shizuku didn't answer — knobs saved, engine not started.") +
                if (UpscaleMirror.supported()) "" else "\n\n" + UpscaleMirror.whyNot()
            Toast.makeText(context, report?.summary() ?: "Saved", Toast.LENGTH_SHORT).show()
        }
    }

    fun stop() {
        busy = true
        runCatching { UpscaleMirror.stop(context) }
        scope.launch {
            val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { RenderEngine.restore(context, target?.first ?: "") }.getOrNull()
            }
            busy = false
            screenScale = 100
            gameScale = 100
            fps = 0
            persist()
            log = report?.details() ?: "Engine stopped."
        }
    }

    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Graphics Enhancer", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
                Text(
                    if (engineOn) "Engine running · $engineState"
                    else if (shellReady) "Engine stopped · sliders are signed offsets"
                    else "Shizuku needed to apply anything",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (engineOn) accent else GC.TextDim,
                    fontSize = 12.sp
                )
            }
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                BigButton(text = "RUN", onClick = { run() }, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f)) {
                BigButton(text = "STOP", onClick = { stop() }, modifier = Modifier.fillMaxWidth(), primary = false)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (UpscaleMirror.supported())
                "RUN applies every knob, then starts the GL engine on your game's frames — sharpening there is live and can go past the game's own limit."
            else "GL engine: " + UpscaleMirror.whyNot() + " Knobs still apply to the game config and the display.",
            color = GC.TextDim,
            fontSize = 10.sp
        )

        if (lossless != null) {
            Spacer(Modifier.height(6.dp))
            Text(lossless.summary(), color = GC.TextSoft, fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))
        Text("SIGNED OFFSETS · 0 = THE GAME'S OWN SETTING", color = GC.TextDim, fontSize = 10.sp, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            if (target != null) "Target: ${target.second}"
            else "Open a game once from Game Space so it becomes the target",
            color = if (target != null) GC.TextSoft else GC.TextDim, fontSize = 11.sp
        )
        Spacer(Modifier.height(2.dp))
        BiSliderRow("SHARPNESS", sharpen, -100, 100, signed(sharpen)) { sharpen = it }
        BiSliderRow("TEXTURE", tex, -3, 3, signed(tex)) { tex = it }
        BiSliderRow("SHADOW", shd, -3, 3, signed(shd)) { shd = it }
        BiSliderRow("EFFECTS", fx, -3, 3, signed(fx)) { fx = it }
        BiSliderRow("ANTI-ALIAS", aa, -3, 3, signed(aa)) { aa = it }
        BiSliderRow("POST FX", post, -3, 3, signed(post)) { post = it }

        Spacer(Modifier.height(10.dp))
        Text("DISPLAY & PACING", color = GC.TextDim, fontSize = 10.sp, letterSpacing = 1.6.sp)
        BiSliderRow("GAME SCALE", gameScale, 50, 100, if (gameScale >= 100) "NATIVE" else "$gameScale%") { gameScale = it }
        BiSliderRow("SCREEN SCALE", screenScale, 50, 100, if (screenScale >= 100) "NATIVE" else "$screenScale%") { screenScale = it }
        BiSliderRow("FPS CAP", fps, 0, 144, if (fps == 0) "DEFAULT" else "$fps") { fps = it }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("performance", "standard", "battery").forEach { option ->
                Chip(option.uppercase(), mode == option) { mode = option }
            }
            Chip(if (angle) "ANGLE" else "NATIVE", angle) { angle = !angle }
        }

        if (log.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(log, color = GC.TextSoft, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Above 0 goes past the game's own cap (Epic → Cinematic), below 0 drops under its setting, " +
                "0 leaves it untouched. Two paths run together: the game's own CVars when it ships a config, " +
                "and the GL engine — which filters and sharpens the real frames of ANY app, UE or not.",
            color = GC.TextDim, fontSize = 10.sp
        )
    }
}

private fun lastGame(context: Context): Pair<String, String>? {
    val prefs = context.getSharedPreferences("gamershub", Context.MODE_PRIVATE)
    val pkg = prefs.getString("last_game_pkg", "") ?: ""
    if (pkg.isBlank()) return null
    val label = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Throwable) {
        pkg
    }
    return pkg to label
}

/* ------------------------------------------------------------------ */
/* Device spoofing                                                     */
/* ------------------------------------------------------------------ */

@Composable
fun SpoofSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var selected by remember { mutableIntStateOf(0) }
    var log by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tick++
        }
    }
    val status = remember(tick) { DeviceSpoofer.status(context) }

    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Device Spoofing", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
                Text("Display · name · identity", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
            }
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill("● ${status.manufacturer} ${status.model}")
            Spacer(Modifier.width(6.dp))
            StatusPill(if (status.rooted) "● Root" else if (status.shell) "● Shizuku" else "● No shell", orange = status.shell)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${status.resolution} · ${status.density}dpi · ${status.refresh} · ${status.deviceName}" +
                (if (status.overrideSize.isNotBlank()) " · override active" else ""),
            color = GC.TextDim,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(12.dp))
        DeviceSpoofer.presets.forEachIndexed { index, preset ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (index == selected) accent.copy(alpha = 0.20f) else GC.SurfaceHi)
                    .clickable { selected = index }
                    .padding(horizontal = 11.dp, vertical = 10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(preset.name, color = GC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${preset.width}p · ${preset.density}dpi · ${preset.refresh}Hz",
                        color = GC.TextDim,
                        fontSize = 10.sp
                    )
                }
                Text(
                    if (index == selected) "SELECTED" else "SELECT",
                    color = if (index == selected) accent else GC.TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        BigButton(
            text = "APPLY SPOOF",
            onClick = {
                val preset = DeviceSpoofer.presets[selected]
                val applied = DeviceSpoofer.applySafe(context, preset)
                tick++
                val identity = if (status.rooted) DeviceSpoofer.applyIdentity(preset) else null
                log = buildString {
                    if (applied.isEmpty()) append("Needs Shizuku to change display/name.\n")
                    applied.forEach { append("✓ $it\n") }
                    append("Device name is restored automatically when you leave the Game Space.\n")
                    append("Apps now see ${DeviceSpoofer.observed(context)}\n")
                    identity?.let { (ok, model) ->
                        if (ok) append("✓ Identity props → $model\n")
                        else append("Identity props: $model (root only)\n")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        BigButton(
            text = "RESTORE STOCK",
            onClick = {
                val restored = DeviceSpoofer.restore(context)
                log = if (restored.isEmpty()) "Needs Shizuku to restore." else restored.joinToString("\n") { "✓ $it" }
            },
            modifier = Modifier.fillMaxWidth(),
            primary = false
        )
        if (log.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(log, color = GC.TextSoft, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The display spoof scales resolution and density by the same factor, so the picture keeps its " +
                "exact aspect ratio and physical size — nothing is cropped or shrunk. Model / build identity " +
                "is a read-only system property: it changes through root (resetprop) only, and the app reads " +
                "it back to confirm.",
            color = GC.TextDim,
            fontSize = 11.sp
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
fun OverlayHubSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            tick++
        }
    }
    val panelOn = remember(tick) { FloatingPanelService.isRunning }
    val monitorOn = remember(tick) { MonitorOverlayService.isRunning }

    SheetFrame(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Floating Panel", style = MaterialTheme.typography.titleMedium, color = GC.Text, fontSize = 22.sp)
                Text("GamersHub bubble + pop-up panel", style = MaterialTheme.typography.labelMedium, color = GC.TextDim, fontSize = 12.sp)
            }
            CircleIconButton(Icons.Filled.Close, onClick = onDismiss)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (panelOn) "● Panel live" else "● Panel off")
            Spacer(Modifier.weight(1f))
            BigButton(
                text = if (panelOn) "STOP" else "START",
                onClick = {
                    if (panelOn) FloatingPanelService.stop(context) else FloatingPanelService.start(context)
                },
                modifier = Modifier.width(130.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Monitor inside the panel", color = GC.Text, fontSize = 14.sp)
                Text(
                    if (monitorOn) "Fields: ${MonitorPrefs.fields(context).size} · live" else "Start the floating monitor",
                    color = GC.TextDim,
                    fontSize = 11.sp
                )
            }
            Chip(if (monitorOn) "ON" else "OFF", monitorOn) {
                MonitorOverlayService.toggle(context)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Record and Live buttons live in the panel's top bar. Tapping them opens the studio sheets.",
            color = accent, fontSize = 11.sp)
    }
}
