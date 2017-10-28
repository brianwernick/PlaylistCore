package com.devbrackets.android.playlistcore.listener

import android.app.Notification

interface ServiceCallbacks {
    fun stop()

    fun runAsForeground(notificationId: Int, notification: Notification)
    fun endForeground(dismissNotification: Boolean)
}