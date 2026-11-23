package com.example.gameturbo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Toàn bộ cài đặt của app, lưu trong SharedPreferences.
 * Mỗi thuộc tính là một "state" của Compose: giao diện tự vẽ lại khi đổi,
 * còn các cửa sổ nổi (View) đọc thẳng giá trị.
 */
class Settings(context: Context) {

    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("gt_settings", Context.MODE_PRIVATE)

    private class StateProp<T>(
        private val prefs: SharedPreferences,
        private val key: String,
        default: T,
        read: (SharedPreferences, String, T) -> T,
        private val write: (SharedPreferences.Editor, String, T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(
            try {
                read(prefs, key, default)
            } catch (_: Throwable) {
                default
            }
        )

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            val e = prefs.edit()
            write(e, key, value)
            e.apply()
        }
    }

    private fun intP(key: String, def: Int): ReadWriteProperty<Any?, Int> =
        StateProp(prefs, key, def, { p, k, d -> p.getInt(k, d) }, { e, k, v -> e.putInt(k, v) })

    private fun floatP(key: String, def: Float): ReadWriteProperty<Any?, Float> =
        StateProp(prefs, key, def, { p, k, d -> p.getFloat(k, d) }, { e, k, v -> e.putFloat(k, v) })

    private fun boolP(key: String, def: Boolean): ReadWriteProperty<Any?, Boolean> =
        StateProp(prefs, key, def, { p, k, d -> p.getBoolean(k, d) }, { e, k, v -> e.putBoolean(k, v) })

    private fun strP(key: String, def: String): ReadWriteProperty<Any?, String> =
        StateProp(prefs, key, def, { p, k, d -> p.getString(k, d) ?: d }, { e, k, v -> e.putString(k, v) })

    private fun setP(key: String): ReadWriteProperty<Any?, Set<String>> =
        StateProp<Set<String>>(
            prefs, key, emptySet(),
            { p, k, _ -> HashSet(p.getStringSet(k, emptySet()) ?: emptySet()) },
            { e, k, v -> e.putStringSet(k, HashSet(v)) },
        )

    // ---------------- Chế độ hiệu năng ----------------
    /** 0 Tiết kiệm pin, 1 Ổn định, 2 Tối thượng, 3 Tùy chỉnh */
    var mode by intP("mode", 1)

    /** Tùy chỉnh: 0 không đổi, 1 pin, 2 ổn định, 3 hiệu năng (Game Mode) */
    var customGm by intP("custom_gm", 3)

    /** Tần số quét tối thiểu: 0 mặc định, hoặc 60/90/120 */
    var minHz by intP("min_hz", 0)
    var fixedPerf by boolP("fixed_perf", false)
    var lockClock by boolP("lock_clock", false)

    /** Ultimate tự về Ổn định khi CPU/SoC chạm 80 °C */
    var guardOn by boolP("guard_on", true)

    // ---------------- Dọn app nền ----------------
    var killBg by boolP("kill_bg", true)
    var killStrong by boolP("kill_strong", false)

    /** 0 tự động khi vào game, 1 chỉ khi bấm nút */
    var killTrigger by intP("kill_trigger", 0)
    var keepList by setP("keep_list")

    // ---------------- HUD ----------------
    var hudOn by boolP("hud_on", true)
    var hudAlpha by floatP("hud_alpha", 0.6f)
    var hudScale by floatP("hud_scale", 1.0f)
    var hudX by intP("hud_x", 24)
    var hudY by intP("hud_y", 24)
    var hudShowFps by boolP("hud_fps", true)
    var hudShowGhz by boolP("hud_ghz", true)
    var hudShowCpu by boolP("hud_cpu", true)
    var hudShowGpu by boolP("hud_gpu", true)
    var hudShowTemp by boolP("hud_temp", true)

    // ---------------- Bong bóng / menu trong game ----------------
    var bubbleX by intP("bubble_x", -1)
    var bubbleY by intP("bubble_y", 220)
    var menuX by intP("menu_x", -1)
    var menuY by intP("menu_y", -1)

    // ---------------- Giao diện ----------------
    var theme by intP("theme", 0)

    /** 0 trong suốt, 1 tối, 2 sáng, 3 theo chủ đề */
    var tint by intP("tint", 0)
    var blur by floatP("blur", 0.6f)
    var dim by floatP("dim", 0.1f)
    var ultSkin by boolP("ult_skin", true)

    /** 0 Cao, 1 Thấp, 2 Tắt (máy yếu) */
    var glassQuality by intP("glass_quality", 0)

