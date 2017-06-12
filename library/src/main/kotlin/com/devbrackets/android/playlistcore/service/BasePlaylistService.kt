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

package com.devbrackets.android.playlistcore.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.support.annotation.DrawableRes
import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.helper.MediaControlsHelper
import com.devbrackets.android.playlistcore.helper.mediasession.DefaultMediaSessionProvider
import com.devbrackets.android.playlistcore.helper.mediasession.MediaSessionProvider
import com.devbrackets.android.playlistcore.helper.notification.DefaultPlaylistNotificationPresenter
import com.devbrackets.android.playlistcore.helper.notification.MediaInfo
import com.devbrackets.android.playlistcore.helper.notification.PlaylistNotificationPresenter
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcore.api.PlaylistItem

/**
 * An extension of the [PlaylistServiceCore] that adds support for handling
 * notifications and media controls (such as the lock screen, bluetooth controls,
 * Android Wear interactions, etc.)
 *
 * {@inheritDoc}
 */
@Deprecated("Merge with PlaylistServiceCore") //todo
abstract class BasePlaylistService<I : PlaylistItem, M : BasePlaylistManager<I>> : PlaylistServiceCore<I, M>() {
    protected var mediaControlsHelper: MediaControlsHelper? = null

    protected var currentLargeNotificationUrl: String? = null
    protected var currentRemoteViewArtworkUrl: String? = null

    protected var foregroundSetup: Boolean = false
    protected var notificationSetup: Boolean = false

    protected lateinit var notificationPresenter: PlaylistNotificationPresenter
    protected lateinit var mediaSessionProvider: MediaSessionProvider

