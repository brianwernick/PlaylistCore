/*
 * Copyright (C) 2016 - 2017 Brian Wernick
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devbrackets.android.playlistcore.helper.notification

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.service.RemoteActions

/**
 * A default implementation for the [PlaylistNotificationProvider] that will correctly
 * handle both normal and large notifications, remote action registration, categories,
 * channels, etc.
 *
 * *Note* The default channel this provides only supports an English name and description
 * so it is recommended that you extend this class and override [buildNotificationChannel]
 * to provide your own custom name and description.
 */
open class DefaultPlaylistNotificationProvider(protected val context: Context) : PlaylistNotificationProvider {
    companion object {
        const val CHANNEL_ID = "PlaylistCoreMediaNotificationChannel"
    }

    protected val notificationManager: NotificationManager by lazy {
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    protected open val clickPendingIntent: PendingIntent?
        get() = null

    override fun buildNotification(info: MediaInfo, mediaSession: MediaSessionCompat, serviceClass: Class<out Service>) : Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            buildNotificationChannel()
        }

        return NotificationCompat.Builder(context, CHANNEL_ID).apply {
            setSmallIcon(info.appIcon)
            setLargeIcon(info.largeNotificationIcon)

            setContentTitle(info.title)
            setContentText("${info.album} - ${info.artist}") //todo only if both are not empty

            setContentIntent(clickPendingIntent)
            setDeleteIntent(createPendingIntent(serviceClass, RemoteActions.ACTION_STOP))

            val allowSwipe = !(info.mediaState.isPlaying)
            setAutoCancel(allowSwipe)
            setOngoing(!allowSwipe)

            //Set the notification category on lollipop
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setCategory(Notification.CATEGORY_TRANSPORT)
                setVisibility(Notification.VISIBILITY_PUBLIC)
            }

            //TODO: handle loading state

            setActions(this, info, serviceClass)
            setStyle(buildMediaStyle(mediaSession, serviceClass))
        }.build()
    }

    protected open fun setActions(builder: NotificationCompat.Builder, info: MediaInfo, serviceClass: Class<out Service>) {
        val playing = info.mediaState.isPlaying
        val playPauseIconRes = if (!playing) R.drawable.playlistcore_notification_play else R.drawable.playlistcore_notification_pause

        //todo enable/disable states
        //TODO: larger Play/Pause icon size, smaller next previous (see play music)
        builder.addAction(R.drawable.playlistcore_notification_previous, "", createPendingIntent(serviceClass, RemoteActions.ACTION_PREVIOUS))
        builder.addAction(playPauseIconRes, "", createPendingIntent(serviceClass, RemoteActions.ACTION_PLAY_PAUSE))
        builder.addAction(R.drawable.playlistcore_notification_next, "", createPendingIntent(serviceClass, RemoteActions.ACTION_NEXT))
    }

    protected open fun buildMediaStyle(mediaSession: MediaSessionCompat, serviceClass: Class<out Service>) : MediaStyle {
        return MediaStyle().apply {
            setMediaSession(mediaSession.sessionToken)
            setShowActionsInCompactView(0, 1, 2) // previous, play/pause, next
            setShowCancelButton(true)
            setCancelButtonIntent(createPendingIntent(serviceClass, RemoteActions.ACTION_STOP))
        }
    }

    /**
     * Builds the notification channel using the default name and description (English Only)
     * if the channel hasn't already been created
     */
    @TargetApi(Build.VERSION_CODES.O)
    protected open fun buildNotificationChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val name = context.resources.getString(R.string.playlistcore_default_notification_channel_name)
            val description = context.resources.getString(R.string.playlistcore_default_notification_channel_description)
            buildNotificationChannel(name, description)
        }
    }

    /**
     * Builds the notification channel using the specified name and description
     */
    @TargetApi(Build.VERSION_CODES.O)
    protected open fun buildNotificationChannel(name: CharSequence, description: String) {
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
        channel.description = description
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates a PendingIntent for the given action to the specified service
     *
     * @param action The action to use
     * @param serviceClass The service class to notify of intents
     * @return The resulting PendingIntent
     */
    protected open fun createPendingIntent(serviceClass: Class<out Service>, action: String): PendingIntent {
        val intent = Intent(context, serviceClass)
        intent.action = action

        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}