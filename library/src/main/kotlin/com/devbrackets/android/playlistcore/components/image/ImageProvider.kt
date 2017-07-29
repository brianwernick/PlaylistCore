package com.devbrackets.android.playlistcore.components.image

import android.graphics.Bitmap
import android.support.annotation.DrawableRes
import com.devbrackets.android.playlistcore.api.PlaylistItem

interface ImageProvider<in I: PlaylistItem> {

    /**
     * Retrieves the Drawable resource that specifies the icon to place in the
     * status bar for the media playback notification.
     */
    @get:DrawableRes
    val notificationIconRes: Int

    /**
     * Retrieves the Drawable resource that specifies the icon to place on the
     * lock screen to indicate the app the owns the content being displayed.
     */
    @get:DrawableRes
    val remoteViewIconRes: Int

    /**
     * Retrieves the image that will be displayed in the notification to represent
     * the currently playing item.
     */
    val largeNotificationImage: Bitmap?

    /**
     * Retrieves the image that will be displayed as the remote view artwork
     * for the currently playing item.
     */
    val remoteViewArtwork: Bitmap?

    /**
     * Called when the notification and remote view artwork needs to be updated
     * due to a playlist item change
     */
    fun updateImages(playlistItem: I)
}