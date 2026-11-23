package com.example.gameturbo.engine

import com.example.gameturbo.RootShell
import com.example.gameturbo.ShizukuShell

/**
 * Chạy lệnh theo quyền hiện có: shell qua Shizuku, hoặc root qua su.
 * Kế hoạch từ lõi (Rust/Kotlin) có dòng dạng `X|lệnh`:
 *  S = quyền shell hoặc root, R = chỉ root, F = dự phòng nếu dòng S ngay trước thất bại.
 */
class Exec(private val shizuku: ShizukuShell) {

    class PlanResult(val ran: Int, val failed: Int, val skippedRoot: Int, val errors: List<String>)

    val hasShell: Boolean get() = shizuku.isReady || RootShell.available

    val hasRoot: Boolean get() = (shizuku.isReady && shizuku.isRootMode) || RootShell.available

    suspend fun sh(cmd: String, timeoutMs: Long = 20_000): ShizukuShell.Result =
        when {
            shizuku.isReady -> shizuku.exec(cmd)
            RootShell.available -> RootShell.exec(cmd, timeoutMs)
            else -> ShizukuShell.Result(-1, "Chưa có quyền shell (cần Shizuku hoặc root)")
        }

    suspend fun root(cmd: String, timeoutMs: Long = 20_000): ShizukuShell.Result =
        when {
            shizuku.isReady && shizuku.isRootMode -> shizuku.exec(cmd)
            RootShell.available -> RootShell.exec(cmd, timeoutMs)
            else -> ShizukuShell.Result(-1, "Không có root")
        }

    suspend fun runPlan(plan: String): PlanResult {
        var ran = 0
        var failed = 0
        var skipped = 0
        val errors = ArrayList<String>()
        var pendingFail: String? = null // lệnh S vừa lỗi, chờ xem có dòng F cứu không
        for (raw in plan.lineSequence()) {
            val line = raw.trim()
            if (line.length < 3 || line[1] != '|') continue
            val kind = line[0]
            val cmd = line.substring(2)
            if (kind != 'F' && pendingFail != null) {
                failed++
                errors.add(pendingFail)
                pendingFail = null
            }
            when (kind) {
                'S' -> {
                    val r = sh(cmd)
                    ran++
                    if (!r.ok) pendingFail = shortErr(cmd, r.output)
                }
                'R' -> {
                    if (hasRoot) {
                        val r = root(cmd)
                        ran++
                        if (!r.ok) {
                            failed++
                            errors.add(shortErr("lệnh root", r.output))
                        }
                    } else {
                        skipped++
                    }
                }
                'F' -> {
                    val pf = pendingFail
                    if (pf != null) {
                        pendingFail = null
                        val r = sh(cmd)
                        ran++
                        if (!r.ok) {
                            failed++
                            errors.add(pf)
                        }
                    }
                }
            }
        }
        pendingFail?.let {
            failed++
            errors.add(it)
        }
        return PlanResult(ran, failed, skipped, errors)
    }

    private fun shortErr(cmd: String, out: String): String {
        val head = cmd.take(48)
        val tail = out.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: ""
        return if (tail.isEmpty()) head else "$head → $tail"
    }
}
