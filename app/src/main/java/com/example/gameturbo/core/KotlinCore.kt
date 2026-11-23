package com.example.gameturbo.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Bản Kotlin của lõi, dùng khi libgtcore.so không tải được và để so sánh hiệu năng với bản Rust.
 * Phải cho kết quả giống bản Rust (xem NATIVE_API.md).
 */
class KotlinCore : Core {
    override val name: String get() = "Kotlin"

    // ------------------------------------------------------------ CPU
    private var havePrev = false
    private var prevTotal = 0L
    private var prevIdle = 0L

    private fun isPkgChar(c: Char): Boolean = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == '_' || c == '.'

    private fun isValidPkg(s: String): Boolean = s.isNotEmpty() && s.length <= 255 && s.all { isPkgChar(it) }

    private fun tokens(s: String): List<String> {
        val out = ArrayList<String>(12)
        var i = 0
        val n = s.length
        while (i < n) {
            while (i < n && s[i].isWhitespace()) i++
            if (i >= n) break
            val st = i
            while (i < n && !s[i].isWhitespace()) i++
            out.add(s.substring(st, i))
        }
        return out
    }

    private fun lastToken(s: String): String? {
        var e = s.length
        while (e > 0 && s[e - 1].isWhitespace()) e--
        if (e == 0) return null
        var b = e
        while (b > 0 && !s[b - 1].isWhitespace()) b--
        return s.substring(b, e)
    }

    /** (total, idleAll) hoặc null. */
    private fun parseStat(text: String): LongArray? {
        for (line in text.lineSequence()) {
            val l = line.trimStart()
            if (!l.startsWith("cpu")) continue
            val rest = l.substring(3)
            if (rest.isEmpty() || !rest[0].isWhitespace()) continue
            val tk = tokens(rest)
            val v = LongArray(8)
            var n = 0
            for (t in tk) {
                if (n >= 8) break
                v[n] = t.toLongOrNull() ?: return null
                n++
            }
            if (n < 4) return null
            var total = 0L
            for (x in v) total += x
            return longArrayOf(total, v[3] + v[4])
        }
        return null
    }

    override fun cpuUsage(procStat: String): Float {
        val cur = parseStat(procStat) ?: return -1f
        val had = havePrev
        val pt = prevTotal
        val pi = prevIdle
        havePrev = true
        prevTotal = cur[0]
        prevIdle = cur[1]
        if (!had) return -1f
        if (cur[0] <= pt || cur[1] < pi) return -1f
        val dt = (cur[0] - pt).toDouble()
        val di = (cur[1] - pi).toDouble()
        val pct = 100.0 * (dt - di) / dt
        return pct.coerceIn(0.0, 100.0).toFloat()
    }

    override fun cpuUsageReset() {
        havePrev = false
    }

    override fun freqStats(lines: String): String {
        var n = 0
        var sum = 0.0
        var max = 0.0
        for (line in lines.lineSequence()) {
            val tok = lastToken(line) ?: continue
            val khz = tok.toDoubleOrNull() ?: continue
            if (!(khz > 0.0) || khz.isInfinite() || khz.isNaN()) continue
            val mhz = khz / 1000.0
            n++
            sum += mhz
            if (mhz > max) max = mhz
        }
        if (n == 0) return "max=-1;avg=-1;n=0"
        return String.format(Locale.US, "max=%.1f;avg=%.1f;n=%d", max, sum / n, n)
    }

    // ------------------------------------------------------------ GPU
    override fun gpuNodes(): String = GPU_NODES

    private fun firstNumber(s: String): Double? {
        var i = 0
        val n = s.length
        while (i < n && s[i] !in '0'..'9') i++
        if (i >= n) return null
        val neg = i > 0 && s[i - 1] == '-'
        val start = i
        while (i < n && s[i] in '0'..'9') i++
        if (i + 1 < n && s[i] == '.' && s[i + 1] in '0'..'9') {
            i++
            while (i < n && s[i] in '0'..'9') i++
        }
        val v = s.substring(start, i).toDoubleOrNull() ?: return null
        return if (neg) -v else v
    }

