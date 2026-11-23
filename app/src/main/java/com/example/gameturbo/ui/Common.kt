package com.example.gameturbo.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.util.TimeZone

/** Tăng giá trị mỗi lần Activity quay lại màn hình (để đọc lại quyền vừa cấp). */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

/** true khi Activity đang hiện trên màn hình. */
@Composable
fun rememberResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return resumed
}

class BatteryInfo(val pct: Int, val charging: Boolean, val tempC: Float)

fun readBattery(ctx: Context): BatteryInfo {
    return try {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) > 0
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        BatteryInfo(pct, plugged, if (temp > 0) temp / 10f else -1f)
    } catch (_: Throwable) {
        BatteryInfo(-1, false, -1f)
    }
}

@Composable
fun rememberBattery(): BatteryInfo {
    val ctx = LocalContext.current
    var b by remember { mutableStateOf(readBattery(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            b = readBattery(ctx)
        }
    }
    return b
}

/** Tần số quét cao nhất màn hình hỗ trợ (Hz), 0 nếu không biết. */
fun maxRefreshHz(ctx: Context): Int = try {
    val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
    Math.round(d.supportedModes.maxOf { it.refreshRate })
} catch (_: Throwable) {
    0
}

/** Bỏ dấu tiếng Việt và chữ hoa để tìm kiếm không phân biệt dấu. */
fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd').replace('Đ', 'D')
        .lowercase()

fun fmtDuration(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "dưới 1 phút"
        m < 60 -> "$m phút"
        else -> {
            val h = m / 60
            val mm = m % 60
            if (mm == 0L) "$h giờ" else "$h giờ $mm phút"
        }
    }
}

fun fmtLast(ts: Long, now: Long = System.currentTimeMillis()): String {
    val tz = TimeZone.getDefault()
    val day = 86_400_000L
    val a = (now + tz.getOffset(now)) / day
    val b = (ts + tz.getOffset(ts)) / day
    val d = (a - b).toInt()
    return when {
        d <= 0 -> "hôm nay"
        d == 1 -> "hôm qua"
        else -> "$d ngày trước"
    }
}

fun fmtTime(ms: Long): String {
    val tz = TimeZone.getDefault()
    val t = (ms + tz.getOffset(ms)) / 1000
    val s = (t % 60).toInt()
    val m = ((t / 60) % 60).toInt()
    val h = ((t / 3600) % 24).toInt()
    return String.format("%02d:%02d:%02d", h, m, s)
}

/** Đọc StateFlow thành State của Compose. */
@Composable
fun <T> StateFlow<T>.collectAsStateCompat(): State<T> = this.collectAsState()

/** Máy đang có quyền root (qua Shizuku chạy root hoặc su) hay không; tự cập nhật khi trạng thái Shizuku đổi. */
@Composable
fun rememberHasRoot(): Boolean {
    val app = LocalContext.current.app
    val st by app.shizuku.status.collectAsStateCompat()
    val tick = rememberResumeTick()
    return remember(st, tick, com.example.gameturbo.RootShell.available) { app.exec.hasRoot }
}

/** Có quyền shell (Shizuku sẵn sàng hoặc root) để áp dụng chế độ hay không. */
@Composable
fun rememberHasShell(): Boolean {
    val app = LocalContext.current.app
    val st by app.shizuku.status.collectAsStateCompat()
    val tick = rememberResumeTick()
    return remember(st, tick, com.example.gameturbo.RootShell.available) { app.exec.hasShell }
}

/** Tìm Activity chứa Context (Context trong Compose có thể bị bọc nhiều lớp). */
fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

/** Mở app Shizuku nếu đã cài, không thì mở trang tải về. */
fun openShizuku(ctx: Context) {
    try {
        val i = ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
        if (i != null) {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
    } catch (_: Throwable) {
    }
    try {
        val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
        ctx.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Throwable) {
    }
}

fun shizukuInstalled(ctx: Context): Boolean = try {
    ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
    true
} catch (_: Throwable) {
    false
}
