package com.example.gameturbo.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.example.gameturbo.RootShell
import com.example.gameturbo.ShizukuShell
import com.example.gameturbo.core.Core
import com.example.gameturbo.core.Cores
import com.example.gameturbo.core.SysReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Số đo tại một thời điểm. Giá trị -1 nghĩa là chưa đo được. */
data class Stats(
    val fps: Float = -1f,
    val cpuPct: Float = -1f,
    val gpuPct: Float = -1f,
    val gpuMhz: Float = -1f,
    val maxMhz: Float = -1f,
    val avgMhz: Float = -1f,
    val tempC: Float = -1f,
    /** 0 = nhiệt độ CPU/SoC, 1 = nhiệt độ pin (dự phòng) */
    val tempSource: Int = 0,
    val time: Long = 0L,
)

/**
 * Lấy số đo CPU/GPU/FPS/nhiệt độ. Đọc file hệ thống gộp một lần (SysReader) qua Shizuku,
 * phần tính toán nằm ở lõi Rust (hoặc Kotlin dự phòng).
 */
class Sampler(context: Context, private val exec: Exec, private val shizuku: ShizukuShell) {
    private val ctx = context.applicationContext

    private val core: Core get() = Cores.best

    private var gpuCandidates: List<Pair<String, String>>? = null
    private var gpuNode: Pair<String, String>? = null
    private var layer = ""
    private var layerPkg = ""
    private var layerMiss = 0

    fun reset() {
        core.cpuUsageReset()
        layer = ""
        layerPkg = ""
        layerMiss = 0
    }

    private suspend fun resolveGpuCandidates(): List<Pair<String, String>> {
        gpuCandidates?.let { return it }
        val raw = try {
            core.gpuNodes()
        } catch (_: Throwable) {
            ""
        }
        val entries = raw.lineSequence().map { it.trim() }.filter { it.contains('|') }.toList()
        val out = ArrayList<Pair<String, String>>()
        val globs = ArrayList<Pair<String, String>>()
        for (e in entries) {
            val kind = e.substringBefore('|')
            val path = e.substringAfter('|')
            if (path.contains('*')) globs.add(kind to path) else out.add(kind to path)
        }
        // Giải ký tự * bằng shell nếu có, không thì tự duyệt thư mục
        val resolved = ArrayList<Pair<String, String>>()
        if (globs.isNotEmpty()) {
            if (exec.hasShell) {
                val script = globs.joinToString("; ") { (k, p) ->
                    "for f in $p; do [ -e \"\$f\" ] && echo \"$k|\$f\"; done"
                }
                val r = exec.sh(script)
                for (line in r.output.lineSequence()) {
                    val t = line.trim()
                    if (t.contains('|')) resolved.add(t.substringBefore('|') to t.substringAfter('|'))
                }
            } else {
                for ((k, p) in globs) for (f in globLocal(p)) resolved.add(k to f)
            }
        }
        // Giữ đúng thứ tự ưu tiên của lõi: ghép lại theo thứ tự danh sách gốc
        val ordered = ArrayList<Pair<String, String>>()
        for (e in entries) {
            val kind = e.substringBefore('|')
            val path = e.substringAfter('|')
            if (path.contains('*')) {
                val prefix = path.substringBefore('*')
                val suffix = path.substringAfterLast('*')
                ordered.addAll(resolved.filter { it.first == kind && it.second.startsWith(prefix) && it.second.endsWith(suffix) })
            } else {
                ordered.add(kind to path)
            }
        }
        val distinct = ordered.distinct()
        gpuCandidates = distinct
        return distinct
    }

    private fun globLocal(pattern: String): List<String> {
        try {
            val parts = pattern.split('/').filter { it.isNotEmpty() }
            var current = listOf("")
            for (part in parts) {
                val next = ArrayList<String>()
                for (base in current) {
                    val dirPath = if (base.isEmpty()) "/" else base
                    if (!part.contains('*')) {
                        val p = "$base/$part"
                        if (File(p).exists()) next.add(p)
                    } else {
                        val rx = Regex("^" + part.split('*').joinToString(".*") { Regex.escape(it) } + "$")
                        File(dirPath).listFiles()?.forEach { if (rx.matches(it.name)) next.add("$base/${it.name}") }
                    }
                }
                current = next
                if (current.isEmpty()) break
            }
            return current
        } catch (_: Throwable) {
            return emptyList()
        }
    }

