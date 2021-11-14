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

package com.devbrackets.android.playlistcore.api

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import com.devbrackets.android.playlistcore.data.MediaProgress
import com.devbrackets.android.playlistcore.listener.MediaStatusListener

interface MediaPlayerApi<I : PlaylistItem> {
    /**
     * The connection state of the [MediaPlayerApi] when it represents
     * a remote player such as a Chromecast
     */
    enum class RemoteConnectionState {
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED
    }

    /**
     * Determines if media is currently playing on this implementation of the
     * [MediaPlayerApi]
     */
    val isPlaying: Boolean

    /**
     * `true` if the [MediaPlayerApi] handles or doesn't require audio focus. If this
     * is `false` then the [com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler]
     * or [com.devbrackets.android.playlistcore.components.audiofocus.AudioFocusProvider] should handle
     * acquiring and monitoring the audio focus.
     */
    val handlesOwnAudioFocus: Boolean

    @get:IntRange(from = 0)
    val currentPosition: Long

    @get:IntRange(from = 0)
    val duration: Long

    /**
     * Retrieves the current buffer percent of the audio item.  If an audio item is not currently
     * prepared or buffering the value will be 0.  This should only be called after the audio item is
     * prepared (see [.setOnMediaPreparedListener])
     *
     * @return The integer percent that is buffered [0, {@value MediaProgress#MAX_BUFFER_PERCENT}] inclusive
     */
    @get:IntRange(from = 0, to = MediaProgress.MAX_BUFFER_PERCENT.toLong())
    val bufferedPercent: Int

    fun play()

    fun pause()

    fun stop()

    fun reset()

    fun release()

    fun setVolume(@FloatRange(from = 0.0, to = 1.0) left: Float, @FloatRange(from = 0.0, to = 1.0) right: Float)

    fun seekTo(@IntRange(from = 0) milliseconds: Long)

    fun setMediaStatusListener(listener: MediaStatusListener<I>)

    fun handlesItem(item: I): Boolean

    fun playItem(item: I)
}
