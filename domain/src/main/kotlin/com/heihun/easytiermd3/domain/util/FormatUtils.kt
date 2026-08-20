package com.heihun.easytiermd3.domain.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${round1(kb)} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${round1(mb)} MB"
        val gb = mb / 1024.0
        return "${round1(gb)} GB"
    }

    fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(Locale.US, h, m, s)
    }

    fun formatLatency(ms: Long?): String = ms?.let { "$it ms" } ?: "--"

    fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    fun relativeTime(epochMillis: Long?): String {
        if (epochMillis == null) return "--"
        val diff = System.currentTimeMillis() - epochMillis
        return when {
            diff < 10_000 -> "刚刚"
            diff < 60_000 -> "${diff / 1000} 秒前"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
        }
    }

    private fun round1(value: Double): String = String.format(Locale.US, "%.1f", value)
}