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

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.devbrackets.android.playlistcore.helper.notification.MediaInfo
import com.devbrackets.android.playlistcore.helper.notification.NotificationMediaState

/**
 * A class to help simplify playback control and artwork display for
 * remote views such as Android Wear, Bluetooth devices, Lock Screens, etc.
 * TODO: This should be changed to a delegated provider like the notification, etc.
 */
open class MediaControlsHelper
/**
 * Creates a new MediaControlsHelper object
 *
 * @param context The context to use for holding a MediaSession and sending action intents
 * @param mediaServiceClass The class for the service that owns the backing MediaService and to notify of playback actions
 */
(protected var context: Context) {
    companion object {
        private val TAG = "MediaControlsHelper"
        val SESSION_TAG = "MediaControlsHelper.Session"
        val RECEIVER_EXTRA_CLASS = "com.devbrackets.android.playlistcore.RECEIVER_EXTRA_CLASS"
    }

    protected var enabled = true

    /**
     * Sets the volatile information for the remote views and controls.  This information is expected to
     * change frequently.
     *
     * @param title The title to display for the notification (e.g. A song name)
     * @param album The name of the album the media is found in
     * @param artist The name of the artist for the media item
     * @param notificationMediaState The current media state for the expanded (big) notification
     */
    fun update(mediaInfo: MediaInfo, mediaSession: MediaSessionCompat) {
        //Updates the available playback controls
        val playbackStateBuilder = PlaybackStateCompat.Builder()
        playbackStateBuilder.setActions(getPlaybackOptions(mediaInfo.mediaState))
        playbackStateBuilder.setState(getPlaybackState(mediaInfo.mediaState.isPlaying), PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)

        mediaSession.setPlaybackState(playbackStateBuilder.build())
        Log.d(TAG, "update, controller is null ? " + if (mediaSession.controller == null) "true" else "false")

        if (enabled && !mediaSession.isActive) {
            mediaSession.isActive = true
        }
    }

    @PlaybackStateCompat.State
    protected fun getPlaybackState(isPlaying: Boolean): Int {
        //todo We should be handling all the appropriate states instead of just these 2
        return if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_CONNECTING
    }

    /**
     * Determines the available playback commands supported for the current media state
     *
     * @param mediaState The current media playback state
     * @return The available playback options
     */
    @PlaybackStateCompat.Actions
    protected fun getPlaybackOptions(mediaState: NotificationMediaState): Long {
        var availableActions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE

        if (mediaState.isNextEnabled) {
            availableActions = availableActions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }

        if (mediaState.isPreviousEnabled) {
            availableActions = availableActions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        }

        return availableActions
    }
}
