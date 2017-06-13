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

package com.devbrackets.android.playlistcore.listener

import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.service.BasePlaylistService

/**
 * A simple callback interface for listening to [BasePlaylistService]
 * changes.
 */
interface PlaylistListener<in T : PlaylistItem> {

    /**
     * Occurs when the currently playing item has changed
     *
     * @return True if the event has been handled
     */
    fun onPlaylistItemChanged(currentItem: T?, hasNext: Boolean, hasPrevious: Boolean): Boolean

    /**
     * Occurs when the current media state changes
     *
     * @return True if the event has been handled
     */
    fun onPlaybackStateChanged(playbackState: BasePlaylistService.PlaybackState): Boolean
}
