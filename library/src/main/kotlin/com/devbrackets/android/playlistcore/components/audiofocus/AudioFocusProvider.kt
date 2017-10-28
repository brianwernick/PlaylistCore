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
package com.devbrackets.android.playlistcore.components.audiofocus

import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler

interface AudioFocusProvider<I : PlaylistItem> {
    fun setPlaylistHandler(playlistHandler: PlaylistHandler<I>)

    fun refreshFocus()

    /**
     * Requests to obtain the audio focus
     *
     * @return `true` if the focus was granted
     */
    fun requestFocus(): Boolean

    /**
     * Requests the system to drop the audio focus
     *
     * @return `true` if the focus was lost
     */
    fun abandonFocus(): Boolean
}