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

package com.devbrackets.android.playlistcore.manager

import android.app.Application
import android.app.Service
import android.support.annotation.IntRange
import com.devbrackets.android.playlistcore.api.PlaylistItem

/**
 * An implementation of the [BasePlaylistManager] that supports Lists
 *
 * {@inheritDoc}
 */
open class ListPlaylistManager<I : PlaylistItem>(application: Application, mediaServiceClass: Class<out Service>) :
        BasePlaylistManager<I>(application, mediaServiceClass) {
    protected var items: List<I>? = null

    override val itemCount: Int
        get() = items?.size ?: 0

    override fun getItem(@IntRange(from = 0) position: Int): I? {
        if (position < itemCount) {
            return items?.get(position)
        }

        return null
    }

    override fun getPositionForItem(itemId: Long): Int {
        items?.forEachIndexed { index, item ->
            if (item.id == itemId) {
                return index
            }
        }

        return BasePlaylistManager.INVALID_POSITION
    }

    /**
     * A utility method to allow for single line implementations to start playing the media
     * item as specified by the passed parameters.
     *
     * @param items The list of items to play
     * @param startPosition The position in the playlistItems to start playback
     * @param playbackPosition The playback position in the item located at `startPosition` to start at in milliseconds
     * @param startPaused True if the media item should start paused instead of playing
     */
    open fun play(items: List<I>?, @IntRange(from = 0) startPosition: Int, @IntRange(from = 0) playbackPosition: Int, startPaused: Boolean) {
        setParameters(items, startPosition)
        play(playbackPosition.toLong(), startPaused)
    }

    /**
     * Sets the List of items to be used for the play list.  This can include both audio
     * and video items.
     *
     * @param items The List of items to play
     * @param startPosition The position in the list to start playback with
     */
    open fun setParameters(items: List<I>?, @IntRange(from = 0) startPosition: Int) {
        this.items = items

        currentPosition = startPosition
        id = BasePlaylistManager.INVALID_ID
    }
}