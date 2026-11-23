package com.example.gameturbo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.example.gameturbo.R
import com.example.gameturbo.data.Settings
import kotlin.random.Random

/**
 * Âm thanh của app. Dùng SoundPool: độ trễ thấp, không tốn CPU khi nhấn nút.
 * File WAV nằm trong res/raw (làm từ tools/synth.py).
 */
class SoundBank(context: Context, private val settings: Settings) {
    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val pool: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    } catch (_: Throwable) {
        null
    }

    private val ticks = Array(3) { IntArray(3) }
    private var ping = 0
    private var play = 0
    private var swoosh = 0
    private var riser = 0
    private var boom = 0
    private var beep = 0
    private var crit = 0
    private var down = 0
    private var chime = 0

    init {
        val p = pool
        if (p != null) {
            try {
                ticks[0][0] = p.load(ctx, R.raw.tick_glass_0, 1)
                ticks[0][1] = p.load(ctx, R.raw.tick_glass_1, 1)
                ticks[0][2] = p.load(ctx, R.raw.tick_glass_2, 1)
                ticks[1][0] = p.load(ctx, R.raw.tick_mech_0, 1)
                ticks[1][1] = p.load(ctx, R.raw.tick_mech_1, 1)
                ticks[1][2] = p.load(ctx, R.raw.tick_mech_2, 1)
                ticks[2][0] = p.load(ctx, R.raw.tick_game_0, 1)
                ticks[2][1] = p.load(ctx, R.raw.tick_game_1, 1)
                ticks[2][2] = p.load(ctx, R.raw.tick_game_2, 1)
                ping = p.load(ctx, R.raw.sfx_ping, 1)
                play = p.load(ctx, R.raw.sfx_play, 1)
                swoosh = p.load(ctx, R.raw.sfx_swoosh, 1)
                riser = p.load(ctx, R.raw.sfx_riser, 1)
                boom = p.load(ctx, R.raw.sfx_boom, 1)
                beep = p.load(ctx, R.raw.sfx_beep, 1)
                crit = p.load(ctx, R.raw.sfx_crit, 1)
                down = p.load(ctx, R.raw.sfx_down, 1)
                chime = p.load(ctx, R.raw.sfx_chime, 1)
            } catch (_: Throwable) {
            }
        }
    }

    /** Máy đang ở chế độ im lặng hoặc rung thì app cũng im. */
    private fun silentRinger(): Boolean = try {
        audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
    } catch (_: Throwable) {
        false
    }

    private fun go(id: Int, vol: Float, rate: Float = 1f) {
        if (id == 0) return
        try {
            val v = vol.coerceIn(0f, 1f)
            pool?.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
        } catch (_: Throwable) {
        }
    }

    private fun later(ms: Long, block: () -> Unit) {
        main.postDelayed({
            try {
                block()
            } catch (_: Throwable) {
            }
        }, ms)
    }

    /** Âm khi chạm nút. Kiểu âm, âm lượng và công tắc lấy từ cài đặt. */
    fun tick() {
        if (!settings.btnSound || silentRinger()) return
        val pack = settings.soundPack.coerceIn(0, 2)
        val id = ticks[pack][Random.nextInt(3)]
        go(id, settings.btnVolume, 0.96f + Random.nextFloat() * 0.09f)
    }

    /** Nghe thử âm nút, bỏ qua công tắc tắt âm. */
    fun tickPreview() {
        val pack = settings.soundPack.coerceIn(0, 2)
        go(ticks[pack][Random.nextInt(3)], settings.btnVolume.coerceAtLeast(0.3f))
    }

    fun ping() {
        if (!settings.btnSound || silentRinger()) return
        go(ping, 0.7f)
    }

    fun swoosh() {
        if (!settings.btnSound || silentRinger()) return
        go(swoosh, 0.6f)
    }

    fun playGame() {
        if (!settings.sfxGame || silentRinger()) return
        go(play, 0.85f)
    }

    fun ultOn() {
        if (!settings.sfxUlt || silentRinger()) return
        go(riser, 0.8f)
        later(430) { go(boom, 0.95f) }
    }

    fun warn() {
        if (!settings.sfxWarn || silentRinger()) return
        go(beep, 0.8f, 1.0f)
        later(180) { go(beep, 0.8f, 1.12f) }
    }

    /** Quá nóng và tự hạ chế độ: cảnh báo, tiếng hạ xung, chuông nhẹ. */
    fun fallback() {
        if (silentRinger()) return
        if (settings.sfxWarn) go(crit, 0.8f)
        if (settings.sfxFall) {
            later(700) { go(down, 0.85f) }
            later(1750) { go(chime, 0.8f) }
        }
    }

    fun release() {
        try {
            pool?.release()
        } catch (_: Throwable) {
        }
    }
}
