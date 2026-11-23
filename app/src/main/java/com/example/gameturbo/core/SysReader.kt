package com.example.gameturbo.core

import java.io.File
import java.io.FileInputStream

/**
 * Đọc nhiều file hệ thống trong một lần gọi, không mở tiến trình con.
 * Dùng chung cho tiến trình app và tiến trình Shizuku (quyền shell), nên chỉ dùng java.io.
 *
 * Mỗi khoá là một đường dẫn file, hoặc khoá đặc biệt:
 *  - `@freqs`   : mọi lõi CPU, mỗi dòng `cpuN<TAB>kHz`
 *  - `@thermal` : các vùng nhiệt giống CPU, mỗi dòng `type<TAB>temp`
 * Kết quả: với mỗi khoá, một khối `\u0001<khoá>\n<nội dung>\n`.
 */
object SysReader {
    const val SEP = '\u0001'
    private const val MAX_BYTES = 64 * 1024

    // Cache danh sách vùng nhiệt (đường dẫn temp, type); vùng nhiệt không đổi suốt vòng đời tiến trình
    @Volatile
    private var thermalZones: List<Pair<String, String>>? = null

    private val THERMAL_SKIP = listOf(
        "battery", "batt", "charger", "usb", "wifi", "modem", "skin", "display", "camera", "flash", "xo", "ddr", "gpu",
    )

    fun readFile(path: String): String {
        return try {
            FileInputStream(path).use { ins ->
                val buf = ByteArray(MAX_BYTES)
                var n = 0
                while (n < MAX_BYTES) {
                    val r = ins.read(buf, n, MAX_BYTES - n)
                    if (r <= 0) break
                    n += r
                }
                String(buf, 0, n, Charsets.UTF_8)
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun freqs(): String {
        val sb = StringBuilder()
        val dirs = File("/sys/devices/system/cpu").listFiles() ?: return ""
        val cpus = dirs.filter { it.name.length > 3 && it.name.startsWith("cpu") && it.name.substring(3).all { c -> c in '0'..'9' } }
            .sortedBy { it.name.substring(3).toInt() }
        for (d in cpus) {
            val v = readFile(d.path + "/cpufreq/scaling_cur_freq").trim()
            if (v.isNotEmpty()) sb.append(d.name).append('\t').append(v).append('\n')
        }
        return sb.toString()
    }

    private fun zones(): List<Pair<String, String>> {
        thermalZones?.let { return it }
        val list = ArrayList<Pair<String, String>>()
        val dirs = File("/sys/class/thermal").listFiles()
        if (dirs != null) {
            for (d in dirs.sortedBy { it.name }) {
                if (!d.name.startsWith("thermal_zone")) continue
                val type = readFile(d.path + "/type").trim()
                if (type.isEmpty()) continue
                val low = type.lowercase()
                if (THERMAL_SKIP.any { low.contains(it) }) continue
                list.add((d.path + "/temp") to type)
            }
        }
        thermalZones = list
        return list
    }

    private fun thermal(): String {
        val sb = StringBuilder()
        for ((path, type) in zones()) {
            val v = readFile(path).trim()
            if (v.isNotEmpty()) sb.append(type).append('\t').append(v).append('\n')
        }
        return sb.toString()
    }

    fun readMany(keys: String): String {
        val sb = StringBuilder()
        for (raw in keys.lineSequence()) {
            val key = raw.trim()
            if (key.isEmpty()) continue
            sb.append(SEP).append(key).append('\n')
            val body = when (key) {
                "@freqs" -> freqs()
                "@thermal" -> thermal()
                else -> if (key.startsWith("/")) readFile(key) else ""
            }
            sb.append(body)
            if (!body.endsWith("\n")) sb.append('\n')
        }
        return sb.toString()
    }

    /** Tách kết quả của [readMany] thành map khoá → nội dung. */
    fun parse(result: String): Map<String, String> {
        val out = HashMap<String, String>()
        if (result.isEmpty()) return out
        for (part in result.split(SEP)) {
            if (part.isEmpty()) continue
            val nl = part.indexOf('\n')
            if (nl < 0) continue
            out[part.substring(0, nl)] = part.substring(nl + 1)
        }
        return out
    }
}
