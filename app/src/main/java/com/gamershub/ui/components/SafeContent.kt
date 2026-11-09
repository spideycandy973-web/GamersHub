package com.gamershub.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamershub.theme.GC

/**
 * Stops a single broken screen, sheet or panel from taking the whole app down.
 *
 * Compose normally kills the activity when a composable throws — which is what made
 * the app look like it crashes "indiscriminately". Here the throw is caught, the
 * details are written to the crash log and copied to the clipboard, and a plain
 * panel with a Copy button takes its place.
 */
@Composable
fun SafeContent(where: String, content: @Composable () -> Unit) {
    try {
        content()
    } catch (t: Throwable) {
        runCatching { com.gamershub.GamersHubApp.note(t, where) }
        FailedPanel(where, t)
    }
}

@Composable
private fun FailedPanel(where: String, t: Throwable) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(18.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GC.Surface)
            .padding(18.dp)
    ) {
        Text("This part had a problem", color = GC.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "$where · ${t.javaClass.simpleName}: ${t.message ?: "no detail"}",
            color = GC.TextDim,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(GC.SurfaceHi)
                .clickable {
                    runCatching {
                        val cm = context.getSystemService(ClipboardManager::class.java)
                        val text = com.gamershub.GamersHubApp.lastCrash.ifBlank { t.stackTraceToString() }
                        cm?.setPrimaryClip(ClipData.newPlainText("GamersHub", text))
                        Toast.makeText(context, "Details copied", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text("Copy details", color = GC.TextSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
