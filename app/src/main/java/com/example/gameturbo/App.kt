package com.example.gameturbo

import android.app.Application
import android.content.Context
import com.example.gameturbo.audio.SoundBank
import com.example.gameturbo.data.GameRepo
import com.example.gameturbo.data.Settings
import com.example.gameturbo.engine.Exec
import com.example.gameturbo.engine.TurboController
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Điểm chung của app: giữ các thành phần dùng chung giữa giao diện, dịch vụ nền và cửa sổ nổi. */
class App : Application() {
    lateinit var settings: Settings
        private set
    lateinit var shizuku: ShizukuShell
        private set
    lateinit var exec: Exec
        private set
    lateinit var games: GameRepo
        private set
    lateinit var sounds: SoundBank
        private set
    lateinit var controller: TurboController
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        settings = Settings(this)
        shizuku = ShizukuShell(this)
        exec = Exec(shizuku)
        games = GameRepo(this, settings)
        sounds = SoundBank(this, settings)
        controller = TurboController(this)
        try {
            shizuku.start()
        } catch (_: Throwable) {
        }
        if (settings.rootEnabled) controller.scope.launch { RootShell.check() }
    }

    // ------------------------------------------------------------ nhật ký lỗi

    private val crashFile: File get() = File(filesDir, "crash.txt")

    private fun installCrashLogger() {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = "[$time] ${t.name}\n${e.stackTraceToString().take(6000)}\n\n"
                val f = crashFile
                val prev = if (f.exists()) f.readText().takeLast(8000) else ""
                f.writeText(prev + text)
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }

    /** Nội dung lỗi gần nhất làm app thoát bất ngờ, hoặc null nếu chưa có. */
    fun lastCrash(): String? = try {
        crashFile.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }

    fun clearCrash() {
        try {
            crashFile.delete()
        } catch (_: Throwable) {
        }
    }
}

val Context.app: App get() = applicationContext as App
