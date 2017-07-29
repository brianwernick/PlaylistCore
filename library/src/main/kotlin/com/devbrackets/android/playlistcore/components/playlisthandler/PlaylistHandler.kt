package com.devbrackets.android.playlistcore.components.playlisthandler

import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.data.MediaProgress
import com.devbrackets.android.playlistcore.data.PlaylistItemChange
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import com.devbrackets.android.playlistcore.data.PlaybackState
import com.devbrackets.android.playlistcore.listener.ServiceCallbacks

abstract class PlaylistHandler<I: PlaylistItem>(val mediaPlayers: List<MediaPlayerApi<I>>) {

    /**
     * Retrieves the current item change event which represents any media item changes.
     * This is intended as a utility method for initializing, or returning to, a media
     * playback UI.  In order to get the changed events you will need to register for
     * callbacks through [BasePlaylistManager.registerPlaylistListener]
     *
     * @return The current PlaylistItem Changed event
     */
    var currentItemChange: PlaylistItemChange<I>? = null

    /**
     * The current playback progress
     */
    var currentMediaProgress = MediaProgress(0, 0, 0)
        protected set

    /**
     * The current playback state of the service
     */
    var currentPlaybackState = PlaybackState.PREPARING
        protected set

    var currentMediaPlayer: MediaPlayerApi<I>? = null

    abstract fun setup(serviceCallbacks: ServiceCallbacks)
    abstract fun tearDown()

    abstract fun play()
    abstract fun pause()
    abstract fun togglePlayPause()
    abstract fun stop()
    abstract fun next()
    abstract fun previous()

    abstract fun startSeek()
    abstract fun seek(positionMillis: Long)

    abstract fun startItemPlayback(positionMillis: Long, startPaused: Boolean)
    abstract fun updateMediaControls()
}