    protected val notificationManager: NotificationManager by lazy {
        baseContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    protected val mediaInfo = MediaInfo()

    /**
     * Retrieves the ID to use for the notification and registering this
     * service as Foreground when media is playing. (Foreground is removed
     * when paused)
     */
    protected abstract val notificationId: Int

    /**
     * Returns the PendingIntent to use when the playback notification is clicked.
     * This is called when the playback is started initially to setup the notification
     * and the service as Foreground.
     */
    protected abstract val notificationClickPendingIntent: PendingIntent

    /**
     * Retrieves the Drawable resource that specifies the icon to place in the
     * status bar for the media playback notification.
     */
    @get:DrawableRes
    protected abstract val notificationIconRes: Int

    /**
     * Retrieves the Drawable resource that specifies the icon to place on the
     * lock screen to indicate the app the owns the content being displayed.
     */
    @get:DrawableRes
    protected abstract val remoteViewIconRes: Int

    /**
     * Retrieves the image that will be displayed in the notification to represent
     * the currently playing item.
     */
    protected var largeNotificationImage: Bitmap? = null

    /**
     * Retrieves the Image to use for the large notification (the double tall notification)
     * when [.getLargeNotificationImage] returns null.
     */
    protected var defaultLargeNotificationImage: Bitmap? = null

    /**
     * Retrieves the image that will be displayed as the remote view artwork
     * for the currently playing item.
     */
    protected var remoteViewArtwork: Bitmap? = null

    /**
     * Retrieves the image that will be displayed as the remote view artwork
     * image if [.getRemoteViewArtwork] returns null.
     */
    protected var defaultRemoteViewArtwork: Bitmap? = null

    /**
     * Called when the image in the notification needs to be updated.

     * @param size The square size for the image to display
     * *
     * @param playlistItem The media item to get the image for
     */
    protected open fun updateLargeNotificationImage(size: Int, playlistItem: I) {
        //Purposefully left blank
    }

    /**
     * Called when the image for the Remote View needs to be updated.
     *
     * @param playlistItem The playlist item to get the remote view image for
     */
    protected open fun updateRemoteViewArtwork(playlistItem: I) {
        //Purposefully left blank
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaControlsHelper = null
    }

    override fun onServiceCreate() {
        super.onServiceCreate()
        notificationPresenter = DefaultPlaylistNotificationPresenter(applicationContext)
        mediaSessionProvider = DefaultMediaSessionProvider(applicationContext, javaClass)
        mediaControlsHelper = MediaControlsHelper(applicationContext)
    }

    /**
     * Sets up the service as a Foreground service only if we aren't already registered as such
     */
    override fun setupForeground() {
        if (!notificationSetup || foregroundSetup) {
            return
        }

        foregroundSetup = true
        startForeground(notificationId, notificationPresenter.buildNotification(mediaInfo, mediaSessionProvider.get(), javaClass))
    }

    /**
     * If the service is registered as a foreground service then it will be unregistered
     * as such without removing the notification
     */
    override fun stopForeground() {
        if (foregroundSetup) {
            foregroundSetup = false
            stopForeground(false)
        }
    }

    override fun relaxResources(releaseAudioPlayer: Boolean) {
        super.relaxResources(releaseAudioPlayer)
        stopForeground(true)

        foregroundSetup = false
        notificationSetup = false

        notificationManager.cancel(notificationId)
        mediaSessionProvider.get().release()
    }

    /**
     * Requests the service be transferred to the foreground, initializing the
     * RemoteView and Notification helpers for playback control.
     */
    override fun setupAsForeground() {
        //Sets up the Notifications
        mediaInfo.showNotifications = true
        mediaInfo.notificationId = notificationId
        mediaInfo.appIcon = notificationIconRes

        //Starts the service as the foreground audio player
        notificationSetup = true
        setupForeground()
    }

    override fun updateMediaControls() {
        if (currentPlaylistItem == null || !notificationSetup) {
            return
        }

        updateMediaInfo()
        mediaSessionProvider.update(mediaInfo)
        mediaControlsHelper?.update(mediaInfo, mediaSessionProvider.get())

        // Updates the notification
        notificationManager.notify(mediaInfo.notificationId, notificationPresenter.buildNotification(mediaInfo, mediaSessionProvider.get(), javaClass))
    }

    override fun mediaItemChanged(item: I?) {
        super.mediaItemChanged(item)

        item?.let { playlistItem ->
            //Starts the notification loading
            if (!(currentLargeNotificationUrl?.equals(playlistItem.thumbnailUrl) ?: false)) {
                val size = resources.getDimensionPixelSize(R.dimen.playlistcore_large_notification_size)
                updateLargeNotificationImage(size, playlistItem)
                currentLargeNotificationUrl = playlistItem.thumbnailUrl
            }

            //Starts the remote view loading
            if (currentRemoteViewArtworkUrl?.equals(playlistItem.artworkUrl, ignoreCase = true) ?: false) {
                updateRemoteViewArtwork(playlistItem)
                currentRemoteViewArtworkUrl = playlistItem.artworkUrl
            }
        }
    }

    /**
     * This should be called when the extending class has loaded an updated
     * image for the Large Notification.
     */
    protected open fun onLargeNotificationImageUpdated() {
        updateMediaControls()
    }

    /**
     * This should be called when the extending class has loaded an updated
     * image for the Remote Views Artwork.
     */
    protected open fun onRemoteViewArtworkUpdated() {
        updateMediaControls()
    }

    protected open fun updateMediaInfo() {
        // Generate the notification state
        mediaInfo.mediaState.isPlaying = isPlaying
        mediaInfo.mediaState.isLoading = isLoading
        mediaInfo.mediaState.isNextEnabled = playlistManager.isNextAvailable
        mediaInfo.mediaState.isPreviousEnabled = playlistManager.isPreviousAvailable

        // Updates the notification information
        mediaInfo.playlistItem = currentPlaylistItem
        mediaInfo.pendingIntent = notificationClickPendingIntent
        mediaInfo.artwork = remoteViewArtwork ?: defaultRemoteViewArtwork
        mediaInfo.largeNotificationIcon = largeNotificationImage ?: defaultLargeNotificationImage
    }
}
