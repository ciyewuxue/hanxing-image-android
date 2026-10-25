package cn.hanxing.image

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 生成期前台服务：只负责一件事——把进程在前台服务里按住，让 4K 长任务切后台也不被系统掐掉。
 * 真正的生图逻辑在 [GenerationEngine]，这里只读它的状态刷新通知，生成一结束就自退。
 */
class GenerationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        val notification = buildNotification("准备开始…")
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        val engine = (application as HanxingApp).engine
        scope.launch {
            engine.state.collectLatest { state ->
                if (state.running.isEmpty()) {
                    stopSelf()
                } else {
                    notify(buildNotification(describe(state)))
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) {
        runCatching {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun describe(state: EngineState): String {
        val running = state.running
        if (running.isEmpty()) return "图片生成中，请稍候…"

        // 多张并行时只报总数和最久的那张，避免通知文字来回跳
        if (running.size > 1) {
            val longest = running.maxOf { it.elapsedMs }
            return "正在生成 ${running.size} 张 · 最久 ${formatDuration(longest)}"
        }

        val single = running.first()
        val parts = mutableListOf<String>()
        if (single.elapsedMs > 0) parts.add("已用 ${formatDuration(single.elapsedMs)}")
        if (single.total > 0) {
            parts.add("已接收 ${formatBytes(single.received)} / ${formatBytes(single.total)}")
        } else if (single.received > 0) {
            parts.add("已接收 ${formatBytes(single.received)}")
        }
        return if (parts.isEmpty()) "图片生成中，请稍候…" else parts.joinToString(" · ")
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("寒星生图正在生成")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "生成进度",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "生成期间的前台保活通知"
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private companion object {
        const val CHANNEL_ID = "generation"
        const val NOTIFICATION_ID = 1
    }
}
