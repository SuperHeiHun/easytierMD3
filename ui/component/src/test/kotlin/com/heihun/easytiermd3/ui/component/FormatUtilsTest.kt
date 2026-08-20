package com.heihun.easytiermd3.ui.component

import com.heihun.easytiermd3.domain.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun `formatBytes handles all units`() {
        assertEquals("0 B", FormatUtils.formatBytes(0))
        assertEquals("512 B", FormatUtils.formatBytes(512))
        assertEquals("1.0 KB", FormatUtils.formatBytes(1024))
        assertEquals("12.4 MB", FormatUtils.formatBytes(13_004_800))
        assertEquals("1.2 GB", FormatUtils.formatBytes(1_288_490_189))
    }

    @Test
    fun `formatSpeed appends per second`() {
        assertEquals("1.0 KB/s", FormatUtils.formatSpeed(1024))
        assertEquals("12.4 MB/s", FormatUtils.formatSpeed(13_004_800))
    }

    @Test
    fun `formatDuration is hh mm ss`() {
        assertEquals("00:00:00", FormatUtils.formatDuration(0))
        assertEquals("02:34:12", FormatUtils.formatDuration(9252))
        assertEquals("25:00:00", FormatUtils.formatDuration(90_000))
    }

    @Test
    fun `formatLatency handles null`() {
        assertEquals("--", FormatUtils.formatLatency(null))
        assertEquals("4 ms", FormatUtils.formatLatency(4))
    }

    @Test
    fun `relativeTime buckets`() {
        val now = System.currentTimeMillis()
        assertEquals("刚刚", FormatUtils.relativeTime(now - 5_000))
        assertEquals("15 秒前", FormatUtils.relativeTime(now - 15_000))
        assertEquals("2 分钟前", FormatUtils.relativeTime(now - 120_000))
    }
}