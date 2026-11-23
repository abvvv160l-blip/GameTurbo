package com.example.gameturbo

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    /** Shizuku chưa cài hoặc chưa được khởi động */
    UNAVAILABLE,

    /** Shizuku đang chạy nhưng app chưa được cấp quyền */
    NEED_PERMISSION,

    /** Đang kết nối tới service chạy lệnh */
    CONNECTING,

    /** Sẵn sàng chạy lệnh */
    READY,
}

class ShizukuShell(context: Context) {

    data class Result(val code: Int, val output: String) {
        val ok: Boolean get() = code == 0
    }

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(ShizukuStatus.UNAVAILABLE)
    val status: StateFlow<ShizukuStatus> = _status

    @Volatile
    private var service: IShellService? = null
    private var bound = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(appContext.packageName, ShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (binder.pingBinder()) {
                service = IShellService.Stub.asInterface(binder)
                _status.value = ShizukuStatus.READY
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
            refresh()
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }

    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        bound = false
        _status.value = ShizukuStatus.UNAVAILABLE
    }

    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        if (bound) {
            try {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
            } catch (_: Throwable) {
            }
            bound = false
        }
        service = null
    }

    /** Kiểm tra lại trạng thái Shizuku và kết nối nếu có thể. */
    fun refresh() {
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) {
                _status.value = ShizukuStatus.UNAVAILABLE
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                _status.value = ShizukuStatus.NEED_PERMISSION
                return
            }
            if (service != null) {
                _status.value = ShizukuStatus.READY
                return
            }
            if (!bound) {
                _status.value = ShizukuStatus.CONNECTING
                bound = true
                Shizuku.bindUserService(userServiceArgs, connection)
            }
        } catch (_: Throwable) {
            bound = false
            _status.value = ShizukuStatus.UNAVAILABLE
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun exec(command: String): Result = withContext(Dispatchers.IO) {
        val s = service ?: return@withContext Result(-1, "Shizuku chưa sẵn sàng")
        try {
            val raw = s.exec(command)
            val newline = raw.indexOf('\n')
            if (newline < 0) {
                Result(-1, raw.trim())
            } else {
                Result(raw.substring(0, newline).toIntOrNull() ?: -1, raw.substring(newline + 1).trim())
            }
        } catch (t: Throwable) {
            Result(-1, "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Đọc nhiều file hệ thống bằng quyền shell trong một lần gọi. Trả "" nếu chưa sẵn sàng. */
    suspend fun readMany(keys: String): String = withContext(Dispatchers.IO) {
        val s = service ?: return@withContext ""
        try {
            s.readMany(keys) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    val isReady: Boolean get() = _status.value == ShizukuStatus.READY

    /** Shizuku đang chạy bằng root (uid 0) thì lệnh gửi qua exec() cũng có quyền root. */
    val isRootMode: Boolean
        get() = try {
            Shizuku.pingBinder() && Shizuku.getUid() == 0
        } catch (_: Throwable) {
            false
        }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
