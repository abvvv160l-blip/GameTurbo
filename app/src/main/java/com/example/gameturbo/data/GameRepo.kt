package com.example.gameturbo.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class GameInfo(val pkg: String, val label: String, val detected: Boolean)

data class AppEntry(val pkg: String, val label: String)

/** Danh sách game: tự nhận từ máy (category game) cộng với game người dùng tự thêm. */
class GameRepo(context: Context, private val settings: Settings) {
    private val ctx = context.applicationContext

    var games by mutableStateOf<List<GameInfo>>(emptyList())
        private set

    /** Mọi app có icon trên màn hình chính, dùng cho nút "thêm game". */
    var otherApps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    private fun isGame(info: ApplicationInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && info.category == ApplicationInfo.CATEGORY_GAME) return true
        @Suppress("DEPRECATION")
        return (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }

    suspend fun reload() {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) { scan() }
            games = result.first
            otherApps = result.second
        } catch (_: Throwable) {
        } finally {
            loading = false
        }
    }

    private fun scan(): Pair<List<GameInfo>, List<AppEntry>> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val gameList = ArrayList<GameInfo>()
        val others = ArrayList<AppEntry>()
        val hidden = settings.hiddenGames
        val added = settings.addedGames
        for (ri in list) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            val pkg = ai.packageName
            if (pkg == ctx.packageName || !seen.add(pkg)) continue
            val label = try {
                ri.loadLabel(pm).toString()
            } catch (_: Throwable) {
                pkg
            }
            val detected = isGame(ai)
            if (pkg in hidden) {
                others.add(AppEntry(pkg, label))
            } else if (detected || pkg in added) {
                gameList.add(GameInfo(pkg, label, detected))
            } else {
                others.add(AppEntry(pkg, label))
            }
        }
        gameList.sortWith(
            compareByDescending<GameInfo> { settings.lastPlayed[it.pkg] ?: 0L }
                .thenBy { it.label.lowercase() }
        )
        others.sortBy { it.label.lowercase() }
        return gameList to others
    }

    /** Tập tên gói của các game, dùng để nhận biết game đang chạy. */
    fun gamePackages(): Set<String> = games.mapTo(HashSet()) { it.pkg }

    fun labelOf(pkg: String): String = games.firstOrNull { it.pkg == pkg }?.label ?: pkg

    suspend fun icon(pkg: String): ImageBitmap? {
        icons[pkg]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val d = ctx.packageManager.getApplicationIcon(pkg)
                val bmp = d.toBitmap(160, 160).asImageBitmap()
                icons[pkg] = bmp
                bmp
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun launchIntent(pkg: String): Intent? =
        try {
            ctx.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (_: Throwable) {
            null
        }
}
