package com.devbrackets.android.playlistcore.components.playlisthandler

import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.data.MediaProgress
import com.devbrackets.android.playlistcore.data.PlaybackState
import com.devbrackets.android.playlistcore.data.PlaylistItemChange
import com.devbrackets.android.playlistcore.listener.ServiceCallbacks
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager

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

    /**
     * Handles playing the media that is currently loaded
     */
    abstract fun play()

    /**
     * Handles pausing the current playback, making sure to keep the temporary
     * pauses for things like seek, transient focus loss (e.g. phone call), etc.
     * separate from permanent pauses
     *
     * @param transient `true` if the pause is temporary
     */
    abstract fun pause(transient: Boolean)

    /**
     * Toggles playback of the currently loaded media
     */
    abstract fun togglePlayPause()

    /**
     * Stops the playback of the current item. This should be used to release
     * any resources that can be re-created
     */
    abstract fun stop()

    /**
     * Seeks to the next available item in the playlist
     */
    abstract fun next()

    /**
     * Seeks to the previous available item in the playlist
     */
    abstract fun previous()

    /**
     * Informs the handler that a seek has started. This should be used to
     * perform functionality such as pausing for the seek, etc. This is *NOT*
     * required to be called before [seek]
     */
    abstract fun startSeek()

    /**
     * Informs the handler to perform the actual seek of the media
     */
    abstract fun seek(positionMillis: Long)

    abstract fun startItemPlayback(positionMillis: Long, startPaused: Boolean)
    abstract fun updateMediaControls()

    /**
     * Informs the handler that we need to verify that the [currentMediaPlayer]
     * is the highest priority one for the current item. This can be used to handle
     * changing media players between local and remote (e.g. Chromecast) by adding and
     * removing a [MediaPlayerApi]
     */
    abstract fun refreshCurrentMediaPlayer()
}