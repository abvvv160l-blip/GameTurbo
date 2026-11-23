package com.example.gameturbo.engine

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.Display
import com.example.gameturbo.App
import com.example.gameturbo.core.Cores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.provider.Settings as AndroidSettings

sealed class TurboEvent {
    object UltOn : TurboEvent()
    class Warn(val tempC: Float) : TurboEvent()
    class Fallback(val tempC: Float) : TurboEvent()
    class GameStart(val pkg: String) : TurboEvent()
    class Toast(val text: String, val level: Int = 0) : TurboEvent()
}

data class LogEntry(val time: Long, val level: Int, val text: String)

data class Session(val pkg: String, val label: String, val startMs: Long)

/**
 * Bộ não của app: áp dụng chế độ cho game, dọn app nền, theo dõi nhiệt độ, nhật ký.
 * Mọi lệnh hệ thống đi qua lõi (Rust hoặc Kotlin dự phòng) để lập kế hoạch rồi chạy bằng [Exec].
 */
class TurboController(private val app: App) {

    private val settings get() = app.settings
    private val exec get() = app.exec
    private val core get() = Cores.best

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    val sampler = Sampler(app, app.exec, app.shizuku)

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    /** Đã bị hạ về Ổn định vì nóng trong phiên này: tạm bỏ các tuỳ chọn tăng xung. */
    private val _safe = MutableStateFlow(false)
    val safe: StateFlow<Boolean> = _safe

    private val _log = MutableStateFlow(loadLog())
    val log: StateFlow<List<LogEntry>> = _log

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    val events: SharedFlow<TurboEvent> = MutableSharedFlow<TurboEvent>(extraBufferCapacity = 16)
    private val eventsOut get() = events as MutableSharedFlow<TurboEvent>

    private var lastWarnAt = 0L

    // ------------------------------------------------------------------ nhật ký