    override fun gpuParse(kind: String, path: String, content: String): Float {
        if (kind == "busy") {
            if (path.endsWith("gpubusy")) {
                val tk = tokens(content)
                val busy = tk.getOrNull(0)?.toDoubleOrNull() ?: return -1f
                val total = tk.getOrNull(1)?.toDoubleOrNull() ?: return -1f
                if (total <= 0.0) return 0f
                return (busy / total * 100.0).coerceIn(0.0, 100.0).toFloat()
            }
            val v = firstNumber(content) ?: return -1f
            return if (v >= 0.0) v.coerceIn(0.0, 100.0).toFloat() else -1f
        }
        if (kind == "freq") {
            val v = firstNumber(content) ?: return -1f
            if (v < 0.0) return -1f
            val mhz = if (v >= 1e7) v / 1e6 else if (v >= 1e4) v / 1e3 else v
            return mhz.toFloat()
        }
        return -1f
    }

    // ------------------------------------------------------------ FPS / layer / focus
    override fun fpsFromLatency(text: String): Float {
        val stamps = ArrayList<Long>(128)
        for (line in text.lineSequence()) {
            val tk = tokens(line)
            if (tk.size < 3) continue
            if (tk[0].toLongOrNull() == null || tk[2].toLongOrNull() == null) continue
            val t = tk[1].toLongOrNull() ?: continue
            if (t > 0 && t != Long.MAX_VALUE) stamps.add(t)
        }
        if (stamps.isEmpty()) return -1f
        var last = Long.MIN_VALUE
        for (t in stamps) if (t > last) last = t
        val lo = last - 1_000_000_000L
        var n = 0
        var tMin = Long.MAX_VALUE
        for (t in stamps) {
            if (t >= lo) {
                n++
                if (t < tMin) tMin = t
            }
        }
        val span = last - tMin
        if (n < 2 || span <= 0) return -1f
        val fps = (n - 1).toDouble() / (span.toDouble() / 1e9)
        return minOf(fps, 240.0).toFloat()
    }

    private fun containsPkg(line: String, pkg: String): Boolean {
        var from = 0
        while (true) {
            val pos = line.indexOf(pkg, from)
            if (pos < 0) return false
            val end = pos + pkg.length
            val beforeOk = pos == 0 || !isPkgChar(line[pos - 1])
            val afterOk = end >= line.length || !isPkgChar(line[end])
            if (beforeOk && afterOk) return true
            from = pos + 1
        }
    }

