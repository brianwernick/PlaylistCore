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

import android.app.PendingIntent
import android.graphics.Bitmap
import android.support.annotation.DrawableRes

import com.devbrackets.android.playlistcore.R
import com.devbrackets.android.playlistcore.helper.MediaControlsHelper
import com.devbrackets.android.playlistcore.helper.NotificationHelper
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcore.manager.IPlaylistItem

/**
 * An extension of the [PlaylistServiceCore] that adds support for handling
 * notifications and media controls (such as the lock screen, bluetooth controls,
 * Android Wear interactions, etc.)
 *
 * {@inheritDoc}
 */
abstract class BasePlaylistService<I : IPlaylistItem, M : BasePlaylistManager<I>> : PlaylistServiceCore<I, M>() {
    companion object {
        private val TAG = "BasePlaylistService"
    }

    protected var notificationHelper: NotificationHelper? = null
    protected var mediaControlsHelper: MediaControlsHelper? = null

    protected var currentLargeNotificationUrl: String? = null
    protected var currentRemoteViewArtworkUrl: String? = null

    protected var foregroundSetup: Boolean = false
    protected var notificationSetup: Boolean = false

    /**
     * Retrieves the ID to use for the notification and registering this
     * service as Foreground when media is playing. (Foreground is removed
     * when paused)

     * @return The ID to use for the notification
     */
    protected abstract val notificationId: Int

    /**
     * Returns the PendingIntent to use when the playback notification is clicked.
     * This is called when the playback is started initially to setup the notification
     * and the service as Foreground.

     * @return The PendingIntent to use when the notification is clicked
     */
    protected abstract val notificationClickPendingIntent: PendingIntent

    /**
     * Retrieves the Image to use for the large notification (the double tall notification)
     * when [.getLargeNotificationImage] returns null.
     *
     * @return The image to use on the large notification when no other one is provided
     */
    protected var defaultLargeNotificationImage: Bitmap? = null

    /**
     * Retrieves the Drawable resource that specifies the icon to place in the
     * status bar for the media playback notification.

     * @return The Drawable resource id
     */
    @get:DrawableRes
    protected abstract val notificationIconRes: Int

    /**
     * Retrieves the Drawable resource that specifies the icon to place on the
     * lock screen to indicate the app the owns the content being displayed.

     * @return The Drawable resource id
     */
    @get:DrawableRes
    protected abstract val remoteViewIconRes: Int

    /**
     * Retrieves the image that will be displayed in the notification to represent
     * the currently playing item.
     *
     * @return The image to display in the notification or null
     */
    protected var largeNotificationImage: Bitmap? = null

    /**
     * Retrieves the image that will be displayed in the notification as a secondary
     * image.  This can be used to specify playback type (e.g. Chromecast).
     *
     * This will be called any time the notification is updated
     *
     * @return The image to display in the secondary position
     */
    protected var largeNotificationSecondaryImage: Bitmap? = null

    /**
     * Retrieves the image that will be displayed in the notification as a secondary
     * image if [.getLargeNotificationSecondaryImage] returns null.

     * @return The fallback image to display in the secondary position
     */
    protected var defaultLargeNotificationSecondaryImage: Bitmap? = null

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
     * Retrieves the image that will be displayed as the remote view artwork
     * for the currently playing item.

     * @return The image to display on the remote views
     */
    protected var remoteViewArtwork: Bitmap? = null

    /**
     * Called when the image for the Remote View needs to be updated.

     * @param playlistItem The playlist item to get the remote view image for
     */
    protected open fun updateRemoteViewArtwork(playlistItem: I) {
        //Purposefully left blank
    }

    override fun onDestroy() {
        super.onDestroy()

        notificationHelper = null
        mediaControlsHelper = null
    }

    override fun onServiceCreate() {
        super.onServiceCreate()

        notificationHelper = NotificationHelper(applicationContext)
        mediaControlsHelper = MediaControlsHelper(applicationContext, javaClass)
    }

    /**
     * This should be called when the extending class has loaded an updated
     * image for the Large Notification.
     */
    protected fun onLargeNotificationImageUpdated() {
        updateNotification()
    }

    /**
     * This should be called when the extending class has loaded an updated
     * image for the Remote Views Artwork.
     */
    protected fun onRemoteViewArtworkUpdated() {
        updateRemoteViews()
    }

