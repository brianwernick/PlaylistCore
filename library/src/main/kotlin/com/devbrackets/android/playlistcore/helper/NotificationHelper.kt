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

package com.devbrackets.android.playlistcore.helper

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.support.annotation.DrawableRes
import android.support.v4.app.NotificationCompat
import android.view.View
import android.widget.RemoteViews

import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.service.RemoteActions

/**
 * A class to help simplify notification creation and modification for
 * media playback applications.
 */
class NotificationHelper(protected var context: Context) {
    protected var mediaServiceClass: Class<out Service>? = null

    protected var notificationManager: NotificationManager? = null
    protected var notificationInfo = NotificationInfo()

    init {
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Dismisses and removes references to any notifications, images, and information
     * associated with current notifications.
     */
    fun release() {
        dismiss()

        mediaServiceClass = null
        notificationInfo.clean()
    }

    /**
     * Dismisses the current active notification
     */
    fun dismiss() {
        if (notificationManager != null) {
            notificationManager!!.cancel(notificationInfo.notificationId)
        }
    }

    /**
     * Sets weather notifications are shown when audio is playing or
     * ready for playback (e.g. paused).  The notification information
     * will need to be updated by calling [.setNotificationBaseInformation]
     * and [.updateNotificationInformation] and can be retrieved
     * with [.getNotification]
     *
     * @param enabled True if notifications should be shown
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        if (enabled == notificationInfo.showNotifications) {
            return
        }

        notificationInfo.showNotifications = enabled

        //Remove the notification when disabling
        if (!enabled && notificationManager != null) {
            notificationManager!!.cancel(notificationInfo.notificationId)
        }
    }

    /**
     * Sets the basic information for the notification that doesn't need to be updated.  Additionally, when
     * the mediaServiceClass is set the big notification will send intents to that service to notify of
     * button clicks.  These intents will have an action from
     *
     *  * [RemoteActions.ACTION_STOP]
     *  * [RemoteActions.ACTION_PLAY_PAUSE]
     *  * [RemoteActions.ACTION_PREVIOUS]
     *  * [RemoteActions.ACTION_NEXT]
     *
     * @param notificationId The ID to specify this notification
     * @param appIcon The applications icon resource
     * @param mediaServiceClass The class for the service to notify of big notification actions
     */
    @JvmOverloads fun setNotificationBaseInformation(notificationId: Int, @DrawableRes appIcon: Int, mediaServiceClass: Class<out Service>? = null) {
        notificationInfo.notificationId = notificationId
        notificationInfo.appIcon = appIcon
        this.mediaServiceClass = mediaServiceClass
    }

    /**
     * Sets the [PendingIntent] to call when the notification is clicked.
     *
     * @param pendingIntent The pending intent to use when the notification itself is clicked
     */
    fun setClickPendingIntent(pendingIntent: PendingIntent?) {
        notificationInfo.pendingIntent = pendingIntent
    }

    /**
     * Sets the volatile information for the notification.  This information is expected to
     * change frequently.
     *
     * @param title The title to display on the notification (e.g. A song name)
     * @param album The album to display on the notification.  This is the second row of text displayed
     * @param artist The artist to display on the notification.  This is the third row of text displayed
     * @param notificationImage An image to display on the notification (e.g. Album artwork)
     * @param secondaryNotificationImage An image to display on the notification should be used to indicate playback type (e.g. Chromecast)
     * @param notificationMediaState The current media state for the expanded (big) notification
     */
    @JvmOverloads fun updateNotificationInformation(title: String?, album: String?, artist: String?, notificationImage: Bitmap?,
                                                    secondaryNotificationImage: Bitmap?, notificationMediaState: NotificationMediaState? = null) {
        notificationInfo.title = title.orEmpty()
        notificationInfo.album = album.orEmpty()
        notificationInfo.artist = artist.orEmpty()
        notificationInfo.largeImage = notificationImage
        notificationInfo.secondaryImage = secondaryNotificationImage
        notificationInfo.mediaState = notificationMediaState

        if (notificationInfo.showNotifications && notificationManager != null && mediaServiceClass != null) {
            val notification = getNotification(notificationInfo.pendingIntent, mediaServiceClass!!)
            notificationManager!!.notify(notificationInfo.notificationId, notification)
        }
    }

    /**
     * Returns a fully constructed notification to use when moving a service to the
     * foreground.  This should be called after the notification information is set with
     * [.setNotificationBaseInformation] and [.updateNotificationInformation].
     *
     * @param pendingIntent The pending intent to use when the notification itself is clicked
     * @return The constructed notification
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    fun getNotification(pendingIntent: PendingIntent?, serviceClass: Class<out Service>): Notification {
        setClickPendingIntent(pendingIntent)
        val customNotificationViews = getCustomNotification(serviceClass)

        val allowSwipe = notificationInfo.mediaState == null || !notificationInfo.mediaState!!.isPlaying

        val builder = NotificationCompat.Builder(context)
        builder.setContent(customNotificationViews)
        builder.setContentIntent(pendingIntent)
        builder.setDeleteIntent(createPendingIntent(RemoteActions.ACTION_STOP, serviceClass))
        builder.setSmallIcon(notificationInfo.appIcon)
        builder.setAutoCancel(allowSwipe)
        builder.setOngoing(!allowSwipe)

        if (pendingIntent != null) {
            customNotificationViews.setOnClickPendingIntent(R.id.playlistcore_notification_touch_area, pendingIntent)
        }

        //Set the notification category on lollipop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_STATUS)
            builder.setVisibility(Notification.VISIBILITY_PUBLIC)
        }

        //Build the notification and set the expanded content view if there is a service to inform of clicks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && mediaServiceClass != null) {
            val bigNotificationView = getBigNotification(serviceClass)
            bigNotificationView.setOnClickPendingIntent(R.id.playlistcore_big_notification_touch_area, pendingIntent)
            builder.setCustomBigContentView(bigNotificationView)
        }

        return builder.build()
    }

    /**
     * Creates the RemoteViews used for the custom (standard) notification
     *
     * @return The resulting RemoteViews
     */
    protected fun getCustomNotification(serviceClass: Class<out Service>): RemoteViews {
        val customNotification = RemoteViews(context.packageName, R.layout.playlistcore_notification_content)

        customNotification.setOnClickPendingIntent(R.id.playlistcore_notification_playpause, createPendingIntent(RemoteActions.ACTION_PLAY_PAUSE, serviceClass))
        customNotification.setOnClickPendingIntent(R.id.playlistcore_notification_next, createPendingIntent(RemoteActions.ACTION_NEXT, serviceClass))
        customNotification.setOnClickPendingIntent(R.id.playlistcore_notification_prev, createPendingIntent(RemoteActions.ACTION_PREVIOUS, serviceClass))

        customNotification.setTextViewText(R.id.playlistcore_notification_title, notificationInfo.title)
        customNotification.setTextViewText(R.id.playlistcore_notification_album, notificationInfo.album)
        customNotification.setTextViewText(R.id.playlistcore_notification_artist, notificationInfo.artist)
        if (notificationInfo.largeImage != null) {
            customNotification.setBitmap(R.id.playlistcore_notification_large_image, "setImageBitmap", notificationInfo.largeImage)
        }

        if (notificationInfo.mediaState != null) {
            updateCustomNotificationMediaState(customNotification)
        }

        return customNotification
    }

    /**
     * Creates the RemoteViews used for the expanded (big) notification
     *
     * @return The resulting RemoteViews
     */
    protected fun getBigNotification(serviceClass: Class<out Service>): RemoteViews {
        val bigContent = RemoteViews(context.packageName, R.layout.playlistcore_big_notification_content)

        bigContent.setOnClickPendingIntent(R.id.playlistcore_big_notification_close, createPendingIntent(RemoteActions.ACTION_STOP, serviceClass))
        bigContent.setOnClickPendingIntent(R.id.playlistcore_big_notification_playpause, createPendingIntent(RemoteActions.ACTION_PLAY_PAUSE, serviceClass))
        bigContent.setOnClickPendingIntent(R.id.playlistcore_big_notification_next, createPendingIntent(RemoteActions.ACTION_NEXT, serviceClass))
        bigContent.setOnClickPendingIntent(R.id.playlistcore_big_notification_prev, createPendingIntent(RemoteActions.ACTION_PREVIOUS, serviceClass))

        bigContent.setTextViewText(R.id.playlistcore_big_notification_title, notificationInfo.title)
        bigContent.setTextViewText(R.id.playlistcore_big_notification_album, notificationInfo.album)
        bigContent.setTextViewText(R.id.playlistcore_big_notification_artist, notificationInfo.artist)
        bigContent.setBitmap(R.id.playlistcore_big_notification_large_image, "setImageBitmap", notificationInfo.largeImage)
        bigContent.setBitmap(R.id.playlistcore_big_notification_secondary_image, "setImageBitmap", notificationInfo.secondaryImage)

        //Makes sure the play/pause, next, and previous are displayed correctly
        if (notificationInfo.mediaState != null) {
            updateBigNotificationMediaState(bigContent)
        }

        return bigContent
    }

    /**
     * Updates the images for the play/pause button so that only valid ones are
     * displayed with the correct state.
     *
     * @param customNotification The RemoteViews to use to modify the state
     */
    protected fun updateCustomNotificationMediaState(customNotification: RemoteViews?) {
        val state = notificationInfo.mediaState ?: return

        customNotification?.let {
            it.setImageViewResource(R.id.playlistcore_notification_playpause, if (state.isPlaying) R.drawable.playlistcore_notification_pause else R.drawable.playlistcore_notification_play)
            it.setInt(R.id.playlistcore_notification_prev, "setVisibility", if (state.isPreviousEnabled) View.VISIBLE else View.GONE)
            it.setInt(R.id.playlistcore_notification_next, "setVisibility", if (state.isNextEnabled) View.VISIBLE else View.GONE)
        }
    }

    /**
     * Updates the images for the play/pause, next, and previous buttons so that only valid ones are
     * displayed with the correct state.
     *
     * @param bigContent The RemoteViews to use to modify the state
     */
    protected fun updateBigNotificationMediaState(bigContent: RemoteViews?) {
        val state = notificationInfo.mediaState ?: return

        bigContent?.let {
            it.setImageViewResource(R.id.playlistcore_big_notification_playpause, if (state.isPlaying) R.drawable.playlistcore_notification_pause else R.drawable.playlistcore_notification_play)
            it.setInt(R.id.playlistcore_big_notification_prev, "setVisibility", if (state.isPreviousEnabled) View.VISIBLE else View.INVISIBLE)
            it.setInt(R.id.playlistcore_big_notification_next, "setVisibility", if (state.isNextEnabled) View.VISIBLE else View.INVISIBLE)
        }
    }

    /**
     * Creates a PendingIntent for the given action to the specified service
     *
     * @param action The action to use
     * @param serviceClass The service class to notify of intents
     * @return The resulting PendingIntent
     */
    protected fun createPendingIntent(action: String, serviceClass: Class<out Service>): PendingIntent {
        val intent = Intent(context, serviceClass)
        intent.action = action

        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    class NotificationMediaState {
        var isPlaying: Boolean = false
        var isPreviousEnabled: Boolean = false
        var isNextEnabled: Boolean = false
    }
}