    override fun findLayer(list: String, pkg: String): String {
        if (pkg.isEmpty()) return ""
        val slash = "$pkg/"
        var bestScore = -1
        var best = ""
        for (raw in list.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || !containsPkg(line, pkg)) continue
            if (LAYER_SKIP.any { line.contains(it) }) continue
            var score = 0
            if (line.contains("SurfaceView")) score += 3
            if (line.contains("(BLAST)")) score += 1
            if (line.contains(slash)) score += 1
            if (score > bestScore) {
                bestScore = score
                best = line
            }
        }
        return best
    }

    private fun pkgInLine(line: String, markerEnd: Int): String? {
        val rest = line.substring(markerEnd)
        val slash = rest.indexOf('/')
        if (slash < 0) return null
        val head = rest.substring(0, slash)
        if (head.isEmpty() || head.last().isWhitespace()) return null
        val pkg = lastToken(head) ?: return null
        if (!pkg.all { isPkgChar(it) }) return null
        if (pkg == "null") return null
        return pkg
    }

    override fun focusedPackage(dump: String): String {
        for (marker in FOCUS_MARKERS) {
            for (line in dump.lineSequence()) {
                var from = 0
                while (true) {
                    val pos = line.indexOf(marker, from)
                    if (pos < 0) break
                    val end = pos + marker.length
                    val p = pkgInLine(line, end)
                    if (p != null) return p
                    from = end
                }
            }
        }
        return ""
    }

    // ------------------------------------------------------------ nhiệt
    override fun cpuTemp(dump: String): Float {
        var best = Double.NaN
        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val ty: String
            val value: String
            val tab = line.lastIndexOf('\t')
            if (tab >= 0) {
                ty = line.substring(0, tab).trim()
                value = line.substring(tab + 1).trim()
            } else {
                var i = line.length - 1
                while (i >= 0 && !line[i].isWhitespace()) i--
                if (i < 0) continue
                ty = line.substring(0, i).trim()
                value = line.substring(i + 1).trim()
            }
            if (ty.isEmpty() || !cpuLike(ty)) continue
            val v = value.toDoubleOrNull() ?: continue
            val c = if (v >= 1000.0) v / 1000.0 else if (v >= 150.0) v / 10.0 else v
            if (c < 10.0 || c > 130.0) continue
            if (best.isNaN() || c > best) best = c
        }
        if (best.isNaN()) return -1f
        return (Math.round(best * 10.0) / 10.0).toFloat()
    }

    private fun cpuLike(type: String): Boolean {
        val t = type.lowercase(Locale.ROOT)
        return TEMP_INCLUDE.any { t.contains(it) } && TEMP_EXCLUDE.none { t.contains(it) }
    }

    // ------------------------------------------------------------ kế hoạch lệnh
    private class P {
        var mode = 1
        var sdk = 0
        var pkg = ""
        var res = 100
        var fps = 0
        var refresh = 0
        var maxrefresh = 0
        var fixed = false
        var gm = "performance"
        var root = false
        var boost = false
    }

    private fun flag(v: String): Boolean = v == "1" || v.equals("true", ignoreCase = true)

    private fun parseParams(text: String): P {
        val p = P()
        for (raw in text.lineSequence()) {
            val eq = raw.indexOf('=')
            if (eq < 0) continue
            val k = raw.substring(0, eq).trim()
            val v = raw.substring(eq + 1).trim()
            when (k) {
                "mode" -> v.toIntOrNull()?.let { if (it in 0..3) p.mode = it }
                "sdk" -> p.sdk = maxOf(0, v.toIntOrNull() ?: 0)
                "pkg" -> p.pkg = if (isValidPkg(v)) v else ""
                "res" -> p.res = (v.toIntOrNull() ?: 100).coerceIn(50, 100)
                "fps" -> p.fps = (v.toIntOrNull() ?: 0).coerceIn(0, 1000)
                "refresh" -> p.refresh = (v.toIntOrNull() ?: 0).coerceIn(0, 1000)
                "maxrefresh" -> p.maxrefresh = (v.toIntOrNull() ?: 0).coerceIn(0, 1000)
                "fixed" -> p.fixed = flag(v)
                "gm" -> p.gm = if (v == "none" || v == "battery" || v == "standard" || v == "performance") v else "performance"
                "root" -> p.root = flag(v)
                "boost" -> p.boost = flag(v)
            }
        }
        return p
    }

    private fun tenths(res: Int): String =
        BigDecimal(res.toDouble() / 100.0).setScale(1, RoundingMode.HALF_EVEN).toPlainString()

    override fun planMode(params: String): String {
        val p = parseParams(params)
        val gm: String
        val res: Int
        val fps: Int
        val refresh: Int
        val fixed: Boolean
        val boost: Boolean
        when (p.mode) {
            0 -> {
                gm = "battery"; res = minOf(p.res, 70)
                fps = if (p.fps == 0 || p.fps > 30) 30 else p.fps
                refresh = 60; fixed = false; boost = false
            }
            2 -> {
                gm = "performance"; res = p.res; fps = p.fps
                refresh = if (p.maxrefresh > 0) p.maxrefresh else 0
                fixed = true; boost = p.root
            }
            3 -> {
                gm = p.gm; res = p.res; fps = p.fps; refresh = p.refresh
                fixed = p.fixed; boost = p.boost && p.root
            }
            else -> {
                gm = "standard"; res = minOf(p.res, 90)
                fps = if (p.fps == 0 || p.fps > 60) 60 else p.fps
                refresh = 0; fixed = false; boost = false
            }
        }
        val out = ArrayList<String>()
        if (p.sdk >= 31 && p.pkg.isNotEmpty() && gm != "none") {
            val sb = StringBuilder("S|cmd game set --mode ").append(gm)
            if (res < 100) sb.append(" --downscale ").append(tenths(res))
            if (fps > 0) sb.append(" --fps ").append(fps)
            sb.append(' ').append(p.pkg)
            out.add(sb.toString())
            out.add("F|cmd game mode $gm ${p.pkg}")
        }
        if (refresh > 0) {
            out.add("S|settings put system min_refresh_rate $refresh")
            out.add("S|settings put system peak_refresh_rate $refresh")
        }
        if (fixed) out.add("S|cmd power set-fixed-performance-mode-enabled true")
        if (boost) {
            out.add("R|$ROOT_SAVE")
            out.add("R|$ROOT_CPU_BOOST")
            out.add("R|$ROOT_GPU_BOOST")
        }
        return out.joinToString("\n")
    }

    override fun planRestore(params: String): String {
        val p = parseParams(params)
        val out = ArrayList<String>()
        if (p.sdk >= 31 && p.pkg.isNotEmpty()) out.add("S|cmd game reset ${p.pkg}")
        out.add("S|settings delete system min_refresh_rate")
        out.add("S|settings delete system peak_refresh_rate")
        out.add("S|cmd power set-fixed-performance-mode-enabled false")
        if (p.root) {
            out.add("R|$ROOT_CPU_RESTORE")
            out.add("R|$ROOT_GPU_RESTORE")
        }
        return out.joinToString("\n")
    }

    // ------------------------------------------------------------ danh sách app
    override fun runningThirdParty(pmList: String, ps: String): String {
        val installed = HashSet<String>()
        for (raw in pmList.lineSequence()) {
            val l = raw.trim()
            if (!l.startsWith("package:")) continue
            val rest = l.substring(8)
            val eq = rest.lastIndexOf('=')
            val name = (if (eq >= 0) rest.substring(eq + 1) else rest).trim()
            if (isValidPkg(name)) installed.add(name)
        }
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (line in ps.lineSequence()) {
            val tok = lastToken(line) ?: continue
            val colon = tok.indexOf(':')
            val name = if (colon >= 0) tok.substring(0, colon) else tok
            if (name.isEmpty() || name !in installed) continue
            if (seen.add(name)) out.add(name)
        }
        return out.joinToString("\n")
    }

    private fun protectedPkg(p: String): Boolean =
        p == "com.example.gameturbo" || p == "moe.shizuku.privileged.api" ||
            p.startsWith("com.google.android.gms") || p.startsWith("com.google.android.gsf") ||
            p.startsWith("com.android.") || p == "android" || p.startsWith("android.")

    override fun killList(running: String, keep: String, game: String): String {
        val keepSet = HashSet<String>()
        for (l in keep.lineSequence()) {
            val t = l.trim()
            if (t.isNotEmpty()) keepSet.add(t)
        }
        val g = game.trim()
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (l in running.lineSequence()) {
            val p = l.trim()
            if (p.isEmpty() || !isValidPkg(p) || p == g || p in keepSet || protectedPkg(p)) continue
            if (seen.add(p)) out.add(p)
        }
        return out.joinToString("\n")
    }

    // ------------------------------------------------------------ bảo vệ nhiệt
    override fun guardUpdate(tempC: Float, source: Int): Int {
        if (tempC.isNaN() || tempC < 0f) return 0
        val hot: Float
        val crit: Float
        val reset: Float
        if (source == 1) { hot = 41f; crit = 45f; reset = 40f } else { hot = 72f; crit = 80f; reset = 70f }
        if (tempC < reset) {
            triggered = false
            return 0
        }
        if (tempC >= crit) {
            val was = triggered
            triggered = true
            return if (was) 3 else 2
        }
        if (tempC >= hot) return 1
        return 0
    }

    private var triggered = false

    override fun guardReset() {
        triggered = false
    }

    companion object {
        private val LAYER_SKIP = listOf("Background for", "Bounds for", "Splash", "Letterbox")
        private val FOCUS_MARKERS = listOf("mCurrentFocus=", "mFocusedApp=", "topResumedActivity=", "mResumedActivity:", "ResumedActivity")
        private val TEMP_INCLUDE = listOf(
            "cpu", "soc", "tsens", "mtktscpu", "cluster", "big", "little", "prime", "gold", "silver", "apc",
            "ap_therm", "ap-therm", "ssoc",
        )
        private val TEMP_EXCLUDE = listOf(
            "battery", "batt", "skin", "charger", "wifi", "modem", "gpu", "ddr", "usb", "xo", "pa-", "quiet",
            "flash", "camera", "display", "bcl",
        )

        private const val GPU_NODES = "busy|/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage\n" +
            "busy|/sys/class/kgsl/kgsl-3d0/gpubusy\n" +
            "freq|/sys/class/kgsl/kgsl-3d0/gpuclk\n" +
            "freq|/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq\n" +
            "busy|/sys/devices/platform/*.mali/utilization\n" +
            "busy|/sys/class/devfreq/*.mali/load\n" +
            "freq|/sys/class/devfreq/*.mali/cur_freq\n" +
            "busy|/sys/class/devfreq/*.gpu/load\n" +
            "freq|/sys/class/devfreq/*.gpu/cur_freq\n" +
            "busy|/sys/class/misc/mali0/device/utilization\n" +
            "busy|/sys/kernel/gpu/gpu_busy\n" +
            "freq|/sys/kernel/gpu/gpu_clock\n" +
            "busy|/proc/mali/utilization"

        // Các đoạn script root: giữ y hệt bản Rust (rust/src/plan.rs)
        private const val ROOT_SAVE =
            "[ -f /data/local/tmp/gt_cpu_prev ] || { for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -d \"\$p\" ] && echo \"\$p \$(cat \$p/scaling_governor 2>/dev/null) \$(cat \$p/scaling_min_freq 2>/dev/null)\"; done > /data/local/tmp/gt_cpu_prev; }; true"
        private const val ROOT_CPU_BOOST =
            "for p in /sys/devices/system/cpu/cpufreq/policy*; do [ -d \"\$p\" ] || continue; cat \$p/cpuinfo_max_freq > \$p/scaling_max_freq 2>/dev/null; cat \$p/cpuinfo_max_freq > \$p/scaling_min_freq 2>/dev/null; echo performance > \$p/scaling_governor 2>/dev/null; done; true"
        private const val ROOT_GPU_BOOST =
            "d=/sys/class/kgsl/kgsl-3d0; if [ -d \$d ]; then for f in force_clk_on force_bus_on force_rail_on; do echo 1 > \$d/\$f 2>/dev/null; done; if [ -r \$d/devfreq/max_freq ]; then cat \$d/devfreq/max_freq > \$d/devfreq/min_freq 2>/dev/null; fi; fi; true"
        private const val ROOT_CPU_RESTORE =
            "if [ -f /data/local/tmp/gt_cpu_prev ]; then while read p g m; do [ -n \"\$p\" ] || continue; [ -n \"\$g\" ] && echo \$g > \$p/scaling_governor 2>/dev/null; [ -n \"\$m\" ] && echo \$m > \$p/scaling_min_freq 2>/dev/null; done < /data/local/tmp/gt_cpu_prev; rm -f /data/local/tmp/gt_cpu_prev; fi; true"
        private const val ROOT_GPU_RESTORE =
            "d=/sys/class/kgsl/kgsl-3d0; if [ -d \$d ]; then for f in force_clk_on force_bus_on force_rail_on; do echo 0 > \$d/\$f 2>/dev/null; done; fi; true"
    }
}
