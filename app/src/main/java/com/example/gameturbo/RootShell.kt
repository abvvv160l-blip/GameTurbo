package com.example.gameturbo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chạy lệnh bằng `su` (chỉ khi máy có root). Không bao giờ chạy gì nếu chưa kiểm tra thành công. */
object RootShell {
    @Volatile
    var available: Boolean = false
        private set

    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        val r = run("id", 20_000)
        available = r.ok && r.output.contains("uid=0")
        available
    }

    suspend fun exec(command: String, timeoutMs: Long = 20_000): ShizukuShell.Result =
        withContext(Dispatchers.IO) { run(command, timeoutMs) }

    private fun run(command: String, timeoutMs: Long): ShizukuShell.Result {
        val p: Process
        try {
            p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        } catch (t: Throwable) {
            return ShizukuShell.Result(-1, "Không chạy được su: ${t.message}")
        }
        val out = StringBuilder()
        val reader = Thread {
            try {
                out.append(p.inputStream.bufferedReader().readText())
            } catch (_: Throwable) {
            }
        }
        reader.start()
        val deadline = System.currentTimeMillis() + timeoutMs
        var code = Int.MIN_VALUE
        while (System.currentTimeMillis() < deadline) {
            try {
                code = p.exitValue()
                break
            } catch (_: IllegalThreadStateException) {
                try {
                    Thread.sleep(30)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        if (code == Int.MIN_VALUE) {
            p.destroy()
            return ShizukuShell.Result(-1, "Hết thời gian chờ su")
        }
        try {
            reader.join(500)
        } catch (_: InterruptedException) {
        }
        return ShizukuShell.Result(code, out.toString().trim())
    }
}