    /** 0 màu, 1 ảnh, 2 video */
    var bgType by intP("bg_type", 0)
    var bgColor by intP("bg_color", 0)

    /** 0..3 ảnh có sẵn, 4 ảnh chọn từ máy */
    var bgImage by intP("bg_image", 0)
    var bgImageUri by strP("bg_image_uri", "")
    var bgVideoUri by strP("bg_video_uri", "")
    var bgVideoName by strP("bg_video_name", "")
    var pauseVideoInGame by boolP("pause_video", true)
    var videoSound by boolP("video_sound", true)
    var videoVolume by floatP("video_volume", 0.6f)

    // ---------------- Âm thanh ----------------
    var btnSound by boolP("btn_sound", true)

    /** 0 Kính, 1 Cơ học, 2 Gaming */
    var soundPack by intP("sound_pack", 0)
    var btnVolume by floatP("btn_volume", 0.7f)
    var sfxUlt by boolP("sfx_ult", true)
    var sfxWarn by boolP("sfx_warn", true)
    var sfxFall by boolP("sfx_fall", true)
    var sfxGame by boolP("sfx_game", true)

    // ---------------- Khác ----------------
    var onboarded by boolP("onboarded", false)

    /** Người dùng đã bật dùng root (su). Chỉ khi bật mới gọi su, tránh hỏi quyền root vô cớ. */
    var rootEnabled by boolP("root_enabled", false)

    /** Tự theo dõi: dịch vụ nền đang bật hay không (để khởi động lại sau khi mở app) */
    var watching by boolP("watching", false)
    var selectedGame by strP("selected_game", "")
    var logText by strP("log_text", "")

    // ---------------- Theo từng game ----------------
    val gameRes = mutableStateMapOf<String, Int>()
    val gameFps = mutableStateMapOf<String, Int>()
    val playMs = mutableStateMapOf<String, Long>()
    val lastPlayed = mutableStateMapOf<String, Long>()
    var hiddenGames by setP("hidden_games")
    var addedGames by setP("added_games")

    init {
        try {
            for ((k, v) in prefs.all) {
                when {
                    k.startsWith("g.res.") && v is Int -> gameRes[k.removePrefix("g.res.")] = v
                    k.startsWith("g.fps.") && v is Int -> gameFps[k.removePrefix("g.fps.")] = v
                    k.startsWith("g.play.") && v is Long -> playMs[k.removePrefix("g.play.")] = v
                    k.startsWith("g.last.") && v is Long -> lastPlayed[k.removePrefix("g.last.")] = v
                }
            }
        } catch (_: Throwable) {
        }
    }

    /** % độ phân giải gốc của game (100 = gốc). */
    fun resOf(pkg: String): Int = gameRes[pkg] ?: 100

    /** Giới hạn FPS của game (0 = mặc định). */
    fun fpsOf(pkg: String): Int = gameFps[pkg] ?: 0

    fun setRes(pkg: String, v: Int) {
        gameRes[pkg] = v
        prefs.edit().putInt("g.res.$pkg", v).apply()
    }

    fun setFps(pkg: String, v: Int) {
        gameFps[pkg] = v
        prefs.edit().putInt("g.fps.$pkg", v).apply()
    }

    fun addPlay(pkg: String, ms: Long, now: Long) {
        if (ms <= 0) return
        val total = (playMs[pkg] ?: 0L) + ms
        playMs[pkg] = total
        lastPlayed[pkg] = now
        prefs.edit().putLong("g.play.$pkg", total).putLong("g.last.$pkg", now).apply()
    }

    fun clearPlay(pkg: String) {
        playMs.remove(pkg)
        lastPlayed.remove(pkg)
        prefs.edit().remove("g.play.$pkg").remove("g.last.$pkg").apply()
    }

    fun toggleKeep(pkg: String) {
        keepList = if (pkg in keepList) keepList - pkg else keepList + pkg
    }

    fun hideGame(pkg: String) {
        hiddenGames = hiddenGames + pkg
        addedGames = addedGames - pkg
    }

    fun addGame(pkg: String) {
        addedGames = addedGames + pkg
        hiddenGames = hiddenGames - pkg
    }

    /** Mức mờ thật sự áp lên cửa sổ HUD: Android 12+ chặn cảm ứng xuyên qua cửa sổ mờ hơn 0.8. */
    fun hudWindowAlpha(sdk: Int): Float {
        val a = hudAlpha.coerceIn(0.2f, 1f)
        return if (sdk >= 31) a.coerceAtMost(0.8f) else a
    }
}
