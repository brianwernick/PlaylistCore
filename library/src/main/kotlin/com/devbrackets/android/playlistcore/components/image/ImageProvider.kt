/*
 * Copyright (C) 2017 Brian Wernick
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
package com.devbrackets.android.playlistcore.components.image

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
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