    private fun batteryTemp(): Float {
        return try {
            val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val t = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (t > 0) t / 10f else -1f
        } catch (_: Throwable) {
            -1f
        }
    }

    private fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    suspend fun sample(pkg: String?, wantFps: Boolean, wantGpu: Boolean): Stats {
        val now = System.currentTimeMillis()
        var cpu = -1f
        var maxMhz = -1f
        var avgMhz = -1f
        var temp = -1f
        var tempSrc = 0
        var gpuPct = -1f
        var gpuMhz = -1f
        var fps = -1f
        try {
            val cands = if (wantGpu) resolveGpuCandidates() else emptyList()
            val gpuKeys: List<Pair<String, String>> = gpuNode?.let { listOf(it) } ?: cands
            val keys = StringBuilder("/proc/stat\n@freqs\n@thermal\n")
            for ((_, p) in gpuKeys) keys.append(p).append('\n')
            val k = keys.toString()
            val raw = withContext(Dispatchers.IO) {
                val viaShell = if (shizuku.isReady) shizuku.readMany(k) else ""
                if (viaShell.isNotEmpty()) viaShell else SysReader.readMany(k)
            }
            val map = SysReader.parse(raw)

            map["/proc/stat"]?.let { if (it.isNotBlank()) cpu = core.cpuUsage(it) }
            map["@freqs"]?.let {
                if (it.isNotBlank()) {
                    val fs = core.freqStats(it)
                    for (part in fs.split(';')) {
                        val kv = part.split('=')
                        if (kv.size == 2) {
                            val v = kv[1].toFloatOrNull() ?: -1f
                            if (kv[0] == "max") maxMhz = v else if (kv[0] == "avg") avgMhz = v
                        }
                    }
                }
            }
            map["@thermal"]?.let { if (it.isNotBlank()) temp = core.cpuTemp(it) }
            if (temp < 0f) {
                temp = batteryTemp()
                tempSrc = 1
            }

            if (wantGpu) {
                var node = gpuNode
                if (node == null) {
                    for (c in gpuKeys) {
                        val v = core.gpuParse(c.first, c.second, map[c.second] ?: "")
                        if (v >= 0f) {
                            node = c
                            gpuNode = c
                            break
                        }
                    }
                }
                if (node != null) {
                    val v = core.gpuParse(node.first, node.second, map[node.second] ?: "")
                    if (v >= 0f) {
                        if (node.first == "busy") gpuPct = v else gpuMhz = v
                    }
                }
            }

            if (wantFps && pkg != null && exec.hasShell) fps = measureFps(pkg)
        } catch (_: Throwable) {
        }
        return Stats(fps, cpu, gpuPct, gpuMhz, maxMhz, avgMhz, temp, tempSrc, now)
    }

    private suspend fun measureFps(pkg: String): Float {
        if (layer.isEmpty() || layerPkg != pkg || layerMiss >= 3) {
            val r = exec.sh("dumpsys SurfaceFlinger --list")
            layer = core.findLayer(r.output, pkg)
            layerPkg = pkg
            layerMiss = 0
            if (layer.isEmpty()) return -1f
        }
        val r = exec.sh("dumpsys SurfaceFlinger --latency ${quote(layer)}")
        val f = core.fpsFromLatency(r.output)
        if (f < 0f) layerMiss++ else layerMiss = 0
        return f
    }

    /** Gói đang ở trên cùng màn hình, "" nếu không biết. */
    suspend fun focusedPackage(): String {
        if (!exec.hasShell) return ""
        var r = exec.sh("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
        var p = core.focusedPackage(r.output)
        if (p.isEmpty()) {
            r = exec.sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'")
            p = core.focusedPackage(r.output)
        }
        return p
    }
}
