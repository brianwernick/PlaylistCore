package com.devbrackets.android.playlistcore.service

import android.app.Notification

//TODO rename to something better
interface ServiceCallbacks {
    fun stop()

    fun runAsForeground(notificationId: Int, notification: Notification)
    fun endForeground(dismissNotification: Boolean)
}