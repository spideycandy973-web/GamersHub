package com.gamershub.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamershub.core.record.RecorderConfig
import com.gamershub.core.record.RecorderController
import com.gamershub.core.stream.StreamEngine
import com.gamershub.di.AppContainer
import com.gamershub.ui.components.GlassButton
import com.gamershub.ui.components.GlassCard
import com.gamershub.ui.components.GlassChip
import com.gamershub.ui.components.GlassSwitchRow
import com.gamershub.ui.components.SectionTitle
import com.gamershub.ui.theme.GhColors
import com.gamershub.ui.theme.LocalGlass
import kotlinx.coroutines.launch

@Composable
fun StreamScreen(container: AppContainer) {
    val g = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recState by container.recorder.state.collectAsStateWithLifecycle()
    val targetStats by container.stream.stats.collectAsStateWithLifecycle()
    val chatMessages by container.chat.messages.collectAsStateWithLifecycle()
    val liveSince by container.stream.liveSinceMs.collectAsStateWithLifecycle()

    val activeState = recState as? RecorderController.State.Active
    val isStreaming = activeState != null &&
            activeState.mode != RecorderConfig.Mode.RECORD

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Live Streaming",
            style = MaterialTheme.typography.headlineLarge,
            color = g.text, modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "One tap — YouTube + Facebook + Instagram simultaneously over RTMP(S)",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )

        if (liveSince != null) {
            Spacer(Modifier.height(10.dp))
            GlassCard(highlight = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔴", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("LIVE", style = MaterialTheme.typography.titleMedium, color = GhColors.Red)
                        Text(
                            "Streaming to ${targetStats.keys.joinToString()}",
                            style = MaterialTheme.typography.labelSmall, color = g.textDim
                        )
                    }
                    GlassChip("End", selected = true) { container.recorder.stop() }
                }
            }
        }

        SectionTitle("PLATFORMS")
        StreamEngine.PLATFORMS.forEach { platform ->
            PlatformCard(container, platform, targetStats[platform.label])
        }

        SectionTitle("YOUTUBE CHAT INTEGRATION")
        GlassCard {
            val ytKey by container.settings.ytApiKey.collectAsStateWithLifecycle("")
            val ytChatId by container.settings.ytLiveChatId.collectAsStateWithLifecycle("")
            var key by remember { mutableStateOf<String?>(null) }
            var chatId by remember { mutableStateOf<String?>(null) }
            GlassTextField(
                value = key ?: ytKey,
                onValueChange = { key = it },
                label = "YouTube Data API key"
            )
            Spacer(Modifier.height(8.dp))
            GlassTextField(
                value = chatId ?: ytChatId,
                onValueChange = { chatId = it },
                label = "liveChatId (from your broadcast)"
            )
            Spacer(Modifier.height(8.dp))
            GlassButton("SAVE", primary = false, onClick = {
                scope.launch {
                    key?.let { container.settings.setYtApiKey(it) }
                    chatId?.let { container.settings.setYtLiveChatId(it) }
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                }
            })
            Text(
                "Viewer chat appears in-game via the floating overlay while streaming.",
                style = MaterialTheme.typography.labelSmall, color = g.textDim,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (chatMessages.isNotEmpty()) {
            SectionTitle("CHAT PREVIEW")
            GlassCard {
                chatMessages.takeLast(6).forEach { msg ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            "[${msg.platform}] ${msg.author}: ",
                            style = MaterialTheme.typography.labelSmall, color = g.accent
                        )
                        Text(
                            msg.text.take(90),
                            style = MaterialTheme.typography.labelSmall, color = g.text
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        GlassButton(
            text = if (isStreaming) "■  END STREAM" else "🔴  GO LIVE EVERYWHERE",
            onClick = {
                if (!isStreaming && !com.gamershub.ui.requireEngine(container, context)) {
                    return@GlassButton
                }
                scope.launch {
                    if (isStreaming) {
                        container.recorder.stop()
                        return@launch
                    }
                    val targets = container.stream.enabledTargets()
                    if (targets.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Enable at least one platform and paste its stream key first",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    container.chat.startForStream()
                    container.recorder.requestStart(RecorderConfig.Mode.STREAM)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The same hardware-encoded frames feed every platform — no re-encode, " +
                    "no extra GPU load, no dropped quality.",
            style = MaterialTheme.typography.labelSmall, color = g.textDim
        )
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun PlatformCard(
    container: AppContainer,
    platform: StreamEngine.Platform,
    stats: StreamEngine.TargetStats?
) {
    val g = LocalGlass.current
    val scope = rememberCoroutineScope()
    val enabled by container.settings.streamEnabled(platform.id).collectAsStateWithLifecycle(false)
    val savedKey by container.settings.streamKey(platform.id).collectAsStateWithLifecycle("")
    val chatOn by container.settings.streamChatEnabled(platform.id).collectAsStateWithLifecycle(true)
    var keyDraft by remember(platform.id) { mutableStateOf<String?>(null) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (platform.id) {
                    "youtube" -> "▶️"
                    "facebook" -> "ⓕ"
                    else -> "◎"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(platform.label, style = MaterialTheme.typography.titleMedium, color = g.text)
                Text(platform.help, style = MaterialTheme.typography.labelSmall, color = g.textDim)
            }
            com.gamershub.ui.components.GlassSwitch(enabled) { on ->
                scope.launch { container.settings.setStreamEnabled(platform.id, on) }
            }
        }
        if (enabled) {
            Spacer(Modifier.height(10.dp))
            GlassTextField(
                value = keyDraft ?: savedKey,
                onValueChange = { keyDraft = it },
                label = "Stream key",
                password = true
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassButton("SAVE KEY", primary = false, onClick = {
                    scope.launch {
                        keyDraft?.let { container.settings.setStreamKey(platform.id, it) }
                    }
                })
                Spacer(Modifier.width(12.dp))
                GlassChip(
                    if (chatOn) "chat on ✓" else "chat off",
                    selected = chatOn
                ) {
                    scope.launch { container.settings.setStreamChatEnabled(platform.id, !chatOn) }
                }
            }
        }
        if (stats != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "status: ${stats.status} · ${stats.framesSent} frames · " +
                        "%.1f MB sent · %d dropped".format(
                            stats.bytesSent / 1048576f, stats.drops
                        ) + (stats.error?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = if (stats.error != null) GhColors.Red else g.textDim
            )
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false
) {
    val g = LocalGlass.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = g.accent,
            unfocusedBorderColor = g.stroke,
            focusedLabelColor = g.accent,
            unfocusedLabelColor = g.textDim,
            cursorColor = g.accent,
            focusedTextColor = g.text,
            unfocusedTextColor = g.text
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