    private fun loadLog(): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        try {
            for (line in settings.logText.lineSequence()) {
                val a = line.indexOf('|')
                val b = line.indexOf('|', a + 1)
                if (a < 0 || b < 0) continue
                val t = line.substring(0, a).toLongOrNull() ?: continue
                val l = line.substring(a + 1, b).toIntOrNull() ?: 0
                out.add(LogEntry(t, l, line.substring(b + 1)))
            }
        } catch (_: Throwable) {
        }
        return out
    }

    fun log(text: String, level: Int = 0) {
        val e = LogEntry(System.currentTimeMillis(), level, text.replace('\n', ' '))
        _log.update { (it + e).takeLast(60) }
        try {
            settings.logText = _log.value.joinToString("\n") { "${it.time}|${it.level}|${it.text}" }
        } catch (_: Throwable) {
        }
    }

    fun clearLog() {
        _log.value = emptyList()
        settings.logText = ""
    }

    fun emit(e: TurboEvent) {
        try {
            when (e) {
                is TurboEvent.UltOn -> app.sounds.ultOn()
                is TurboEvent.Warn -> app.sounds.warn()
                is TurboEvent.Fallback -> app.sounds.fallback()
                is TurboEvent.GameStart -> app.sounds.playGame()
                is TurboEvent.Toast -> {}
            }
        } catch (_: Throwable) {
        }
        eventsOut.tryEmit(e)
    }

    // ------------------------------------------------------------------ kế hoạch

    private fun maxRefresh(): Int = try {
        val dm = app.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        Math.round(d.supportedModes.maxOf { it.refreshRate })
    } catch (_: Throwable) {
        0
    }

    private fun b(v: Boolean) = if (v) 1 else 0

    fun buildPlan(mode: Int, pkg: String): String {
        val s = settings
        val root = b(exec.hasRoot)
        val maxHz = maxRefresh()
        val sdk = Build.VERSION.SDK_INT
        val sb = StringBuilder()
        sb.append("mode=").append(mode).append('\n')
        sb.append("sdk=").append(sdk).append('\n')
        sb.append("pkg=").append(pkg).append('\n')
        sb.append("res=").append(s.resOf(pkg)).append('\n')
        sb.append("fps=").append(s.fpsOf(pkg)).append('\n')
        sb.append("maxrefresh=").append(maxHz).append('\n')
        sb.append("root=").append(root).append('\n')
        if (mode == 3) {
            val gm = arrayOf("none", "battery", "standard", "performance")
            sb.append("gm=").append(gm[s.customGm.coerceIn(0, 3)]).append('\n')
            sb.append("refresh=").append(s.minHz).append('\n')
            sb.append("fixed=").append(b(s.fixedPerf)).append('\n')
            sb.append("boost=").append(b(s.lockClock)).append('\n')
        }
        val lines = LinkedHashSet<String>()
        for (l in core.planMode(sb.toString()).lineSequence()) if (l.isNotBlank()) lines.add(l)

        // Công tắc bên phải (Hệ thống) cộng thêm vào chế độ Ổn định / Tối thượng
        if ((mode == 1 || mode == 2) && !_safe.value) {
            val o = "mode=3\nsdk=$sdk\npkg=\nres=100\nfps=0\nmaxrefresh=$maxHz\nroot=$root\n" +
                "gm=none\nrefresh=${s.minHz}\nfixed=${b(s.fixedPerf)}\nboost=${b(s.lockClock)}\n"
            for (l in core.planMode(o).lineSequence()) if (l.isNotBlank()) lines.add(l)
        }
        return lines.joinToString("\n")
    }

    private fun restorePlan(pkg: String): String =
        core.planRestore("sdk=${Build.VERSION.SDK_INT}\npkg=$pkg\nroot=${b(exec.hasRoot)}")

    fun hasRoot(): Boolean = exec.hasRoot

    fun modeName(m: Int): String = when (m) {
        0 -> "Tiết kiệm pin"
        1 -> "Ổn định"
        2 -> "Tối thượng"
        else -> "Tùy chỉnh"
    }

    private suspend fun applyLocked(pkg: String, why: String) {
        val mode = settings.mode
        if (!exec.hasShell) {
            log("Chưa có Shizuku hoặc root nên chưa áp dụng được chế độ", 1)
            return
        }
        val r = exec.runPlan(buildPlan(mode, pkg))
        val label = app.games.labelOf(pkg)
        val ok = r.ran - r.failed
        val extra = buildString {
            if (r.skippedRoot > 0) append(" · bỏ qua ${r.skippedRoot} lệnh cần root")
            if (r.failed > 0) append(" · ${r.failed} lệnh lỗi")
        }
        log("$why: ${modeName(mode)} cho $label ($ok lệnh$extra)", if (r.failed > 0) 1 else 0)
        for (e in r.errors.take(3)) log("Lỗi: $e", 2)
    }

    private suspend fun restoreLocked(pkg: String, why: String) {
        if (!exec.hasShell) return
        val r = exec.runPlan(restorePlan(pkg))
        log("$why: đã trả máy về như cũ" + if (r.failed > 0) " (${r.failed} lệnh lỗi)" else "")
    }

    // ------------------------------------------------------------------ thao tác từ giao diện

    /** Nút "Áp dụng" trong chi tiết game: áp dụng ngay cho game đó, không cần mở game. */
    fun applyNow(pkg: String) {
        scope.launch {
            _busy.value = true
            try {
                lock.withLock { applyLocked(pkg, "Áp dụng") }
                emit(TurboEvent.Toast("Đã áp dụng cho ${app.games.labelOf(pkg)}"))
            } finally {
                _busy.value = false
            }
        }
    }

    /** Nút "Đặt lại": trả hệ thống về như cũ và đưa cấu hình game về mặc định. */
    fun resetGame(pkg: String) {
        settings.setRes(pkg, 100)
        settings.setFps(pkg, 0)
        scope.launch {
            _busy.value = true
            try {
                lock.withLock { restoreLocked(pkg, "Đặt lại") }
                emit(TurboEvent.Toast("Đã đặt lại ${app.games.labelOf(pkg)}"))
            } finally {
                _busy.value = false
            }
        }
    }

    /** Đổi chế độ. Đang trong game thì áp dụng lại ngay. */
    fun setMode(mode: Int) {
        val old = settings.mode
        settings.mode = mode
        _safe.value = false
        core.guardReset()
        if (mode == 2 && old != 2) emit(TurboEvent.UltOn)
        val s = _session.value ?: return
        scope.launch {
            lock.withLock {
                restoreLocked(s.pkg, "Đổi chế độ")
                applyLocked(s.pkg, "Đổi chế độ")
            }
        }
    }

    /** Đổi một công tắc/tham số khi đang chơi: áp dụng lại. */
    fun reapply() {
        val s = _session.value ?: return
        scope.launch {
            lock.withLock {
                restoreLocked(s.pkg, "Cập nhật")
                applyLocked(s.pkg, "Cập nhật")
            }
        }
    }

    /** Bấm "Chơi ngay": mở game và bắt đầu phiên luôn (không phải chờ nhận diện). */
    fun play(pkg: String): Boolean {
        val intent: Intent = app.games.launchIntent(pkg) ?: return false
        scope.launch { onGameFront(pkg) }
        return try {
            app.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ------------------------------------------------------------------ phiên chơi

    suspend fun onGameFront(pkg: String) {
        val cur = _session.value
        if (cur != null && cur.pkg == pkg) return
        lock.withLock {
            val again = _session.value
            if (again != null && again.pkg == pkg) return
            if (again != null) endLocked("Chuyển game")
            startLocked(pkg)
        }
    }

    suspend fun onGameBack(reason: String) {
        lock.withLock { endLocked(reason) }
    }

    private suspend fun startLocked(pkg: String) {
        val label = app.games.labelOf(pkg)
        _session.value = Session(pkg, label, System.currentTimeMillis())
        _safe.value = false
        core.guardReset()
        sampler.reset()
        lastWarnAt = 0L
        log("Vào game: $label")
        emit(TurboEvent.GameStart(pkg))
        if (settings.killBg && settings.killTrigger == 0) killBackground(pkg)
        applyLocked(pkg, "Vào game")
    }

    private suspend fun endLocked(reason: String) {
        val s = _session.value ?: return
        _session.value = null
        val now = System.currentTimeMillis()
        settings.addPlay(s.pkg, now - s.startMs, now)
        log("Thoát game: ${s.label} (${(now - s.startMs) / 60000} phút)")
        restoreLocked(s.pkg, reason)
        _safe.value = false
        _stats.value = Stats()
    }

    // ------------------------------------------------------------------ số đo và bảo vệ nhiệt

    fun publish(stats: Stats) {
        _stats.value = stats
    }

    private fun hotCapable(): Boolean {
        val m = settings.mode
        return m == 2 || (m == 3 && (settings.lockClock || settings.fixedPerf || settings.customGm == 3)) ||
            (m == 1 && !_safe.value && (settings.lockClock || settings.fixedPerf))
    }

    /** Gọi mỗi lần đo xong. Tự hạ về Ổn định khi CPU/SoC đạt 80 °C. */
    suspend fun guard(st: Stats) {
        val sess = _session.value ?: return
        if (!settings.guardOn || st.tempC <= 0f) return
        val r = core.guardUpdate(st.tempC, st.tempSource)
        val capable = hotCapable()
        when (r) {
            1 -> {
                val now = System.currentTimeMillis()
                if (capable && now - lastWarnAt > 30_000) {
                    lastWarnAt = now
                    log("Máy đang nóng: ${fmtTemp(st.tempC)}", 1)
                    emit(TurboEvent.Warn(st.tempC))
                    emit(TurboEvent.Toast("Máy đang nóng ${fmtTemp(st.tempC)}", 1))
                }
            }
            2 -> {
                if (!capable) {
                    log("Nhiệt độ rất cao: ${fmtTemp(st.tempC)}", 2)
                    return
                }
                log("Quá nóng ${fmtTemp(st.tempC)} — tự về chế độ Ổn định", 2)
                lock.withLock {
                    _safe.value = true
                    if (settings.mode != 0) settings.mode = 1
                    restoreLocked(sess.pkg, "Quá nóng")
                    applyLocked(sess.pkg, "Quá nóng")
                }
                emit(TurboEvent.Fallback(st.tempC))
                emit(TurboEvent.Toast("Máy quá nóng ${fmtTemp(st.tempC)} — đã về Ổn định", 2))
            }
        }
    }

    private fun fmtTemp(t: Float): String = String.format("%.0f°C", t)

    // ------------------------------------------------------------------ dọn app nền

    fun defaultKeep(): List<String> {
        val out = ArrayList<String>()
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            app.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName?.let { out.add(it) }
        } catch (_: Throwable) {
        }
        try {
            AndroidSettings.Secure.getString(app.contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { if (it.isNotEmpty()) out.add(it) }
        } catch (_: Throwable) {
        }
        try {
            val tm = app.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.defaultDialerPackage?.let { out.add(it) }
        } catch (_: Throwable) {
        }
        try {
            Telephony.Sms.getDefaultSmsPackage(app)?.let { out.add(it) }
        } catch (_: Throwable) {
        }
        return out
    }

    private val pkgRegex = Regex("^[A-Za-z0-9_.]+$")

    /** Đóng các app bên thứ ba đang chạy nền, trừ danh sách giữ lại. Trả số app đã dọn. */
    suspend fun killBackground(game: String?): Int {
        if (!exec.hasShell) {
            log("Chưa dọn được app nền: cần Shizuku hoặc root", 1)
            return 0
        }
        val pm = exec.sh("pm list packages -3")
        var ps = exec.sh("ps -A -o NAME")
        if (!ps.ok || ps.output.isBlank()) ps = exec.sh("ps")
        val running = core.runningThirdParty(pm.output, ps.output)
        val keep = (defaultKeep() + settings.keepList).joinToString("\n")
        val list = core.killList(running, keep, game ?: "")
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() && pkgRegex.matches(it) }.toList()
        if (list.isEmpty()) {
            log("Không có app nền nào cần dọn")
            return 0
        }
        val verb = if (settings.killStrong) "am force-stop" else "am kill"
        for (chunk in list.chunked(25)) exec.sh(chunk.joinToString("; ") { "$verb $it" })
        log("Đã dọn ${list.size} app nền")
        return list.size
    }

    /** Nút "Dọn app nền ngay" (trong menu game hoặc app). */
    fun killNow() {
        scope.launch {
            _busy.value = true
            try {
                val n = killBackground(_session.value?.pkg)
                emit(TurboEvent.Toast(if (n > 0) "Đã dọn $n app nền" else "Không có app nền cần dọn"))
            } finally {
                _busy.value = false
            }
        }
    }
}
