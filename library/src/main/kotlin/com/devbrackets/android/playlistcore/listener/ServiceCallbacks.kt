package com.devbrackets.android.playlistcore.listener

import android.app.Notification

//TODO rename to something better
interface ServiceCallbacks {
    fun stop()

    fun runAsForeground(notificationId: Int, notification: Notification)
    fun endForeground(dismissNotification: Boolean)
}