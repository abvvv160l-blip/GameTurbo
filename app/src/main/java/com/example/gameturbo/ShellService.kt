package com.example.gameturbo

import android.content.Context
import kotlin.system.exitProcess

/**
 * Chạy trong tiến trình riêng do Shizuku khởi tạo (quyền shell/ADB hoặc root).
 * Mọi lệnh gửi qua exec() sẽ chạy với quyền đó, không phải quyền của app thường.
 */
class ShellService() : IShellService.Stub() {

    // Shizuku có thể tạo service bằng constructor nhận Context
    @Suppress("unused", "UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            "$code\n$output"
        } catch (t: Throwable) {
            "-1\n${t.javaClass.simpleName}: ${t.message}"
        }
    }
}
