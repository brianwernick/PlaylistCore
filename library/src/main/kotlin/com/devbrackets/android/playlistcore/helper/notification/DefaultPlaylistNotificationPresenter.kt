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

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v7.app.NotificationCompat
import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.service.RemoteActions

open class DefaultPlaylistNotificationPresenter(protected val context: Context) : PlaylistNotificationPresenter {

    //TODO: or should the mediaSession be "owned" by this class? (i.e. not passed in to build)
    override fun buildNotification(info: NotificationInfo, mediaSession: MediaSessionCompat, serviceClass: Class<out Service>) : Notification {
        return NotificationCompat.Builder(context).apply {
            setSmallIcon(info.appIcon)
            setLargeIcon(info.largeNotificationIcon)

            setContentTitle(info.title)
            setContentText("${info.album} - ${info.artist}") //todo only if both are not empty

            setContentIntent(info.pendingIntent)
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

            //TODO: O notification channels
            setStyle(buildMediaStyle(mediaSession, serviceClass))
        }.build()
    }

    protected open fun setActions(builder: NotificationCompat.Builder, info: NotificationInfo, serviceClass: Class<out Service>) {
        val playing = info.mediaState.isPlaying
        val playPauseIconRes = if (!playing) R.drawable.playlistcore_notification_play else R.drawable.playlistcore_notification_pause

        //todo enable/disable states
        builder.addAction(R.drawable.playlistcore_notification_previous, "", createPendingIntent(serviceClass, RemoteActions.ACTION_PREVIOUS))
        builder.addAction(playPauseIconRes, "", createPendingIntent(serviceClass, RemoteActions.ACTION_PLAY_PAUSE))
        builder.addAction(R.drawable.playlistcore_notification_next, "", createPendingIntent(serviceClass, RemoteActions.ACTION_NEXT))
    }

    protected open fun buildMediaStyle(mediaSession: MediaSessionCompat, serviceClass: Class<out Service>) : NotificationCompat.MediaStyle {
        return NotificationCompat.MediaStyle().apply {
            setMediaSession(mediaSession.sessionToken)
            setShowActionsInCompactView(0, 1, 2) // previous, play/pause, next
            setShowCancelButton(true)
            setCancelButtonIntent(createPendingIntent(serviceClass, RemoteActions.ACTION_STOP))
        }
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