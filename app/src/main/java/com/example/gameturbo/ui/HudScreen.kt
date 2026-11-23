package com.example.gameturbo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gameturbo.Perms
import com.example.gameturbo.app
import com.example.gameturbo.engine.Stats
import com.example.gameturbo.service.TurboService

private fun fpsColor(f: Float): Color = when {
    f >= 55f -> Ink.green
    f >= 30f -> Ink.amber
    else -> Ink.red
}

private fun tempColor(t: Float): Color = when {
    t >= 80f -> Ink.red
    t >= 70f -> Ink.amber
    else -> Color.White
}

@Composable
private fun HudCell(label: String, value: String, color: Color = Color.White) {
    Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(label, 9.sp, FontWeight.Bold, Color(0xB3FFFFFF), maxLines = 1)
        Txt(value, 19.sp, FontWeight.Black, color, maxLines = 1)
    }
}

/** HUD xem trước: cùng kiểu với HUD thật trong game. */
@Composable
fun HudPreview(stats: Stats, modifier: Modifier = Modifier) {
    val st = LocalContext.current.app.settings
    val shape = RoundedCornerShape(18.dp)
    val ultColor = LocalUi.current.ultimate
    Row(
        modifier
            .graphicsLayer {
                scaleX = st.hudScale
                scaleY = st.hudScale
                alpha = st.hudAlpha.coerceIn(0.2f, 1f)
            }
            .clip(shape)
            .background(Color(0xB3080A1E))
            .border(1.dp, if (ultColor) Color(0x99FF4A5A) else Color(0x44FFFFFF), shape)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (st.hudShowFps) HudCell("FPS", if (stats.fps >= 0) "%.0f".format(stats.fps) else "--", if (stats.fps >= 0) fpsColor(stats.fps) else Color.White)
        if (st.hudShowGhz) HudCell("GHz", if (stats.maxMhz > 0) "%.2f".format(stats.maxMhz / 1000f) else "--")
        if (st.hudShowCpu) HudCell("CPU", if (stats.cpuPct >= 0) "%.0f%%".format(stats.cpuPct) else "--")
        if (st.hudShowGpu) {
            val v = when {
                stats.gpuPct >= 0 -> "%.0f%%".format(stats.gpuPct)
                stats.gpuMhz >= 0 -> "%.0fM".format(stats.gpuMhz)
                else -> "--"
            }
            HudCell("GPU", v)
        }
        if (st.hudShowTemp) HudCell("°C", if (stats.tempC > 0) "%.0f".format(stats.tempC) else "--", if (stats.tempC > 0) tempColor(stats.tempC) else Color.White)
    }
}

@Composable
fun HudScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val app = ctx.app
    val st = app.settings
    val session by app.controller.session.collectAsStateCompat()
    val live by app.controller.stats.collectAsStateCompat()
    val sample = Stats(fps = 59f, cpuPct = 38f, gpuPct = 52f, maxMhz = 2840f, avgMhz = 2100f, tempC = 46f, time = 1L)
    val liveNow = session != null && live.time > 0
    var permDialog by remember { mutableStateOf(false) }

    Row(modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().glass(26.dp).padding(14.dp)) {
            H2("Xem trước HUD")
            Txt(if (liveNow) "Số liệu thật từ ${session?.label}" else "Dữ liệu mẫu — số thật hiện khi bạn đang chơi game", 11.sp, color = Faint)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                HudPreview(if (liveNow) live else sample)
            }
            Txt(
                "HUD để chạm xuyên qua nên không cản thao tác trong game. Mở menu bằng bong bóng; chạm đúp thanh trên của menu để di chuyển menu.",
                10.5.sp, color = Dim,
            )
        }
        Column(Modifier.width(300.dp).fillMaxHeight().glass(26.dp).verticalScroll(rememberScrollState()).padding(14.dp)) {
            H2("Hiển thị")
            Line("Bật HUD") { GtSwitch(st.hudOn, { st.hudOn = it }) }
            Line("FPS") { GtSwitch(st.hudShowFps, { st.hudShowFps = it }) }
            Line("Xung CPU (GHz)") { GtSwitch(st.hudShowGhz, { st.hudShowGhz = it }) }
            Line("CPU (%)") { GtSwitch(st.hudShowCpu, { st.hudShowCpu = it }) }
            Line("GPU") { GtSwitch(st.hudShowGpu, { st.hudShowGpu = it }) }
            Line("Nhiệt độ") { GtSwitch(st.hudShowTemp, { st.hudShowTemp = it }) }
            Label("Độ mờ")
            GtSlider(
                ((st.hudAlpha - 0.2f) / 0.8f).coerceIn(0f, 1f), { st.hudAlpha = 0.2f + it * 0.8f },
                Modifier.fillMaxWidth(), "${(st.hudAlpha * 100).toInt()}%",
            )
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                Txt("Android 12+: khi chạm xuyên qua, độ mờ trong game tối đa 80%.", 10.sp, color = Faint)
            }
            Label("Kích thước")
            GtSlider(
                ((st.hudScale - 0.6f) / 1.2f).coerceIn(0f, 1f), { st.hudScale = 0.6f + it * 1.2f },
                Modifier.fillMaxWidth(), "${(st.hudScale * 100).toInt()}%",
            )
            Spacer(Modifier.height(10.dp))
            GtButton("Chỉnh vị trí và kích thước trên màn hình", Modifier.fillMaxWidth(), compact = true) {
                if (!Perms.canOverlay(ctx)) {
                    permDialog = true
                } else {
                    TurboService.editHud(ctx)
                    ctx.findActivity()?.moveTaskToBack(true)
                }
            }
            Txt("Kéo HUD để di chuyển, kéo góc để đổi kích thước, chạm ✓ để lưu.", 10.sp, color = Faint, modifier = Modifier.padding(top = 6.dp), align = TextAlign.Start)
        }
    }
    if (permDialog) OverlayPermissionDialog { permDialog = false }
}
