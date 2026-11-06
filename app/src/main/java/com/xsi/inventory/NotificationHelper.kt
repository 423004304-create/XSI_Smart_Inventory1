package com.xsi.inventory

import android.content.Context

object NotificationHelper {

    private const val PREFS_NAME = "xsi_prefs"
    private const val KEY_READ_ALL = "are_alerts_read"

    var currentActiveAlertsCount: Int = 0

    fun getUnreadCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isRead = prefs.getBoolean(KEY_READ_ALL, false)
        return if (isRead) 0 else currentActiveAlertsCount
    }

    fun markAllAsRead(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_READ_ALL, true).apply()
    }

    fun isRead(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_READ_ALL, false)
    }
}
