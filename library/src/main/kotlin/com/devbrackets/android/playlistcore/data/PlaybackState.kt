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

package com.devbrackets.android.playlistcore.data

enum class PlaybackState {
    /**
     * Media is currently being retrieved to be prepared and played. This is the first state
     * entered for playback.
     */
    RETRIEVING,

    /**
     * Media is currently being prepared for playback. This typically means that the media is being
     * buffered, synced, etc. and occurs after [RETRIEVING]
     */
    PREPARING,

    /**
     * Media is currently being played.
     */
    PLAYING,

    /**
     * Media is currently prepared but has been paused. Playback can be started at any time
     */
    PAUSED,

    /**
     * Media playback is currently seeking to a requested timestamp. This can be started from either
     * [PLAYING] or [PAUSED]
     */
    SEEKING,

    /**
     * No media is currently being prepared to play, playing, paused, etc.
     */
    STOPPED,

    /**
     * An error occurred when playing back media. This is effectively the same as [STOPPED]
     * but only accessible through an error
     */
    ERROR
}