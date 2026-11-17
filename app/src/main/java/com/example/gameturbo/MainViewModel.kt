package com.example.gameturbo

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class AppItem(
    val packageName: String,
    val label: String,
    val isGame: Boolean,
)

data class GameProfile(
    /** 1.0 = độ phân giải gốc */
    val downscale: Float = 1f,
    /** 0 = không giới hạn FPS */
    val fps: Int = 0,
) {
    val isDefault: Boolean get() = downscale >= 0.999f && fps == 0
}

data class UiState(
    val status: ShizukuStatus = ShizukuStatus.UNAVAILABLE,
    val shizukuInstalled: Boolean = false,
    val apps: List<AppItem> = emptyList(),
    val loadingApps: Boolean = true,
    val query: String = "",
    val selected: String? = null,
    val profiles: Map<String, GameProfile> = emptyMap(),
    val refreshRate: Int = 0,
    val fixedPerformance: Boolean = false,
    val resolutionPercent: Int = 100,
    val tempC: Float? = null,
    val thermalStatus: Int = 0,
    val busy: Boolean = false,
    val log: List<String> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app
    private val prefs = app.getSharedPreferences("gameturbo", Context.MODE_PRIVATE)
    private val shell = ShizukuShell(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init {
        shell.start()
        viewModelScope.launch {
            shell.status.collect { st ->
                _ui.update { it.copy(status = st) }
                if (st == ShizukuStatus.READY) readSystemState()
            }
        }
        loadApps()
    }

    override fun onCleared() {
        shell.stop()
    }

    // ---------------------------------------------------------------- giám sát

    /** Gọi khi màn hình hiển thị: cập nhật nhiệt độ và trạng thái Shizuku mỗi 3 giây. */
    suspend fun monitor() {
        while (true) {
            if (_ui.value.status != ShizukuStatus.READY) shell.refresh()

            val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val rawTemp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            val temp = if (rawTemp == null || rawTemp == Int.MIN_VALUE) null else rawTemp / 10f
            val thermal = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus

            _ui.update {
                it.copy(tempC = temp, thermalStatus = thermal, shizukuInstalled = isShizukuInstalled())
            }
            delay(3000)
        }
    }

    @Suppress("DEPRECATION")
    private fun isShizukuInstalled(): Boolean = try {
        ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    // ---------------------------------------------------------------- danh sách app

    private fun loadApps() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcher, 0)
                    .map { it.activityInfo.applicationInfo }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != ctx.packageName }
                    .map { ai -> AppItem(ai.packageName, pm.getApplicationLabel(ai).toString(), isGame(ai)) }
                    .sortedWith(compareByDescending<AppItem> { it.isGame }.thenBy { it.label.lowercase() })
            }
            val profiles = items.associate { it.packageName to loadProfile(it.packageName) }
            _ui.update { it.copy(apps = items, profiles = profiles, loadingApps = false) }
        }
    }

    @Suppress("DEPRECATION")
    private fun isGame(ai: ApplicationInfo): Boolean =
        ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }

    fun select(pkg: String) = _ui.update { it.copy(selected = if (it.selected == pkg) null else pkg) }

    // ---------------------------------------------------------------- profile từng game

    private fun loadProfile(pkg: String) =
        GameProfile(prefs.getFloat("ds_$pkg", 1f), prefs.getInt("fps_$pkg", 0))

    fun setDownscale(pkg: String, value: Float) = updateProfile(pkg) { it.copy(downscale = value) }

    fun setFps(pkg: String, value: Int) = updateProfile(pkg) { it.copy(fps = value) }

    private fun updateProfile(pkg: String, change: (GameProfile) -> GameProfile) {
        val profile = change(_ui.value.profiles[pkg] ?: GameProfile())
        prefs.edit().putFloat("ds_$pkg", profile.downscale).putInt("fps_$pkg", profile.fps).apply()
        _ui.update { it.copy(profiles = it.profiles + (pkg to profile)) }
    }

    /** Áp dụng Game Mode (Performance) + độ phân giải + FPS cho một game. */
    fun applyGame(pkg: String, play: Boolean) {
        viewModelScope.launch {
            if (!ensureReady()) return@launch
            if (Build.VERSION.SDK_INT < 31) {
                log("Game Mode cần Android 12 trở lên.")
                return@launch
            }
            val profile = _ui.value.profiles[pkg] ?: GameProfile()
            busy {
                val sb = StringBuilder("cmd game set --mode performance")
                if (profile.downscale < 0.999f) {
                    sb.append(" --downscale ").append(String.format(Locale.US, "%.1f", profile.downscale))
                }
                if (profile.fps > 0) sb.append(" --fps ").append(profile.fps)
                sb.append(' ').append(pkg)

                var result = run(sb.toString())
                if (!result.ok) {
                    // Android 12 chưa có "cmd game set", chỉ đặt được chế độ Performance
                    log("Thử lại bằng lệnh đơn giản (không có độ phân giải/FPS riêng).")
                    result = run("cmd game mode performance $pkg")
                }
                if (result.ok && play) {
                    run("am kill-all")
                    launchApp(pkg)
                }
            }
        }
    }

    fun resetGame(pkg: String) {
        viewModelScope.launch {
            prefs.edit().remove("ds_$pkg").remove("fps_$pkg").apply()
            _ui.update { it.copy(profiles = it.profiles + (pkg to GameProfile())) }
            if (!ensureReady()) return@launch
            busy { run("cmd game reset $pkg") }
        }
    }

    private fun launchApp(pkg: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    // ---------------------------------------------------------------- tuỳ chọn hệ thống

    fun setRefreshRate(hz: Int) {
        viewModelScope.launch {
            if (!ensureReady()) return@launch
            busy {
                if (hz == 0) {
                    restoreRefreshRate()
                } else {
                    saveOriginalRefreshRate()
                    run("settings put system min_refresh_rate $hz")
                    run("settings put system peak_refresh_rate $hz")
                }
                _ui.update { it.copy(refreshRate = hz) }
            }
        }
    }

    private suspend fun saveOriginalRefreshRate() {
        if (prefs.contains("orig_min")) return
        val min = shell.exec("settings get system min_refresh_rate").output.trim()
        val peak = shell.exec("settings get system peak_refresh_rate").output.trim()
        prefs.edit().putString("orig_min", min).putString("orig_peak", peak).apply()
    }

    private suspend fun restoreRefreshRate() {
        restoreSetting("min_refresh_rate", prefs.getString("orig_min", null))
        restoreSetting("peak_refresh_rate", prefs.getString("orig_peak", null))
        prefs.edit().remove("orig_min").remove("orig_peak").apply()
    }

    private suspend fun restoreSetting(key: String, original: String?) {
        if (original?.toFloatOrNull() == null) {
            run("settings delete system $key")
        } else {
            run("settings put system $key $original")
        }
    }

    fun setFixedPerformance(enabled: Boolean) {
        viewModelScope.launch {
            if (!ensureReady()) return@launch
            busy {
                val r = run("cmd power set-fixed-performance-mode-enabled $enabled")
                if (r.ok) _ui.update { it.copy(fixedPerformance = enabled) }
            }
        }
    }

    fun killBackground() {
        viewModelScope.launch {
            if (!ensureReady()) return@launch
            busy { run("am kill-all") }
        }
    }

    fun setResolution(percent: Int) {
        viewModelScope.launch {
            if (!ensureReady()) return@launch
            busy {
                if (percent == 100) {
                    run("wm size reset")
                    run("wm density reset")
                } else {
                    val physical = parseSize(shell.exec("wm size").output, "Physical")
                    val density = Regex("Physical density:\\s*(\\d+)")
                        .find(shell.exec("wm density").output)?.groupValues?.get(1)?.toIntOrNull()
                    if (physical == null || density == null) {
                        log("Không đọc được kích thước màn hình, bỏ qua.")
                    } else {
                        val f = percent / 100f
                        val w = even((physical.first * f).roundToInt())
                        val h = even((physical.second * f).roundToInt())
                        run("wm size ${w}x$h")
                        // Giảm density theo cùng tỉ lệ để giao diện không bị phóng to
                        run("wm density ${(density * f).roundToInt()}")
                    }
                }
                readSystemState()
            }
        }
    }

    /** Trả mọi thay đổi hệ thống về mặc định (không đụng tới profile từng game). */
    fun restoreAll() {
        viewModelScope.launch {
            if (!ensureReady()) return@launch
            busy {
                restoreRefreshRate()
                run("cmd power set-fixed-performance-mode-enabled false")
                run("wm size reset")
                run("wm density reset")
                _ui.update { it.copy(refreshRate = 0, fixedPerformance = false, resolutionPercent = 100) }
                readSystemState()
            }
        }
    }

    /** Đọc trạng thái thật của máy để giao diện không hiển thị sai. */
    private suspend fun readSystemState() {
        val minRefresh = shell.exec("settings get system min_refresh_rate").output.toFloatOrNull()?.roundToInt() ?: 0
        val sizeText = shell.exec("wm size").output
        val physical = parseSize(sizeText, "Physical")
        val override = parseSize(sizeText, "Override")
        val percent = if (physical == null || override == null) {
            100
        } else {
            val actual = override.first * 100f / physical.first
            RESOLUTION_STEPS.minByOrNull { abs(it - actual) } ?: 100
        }
        _ui.update {
            it.copy(
                refreshRate = if (minRefresh in REFRESH_STEPS) minRefresh else 0,
                resolutionPercent = percent,
            )
        }
    }

    private fun parseSize(text: String, label: String): Pair<Int, Int>? {
        val m = Regex("$label size:\\s*(\\d+)x(\\d+)").find(text) ?: return null
        val w = m.groupValues[1].toIntOrNull() ?: return null
        val h = m.groupValues[2].toIntOrNull() ?: return null
        return w to h
    }

    private fun even(v: Int) = if (v % 2 == 0) v else v + 1

    // ---------------------------------------------------------------- Shizuku

    fun requestPermission() = shell.requestPermission()

    fun openShizuku() {
        val intent = ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun openShizukuDownload() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    // ---------------------------------------------------------------- tiện ích

    private fun ensureReady(): Boolean {
        if (_ui.value.status == ShizukuStatus.READY) return true
        log("Shizuku chưa sẵn sàng. Hãy khởi động Shizuku và cấp quyền cho app.")
        return false
    }

    private suspend fun busy(block: suspend () -> Unit) {
        _ui.update { it.copy(busy = true) }
        try {
            block()
        } finally {
            _ui.update { it.copy(busy = false) }
        }
    }

    private suspend fun run(command: String): ShizukuShell.Result {
        val r = shell.exec(command)
        val sb = StringBuilder("$ ").append(command)
        if (r.output.isNotBlank()) sb.append('\n').append(r.output.lines().take(8).joinToString("\n"))
        if (!r.ok) sb.append("\n→ lỗi (mã ").append(r.code).append(')')
        log(sb.toString())
        return r
    }

    private fun log(line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _ui.update { it.copy(log = (listOf("[$time] $line") + it.log).take(40)) }
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        val REFRESH_STEPS = listOf(60, 90, 120)
        val RESOLUTION_STEPS = listOf(100, 90, 80, 70)
    }
}