    /**
     * Sets up the service as a Foreground service only if we aren't already registered as such
     */
    override fun setupForeground() {
        if (!foregroundSetup && notificationSetup && notificationHelper != null) {
            foregroundSetup = true
            startForeground(notificationId, notificationHelper!!.getNotification(notificationClickPendingIntent, javaClass))
        }
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

        if (notificationHelper != null) {
            notificationHelper!!.release()
        }

        if (mediaControlsHelper != null) {
            mediaControlsHelper!!.release()
        }
    }

    /**
     * Requests the service be transferred to the foreground, initializing the
     * RemoteView and Notification helpers for playback control.
     */
    override fun setupAsForeground() {
        //Sets up the Lock Screen playback controls
        if (mediaControlsHelper != null) {
            mediaControlsHelper!!.setMediaControlsEnabled(true)
            mediaControlsHelper!!.setBaseInformation(remoteViewIconRes)
        }

        //Sets up the Notifications
        if (notificationHelper != null) {
            notificationHelper!!.setNotificationsEnabled(true)
            notificationHelper!!.setNotificationBaseInformation(notificationId, notificationIconRes, javaClass)
        }

        //Starts the service as the foreground audio player
        notificationSetup = true
        setupForeground()

        updateRemoteViews()
        updateNotification()
    }

    /**
     * Performs the process to update the playback controls and images in the notification
     * associated with the current playlist item.
     */
    override fun updateNotification() {
        if (currentPlaylistItem == null || !notificationSetup || notificationHelper == null) {
            return
        }

        //Generate the notification state
        val mediaState = NotificationHelper.NotificationMediaState()
        mediaState.isNextEnabled = playlistManager.isNextAvailable
        mediaState.isPreviousEnabled = playlistManager.isPreviousAvailable
        mediaState.isPlaying = isPlaying


        //Update the big notification images
        var bitmap = largeNotificationImage
        if (bitmap == null) {
            bitmap = defaultLargeNotificationImage
        }

        var secondaryImage = largeNotificationSecondaryImage
        if (secondaryImage == null) {
            secondaryImage = defaultLargeNotificationSecondaryImage
        }

        //Finish up the update
        val title = currentPlaylistItem!!.title
        val album = currentPlaylistItem!!.album
        val artist = currentPlaylistItem!!.artist
        notificationHelper!!.setClickPendingIntent(notificationClickPendingIntent)
        notificationHelper!!.updateNotificationInformation(title, album, artist, bitmap, secondaryImage, mediaState)
    }

    /**
     * Performs the process to update the playback controls and the background
     * (artwork) image displayed on the lock screen and other remote views.
     */
    override fun updateRemoteViews() {
        if (currentPlaylistItem == null || !notificationSetup || mediaControlsHelper == null) {
            return
        }

        //Generate the notification state
        val mediaState = NotificationHelper.NotificationMediaState()
        mediaState.isNextEnabled = playlistManager.isNextAvailable
        mediaState.isPreviousEnabled = playlistManager.isPreviousAvailable
        mediaState.isPlaying = isPlaying


        //Finish up the update
        val title = currentPlaylistItem!!.title
        val album = currentPlaylistItem!!.album
        val artist = currentPlaylistItem!!.artist
        mediaControlsHelper!!.update(title, album, artist, remoteViewArtwork, mediaState)
    }

    override fun mediaItemChanged() {
        super.mediaItemChanged()

        //Starts the notification loading
        if (currentPlaylistItem != null && (currentLargeNotificationUrl == null || currentLargeNotificationUrl != currentPlaylistItem!!.thumbnailUrl)) {
            val size = resources.getDimensionPixelSize(R.dimen.playlistcore_big_notification_height)
            updateLargeNotificationImage(size, currentPlaylistItem!!)
            currentLargeNotificationUrl = currentPlaylistItem!!.thumbnailUrl
        }

        //Starts the remote view loading
        if (currentPlaylistItem != null && (currentRemoteViewArtworkUrl == null || !currentRemoteViewArtworkUrl!!.equals(currentPlaylistItem!!.artworkUrl!!, ignoreCase = true))) {
            updateRemoteViewArtwork(currentPlaylistItem!!)
            currentRemoteViewArtworkUrl = currentPlaylistItem!!.artworkUrl
        }
    }
}