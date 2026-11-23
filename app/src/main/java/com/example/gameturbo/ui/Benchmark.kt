package com.example.gameturbo.ui

import com.example.gameturbo.core.Core
import com.example.gameturbo.core.Cores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BenchRow(val name: String, val rustMs: Double, val kotlinMs: Double)

class BenchResult(val rows: List<BenchRow>, val rustAvailable: Boolean, val iterations: Int)

/** So tốc độ lõi Rust với lõi Kotlin dự phòng trên cùng dữ liệu mẫu. Số đo thật, không hứa trước. */
object Benchmark {
    private const val N = 20_000

    private val stat1 = "cpu  4705 150 2501 135000 300 10 25 0 0 0\ncpu0 600 10 300 16000 40 1 3 0 0 0\ncpu1 590 20 310 16100 35 2 4 0 0 0\n" +
        "intr 123456\nctxt 987654\n"
    private val stat2 = "cpu  5105 160 2701 135900 310 12 28 0 0 0\ncpu0 650 11 320 16100 41 1 3 0 0 0\ncpu1 640 21 330 16200 36 2 4 0 0 0\n" +
        "intr 123999\nctxt 988654\n"

    private val freqs: String = (0 until 8).joinToString("\n") { "cpu$it\t${1_804_800 + it * 130_000}" }

    private val latency: String = buildString {
        append("16666667\n")
        var t = 1_000_000_000_000L
        for (i in 0 until 127) {
            append(t - 8_000_000L).append('\t').append(t).append('\t').append(t - 4_000_000L).append('\n')
            t += 16_666_667L + (i % 3) * 120_000L
        }
    }

    private val planParams = "mode=2\nsdk=34\npkg=com.example.game\nres=80\nfps=60\nmaxrefresh=120\nroot=1\n"

    private val pmList: String = (0 until 150).joinToString("\n") { "package:com.vendor.app$it" }
    private val ps: String = (0 until 220).joinToString("\n") { i ->
        if (i % 3 == 0) "com.vendor.app${i / 2}:service" else if (i % 3 == 1) "com.vendor.app${i / 2}" else "system_server$i"
    }
    private val keep: String = (0 until 20).joinToString("\n") { "com.vendor.app${it * 5}" }

    private class Case(val name: String, val run: (Core, Int) -> Int)

    private val cases = listOf(
        Case("Tính % CPU") { c, i -> c.cpuUsage(if (i % 2 == 0) stat1 else stat2).toInt() },
        Case("Xung CPU") { c, _ -> c.freqStats(freqs).length },
        Case("Đo FPS từ SurfaceFlinger") { c, _ -> c.fpsFromLatency(latency).toInt() },
        Case("Lập kế hoạch chế độ") { c, _ -> c.planMode(planParams).length },
        Case("Chọn app cần dọn") { c, _ -> c.killList(c.runningThirdParty(pmList, ps), keep, "com.vendor.app7").length },
    )

    private fun time(core: Core, case: Case): Double {
        var sink = 0
        core.cpuUsageReset()
        for (i in 0 until N / 10) sink += case.run(core, i) // làm nóng
        core.cpuUsageReset()
        val t0 = System.nanoTime()
        for (i in 0 until N) sink += case.run(core, i)
        val t1 = System.nanoTime()
        if (sink == Int.MIN_VALUE) println(sink) // giữ kết quả, tránh bị tối ưu bỏ
        return (t1 - t0) / 1_000_000.0
    }

    suspend fun run(): BenchResult = withContext(Dispatchers.Default) {
        val rust = Cores.rust
        val kt = Cores.kotlin
        val rows = cases.map { c ->
            val k = time(kt, c)
            val r = if (rust != null) time(rust, c) else -1.0
            BenchRow(c.name, r, k)
        }
        BenchResult(rows, rust != null, N)
    }
}
