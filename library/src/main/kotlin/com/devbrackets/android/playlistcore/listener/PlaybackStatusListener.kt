package com.devbrackets.android.playlistcore.listener

import com.devbrackets.android.playlistcore.api.PlaylistItem

interface PlaybackStatusListener<in I : PlaylistItem> {

    /**
     * Called when a media item has started playback.
     *
     * @param playlistItem The PlaylistItem that has started playback
     * @param currentPosition The position the playback has started at
     * @param duration The duration of the PlaylistItem
     */
    fun onMediaPlaybackStarted(item: I, currentPosition: Long, duration: Long) {
        //Purposefully left blank
    }

    /**
     * Called when a playlist item in playback has ended
     */
    fun onItemPlaybackEnded(item: I?) {
        // Purposefully left blank
    }

    /**
     * Called when the [PlaylistHandler] has reached the end of the playlist
     */
    fun onPlaylistEnded() {
        //Purposefully left blank
    }
}