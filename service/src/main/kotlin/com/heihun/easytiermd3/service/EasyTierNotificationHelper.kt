package com.heihun.easytiermd3.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.heihun.easytiermd3.core.api.EasyTierIntents
import com.heihun.easytiermd3.core.api.model.EasyTierConnectionState
import com.heihun.easytiermd3.core.api.model.EasyTierStatistics
import com.heihun.easytiermd3.domain.util.FormatUtils
import com.heihun.easytiermd3.service.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EasyTierNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastUpdateAt = 0L
    private var lastKey = ""

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "EasyTier 服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "EasyTier 后台运行状态"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notify(state: EasyTierConnectionState, stats: EasyTierStatistics, networkName: String?) {
        val key = state.javaClass.simpleName + "|" + (networkName ?: "")
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastUpdateAt < 2000) return
        lastKey = key
        lastUpdateAt = now
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state, stats, networkName))
    }

    fun buildNotification(
        state: EasyTierConnectionState,
        stats: EasyTierStatistics,
        networkName: String?,
    ): Notification {
        val (title, content) = when (state) {
            EasyTierConnectionState.Running -> {
                "已连接" to buildString {
                    append(networkName ?: "EasyTier")
                    append("\n↓ ${FormatUtils.formatSpeed(stats.downloadSpeed)}  ↑ ${FormatUtils.formatSpeed(stats.uploadSpeed)}")
                }
            }
            EasyTierConnectionState.Starting -> "正在启动" to (networkName ?: "正在启动 EasyTier...")
            EasyTierConnectionState.Stopping -> "正在停止" to "正在停止 EasyTier..."
            is EasyTierConnectionState.Error -> "连接失败" to (state.message)
            EasyTierConnectionState.Stopped -> "已断开" to "EasyTier 未运行"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_easytier)
            .setContentTitle("EasyTier · $title")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOnlyAlertOnce(true)
            .setOngoing(state is EasyTierConnectionState.Running)

        openAppIntent()?.let { builder.setContentIntent(it) }
        if (state is EasyTierConnectionState.Running) {
            builder.addAction(0, "断开", stopIntent())
        }
        return builder.build()
    }

    private fun stopIntent(): PendingIntent {
        val intent = Intent(context, EasyTierForegroundService::class.java)
            .setAction(EasyTierIntents.ACTION_STOP_CORE)
        return PendingIntent.getService(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "easytier_service"
        const val NOTIFICATION_ID = 1001
    }
}