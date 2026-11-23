package com.example.gameturbo.core

/**
 * Lõi tính toán của Game Turbo. Hợp đồng chi tiết nằm trong NATIVE_API.md.
 * Có hai bản cài đặt cho cùng một hợp đồng: Rust ([NativeCore]) và Kotlin ([KotlinCore], dự phòng).
 */
interface Core {
    val name: String

    fun cpuUsage(procStat: String): Float
    fun cpuUsageReset()
    fun freqStats(lines: String): String
    fun gpuNodes(): String
    fun gpuParse(kind: String, path: String, content: String): Float
    fun fpsFromLatency(text: String): Float
    fun findLayer(list: String, pkg: String): String
    fun focusedPackage(dump: String): String
    fun cpuTemp(dump: String): Float
    fun planMode(params: String): String
    fun planRestore(params: String): String
    fun runningThirdParty(pmList: String, ps: String): String
    fun killList(running: String, keep: String, game: String): String
    fun guardUpdate(tempC: Float, source: Int): Int
    fun guardReset()
}

/** Cầu nối JNI sang libgtcore.so. Tên hàm khớp NATIVE_API.md. */
object Native {
    @Volatile
    var loaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("gtcore")
            loaded = true
        } catch (_: Throwable) {
            loaded = false
        }
    }

    @JvmStatic external fun version(): String
    @JvmStatic external fun cpuUsage(procStat: String): Float
    @JvmStatic external fun cpuUsageReset()
    @JvmStatic external fun freqStats(lines: String): String
    @JvmStatic external fun gpuNodes(): String
    @JvmStatic external fun gpuParse(kind: String, path: String, content: String): Float
    @JvmStatic external fun fpsFromLatency(text: String): Float
    @JvmStatic external fun findLayer(list: String, pkg: String): String
    @JvmStatic external fun focusedPackage(dump: String): String
    @JvmStatic external fun cpuTemp(dump: String): Float
    @JvmStatic external fun planMode(params: String): String
    @JvmStatic external fun planRestore(params: String): String
    @JvmStatic external fun runningThirdParty(pmList: String, ps: String): String
    @JvmStatic external fun killList(running: String, keep: String, game: String): String
    @JvmStatic external fun guardUpdate(tempC: Float, source: Int): Int
    @JvmStatic external fun guardReset()
}

object NativeCore : Core {
    override val name: String get() = "Rust"

    override fun cpuUsage(procStat: String): Float = Native.cpuUsage(procStat)
    override fun cpuUsageReset() = Native.cpuUsageReset()
    override fun freqStats(lines: String): String = Native.freqStats(lines)
    override fun gpuNodes(): String = Native.gpuNodes()
    override fun gpuParse(kind: String, path: String, content: String): Float = Native.gpuParse(kind, path, content)
    override fun fpsFromLatency(text: String): Float = Native.fpsFromLatency(text)
    override fun findLayer(list: String, pkg: String): String = Native.findLayer(list, pkg)
    override fun focusedPackage(dump: String): String = Native.focusedPackage(dump)
    override fun cpuTemp(dump: String): Float = Native.cpuTemp(dump)
    override fun planMode(params: String): String = Native.planMode(params)
    override fun planRestore(params: String): String = Native.planRestore(params)
    override fun runningThirdParty(pmList: String, ps: String): String = Native.runningThirdParty(pmList, ps)
    override fun killList(running: String, keep: String, game: String): String = Native.killList(running, keep, game)
    override fun guardUpdate(tempC: Float, source: Int): Int = Native.guardUpdate(tempC, source)
    override fun guardReset() = Native.guardReset()
}

object Cores {
    val kotlin: Core by lazy { KotlinCore() }

    /** Lõi Rust nếu tải được và gọi thử thành công, ngược lại null. */
    val rust: Core? by lazy {
        if (!Native.loaded) {
            null
        } else {
            try {
                Native.version()
                NativeCore
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** Lõi đang dùng thật: Rust nếu có, không thì Kotlin. */
    val best: Core get() = rust ?: kotlin
}
