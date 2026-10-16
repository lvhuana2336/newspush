package com.newspush.app.util

import java.util.Locale

/**
 * 判断当前时刻是否处于推送时段内。start == end 或未启用时表示全天可推；
 * 跨零点（如 22:00 - 06:00）也能正确处理。
 */
fun isWithinPushWindow(
    nowMinutes: Int,
    startMinutes: Int,
    endMinutes: Int,
    enabled: Boolean
): Boolean {
    if (!enabled) return true
    val start = normalizeMinutes(startMinutes)
    val end = normalizeMinutes(endMinutes)
    if (start == end) return true
    val now = normalizeMinutes(nowMinutes)
    return if (start < end) now in start until end else now >= start || now < end
}

fun formatClockMinutes(minutes: Int): String {
    val value = normalizeMinutes(minutes)
    return String.format(Locale.US, "%02d:%02d", value / 60, value % 60)
}

private fun normalizeMinutes(minutes: Int): Int = ((minutes % 1440) + 1440) % 1440
